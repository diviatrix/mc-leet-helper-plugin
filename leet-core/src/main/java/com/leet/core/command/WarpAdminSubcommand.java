package com.leet.core.command;

import com.leet.core.LeetCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class WarpAdminSubcommand implements AdminSubcommand {

    private static final List<String> ACTIONS = List.of("add", "del");

    private final LeetCore plugin;
    private final WarpCommand warpCommand;

    public WarpAdminSubcommand(LeetCore plugin, WarpCommand warpCommand) {
        this.plugin = plugin;
        this.warpCommand = warpCommand;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /leeta warp add|del <name>"));
            return;
        }
        String action = args[0].toLowerCase();
        String name = WarpCommand.normalize(args[1]);
        if (name.isBlank()) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Invalid warp name: " + args[1]));
            return;
        }
        switch (action) {
            case "add" -> add(sender, name);
            case "del" -> del(sender, name);
            default -> sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /leeta warp add|del <name>"));
        }
    }

    private void add(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return;
        }
        Location loc = player.getLocation();
        String path = "warps." + name;
        plugin.getConfig().set(path + ".world", loc.getWorld().getName());
        plugin.getConfig().set(path + ".x", loc.getX());
        plugin.getConfig().set(path + ".y", loc.getY());
        plugin.getConfig().set(path + ".z", loc.getZ());
        plugin.getConfig().set(path + ".yaw", (double) loc.getYaw());
        plugin.getConfig().set(path + ".pitch", (double) loc.getPitch());
        plugin.saveConfig();
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Warp <white>" + name + " <green>saved."));
    }

    private void del(CommandSender sender, String name) {
        if (plugin.getConfig().getConfigurationSection("warps." + name) == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Unknown warp: " + name));
            return;
        }
        plugin.getConfig().set("warps." + name, null);
        plugin.saveConfig();
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Warp <white>" + name + " <green>deleted."));
    }

    @Override
    public List<String> tab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return ACTIONS;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("del")) {
            return warpCommand.warpNames();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return new ArrayList<>(warpCommand.warpNames());
        }
        return List.of();
    }
}
