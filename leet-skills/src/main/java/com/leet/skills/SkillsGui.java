package com.leet.skills;

import com.leet.core.gui.GuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The /skills interface: a skill-tree screen (stamina at the center, the ring
 * skills around it, the advanced skills in the lower band), a per-skill detail
 * screen, and a confirm screen where leveling spends XP.
 *
 * <p>This is a thin, skills-specific view that builds inventories and routes
 * clicks through the shared {@link GuiManager} (from core) — it owns none of the
 * click-consumption, per-player context, or close/quit cleanup that the manager
 * handles for every GUI plugin. Navigation between screens is just opening the
 * next inventory with a new handler.
 */
public final class SkillsGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Slots of the 3x3 ring around the center skill (slot 22). */
    private static final int[] RING_SLOTS = {12, 13, 14, 21, 23, 30, 31, 32};
    private static final int CENTER = 22;

    // Detail/confirm layout on a 9x6 grid (1-based): effects stack down the
    // center column; the bottom row holds back, the skill icon and the action.
    private static final int EFFECT_COL_START = 4; // x=5, y=1
    private static final int BACK_BOTTOM = 47;      // x=3, y=6
    private static final int SKILL_BOTTOM = 49;     // x=5, y=6
    private static final int ACTION_BOTTOM = 51;    // x=7, y=6
    private static final int TOGGLE_SLOT = 35;      // x=9, y=4 (per-player effect toggle)
    private static final int EXIT_BOTTOM = 49;      // x=5, y=6 (on the tree screen)

    private final SkillsFeature feature;

    public SkillsGui(SkillsFeature feature) {
        this.feature = feature;
    }

    private GuiManager gui() {
        return feature.core().guiManager();
    }

    // --- entry point ---

    public void openTree(Player player) {
        Inventory inv = feature.newInventory(player);
        fill(inv);

        SkillConfig stamina = feature.skill(SkillsFeature.STAMINA);
        int level = feature.currentLevel(player, SkillsFeature.STAMINA);
        int cost = feature.nextCost(player, SkillsFeature.STAMINA);
        inv.setItem(CENTER, skillIcon(stamina, level, cost, true));
        inv.setItem(8, infoPane("Level 10 ring skills unlock the advanced skills "
            + "spread around the tree. Ring skills need " + stamina.name() + " level " + stamina.maxLevel() + "."));
        inv.setItem(EXIT_BOTTOM, gui().action(Material.OAK_DOOR, "<red><bold>\u2715 Exit",
            "<gray>Close the skills menu", "close"));

        setTier(player, inv, feature.ringSkillIds(), RING_SLOTS);
        setAdvancedTier(player, inv);
        addExpIndicator(player, inv);

        gui().open(player, inv, (p, tag) -> {
            if (tag.startsWith("skill:")) {
                openDetail(p, tag.substring("skill:".length()));
            }
            // tag == "action:close" is handled by GuiManager itself.
        });
    }

    private void setTier(Player player, Inventory inv, List<String> ids, int[] slots) {
        for (int i = 0; i < slots.length && i < ids.size(); i++) {
            String id = ids.get(i);
            SkillConfig skill = feature.skill(id);
            if (skill == null) continue;
            if (feature.prerequisiteSatisfied(player, skill)) {
                inv.setItem(slots[i],
                    skillIcon(skill, feature.currentLevel(player, id), feature.nextCost(player, id), true));
            } else {
                inv.setItem(slots[i], lockedPane(skill));
            }
        }
    }

    private void setAdvancedTier(Player player, Inventory inv) {
        for (String id : feature.advancedSkillIds()) {
            int slot = feature.advancedSlot(id);
            SkillConfig skill = feature.skill(id);
            if (slot < 0 || skill == null) continue;
            if (feature.prerequisiteSatisfied(player, skill)) {
                inv.setItem(slot,
                    skillIcon(skill, feature.currentLevel(player, id), feature.nextCost(player, id), true));
            } else {
                inv.setItem(slot, lockedPane(skill));
            }
        }
    }

    private void openDetail(Player player, String skillId) {
        Inventory inv = feature.newInventory(player);
        fill(inv);
        SkillConfig skill = feature.skill(skillId);
        if (skill == null) return;

        int level = feature.currentLevel(player, skillId);
        int cost = feature.nextCost(player, skillId);
        inv.setItem(SKILL_BOTTOM, skillIcon(skill, level, cost, true));
        inv.setItem(BACK_BOTTOM, gui().action(Material.ARROW, "<gray><bold>\u2190 Back",
            "<gray>Return to the skill tree", "back"));

        if (cost >= 0) {
            Material upgradeIcon = feature.hasXp(player, cost)
                ? Material.EXPERIENCE_BOTTLE
                : Material.BARRIER;
            String upgradeName = feature.hasXp(player, cost)
                ? "<green><bold>Level Up"
                : "<red><bold>Level Up";
            String upgradeLore = feature.hasXp(player, cost)
                ? "<green>Cost: " + cost + " XP"
                : "<red>Not enough XP <gray>(need " + cost + ")";
            inv.setItem(ACTION_BOTTOM, gui().action(upgradeIcon, upgradeName, upgradeLore, "upgrade"));
        }

        int slot = EFFECT_COL_START;
        for (SkillConfig.Effect effect : skill.effects()) {
            inv.setItem(slot, effectIcon(skill, effect, level));
            slot += 9;
        }

        renderEffectToggle(player, inv, skillId);
        addExpIndicator(player, inv);

        gui().open(player, inv, (p, tag) -> {
            switch (tag) {
                case "action:back" -> openTree(p);
                case "action:upgrade" -> onUpgradeClicked(p, skillId);
                case "action:toggle" -> {
                    feature.setSkillEnabled(p, skillId, !feature.skillEnabled(p, skillId));
                    openDetail(p, skillId);
                }
                default -> { /* ignore */ }
            }
        });
    }

    private void openConfirm(Player player, String skillId, int cost) {
        Inventory inv = feature.newInventory(player);
        fill(inv);
        SkillConfig skill = feature.skill(skillId);
        if (skill == null) return;

        int level = feature.currentLevel(player, skillId);
        inv.setItem(SKILL_BOTTOM, skillIcon(skill, level, cost, true));
        inv.setItem(BACK_BOTTOM, gui().action(Material.ARROW, "<gray><bold>\u2190 Back",
            "<gray>Go back without leveling", "back"));
        inv.setItem(ACTION_BOTTOM, gui().action(Material.LIME_DYE, "<green><bold>Apply",
            "<green>Spend " + cost + " XP to reach level " + (level + 1), "apply"));

        addExpIndicator(player, inv);

        gui().open(player, inv, (p, tag) -> {
            switch (tag) {
                case "action:back" -> openDetail(p, skillId);
                case "action:apply" -> {
                    feature.levelUp(p, skillId);
                    openDetail(p, skillId);
                }
                default -> { /* ignore */ }
            }
        });
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

    // --- item builders (skill-specific) ---

    private ItemStack skillIcon(SkillConfig skill, int level, int cost, boolean unlocked) {
        var item = gui().action(skill.icon(), "<yellow><bold>" + skill.name()
            + " <gray>[ " + level + "/" + skill.maxLevel() + " ]", "", "skill:" + skill.id());
        // Replace the auto lore with the detailed skill lore.
        var meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        List<SkillConfig.Effect> effects = skill.effects();
        if (effects.isEmpty()) {
            for (String line : skill.lore()) {
                lore.add(MM.deserialize("<gray>" + line));
            }
        } else {
            for (SkillConfig.Effect effect : effects) {
                lore.add(effectLine(skill, effect, level));
            }
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
        item.setItemMeta(meta);
        return item;
    }

    private void renderEffectToggle(Player player, Inventory inv, String skillId) {
        if (!feature.skillHasToggle(skillId)) return;
        if (feature.currentLevel(player, skillId) < 1) return;
        boolean on = feature.skillEnabled(player, skillId);
        inv.setItem(TOGGLE_SLOT, gui().action(Material.LEVER,
            on ? "<green><bold>Effect: On" : "<red><bold>Effect: Off",
            "<gray>Click to toggle this skill's effect on/off.", "toggle"));
    }

    private Component effectLine(SkillConfig skill, SkillConfig.Effect effect, int level) {
        String desc = effect.name() + ":";
        if (effect.desc() != null && !effect.desc().isEmpty()) {
            desc = effect.desc();
        }
        if (effect.perLevel() > 0) {
            double current = skill.valueAt(effect.id(), level);
            return MM.deserialize("<green>" + format(current) + "%<reset> <gray>" + desc);
        }
        if (effect.unlockAt() > 0) {
            if (skill.unlocked(effect.id(), level)) {
                double current = skill.valueAt(effect.id(), level);
                return MM.deserialize("<green>" + format(current) + "%<reset> <gray>" + desc);
            }
            return MM.deserialize("<yellow>Lv " + effect.unlockAt() + ":<reset> <gray>" + desc);
        }
        return MM.deserialize("<gray>" + desc);
    }

    private ItemStack lockedPane(SkillConfig skill) {
        var item = gui().action(Material.EMERALD, "<red><bold>Locked</bold> <gray>\u2022</gray> <yellow>" + skill.name(),
            "", "noop");
        var meta = item.getItemMeta();
        SkillTreeConfig.Prerequisite requirement = feature.requirementFor(skill.id());
        String unlock = requirement.isPresent()
            ? "<gray>Reach <yellow>" + feature.nameOf(requirement.skill()) + " level " + requirement.level() + "<gray>"
            : "<gray>Prerequisite not met";
        meta.lore(List.of(
            MM.deserialize(unlock),
            MM.deserialize("<gray>to unlock this skill.")));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack effectIcon(SkillConfig skill, SkillConfig.Effect effect, int level) {
        var item = gui().action(effect.icon(), "", "", "noop");
        var meta = item.getItemMeta();
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
            value = "<gray>\u2014";
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
        var item = new org.bukkit.inventory.ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gray>" + text));
        item.setItemMeta(meta);
        return item;
    }

    private void addExpIndicator(Player player, Inventory inv) {
        var item = gui().action(Material.EXPERIENCE_BOTTLE, "<yellow><bold>Experience", "", "noop");
        var metaKey = item.getItemMeta();
        metaKey.lore(List.of(
            MM.deserialize("<aqua>" + player.getTotalExperience() + " XP points"),
            MM.deserialize("<gray>Spent to level up skills.")));
        item.setItemMeta(metaKey);
        inv.setItem((feature.rows() - 1) * 9, item);
    }

    private void fill(Inventory inv) {
        var filler = gui().filler(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler.clone());
        }
    }
}
