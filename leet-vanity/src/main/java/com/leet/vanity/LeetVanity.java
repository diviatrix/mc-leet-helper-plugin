package com.leet.vanity;

import com.leet.core.CoreApi;
import com.leet.core.plugin.FeaturePluginSupport;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class LeetVanity extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveFeatureConfigs();
        CoreApi core = FeaturePluginSupport.requireCore(this);
        if (core == null) return;

        VanityFeature feature = new VanityFeature(core, this);
        if (!core.registerFeature(feature)) {
            getLogger().severe("Failed to register the 'vanity' feature with LeetCore. LeetVanity will not enable.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        DanceCommand danceCommand = new DanceCommand(this, feature);
        getCommand("dance").setExecutor(danceCommand);
        getCommand("dance").setTabCompleter(danceCommand);

        getLogger().info("LeetVanity registered the 'vanity' feature with LeetCore.");
    }

    private void saveFeatureConfigs() {
        FeaturePluginSupport.saveResourceIfMissing(this, "features/vanity.yml");
    }
}
