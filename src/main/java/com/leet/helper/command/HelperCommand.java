package com.leet.helper.command;

import com.leet.helper.Core;
import com.leet.helper.feature.AbstractFeature;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class HelperCommand implements CommandExecutor, TabCompleter {

    private final Core plugin;

    public HelperCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /leeta <list|toggle|info|give> [args]"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(sender);
            case "toggle" -> handleToggle(sender, args);
            case "info" -> handleInfo(sender, args);
            case "give" -> handleGive(sender, args);
            default -> sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /leeta <list|toggle|info|give> [args]"));
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

    private void handleInfo(CommandSender sender, String[] args) {        if (!sender.hasPermission("leet.admin")) {
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

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("leet.admin")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>No permission."));
            return;
        }
        // Usage: /leeta give <itemId> [amount] [player]
        if (args.length < 2) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /leeta give <item-id> [amount] [player]"));
            return;
        }
        String itemId = args[1].toLowerCase();
        ItemStack stack = plugin.itemRegistry().create(itemId);
        if (stack == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Unknown custom item: " + itemId));
            return;
        }
        int amount = 1;
        try {
            if (args.length >= 3) amount = Math.max(1, Math.min(Integer.parseInt(args[2]), 64));
        } catch (NumberFormatException e) {
            // ignore and keep amount 1
        }
        stack.setAmount(amount);

        Player target = null;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not found: " + args[3]));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Console needs a player: /leeta give <item-id> <amount> <player>"));
            return;
        }

        target.getInventory().addItem(stack);
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<green>Gave <white>" + amount + "x " + itemId + " <green>to <white>" + target.getName()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("list", "toggle", "info", "give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filterPrefix(new ArrayList<>(plugin.itemRegistry().ids()), args[1]);
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
