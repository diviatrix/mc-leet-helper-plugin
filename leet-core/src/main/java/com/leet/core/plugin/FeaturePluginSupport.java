package com.leet.core.plugin;

import com.leet.core.CoreApi;
import com.leet.core.feature.AbstractFeature;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Small static bootstrap helpers for feature plugins (skills, crafting) that
 * soft-depend on LeetCore. Collapses the repeated look-up / fail-gracefully /
 * save-resource / disable pattern every dependent plugin would otherwise copy,
 * plus the shared YAML-merge primitive used by both LeetCore's global config
 * and {@link AbstractFeature}'s per-feature config.
 */
public final class FeaturePluginSupport {

    private FeaturePluginSupport() {
    }

    /**
     * Looks up the shared {@link CoreApi} service. If absent, logs a clear error,
     * disables {@code plugin} gracefully and returns null — the caller's onEnable
     * should then simply {@code return}.
     */
    public static CoreApi requireCore(JavaPlugin plugin) {
        RegisteredServiceProvider<CoreApi> rsp =
            Bukkit.getServicesManager().getRegistration(CoreApi.class);
        CoreApi core = rsp == null ? null : rsp.getProvider();
        if (core == null) {
            plugin.getLogger().severe("LeetCore is not present/ready. " + plugin.getName() + " will not enable.");
            Bukkit.getPluginManager().disablePlugin(plugin);
        }
        return core;
    }

    /** Writes a bundled resource to the plugin's data folder when missing. */
    public static void saveResourceIfMissing(JavaPlugin plugin, String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
    }

    /**
     * Adds any keys present in the bundled {@code resourcePath} that are missing
     * from {@code cfg}, preserving the user's existing values. Sections are not
     * considered "missing" if any of their children exist (Bukkit's {@code cfg.set}
     * handles shallow keys correctly, so we walk flat keys only).
     */
    public static void mergeMissingKeys(JavaPlugin plugin, YamlConfiguration cfg, File file, String resourcePath) {
        InputStream defaultStream = plugin.getResource(resourcePath);
        if (defaultStream == null) return;
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
            new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            Object value = defaults.get(key);
            if (value instanceof ConfigurationSection) continue;
            if (!cfg.contains(key)) {
                cfg.set(key, value);
                changed = true;
            }
        }
        if (!changed) return;
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                "Failed to merge defaults into " + file.getName(), e);
        }
    }

    /** Disables the given features (registered in core) if present. */
    public static void disableFeature(CoreApi core, String id) {
        if (core != null) {
            core.featureRegistry().get(id).ifPresent(AbstractFeature::disable);
        }
    }
}