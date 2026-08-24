package com.leet.core.command;

import com.leet.core.LeetCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class SpawnCommand implements CommandExecutor, TabCompleter {

    private final LeetCore plugin;

    public SpawnCommand(LeetCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (label.equalsIgnoreCase("setspawn")) {
            handleSetSpawn(player);
        } else {
            handleSpawn(player);
        }
        return true;
    }

    private void handleSetSpawn(Player player) {
        Location loc = player.getLocation();
        plugin.getConfig().set("spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("spawn.x", loc.getX());
        plugin.getConfig().set("spawn.y", loc.getY());
        plugin.getConfig().set("spawn.z", loc.getZ());
        plugin.getConfig().set("spawn.yaw", (double) loc.getYaw());
        plugin.getConfig().set("spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<green>Spawn set to <white>" + loc.getWorld().getName()
                + " <gray>(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")"));
    }

    private void handleSpawn(Player player) {
        String worldName = plugin.getConfig().getString("spawn.world");
        if (worldName == null || Bukkit.getWorld(worldName) == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>No spawn point is set."));
            return;
        }
        Location spawn = new Location(
            Bukkit.getWorld(worldName),
            plugin.getConfig().getDouble("spawn.x"),
            plugin.getConfig().getDouble("spawn.y"),
            plugin.getConfig().getDouble("spawn.z"),
            (float) plugin.getConfig().getDouble("spawn.yaw"),
            (float) plugin.getConfig().getDouble("spawn.pitch"));
        player.teleport(spawn);
        player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Teleported to spawn."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return Collections.emptyList();
    }
}
