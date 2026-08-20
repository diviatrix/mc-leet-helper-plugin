package com.leet.core.plugin;

import com.leet.core.CoreApi;
import com.leet.core.feature.AbstractFeature;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Optional;

/**
 * Small static bootstrap helpers for feature plugins (skills, crafting) that
 * soft-depend on LeetCore. Collapses the repeated look-up / fail-gracefully /
 * save-resource / disable pattern every dependent plugin would otherwise copy.
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
        CoreApi core = rsp == null ? null : Optional.ofNullable(rsp.getProvider()).orElse(null);
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

    /** Disables the given features (registered in core) if present. */
    public static void disableFeature(CoreApi core, String id) {
        if (core != null) {
            core.featureRegistry().get(id).ifPresent(AbstractFeature::disable);
        }
    }
}
