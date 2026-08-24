package com.leet.core.command;

import com.leet.core.LeetCore;
import com.leet.core.storage.StorageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class HomeCommand implements CommandExecutor {

    private static final String FEATURE_ID = "home";
    private static final String LOCATION_KEY = "location";
    private final LeetCore plugin;

    public HomeCommand(LeetCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (label.equalsIgnoreCase("sethome")) {
            setHome(player);
        } else {
            goHome(player);
        }
        return true;
    }

    private void setHome(Player player) {
        Location location = player.getLocation();
        StorageManager storage = plugin.storageManager();
        storage.setPersistent(FEATURE_ID, LOCATION_KEY, player.getUniqueId(), serialize(location));
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<green>Home set to <white>" + location.getWorld().getName()
                + " <gray>(" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")"));
    }

    private void goHome(Player player) {
        String value = plugin.storageManager().getPersistent(
            FEATURE_ID, LOCATION_KEY, player.getUniqueId());
        Location location = value == null ? null : deserialize(value);
        if (location == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>You have not set a home."));
            return;
        }
        player.teleport(location);
        player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Teleported home."));
    }

    private String serialize(Location location) {
        return String.join("|",
            location.getWorld().getName(),
            Double.toString(location.getX()),
            Double.toString(location.getY()),
            Double.toString(location.getZ()),
            Float.toString(location.getYaw()),
            Float.toString(location.getPitch()));
    }

    private Location deserialize(String value) {
        try {
            String[] parts = value.split("\\|", -1);
            if (parts.length != 6) return null;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            return new Location(world,
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
