package com.leet.interaction.command;

import com.leet.core.command.AdminSubcommand;
import com.leet.interaction.LeetInteraction;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /leeta bind <definition-id> — binds the definition to the entity (NPC) or
 * block the sender is looking at. /leeta unbind clears the binding. Bindings on
 * entities live in the entity's PDC; block bindings in the plugin's SQLite.
 */
public final class BindSubcommand implements AdminSubcommand {

    private static final UUID ZERO = new UUID(0, 0);
    private static final int REACH = 6;

    private final LeetInteraction plugin;
    private final boolean bind;

    public BindSubcommand(LeetInteraction plugin, boolean bind) {
        this.plugin = plugin;
        this.bind = bind;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be used by players."));
            return;
        }
        if (!player.hasPermission("leet.admin")) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>No permission."));
            return;
        }
        if (bind && args.length < 1) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /leeta bind <definition-id>"));
            return;
        }

        String id = bind ? args[0].toLowerCase(Locale.ROOT) : null;
        if (bind && plugin.definitions().get(id) == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Unknown definition: " + id));
            return;
        }

        Entity target = player.getTargetEntity(REACH);
        if (target != null) {
            NamespacedKey key = new NamespacedKey(plugin, "interaction-id");
            if (bind) {
                target.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<green>Bound entity to interaction <white>" + id + "</white>."));
            } else {
                target.getPersistentDataContainer().remove(key);
                player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Unbound entity."));
            }
            return;
        }

        Block block = player.getTargetBlockExact(REACH);
        if (block == null || block.getType().isAir()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<red>Look at an entity or block within " + REACH + " blocks."));
            return;
        }
        String blockKey = blockKey(block);
        if (bind) {
            plugin.storage().setPersistent("interaction", blockKey, ZERO, id);
            indexBlockBinding(blockKey, id);
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<green>Bound block to interaction <white>" + id + "</white>."));
        } else {
            String existing = plugin.storage().getPersistent("interaction", blockKey, ZERO);
            if (existing == null) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>This block has no binding."));
                return;
            }
            plugin.storage().deletePersistent("interaction", blockKey, ZERO);
            indexBlockBinding(blockKey, null);
            player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Unbound block."));
        }
    }

    /**
     * Maintains the {@code block-index} row (a {@code world:x:y:z=id;...} string)
     * so /leeta bindings can enumerate block bindings; a null id removes the entry.
     */
    private void indexBlockBinding(String blockKey, String id) {
        String raw = plugin.storage().getPersistent("interaction", "block-index", ZERO);
        Map<String, String> index = new java.util.LinkedHashMap<>();
        if (raw != null && !raw.isBlank()) {
            for (String entry : raw.split(";")) {
                String[] parts = entry.split("=", 2);
                if (parts.length == 2) {
                    index.put(parts[0], parts[1]);
                }
            }
        }
        if (id == null) {
            index.remove(blockKey);
        } else {
            index.put(blockKey, id);
        }
        plugin.storage().setPersistent("interaction", "block-index", ZERO,
            String.join(";", index.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
    }

    public static String blockKey(Block block) {
        return "block:" + block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    @Override
    public List<String> tab(CommandSender sender, String[] args) {
        if (!bind || args.length > 1) return List.of();
        return new ArrayList<>(plugin.definitions().ids());
    }
}
