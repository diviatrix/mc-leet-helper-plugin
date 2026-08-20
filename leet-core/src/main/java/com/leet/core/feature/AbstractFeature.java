package com.leet.core.feature;

import com.leet.core.CoreApi;
import com.leet.core.plugin.FeaturePluginSupport;
import com.leet.core.storage.StorageManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thin base for gameplay features that are gated by permission + per-player
 * toggle + world whitelist. It owns the config + lifecycle (enable/disable event
 * lifecycle) and the shared gating — the "some player action, gated" pattern.
 *
 * <p>Behavioral capabilities (cost, cooldown, messages, protection-aware block
 * breaking) are NOT inherited by default: a feature opts in by implementing the
 * matching role interface ({@link CostedFeature}, {@link CooldownAware},
 * {@link MessagingFeature}, {@link BlockBreakerFeature}). Large non-gated systems
 * (NPCs, quests, marriages) need not extend this base at all; they compose the
 * role interfaces or use core's infra directly.
 */
public abstract class AbstractFeature implements Listener, ToggleableFeature {

    protected final CoreApi core;
    protected final JavaPlugin owner;
    protected boolean enabled;
    protected String permission;
    protected String defaultPermission;
    protected boolean permissionLookupEnabled;
    protected List<String> worlds;
    protected int cooldownSeconds;
    protected double cost;
    protected String messageType;
    protected Map<String, String> messages;

    protected AbstractFeature(CoreApi core, JavaPlugin owner) {
        this.core = core;
        this.owner = owner;
        this.messages = new HashMap<>();
    }

    public abstract String featureId();

    protected abstract void loadFeatureConfig(YamlConfiguration cfg);

    // --- lifecycle + config ---

    public void loadConfig() {
        YamlConfiguration cfg = loadAndMerge(featureId() + ".yml");
        if (cfg == null) {
            enabled = false;
            permission = "leet.feat." + featureId();
            defaultPermission = "true";
            permissionLookupEnabled = true;
        } else {
            enabled = cfg.getBoolean("base.enabled", false);
            permission = cfg.getString("base.permission", "leet.feat." + featureId());
            defaultPermission = cfg.getString("base.default-permission", "true");
            // Only register a runtime permission when the config actually declares
            // a base.permission key; features that omit it (e.g. crafting, which
            // is open to everyone) get no phantom node.
            permissionLookupEnabled = cfg.contains("base.permission");
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
        // The feature owns its permission node: register it as soon as the config
        // is loaded, independent of whether the feature is currently enabled.
        if (permissionLookupEnabled) {
            registerPermission();
        }
    }

    private void registerPermission() {
        org.bukkit.permissions.PermissionDefault pd;
        String def = defaultPermission;
        if ("op".equalsIgnoreCase(def)) {
            pd = org.bukkit.permissions.PermissionDefault.OP;
        } else if ("false".equalsIgnoreCase(def)) {
            pd = org.bukkit.permissions.PermissionDefault.FALSE;
        } else {
            pd = org.bukkit.permissions.PermissionDefault.TRUE;
        }
        try {
            Bukkit.getPluginManager().addPermission(
                new org.bukkit.permissions.Permission(permission, pd)
            );
        } catch (IllegalArgumentException ignored) {
            // Already registered on a prior load (e.g. re-enable after toggle).
        }
    }

    public void enable() {
        loadConfig();
        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, owner);
        }
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        enabled = false;
    }

    /**
     * Loads {@code features/<fileName>} from the owning plugin, merging missing
     * keys from the packaged default. Null when the file is absent.
     */
    protected YamlConfiguration loadAndMerge(String fileName) {
        File file = new File(owner.getDataFolder(), "features/" + fileName);
        if (!file.exists()) {
            owner.getLogger().severe("Feature config not found: " + file.getName());
            return null;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        FeaturePluginSupport.mergeMissingKeys(owner, cfg, file, "features/" + fileName);
        return cfg;
    }

    // --- shared gating (permission + toggle + world) ---

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

    /** Public alias of {@link #check} for cross-feature queries. */
    public boolean appliesTo(Player player) {
        return check(player);
    }

    /**
     * Whether this feature is enabled for a specific player (personal toggle).
     * Absent = enabled; stored "false" = disabled.
     */
    public boolean isUserEnabled(UUID uuid) {
        Boolean toggle = core.storageManager().getUserToggle(featureId(), uuid);
        return toggle == null || toggle;
    }

    // --- getters for the opt-in role interfaces ---

    @Override public String toString() {
        return featureId();
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

    public CoreApi core() {
        return core;
    }

    /** Owning plugin's data folder (where this feature's config files live). */
    public File ownerDataFolder() {
        return owner.getDataFolder();
    }

    /** Configured per-use cost (0 = free). */
    public double cost() {
        return cost;
    }

    /** Configured cooldown in seconds (0 = none). */
    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    /** Message templates (for {@link MessagingFeature}). */
    public Map<String, String> messages() {
        return Collections.unmodifiableMap(messages);
    }

    /** Configured message delivery type (for {@link MessagingFeature}). */
    public String messageType() {
        return messageType;
    }

    /** Shared Vault economy (for {@link CostedFeature}); may be null. */
    public Economy economy() {
        return core.economy();
    }

    public StorageManager storage() {
        return core.storageManager();
    }
}
