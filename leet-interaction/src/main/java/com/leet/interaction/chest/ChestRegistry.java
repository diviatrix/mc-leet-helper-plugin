package com.leet.interaction.chest;

import com.leet.interaction.LeetInteraction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Chest id -> chest block location, persisted in the plugin's SQLite store
 * (non-player rows use a zero UUID under the existing kv_store schema).
 * Bindings are created by placing a [Chest] #id sign on top of a chest and
 * removed when that sign or the chest is broken.
 */
public final class ChestRegistry {

    private static final UUID ZERO = new UUID(0, 0);

    private final LeetInteraction plugin;
    private final Map<String, Location> chests = new HashMap<>();
    private final Map<Location, String> byLocation = new HashMap<>();

    public ChestRegistry(LeetInteraction plugin) {
        this.plugin = plugin;
    }

    public void load() {
        chests.clear();
        byLocation.clear();
        // Bindings are read lazily from storage; kv_store has no "list keys"
        // API, so an index is kept under one row instead.
        String index = plugin.storage().getPersistent("interaction", "chest-index", ZERO);
        if (index == null || index.isBlank()) return;
        for (String entry : index.split(";")) {
            String[] parts = entry.split("=");
            if (parts.length != 2) continue;
            Location loc = parse(parts[1]);
            if (loc != null) {
                chests.put(parts[0], loc);
                byLocation.put(loc, parts[0]);
            }
        }
    }

    public boolean isBound(String id) {
        return chests.containsKey(id.toLowerCase());
    }

    public boolean isBoundLocation(Block block) {
        return byLocation.containsKey(block.getLocation());
    }

    public void bind(String id, Block block) {
        String key = id.toLowerCase();
        chests.put(key, block.getLocation());
        byLocation.put(block.getLocation(), key);
        persist();
    }

    public void unbindId(String id) {
        String key = id.toLowerCase();
        Location loc = chests.remove(key);
        if (loc != null) {
            byLocation.remove(loc);
        }
        persist();
    }

    public void unbindLocation(Block block) {
        String key = byLocation.remove(block.getLocation());
        if (key != null) {
            chests.remove(key);
        }
        persist();
    }

    public Block chest(String id) {
        Location loc = chests.get(id.toLowerCase());
        if (loc == null) return null;
        Block block = loc.getBlock();
        return block.getType() == org.bukkit.Material.CHEST
            || block.getType() == org.bukkit.Material.TRAPPED_CHEST ? block : null;
    }

    /** All bindings as id -> location entries (for /leeta bindings). */
    public Map<String, Location> entries() {
        return Map.copyOf(chests);
    }

    private void persist() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Location> e : chests.entrySet()) {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(e.getKey()).append('=').append(format(e.getValue()));
        }
        plugin.storage().setPersistent("interaction", "chest-index", ZERO, sb.toString());
    }

    private static String format(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static Location parse(String spec) {
        String[] parts = spec.split(",");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
