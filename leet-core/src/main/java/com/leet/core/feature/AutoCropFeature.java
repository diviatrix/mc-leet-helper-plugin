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

public class AutoCropFeature extends AbstractFeature implements CostedFeature, BlockBreakerFeature {

    private int radius;
    private boolean requireMature;
    private boolean requireHoe;
    private Set<Material> materials;
    private boolean harvesting;

    public AutoCropFeature(CoreApi core, JavaPlugin owner) {
        super(core, owner);
    }

    @Override
    public String featureId() {
        return "auto_crop";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        radius = Math.min(cfg.getInt("feature.radius", 3), 5);
        requireMature = cfg.getBoolean("feature.require-mature", true);
        requireHoe = cfg.getBoolean("feature.require-hoe", false);
        materials = MaterialSets.readSet(owner.getLogger(), cfg.getStringList("feature.materials"), "auto_crop materials");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        // Respect protection plugins: run late so a claim/region cancellation of
        // the original break is already visible, and route each adjacent crop
        // through breakIfAllowed (fires a BlockBreakEvent) so protected blocks
        // inside a claim/region are skipped.
        if (event.isCancelled()) return;
        if (harvesting) return; // guard against the synthetic per-block events below

        Player player = event.getPlayer();
        if (!check(player)) return;

        Block broken = event.getBlock();
        Material type = broken.getType();
        if (!materials.contains(type)) return;
        if (requireMature && !AutoCropUtil.isMature(broken)) return;

        if (requireHoe && !AutoCropUtil.isHoe(player.getInventory().getItemInMainHand())) return;

        if (!chargeUse(player)) return;

        ItemStack tool = player.getInventory().getItemInMainHand();

        harvesting = true;
        try {
            // Square on the horizontal plane, one block high: only the same Y
            // level as the broken crop is harvested (a plant grows one high).
            AutoCropUtil.harvestRadius(this, player, broken, type, radius, requireMature, tool);
        } finally {
            harvesting = false;
        }
    }
}
