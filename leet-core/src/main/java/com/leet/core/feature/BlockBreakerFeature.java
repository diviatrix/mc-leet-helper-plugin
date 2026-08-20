package com.leet.core.feature;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Opt-in role: a feature that breaks multiple blocks and must respect
 * protection plugins (claims/regions) per block by firing a BlockBreakEvent.
 * Multi-block breakers (Tree Feller, Auto Crop) route each block through here.
 */
public interface BlockBreakerFeature {

    /**
     * Breaks {@code block} as the player only if no protection plugin cancels it.
     * Returns true when the block was actually broken.
     */
    default boolean breakIfAllowed(Player player, Block block, ItemStack tool) {
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        block.breakNaturally(tool);
        return true;
    }
}
