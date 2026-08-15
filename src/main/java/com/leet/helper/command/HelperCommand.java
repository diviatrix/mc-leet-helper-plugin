package com.leet.helper.command;

import com.leet.helper.HelperPlugin;
import com.leet.helper.feature.AbstractFeature;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class HelperCommand implements CommandExecutor, TabCompleter {

    private final HelperPlugin plugin;

    public HelperCommand(HelperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /leeta <list|toggle|info> [args]"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(sender);
            case "toggle" -> handleToggle(sender, args);
            case "info" -> handleInfo(sender, args);
            default -> sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /leeta <list|toggle|info> [args]"));
        }
        return true;
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("leet.admin")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>No permission."));
            return;
        }
        for (AbstractFeature feature : plugin.featureManager().all()) {
            String status = feature.isEnabled() ? "<green>ON" : "<red>OFF";
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gray>- <white>" + feature.id() + " <gray>[ " + status + " <gray>]"));
        }
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("leet.admin.toggle")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>No permission."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /leeta toggle <feature-id>"));
            return;
        }
        String id = args[1];
        Optional<AbstractFeature> opt = plugin.featureManager().get(id);
        if (opt.isEmpty()) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Unknown feature: " + id));
            return;
        }
        plugin.featureManager().toggle(id);
        boolean nowEnabled = opt.get().isEnabled();
        String status = nowEnabled ? "<green>enabled" : "<red>disabled";
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Feature <white>" + id + " <gray>is now " + status));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("leet.admin")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>No permission."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /leeta info <feature-id>"));
            return;
        }
        String id = args[1];
        Optional<AbstractFeature> opt = plugin.featureManager().get(id);
        if (opt.isEmpty()) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Unknown feature: " + id));
            return;
        }
        AbstractFeature feature = opt.get();
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>ID: <white>" + feature.id()));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Permission: <white>" + feature.permission()));
        String status = feature.isEnabled() ? "<green>enabled" : "<red>disabled";
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Status: " + status));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("list", "toggle", "info"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("toggle") || args[0].equalsIgnoreCase("info"))) {
            List<String> ids = plugin.featureManager().all().stream()
                .map(AbstractFeature::id)
                .toList();
            return filterPrefix(ids, args[1]);
        }
        return Collections.emptyList();
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
