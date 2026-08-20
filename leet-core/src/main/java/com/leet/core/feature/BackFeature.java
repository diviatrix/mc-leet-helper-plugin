package com.leet.core.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leet.core.CoreApi;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BackFeature extends AbstractFeature implements CostedFeature, CooldownAware, MessagingFeature {

    private int maxAge;

    public BackFeature(CoreApi core, JavaPlugin owner) {
        super(core, owner);
    }

    @Override
    public String featureId() {
        return "back";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        maxAge = cfg.getInt("feature.max-age", 3600);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!check(player)) return;

        String json = serializeLocation(player.getLocation());
        core.storageManager().setPersistent(featureId(), "death", player.getUniqueId(), json);
        sendMessage(player, "death-location-saved");
    }

    public boolean teleportBack(Player player) {
        if (!check(player)) {
            sendMessage(player, "no-location");
            return false;
        }

        String json = core.storageManager().getPersistent(featureId(), "death", player.getUniqueId());
        if (json == null) {
            sendMessage(player, "no-location");
            return false;
        }

        Location deathLoc = deserializeLocation(json);
        if (deathLoc == null || deathLoc.getWorld() == null) {
            sendMessage(player, "no-location");
            return false;
        }

        long timestamp = getTimestamp(json);
        if (timestamp > 0 && (System.currentTimeMillis() - timestamp) > maxAge * 1000L) {
            core.storageManager().deletePersistent(featureId(), "death", player.getUniqueId());
            sendMessage(player, "expired");
            return false;
        }

        if (!player.getWorld().getName().equals(deathLoc.getWorld().getName())) {
            sendMessage(player, "wrong-world");
            return false;
        }

        if (!checkCooldown(player.getUniqueId())) {
            long remaining = getCooldownRemaining(player.getUniqueId());
            sendMessage(player, "cooldown-active", "<time>", String.valueOf(remaining));
            return false;
        }

        if (!chargeUse(player)) return false;

        player.teleport(deathLoc);
        setCooldown(player.getUniqueId());
        core.storageManager().deletePersistent(featureId(), "death", player.getUniqueId());
        sendMessage(player, "teleport");
        return true;
    }

    /** Back persists its cooldown across restarts (SQLite). */
    @Override
    public boolean persistentCooldown() {
        return true;
    }

    private String serializeLocation(Location loc) {
        JsonObject json = new JsonObject();
        json.addProperty("world", loc.getWorld().getName());
        json.addProperty("x", loc.getX());
        json.addProperty("y", loc.getY());
        json.addProperty("z", loc.getZ());
        json.addProperty("yaw", loc.getYaw());
        json.addProperty("pitch", loc.getPitch());
        json.addProperty("timestamp", System.currentTimeMillis());
        return json.toString();
    }

    private Location deserializeLocation(String str) {
        try {
            JsonObject json = JsonParser.parseString(str).getAsJsonObject();
            World world = org.bukkit.Bukkit.getWorld(json.get("world").getAsString());
            if (world == null) return null;
            double x = json.get("x").getAsDouble();
            double y = json.get("y").getAsDouble();
            double z = json.get("z").getAsDouble();
            float yaw = json.get("yaw").getAsFloat();
            float pitch = json.get("pitch").getAsFloat();
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            return null;
        }
    }

    private long getTimestamp(String str) {
        try {
            JsonObject json = JsonParser.parseString(str).getAsJsonObject();
            return json.get("timestamp").getAsLong();
        } catch (Exception e) {
            return 0;
        }
    }
}
