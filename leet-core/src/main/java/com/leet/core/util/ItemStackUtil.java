package com.leet.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Small helpers for building display names / lores / PDC tags on items. Reduces
 * the repeated {@code getItemMeta() -> mutate -> setItemMeta()} boilerplate across
 * features and GUIs.
 */
public final class ItemStackUtil {

    private ItemStackUtil() {
    }

    public static ItemStack of(Material material) {
        return new ItemStack(material);
    }

    /** Replaces the item's display name (MiniMessage) in place. */
    public static ItemStack name(ItemStack item, String miniMessage) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessageUtil.deserialize(miniMessage));
        item.setItemMeta(meta);
        return item;
    }

    /** Replaces the item's lore with the given MiniMessage lines. */
    public static ItemStack lore(ItemStack item, String... lines) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = java.util.Arrays.stream(lines)
            .map(MiniMessageUtil::deserialize)
            .toList();
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Sets a string tag in the item's persistent data container, in place. */
    public static ItemStack tag(ItemStack item, NamespacedKey key, String value) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }
}
