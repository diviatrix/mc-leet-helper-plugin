package com.leet.helper.command;

import com.leet.helper.Core;
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
 * Player-side feature toggles. Each alias maps to a feature ID; the player's
 * preference for each feature is persisted per-player in the SQLite kv_store.
 */
public class LeetCommand implements CommandExecutor, TabCompleter {

    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("dj", "double_jump");
        ALIASES.put("crop", "auto_crop");
        ALIASES.put("tree", "tree_feller");
        ALIASES.put("fall", "fall_damage");
        ALIASES.put("xp", "xp");
        ALIASES.put("skills", "skills");
        ALIASES.put("cook", "cooking");
    }

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
        String featureId = ALIASES.get(args[0].toLowerCase());
        if (featureId == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /leet <list|dj|crop|tree|fall|xp|skills|cook>"));
            return true;
        }
        if (!player.hasPermission(permissionFor(featureId))) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>No permission."));
            return true;
        }
        toggle(player, featureId);
        return true;
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
            String status = on ? "<green>ON" : "<red>OFF";
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gray>- <white>" + displayName(featureId) + " <gray>[ " + status + " <gray>]"));
        }
    }

    private boolean hasAnyPermission(Player player) {
        for (String featureId : ALIASES.values()) {
            if (player.hasPermission(permissionFor(featureId))) return true;
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
                for (String featureId : ALIASES.values()) {
                    if (player.hasPermission(permissionFor(featureId))) {
                        options.add(aliasFor(featureId));
                    }
                }
            }
            return filterPrefix(options, args[0]);
        }
        return Collections.emptyList();
    }

    private static String aliasFor(String featureId) {
        for (Map.Entry<String, String> e : ALIASES.entrySet()) {
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