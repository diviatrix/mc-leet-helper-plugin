package com.leet.helper.feature;

import com.leet.helper.HelperPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoCropFeature extends AbstractFeature {

    private int radius;
    private boolean requireMature;
    private boolean requireHoe;
    private Set<Material> materials;
    private boolean harvesting;

    public AutoCropFeature(HelperPlugin plugin) {
        super(plugin);
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
        materials = new HashSet<>();
        List<String> materialNames = cfg.getStringList("feature.materials");
        for (String name : materialNames) {
            try {
                materials.add(Material.valueOf(name));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in auto_crop materials: " + name);
            }
        }
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
        if (requireMature && !isMature(broken)) return;

        if (requireHoe && !isHoe(player.getInventory().getItemInMainHand())) return;

        ItemStack tool = player.getInventory().getItemInMainHand();

        harvesting = true;
        try {
            // Square on the horizontal plane, one block high: only the same Y
            // level as the broken crop is harvested (a plant grows one high).
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && z == 0) continue;
                    Block nearby = broken.getRelative(x, 0, z);
                    if (nearby.getType() != type) continue;
                    if (requireMature && !isMature(nearby)) continue;
                    breakIfAllowed(player, nearby, tool);
                }
            }
        } finally {
            harvesting = false;
        }
    }

    private boolean isMature(Block block) {
        if (!(block.getBlockData() instanceof Ageable ageable)) return false;
        return ageable.getAge() == ageable.getMaximumAge();
    }

    private boolean isHoe(ItemStack item) {
        if (item == null || !item.getType().name().endsWith("_HOE")) return false;
        return switch (item.getType().name()) {
            case "WOODEN_HOE", "STONE_HOE", "IRON_HOE", "GOLDEN_HOE", "DIAMOND_HOE", "NETHERITE_HOE" -> true;
            default -> false;
        };
    }
}
