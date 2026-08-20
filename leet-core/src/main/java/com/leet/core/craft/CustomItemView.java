package com.leet.core.craft;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Read-only view over a custom-item registry, exposed by core so its command
 * layer (/leeta give) can hand out custom items without owning the mutable
 * registry. The crafting plugin registers its {@code LeetItemRegistry} as the
 * implementation of this via Bukkit's ServicesManager.
 */
public interface CustomItemView {

    /** Builds a fresh instance of the item with the given id, or null when unknown. */
    ItemStack create(String id);

    /** Whether an item with the given id is registered. */
    boolean contains(String id);

    /** All registered item ids (for tab-completion / discovery). */
    Collection<String> ids();
}
