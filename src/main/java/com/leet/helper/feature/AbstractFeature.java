package com.leet.helper.feature;

import com.leet.helper.Core;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public abstract class AbstractFeature implements Listener {

    protected final Core plugin;
    protected boolean enabled;
    protected String permission;
    protected String defaultPermission;
    protected List<String> worlds;
    protected int cooldownSeconds;
    protected double cost;
    protected String messageType;
    protected Map<String, String> messages;

    protected AbstractFeature(Core plugin) {
        this.plugin = plugin;
        this.messages = new HashMap<>();
    }

    public abstract String featureId();

    protected abstract void loadFeatureConfig(YamlConfiguration cfg);

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "features/" + featureId() + ".yml");
        if (!file.exists()) {
            plugin.getLogger().severe("Feature config not found: " + file.getName());
            enabled = false;
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        mergeMissingKeys(cfg, file);

        enabled = cfg.getBoolean("base.enabled", false);
        permission = cfg.getString("base.permission", "leet.feat." + featureId());
        defaultPermission = cfg.getString("base.default-permission", "true");
        worlds = cfg.getStringList("base.worlds");
        cooldownSeconds = cfg.getInt("base.cooldown", 0);
        messageType = cfg.getString("base.message-type", "ACTION_BAR");
        cost = cfg.getDouble("feature.cost", 0.0);

        messages.clear();
        if (cfg.isConfigurationSection("messages")) {
            for (String key : cfg.getConfigurationSection("messages").getKeys(false)) {
                messages.put(key, cfg.getString("messages." + key, ""));
            }
        }

        loadFeatureConfig(cfg);
    }

    public void enable() {
        loadConfig();
        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        enabled = false;
    }

    /**
     * Adds any keys present in the bundled default config that are missing from
     * the on-disk config, preserving the server admin's existing values. This lets
     * old feature configs gain new options (e.g. require-hoe) on plugin update.
     */
    private void mergeMissingKeys(YamlConfiguration cfg, File file) {
        InputStream defaultStream = plugin.getResource("features/" + featureId() + ".yml");
        if (defaultStream == null) return;
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
            new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            Object value = defaults.get(key);
            if (value instanceof ConfigurationSection) {
                continue; // parent map node, not a leaf to merge
            }
            if (!cfg.contains(key)) {
                cfg.set(key, value);
                changed = true;
            }
        }

        if (!changed) return;
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Failed to merge defaults into feature config: " + featureId(), e);
        }
    }

    protected boolean check(Player player) {
        if (!enabled) return false;
        if (!player.hasPermission(permission)) return false;
        if (!isUserEnabled(player.getUniqueId())) return false;
        if (worlds != null && !worlds.isEmpty()) {
            String worldName = player.getWorld().getName();
            if (!worlds.contains(worldName)) return false;
        }
        return true;
    }

    public boolean checkCooldown(UUID uuid) {
        if (cooldownSeconds <= 0) return true;
        long lastUse = plugin.storageManager().getRuntime(featureId(), "cooldown", uuid, 0);
        long now = System.currentTimeMillis();
        return (now - lastUse) >= cooldownSeconds * 1000L;
    }

    public void setCooldown(UUID uuid) {
        if (cooldownSeconds <= 0) return;
        plugin.storageManager().setRuntime(featureId(), "cooldown", uuid, System.currentTimeMillis());
    }

    public long getCooldownRemaining(UUID uuid) {
        if (cooldownSeconds <= 0) return 0;
        long lastUse = plugin.storageManager().getRuntime(featureId(), "cooldown", uuid, 0);
        long elapsed = System.currentTimeMillis() - lastUse;
        long remaining = cooldownSeconds * 1000L - elapsed;
        return Math.max(0, remaining / 1000);
    }

    public void sendMessage(Player player, String key, String... placeholders) {
        String template = messages.get(key);
        if (template == null || template.isEmpty()) return;

        String result = template;
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            result = result.replace(placeholders[i], placeholders[i + 1]);
        }

        Component component = MiniMessage.miniMessage().deserialize(result);

        switch (messageType.toUpperCase()) {
            case "CHAT":
                player.sendMessage(component);
                break;
            case "TITLE":
                player.showTitle(Title.title(
                    component, Component.empty(),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))
                ));
                break;
            case "ACTION_BAR":
            default:
                player.sendActionBar(component);
                break;
        }
    }

    public boolean hasBalance(Player player, double amount) {
        if (amount <= 0) return true;
        if (plugin.economy() == null) return true;
        return plugin.economy().has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (amount <= 0) return true;
        if (plugin.economy() == null) return true;
        return plugin.economy().withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Applies the feature's per-use {@code feature.cost}: returns true (free,
     * no economy, or the charge succeeded) when the use may proceed. If the
     * player lacks the funds it sends the "insufficient-funds" message and
     * returns false, blocking the use. With a cost of 0 (the default) this is
     * always free.
     */
    protected boolean chargeUse(Player player) {
        if (cost <= 0) return true;
        if (plugin.economy() == null) return true;
        if (!hasBalance(player, cost)) {
            sendMessage(player, "insufficient-funds", "<cost>", String.valueOf(cost));
            return false;
        }
        return withdraw(player, cost);
    }

    /**
     * Breaks <code>block</code> as the player, but only if no protection plugin
     * (GriefPrevention, WorldGuard, ...) would cancel the break. A plain
     * <code>block.breakNaturally(tool)</code> fires no BlockBreakEvent, so it
     * silently bypasses claims/regions — features that break several blocks
     * (Tree Feller, Auto Crop) must route each block through here instead.
     * Returns true if the block was actually broken.
     */
    protected boolean breakIfAllowed(Player player, Block block, ItemStack tool) {
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        block.breakNaturally(tool);
        return true;
    }

    public String id() {
        return featureId();
    }

    public String permission() {
        return permission;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether this feature is enabled for a specific player (their personal
     * /leet toggle). Absent = enabled; stored "false" = disabled for that player.
     */
    public boolean isUserEnabled(UUID uuid) {
        Boolean toggle = plugin.storageManager().getUserToggle(featureId(), uuid);
        return toggle == null || toggle;
    }

    public String getDefaultPermission() {
        return defaultPermission;
    }
}
