package com.leet.core.feature;

import com.leet.core.CoreApi;
import com.leet.core.util.MaterialSets;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public class TreeFellerFeature extends AbstractFeature implements CostedFeature, BlockBreakerFeature {

    private Set<Material> logs;
    private int maxBlocks;
    private boolean felling;

    public TreeFellerFeature(CoreApi core, JavaPlugin owner) {
        super(core, owner);
    }

    @Override
    public String featureId() {
        return "tree_feller";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        logs = MaterialSets.readSet(owner.getLogger(), cfg.getStringList("feature.logs"), "tree_feller logs");
        maxBlocks = cfg.getInt("feature.max-blocks", 100);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        // Respect protection plugins: run late (MONITOR) so any claim/region
        // cancellation is already visible, and bail if the original break was
        // blocked. Each connected log is then broken through breakIfAllowed,
        // which fires a BlockBreakEvent so claims/regions apply per block too.
        if (event.isCancelled()) return;
        if (felling) return; // guard against the synthetic per-block events below

        Player player = event.getPlayer();
        if (!check(player)) return;

        Block broken = event.getBlock();
        if (!logs.contains(broken.getType())) return;

        if (!chargeUse(player)) return;

        ItemStack tool = player.getInventory().getItemInMainHand();

        // Fell the whole connected log cluster (shared helper). The origin
        // block is excluded (already handled by the vanilla break) so it is not
        // re-broken / re-dropped.
        felling = true;
        try {
            TreeFellerUtil.fell(this, player, broken, logs, maxBlocks, tool);
        } finally {
            felling = false;
        }
    }
}