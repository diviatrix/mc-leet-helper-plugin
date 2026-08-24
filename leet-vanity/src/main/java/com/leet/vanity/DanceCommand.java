package com.leet.vanity;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DanceCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final VanityFeature feature;
    private final Map<UUID, BukkitTask> active = new ConcurrentHashMap<>();
    private final List<String> dances;

    public DanceCommand(JavaPlugin plugin, VanityFeature feature) {
        this.plugin = plugin;
        this.feature = feature;
        List<String> configured = plugin.getConfig().getStringList("dances").stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();
        this.dances = configured.isEmpty() ? List.of("groove", "bounce", "spin") : configured;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!feature.danceAppliesTo(player)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>No permission."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gray>Dances: <white>" + String.join(", ", dances)));
            return true;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            stop(player);
            player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Dance stopped."));
            return true;
        }
        String dance = args[0].toLowerCase(Locale.ROOT);
        if (!dances.contains(dance)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Unknown dance: " + args[0]));
            return true;
        }
        start(player, dance);
        return true;
    }

    private void start(Player player, String dance) {
        stop(player);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || !feature.danceAppliesTo(player)) {
                    stop(player);
                    return;
                }
                animate(player, dance, tick++);
            }
        }, 0L, 4L);
        active.put(player.getUniqueId(), task);
        player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Dancing: <white>" + dance));
    }

    private void animate(Player player, String dance, int tick) {
        float yaw = player.getLocation().getYaw();
        switch (dance) {
            case "spin" -> player.setRotation(player.getLocation().getYaw() + 24, player.getLocation().getPitch());
            case "bounce" -> {
                if (tick % 2 == 0) player.setVelocity(player.getVelocity().setY(0.35));
                player.getWorld().spawnParticle(Particle.NOTE, player.getLocation().add(0, 1, 0), 2);
            }
            default -> {
                player.setSneaking(tick % 2 == 0);
                player.setRotation(yaw + (tick % 2 == 0 ? 18 : -18), player.getLocation().getPitch());
                player.getWorld().spawnParticle(Particle.NOTE, player.getLocation().add(0, 1, 0), 1);
                if (tick % 4 == 0) player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.3f);
            }
        }
    }

    private void stop(Player player) {
        BukkitTask task = active.remove(player.getUniqueId());
        if (task != null) task.cancel();
        player.setSneaking(false);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        List<String> values = new ArrayList<>(dances);
        values.add("list");
        values.add("stop");
        return values.stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
