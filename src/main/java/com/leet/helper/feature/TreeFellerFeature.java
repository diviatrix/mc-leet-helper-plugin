package com.leet.helper.feature;

import com.leet.helper.Core;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

public class TreeFellerFeature extends AbstractFeature {

    private Set<Material> logs;
    private int maxBlocks;
    private boolean felling;

    public TreeFellerFeature(Core plugin) {
        super(plugin);
    }

    @Override
    public String featureId() {
        return "tree_feller";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        logs = new HashSet<>();
        for (String name : cfg.getStringList("feature.logs")) {
            try {
                logs.add(Material.valueOf(name));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in tree_feller logs: " + name);
            }
        }
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