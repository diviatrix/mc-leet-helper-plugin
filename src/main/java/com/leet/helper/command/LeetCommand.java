package com.leet.helper.command;

import com.leet.helper.Core;
import com.leet.helper.feature.AbstractFeature;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player-side feature control. Each alias maps to a feature ID. Toggleable
 * features (`ALIASES`) are a personal off-switch, persisted per-player in the
 * SQLite kv_store. Non-toggleable features (`INFO_ALIASES`) instead show info
 * (e.g. cooking: recipes are available whenever the feature is enabled, so
 * there is nothing to toggle).
 */
public class LeetCommand implements CommandExecutor, TabCompleter {

    /** Toggleable features: `/leet <alias>` flips the player's per-feature off-switch. */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("dj", "double_jump");
        ALIASES.put("crop", "auto_crop");
        ALIASES.put("tree", "tree_feller");
        ALIASES.put("fall", "fall_damage");
        ALIASES.put("xp", "xp");
        ALIASES.put("skills", "skills");
    }

    /** Non-toggleable features: `/leet <alias>` shows info instead of toggling. */
    private static final Map<String, String> INFO_ALIASES = Map.of(
        "cook", "cooking"
    );

    private final Core plugin;

    public LeetCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!hasAnyPermission(player)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>No permission."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            showStatus(player);
            return true;
        }
        String alias = args[0].toLowerCase();
        if (alias.equals("debugcmd")) {
            showHeldCmd(player);
            return true;
        }
        String infoFeatureId = INFO_ALIASES.get(alias);
        if (infoFeatureId != null) {
            showInfo(player, infoFeatureId);
            return true;
        }
        String featureId = ALIASES.get(alias);
        if (featureId == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /leet <list|dj|crop|tree|fall|xp|skills|cook|debugcmd>"));
            return true;
        }
        if (!player.hasPermission(permissionFor(featureId))) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>No permission."));
            return true;
        }
        toggle(player, featureId);
        return true;
    }

    /**
     * Debug: prints the held item's {@code minecraft:custom_model_data} (and what the cooking pack
     * expects) so we can confirm the dish icon routing value is actually present on the item.
     */
    private void showHeldCmd(Player player) {
        var stack = player.getInventory().getItemInMainHand();
        if (stack.getType().isAir()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Hold a cooking dish/item in your main hand."));
            return;
        }
        var meta = stack.getItemMeta();
        int legacy = meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : 0;
        List<Float> floats = meta != null && meta.hasCustomModelDataComponent()
            ? meta.getCustomModelDataComponent().getFloats() : List.of();

        StringBuilder sb = new StringBuilder();
        sb.append("<gray>Item: <white>").append(stack.getType()).append("\n");
        sb.append("<gray>custom_model_data floats: <white>");
        if (floats.isEmpty()) sb.append("(none)");
        else for (Float f : floats) sb.append(f).append(" ");
        sb.append("\n<gray>getCustomModelData() (int): <white>").append(legacy);
        sb.append("\n<gray>pack expects for this base: <white>");
        var expected = new java.util.TreeMap<Integer, String>();
        if (plugin.featureManager().get("cooking").orElse(null) instanceof com.leet.helper.feature.CookingFeature c) {
            for (var id : c.itemIdsForBaseMaterial(stack.getType())) {
                expected.put(com.leet.helper.resource.ResourcePackService.customModelData(id), id);
            }
        }
        if (expected.isEmpty()) sb.append("(no cooking dish uses base ").append(stack.getType()).append(")");
        else for (var e : expected.entrySet()) sb.append(e.getValue()).append("=").append(e.getKey()).append("  ");
        player.sendMessage(MiniMessage.miniMessage().deserialize(sb.toString()));
    }

    private void toggle(Player player, String featureId) {
        UUID uuid = player.getUniqueId();
        Boolean current = plugin.storageManager().getUserToggle(featureId, uuid);
        boolean next = !(current == null || current);
        plugin.storageManager().setUserToggle(featureId, uuid, next);

        String color = next ? "green" : "red";
        String state = next ? "enabled" : "disabled";
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>Feature <white>" + displayName(featureId) + " <gray>is now <" + color + ">" + state + "<reset>."));
    }

    private void showStatus(Player player) {
        UUID uuid = player.getUniqueId();
        for (String featureId : ALIASES.values()) {
            if (!player.hasPermission(permissionFor(featureId))) continue;
            boolean on = plugin.storageManager().getUserToggle(featureId, uuid) != Boolean.FALSE;
            sendStatusLine(player, featureId, on);
        }
        // Info-only features need no permission; they're ON whenever the feature is enabled.
        for (String featureId : INFO_ALIASES.values()) {
            sendStatusLine(player, featureId, featureActive(player, featureId));
        }
    }

    private void sendStatusLine(Player player, String featureId, boolean on) {
        String status = on ? "<green>ON" : "<red>OFF";
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>- <white>" + displayName(featureId) + " <gray>[ " + status + " <gray>]"));
    }

    /** Shows feature info for a non-toggleable feature (e.g. cooking). */
    private void showInfo(Player player, String featureId) {
        boolean on = featureActive(player, featureId);
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>Feature <white>" + displayName(featureId)
            + " <gray>is <" + (on ? "green>enabled" : "red>disabled") + "<reset>. "
            + infoNote(featureId)));
    }

    private static String infoNote(String featureId) {
        return switch (featureId) {
            case "cooking" -> "Custom food recipes \u2014 available whenever the feature is enabled (no permission).";
            default -> "";
        };
    }

    /** Whether the feature currently applies to the player (its own gating rules). */
    private boolean featureActive(Player player, String featureId) {
        return plugin.featureManager().get(featureId)
            .map(f -> f.appliesTo(player)).orElse(false);
    }

    /** True when the player can use /leet at all: any permission, or an open info feature. */
    private boolean hasAnyPermission(Player player) {
        for (String featureId : ALIASES.values()) {
            if (player.hasPermission(permissionFor(featureId))) return true;
        }
        for (String featureId : INFO_ALIASES.values()) {
            AbstractFeature feature = plugin.featureManager().get(featureId).orElse(null);
            if (feature != null && feature.isEnabled()) return true;
        }
        return false;
    }

    private static String permissionFor(String featureId) {
        return "leet.feat." + featureId;
    }

    private static String displayName(String featureId) {
        return switch (featureId) {
            case "auto_crop" -> "Auto Crop";
            case "tree_feller" -> "Tree Feller";
            case "fall_damage" -> "Fall Damage";
            case "xp" -> "XP";
            case "skills" -> "Skills";
            case "cooking" -> "Cooking";
            default -> "Double Jump";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender instanceof Player player && hasAnyPermission(player)) {
                options.add("list");
                options.add("debugcmd");
                for (String featureId : ALIASES.values()) {
                    if (player.hasPermission(permissionFor(featureId))) {
                        options.add(aliasFor(featureId));
                    }
                }
                // Info-only features are always available (no permission required).
                options.addAll(INFO_ALIASES.keySet());
            }
            return filterPrefix(options, args[0]);
        }
        return Collections.emptyList();
    }

    private static String aliasFor(String featureId) {
        for (Map.Entry<String, String> e : ALIASES.entrySet()) {
            if (e.getValue().equals(featureId)) return e.getKey();
        }
        for (Map.Entry<String, String> e : INFO_ALIASES.entrySet()) {
            if (e.getValue().equals(featureId)) return e.getKey();
        }
        return featureId;
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(prefix.toLowerCase())) {
                result.add(option);
            }
        }
        return result;
    }
}