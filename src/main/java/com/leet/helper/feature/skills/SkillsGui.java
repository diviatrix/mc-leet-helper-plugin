package com.leet.helper.feature.skills;

import com.leet.helper.Core;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The /skills inventory interface: a skill-tree screen (stamina at the center,
 * the eight gather/combat skills around it once stamina hits its max level),
 * a per-skill detail screen, and a confirm screen where leveling spends XP.
 *
 * This class only builds inventories and translates clicks; the actual gating,
 * XP spending and passive effects live in {@link SkillsFeature}.
 */
public final class SkillsGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Slots of the 3x3 ring around the center skill (slot 22). */
    private static final int[] RING_SLOTS = {12, 13, 14, 21, 23, 30, 31, 32};
    private static final int CENTER = 22;

    // Detail/confirm layout on a 9x6 grid (1-based): effects stack down the
    // center column (x=5, y=1..5 -> slots 4,13,22,31,40); the bottom row holds
    // back (x=3), the skill icon (x=5) and the action button (x=7).
    private static final int EFFECT_COL_START = 4; // x=5, y=1
    private static final int BACK_BOTTOM = 47;      // x=3, y=6
    private static final int SKILL_BOTTOM = 49;     // x=5, y=6
    private static final int ACTION_BOTTOM = 51;    // x=7, y=6
    private static final int EXIT_BOTTOM = 49;      // x=5, y=6 (on the tree screen, listed below the center skill)

    private final Core plugin;
    private final SkillsFeature feature;
    private final NamespacedKey tagKey;

    private enum Screen { TREE, DETAIL, CONFIRM }

    private static final class Context {
        Inventory inventory;
        Screen screen;
        String skillId;
        int cost;
    }

    private final Map<UUID, Context> contexts = new HashMap<>();

    public SkillsGui(Core plugin, SkillsFeature feature) {
        this.plugin = plugin;
        this.feature = feature;
        this.tagKey = new NamespacedKey(plugin, "sk");
    }

    // --- entry points ---

    public void openTree(Player player) {
        Inventory inv = feature.newInventory(player);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");

        SkillConfig stamina = feature.skill(SkillsFeature.STAMINA);
        int basicLevel = feature.currentLevel(player, SkillsFeature.STAMINA);
        int basicCost = feature.nextCost(player, SkillsFeature.STAMINA);
        inv.setItem(CENTER, skillIcon(stamina, basicLevel, basicCost, true));
        inv.setItem(8, infoPane("Unlock the surrounding skills by reaching "
            + stamina.name() + " level " + stamina.maxLevel()));
        inv.setItem(EXIT_BOTTOM, actionIcon(Material.OAK_DOOR, "<red><bold>\u2715 Exit",
            "<gray>Close the skills menu", "action:close"));

        boolean ringUnlocked = basicLevel >= stamina.maxLevel();
        for (int i = 0; i < RING_SLOTS.length; i++) {
            String id = feature.ringSkillIds().get(i);
            SkillConfig skill = feature.skill(id);
            if (skill == null) continue;
            if (ringUnlocked) {
                inv.setItem(RING_SLOTS[i],
                    skillIcon(skill, feature.currentLevel(player, id), feature.nextCost(player, id), true));
            } else {
                inv.setItem(RING_SLOTS[i], lockedPane(skill, stamina));
            }
        }

        setContext(player.getUniqueId(), inv, Screen.TREE, null, 0);
        player.openInventory(inv);
    }

    private void openDetail(Player player, String skillId) {
        Inventory inv = feature.newInventory(player);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");
        SkillConfig skill = feature.skill(skillId);
        if (skill == null) return;

        int level = feature.currentLevel(player, skillId);
        int cost = feature.nextCost(player, skillId);
        inv.setItem(SKILL_BOTTOM, skillIcon(skill, level, cost, true));
        inv.setItem(BACK_BOTTOM, actionIcon(Material.ARROW, "<gray><bold>\u2190 Back",
            "<gray>Return to the skill tree", "action:back"));

        // Level-up button: a potion when the next level is affordable (or already
        // maxed), or an X icon when the player doesn't have enough XP.
        Material upgradeIcon;
        String upgradeName;
        String upgradeLore;
        if (cost < 0) {
            upgradeIcon = Material.EXPERIENCE_BOTTLE;
            upgradeName = "<green><bold>Level Up";
            upgradeLore = "<gray>Already max level";
        } else if (feature.hasXp(player, cost)) {
            upgradeIcon = Material.EXPERIENCE_BOTTLE;
            upgradeName = "<green><bold>Level Up";
            upgradeLore = "<green>Cost: " + cost + " XP";
        } else {
            upgradeIcon = Material.BARRIER;
            upgradeName = "<red><bold>Level Up";
            upgradeLore = "<red>Not enough XP <gray>(need " + cost + ")";
        }
        inv.setItem(ACTION_BOTTOM, actionIcon(upgradeIcon, upgradeName, upgradeLore, "action:upgrade"));

        // Every effect as its own icon with the current modifier at this level,
        // stacked down the center column.
        int slot = EFFECT_COL_START;
        for (SkillConfig.Effect effect : skill.effects()) {
            inv.setItem(slot, effectIcon(skill, effect, level));
            slot += 9; // move one row down on the same column
        }

        setContext(player.getUniqueId(), inv, Screen.DETAIL, skillId, 0);
        player.openInventory(inv);
    }

    private void openConfirm(Player player, String skillId, int cost) {
        Inventory inv = feature.newInventory(player);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");
        SkillConfig skill = feature.skill(skillId);
        if (skill == null) return;

        int level = feature.currentLevel(player, skillId);
        inv.setItem(SKILL_BOTTOM, skillIcon(skill, level, cost, true));
        inv.setItem(BACK_BOTTOM, actionIcon(Material.ARROW, "<gray><bold>\u2190 Back",
            "<gray>Go back without leveling", "action:back"));
        inv.setItem(ACTION_BOTTOM, actionIcon(Material.LIME_DYE, "<green><bold>Apply",
            "<green>Spend " + cost + " XP to reach level " + (level + 1),
            "action:apply"));

        setContext(player.getUniqueId(), inv, Screen.CONFIRM, skillId, cost);
        player.openInventory(inv);
    }

    // --- click handling ---

    /**
     * Returns true when the click was inside one of our skill inventories.
     * Only clicks in a skills menu are consumed (blocked); clicks on any other
     * inventory (the player's own, chests, ...) are left alone so normal
     * inventory interaction keeps working.
     */
    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;

        Context ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return true; // not one of our menus: don't interfere

        event.setCancelled(true); // it's a skills menu: block every click in it

        // Clicks on the player's own (bottom) inventory while a skills menu is
        // open are swallowed too, so no item can be moved around.
        if (!event.getView().getTopInventory().equals(ctx.inventory)) return true;

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return true;
        String tag = tagOf(current);
        if (tag == null) return true;

        switch (ctx.screen) {
            case TREE -> {
                if (tag.startsWith("skill:")) {
                    openDetail(player, tag.substring("skill:".length()));
                } else if (tag.equals("action:close")) {
                    player.closeInventory();
                }
            }
            case DETAIL -> {
                if (tag.equals("action:back")) {
                    openTree(player);
                } else if (tag.equals("action:upgrade")) {
                    onUpgradeClicked(player, ctx.skillId);
                }
            }
            case CONFIRM -> {
                if (tag.equals("action:back")) {
                    openDetail(player, ctx.skillId);
                } else if (tag.equals("action:apply")) {
                    feature.levelUp(player, ctx.skillId);
                    openDetail(player, ctx.skillId);
                }
            }
        }
        return true;
    }

    private void onUpgradeClicked(Player player, String skillId) {
        int cost = feature.nextCost(player, skillId);
        if (cost < 0) {
            feature.sendMessage(player, "max-level", "<skill>", feature.nameOf(skillId));
            return;
        }
        if (!feature.hasXp(player, cost)) {
            feature.sendMessage(player, "insufficient-xp",
                "<cost>", String.valueOf(cost), "<needed>", String.valueOf(cost));
            return;
        }
        openConfirm(player, skillId, cost);
    }

    /**
     * Called when a view closes. The context is only cleared if the inventory
     * being closed is the one this player has open in the skills GUI. Screen
     * switches open a new inventory, which fires a close for the *previous* one
     * first — clearing the context there would drop the newly-set context and
     * make the buttons on the next screen do nothing.
     */
    public void onClose(Player player, Inventory closed) {
        Context ctx = contexts.get(player.getUniqueId());
        if (ctx != null && ctx.inventory.equals(closed)) {
            contexts.remove(player.getUniqueId());
        }
    }

    /** Force-drops the player's GUI context (e.g. on quit) so clicks never get swallowed afterwards. */
    public void playerLeft(Player player) {
        contexts.remove(player.getUniqueId());
    }

    // --- item builders ---

    private ItemStack skillIcon(SkillConfig skill, int level, int cost, boolean unlocked) {
        ItemStack item = new ItemStack(skill.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<yellow><bold>" + skill.name()
            + " <gray>[ " + level + "/" + skill.maxLevel() + " ]"));

        List<Component> lore = new ArrayList<>();
        for (String line : skill.lore()) {
            lore.add(MM.deserialize("<gray>" + line));
        }
        if (!unlocked) {
            lore.add(MM.deserialize("<red>Locked"));
        } else {
            lore.add(Component.empty());
            lore.add(MM.deserialize(cost < 0
                ? "<green>Max level reached"
                : "<aqua>Level up: <yellow>" + cost + " XP"));
        }
        meta.lore(lore);
        tag(meta, "skill:" + skill.id());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack lockedPane(SkillConfig skill, SkillConfig stamina) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<red><bold>Locked</bold> <gray>•</gray> <yellow>" + skill.name()));
        meta.lore(List.of(
            MM.deserialize("<gray>Reach <yellow>" + stamina.name() + " level " + stamina.maxLevel() + "<gray>"),
            MM.deserialize("<gray>to unlock this skill.")));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * A single passive effect rendered as its own icon: the effect name and its
     * current modifier at the player's level (numeric effects show the value,
     * level-gated unlocks show when they become active).
     */
    private ItemStack effectIcon(SkillConfig skill, SkillConfig.Effect effect, int level) {
        ItemStack item = new ItemStack(effect.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gold>" + effect.name()));

        String value;
        if (effect.perLevel() > 0) {
            double current = skill.valueAt(effect.id(), level);
            value = "<green>" + format(current) + "%<reset>  <gray>(+" + format(effect.perLevel()) + "%/lvl)";
        } else if (effect.unlockAt() > 0) {
            boolean active = skill.unlocked(effect.id(), level);
            value = active
                ? "<green>Active (level " + effect.unlockAt() + ")"
                : "<yellow>Unlocks at level " + effect.unlockAt();
        } else {
            value = "<gray>—";
        }
        meta.lore(List.of(MM.deserialize(value)));
        item.setItemMeta(meta);
        return item;
    }

    private static String format(double value) {
        if (Math.abs(value - Math.round(value)) < 1e-9) {
            return String.valueOf((long) Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private ItemStack infoPane(String text) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gray>" + text));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack actionIcon(Material material, String name, String lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        meta.lore(List.of(MM.deserialize(lore)));
        tag(meta, action);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inv, Material material, String name) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(MM.deserialize(name));
        filler.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler.clone());
        }
    }

    private void tag(ItemMeta meta, String value) {
        meta.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, value);
    }

    private String tagOf(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
    }

    private void setContext(UUID uuid, Inventory inv, Screen screen, String skillId, int cost) {
        Context ctx = new Context();
        ctx.inventory = inv;
        ctx.screen = screen;
        ctx.skillId = skillId;
        ctx.cost = cost;
        contexts.put(uuid, ctx);
    }
}