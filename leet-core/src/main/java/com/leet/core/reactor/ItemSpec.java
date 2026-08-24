package com.leet.core.reactor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.leet.core.craft.CustomItemView;

import java.util.Locale;

/**
 * Item references shared by definitions, signs, kits and quests across plugins.
 *
 * <p>Format: {@code material:STONE_SWORD} (vanilla), {@code item:<custom-id>}
 * (custom item from the crafting plugin's registry), or a bare
 * {@code STONE_SWORD} treated as a material.
 */
public final class ItemSpec {

    private ItemSpec() {
    }

    private static CustomItemView items() {
        var rsp = Bukkit.getServicesManager().getRegistration(CustomItemView.class);
        return rsp == null ? null : rsp.getProvider();
    }

    /** Builds a reference stack, or null when the spec cannot be resolved. */
    public static ItemStack build(String spec, int amount) {
        if (spec == null || spec.isBlank()) return null;
        String s = spec.trim();
        ItemStack stack = null;
        if (s.toLowerCase(Locale.ROOT).startsWith("material:")) {
            stack = vanilla(s.substring(9));
        } else if (s.toLowerCase(Locale.ROOT).startsWith("item:")) {
            CustomItemView view = items();
            if (view != null) {
                stack = view.create(s.substring(5).toLowerCase(Locale.ROOT));
            }
        } else {
            stack = vanilla(s);
        }
        if (stack == null) return null;
        stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
        return stack;
    }

    private static ItemStack vanilla(String name) {
        try {
            return new ItemStack(Material.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A short display name for the spec (used in messages). */
    public static String describe(String spec) {
        return spec == null ? "?" : spec.replace("material:", "").replace("item:", "");
    }

    /** Total count of stacks similar to the reference in the inventory. */
    public static int count(PlayerInventory inv, ItemStack reference) {
        int total = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.isSimilar(reference)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** Removes up to {@code amount} items similar to the reference; returns the removed count. */
    public static int remove(PlayerInventory inv, ItemStack reference, int amount) {
        int left = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.isSimilar(reference)) continue;
            int take = Math.min(left, item.getAmount());
            if (take >= item.getAmount()) {
                inv.setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - take);
                inv.setItem(i, item);
            }
            left -= take;
        }
        return amount - left;
    }

    /** Adds items; anything that does not fit drops at the player's feet. */
    public static void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
