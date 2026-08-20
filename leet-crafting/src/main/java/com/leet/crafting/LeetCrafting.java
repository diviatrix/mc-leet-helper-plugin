package com.leet.crafting;

import com.leet.core.CoreApi;
import com.leet.core.craft.CustomItemView;
import com.leet.core.plugin.FeaturePluginSupport;
import com.leet.crafting.craft.LeetItemRegistry;
import com.leet.crafting.resource.ResourcePackService;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * LeetCrafting — the custom items + recipes plugin. It soft-depends on
 * LeetCore, looks up the shared {@link CoreApi}, registers a single
 * {@code crafting} feature into core's shared feature registry, and owns its
 * item domain: the {@link LeetItemRegistry} (registered with core as a read-only
 * {@link CustomItemView} so /leeta give works) and the item resource pack
 * ({@link ResourcePackService}), constructed, started and stopped here. Without
 * LeetCore it disables itself gracefully.
 */
public final class LeetCrafting extends JavaPlugin {

    private static final String FEATURE_ID = "crafting";

    private CoreApi core;
    private LeetItemRegistry itemRegistry;
    private ResourcePackService resourcePackService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveFeatureConfigs();
        core = FeaturePluginSupport.requireCore(this);
        if (core == null) return;

        itemRegistry = new LeetItemRegistry(getLogger(), new NamespacedKey(this, "ci"));
        Bukkit.getServicesManager().register(CustomItemView.class, itemRegistry, this, org.bukkit.plugin.ServicePriority.Normal);

        CraftFeature feature = new CraftFeature(core, this, itemRegistry);
        feature.setRecipeBookAutodiscovery(getConfig().getBoolean("recipe-book-autodiscovery", true));
        if (!core.registerFeature(feature)) {
            getLogger().severe("Failed to register the 'crafting' feature with LeetCore. LeetCrafting will not enable.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        resourcePackService = new ResourcePackService(this);
        resourcePackService.start();
        getLogger().info("LeetCrafting registered the 'crafting' feature with LeetCore.");
    }

    @Override
    public void onDisable() {
        if (resourcePackService != null) {
            resourcePackService.stop();
            resourcePackService = null;
        }
        FeaturePluginSupport.disableFeature(core, FEATURE_ID);
        core = null;
        itemRegistry = null;
    }

    /** Writes this plugin's default feature configs on first run and migrates any pre-merge state. */
    private void saveFeatureConfigs() {
        FeaturePluginSupport.saveResourceIfMissing(this, "features/crafting.yml");
        // One-off migration: pre-1.5.x split the crafting domain into two YAMLs
        // (crafting.yml + cooking.yml) and two feature ids. Existing servers may
        // have an on-disk cooking.yml with a custom base.enabled; carry that value
        // over to the merged crafting.yml and remove the stale file.
        File legacy = new File(getDataFolder(), "features/cooking.yml");
        if (legacy.isFile()) {
            File merged = new File(getDataFolder(), "features/crafting.yml");
            try {
                YamlConfiguration legacyCfg = YamlConfiguration.loadConfiguration(legacy);
                YamlConfiguration mergedCfg = YamlConfiguration.loadConfiguration(merged);
                if (!mergedCfg.contains("base.enabled")) {
                    mergedCfg.set("base.enabled", legacyCfg.getBoolean("base.enabled", true));
                    mergedCfg.save(merged);
                    getLogger().info("Migrated base.enabled from legacy features/cooking.yml into features/crafting.yml.");
                }
            } catch (Exception e) {
                getLogger().warning("Failed to migrate legacy features/cooking.yml: " + e.getMessage());
            }
            if (!legacy.delete()) {
                legacy.deleteOnExit();
            }
        }
    }
}