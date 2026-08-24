package com.leet.interaction.command;

import com.leet.core.command.AdminSubcommand;
import com.leet.interaction.LeetInteraction;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /leeta bindings — lists every interaction binding: NPCs (entities carrying
 * the interaction-id tag), blocks (the persisted block-index) and remote
 * chests. Definitions with no binding, or bindings whose definition was
 * deleted, are flagged so admins can clean up.
 */
public final class BindingsSubcommand implements AdminSubcommand {

    private static final UUID ZERO = new UUID(0, 0);

    private final LeetInteraction plugin;

    public BindingsSubcommand(LeetInteraction plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("leet.admin")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>No permission."));
            return;
        }
        var mm = MiniMessage.miniMessage();

        NamespacedKey key = new NamespacedKey(plugin, "interaction-id");
        List<String> lines = new ArrayList<>();
        int npcs = 0;
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String id = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (id == null) continue;
                npcs++;
                lines.add("<gray>NPC <white>" + entity.getType().name().toLowerCase()
                    + " <gray>at <white>" + fmt(entity.getWorld().getName(), entity.getLocation().getBlockX(),
                        entity.getLocation().getBlockY(), entity.getLocation().getBlockZ())
                    + " <gray>-> <white>" + id + flag(id));
            }
        }

        String index = plugin.storage().getPersistent("interaction", "block-index", ZERO);
        int blocks = 0;
        if (index != null && !index.isBlank()) {
            for (String entry : index.split(";")) {
                String[] parts = entry.split("=", 2);
                if (parts.length != 2) continue;
                blocks++;
                String blockKey = parts[0].substring("block:".length());
                lines.add("<gray>Block <white>" + blockKey.replace(":", ", ")
                    + " <gray>-> <white>" + parts[1] + flag(parts[1]));
            }
        }

        int chests = 0;
        for (Map.Entry<String, org.bukkit.Location> e : plugin.chests().entries().entrySet()) {
            chests++;
            org.bukkit.Location loc = e.getValue();
            lines.add("<gray>Chest <white>#" + e.getKey() + " <gray>at <white>"
                + fmt(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        }

        sender.sendMessage(mm.deserialize("<yellow>Bindings: <white>" + npcs + "</white> npc(s), <white>"
            + blocks + "</white> block(s), <white>" + chests + "</white> chest(s)"));
        if (lines.isEmpty()) {
            sender.sendMessage(mm.deserialize("<gray>Nothing is bound. Use /leeta bind <definition-id>."));
            return;
        }
        for (String line : lines) {
            sender.sendMessage(mm.deserialize(line));
        }
    }

    private String flag(String definitionId) {
        return plugin.definitions().get(definitionId) == null
            ? " <red>(missing definition!)" : "";
    }

    private static String fmt(String world, int x, int y, int z) {
        return world + "," + x + "," + y + "," + z;
    }
}
