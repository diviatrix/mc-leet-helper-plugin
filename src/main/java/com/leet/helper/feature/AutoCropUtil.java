package com.leet.helper.feature;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Shared auto-harvest logic, used by both {@code AutoCropFeature} and the
 * farmer skill (SkillsFeature). Collected here so the two don't drift.
 */
public final class AutoCropUtil {

    private AutoCropUtil() {
    }

    /**
     * Harvests every mature crop of the same type as {@code origin} within the
     * square of the given radius on the horizontal plane, one block high. Each
     * crop is broken via {@link AbstractFeature#breakIfAllowed} so protection
     * plugins are consulted per block. Returns how many crops were broken.
     */
    public static int harvestRadius(AbstractFeature feature, Player player, Block origin,
                                    Material type, int radius, boolean requireMature, ItemStack tool) {
        int harvested = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x == 0 && z == 0) continue;
                Block nearby = origin.getRelative(x, 0, z);
                if (nearby.getType() != type) continue;
                if (requireMature && !isMature(nearby)) continue;
                if (feature.breakIfAllowed(player, nearby, tool)) {
                    harvested++;
                }
            }
        }
        return harvested;
    }

    public static boolean isMature(Block block) {
        if (!(block.getBlockData() instanceof Ageable ageable)) return false;
        return ageable.getAge() == ageable.getMaximumAge();
    }

    public static boolean isHoe(ItemStack item) {
        if (item == null || !item.getType().name().endsWith("_HOE")) return false;
        return switch (item.getType().name()) {
            case "WOODEN_HOE", "STONE_HOE", "IRON_HOE", "GOLDEN_HOE", "DIAMOND_HOE", "NETHERITE_HOE" -> true;
            default -> false;
        };
    }
}