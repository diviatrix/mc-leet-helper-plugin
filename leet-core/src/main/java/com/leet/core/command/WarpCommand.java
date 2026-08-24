package com.leet.core.command;

import com.leet.core.LeetCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WarpCommand implements CommandExecutor, TabCompleter {

    private final LeetCore plugin;

    public WarpCommand(LeetCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /warp <name>"));
            return true;
        }
        Location warp = getWarp(args[0]);
        if (warp == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Unknown warp: " + args[0]));
            return true;
        }
        player.teleport(warp);
        player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Warped to <white>" + args[0].toLowerCase()));
        return true;
    }

    Location getWarp(String name) {
        String path = "warps." + normalize(name);
        String worldName = plugin.getConfig().getString(path + ".world");
        if (worldName == null || Bukkit.getWorld(worldName) == null) return null;
        return new Location(
            Bukkit.getWorld(worldName),
            plugin.getConfig().getDouble(path + ".x"),
            plugin.getConfig().getDouble(path + ".y"),
            plugin.getConfig().getDouble(path + ".z"),
            (float) plugin.getConfig().getDouble(path + ".yaw"),
            (float) plugin.getConfig().getDouble(path + ".pitch"));
    }

    List<String> warpNames() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("warps");
        if (section == null) return List.of();
        return new ArrayList<>(section.getKeys(false));
    }

    static String normalize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_-]", "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return CommandUtil.filterPrefix(warpNames(), args[0]);
        }
        return Collections.emptyList();
    }
}
