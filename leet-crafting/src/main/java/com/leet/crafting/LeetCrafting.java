package com.leet.crafting;

import com.leet.core.CoreApi;
import com.leet.core.craft.CustomItemView;
import com.leet.core.plugin.FeaturePluginSupport;
import com.leet.crafting.craft.LeetItemRegistry;
import com.leet.crafting.resource.ResourcePackService;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * LeetCrafting — the custom items + recipes plugin (merged Cooking + Crafting).
 * It soft-depends on LeetCore, looks up the shared {@link CoreApi}, registers its
 * crafting/cooking features into core's shared feature registry, and owns its item
 * domain: the {@link LeetItemRegistry} (registered with core as a read-only
 * {@link CustomItemView} so /leeta give works) and the item resource pack
 * ({@link ResourcePackService}), constructed, started and stopped here. Without
 * LeetCore it disables itself gracefully.
 */
public final class LeetCrafting extends JavaPlugin {

    private static final List<String> DOMAINS = List.of("crafting", "cooking");

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

        // Pre-load every domain's items before registering either feature, so recipe
        // ingredient resolution (e.g. cooking referencing crafting's salt) is
        // independent of feature load order.
        CraftFeature.preloadItemSections(this, itemRegistry, "crafting", "cooking");

        if (DOMAINS.stream().noneMatch(id -> core.registerFeature(new CraftFeature(core, this, id, itemRegistry)))) {
            getLogger().severe("Failed to register crafting/cooking features with LeetCore. LeetCrafting will not enable.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        resourcePackService = new ResourcePackService(this);
        resourcePackService.start();
        getLogger().info("LeetCrafting registered 'crafting' and 'cooking' features with LeetCore.");
    }

    @Override
    public void onDisable() {
        if (resourcePackService != null) {
            resourcePackService.stop();
            resourcePackService = null;
        }
        DOMAINS.forEach(id -> FeaturePluginSupport.disableFeature(core, id));
        core = null;
        itemRegistry = null;
    }

    /** Writes this plugin's default feature configs on first run. */
    private void saveFeatureConfigs() {
        FeaturePluginSupport.saveResourceIfMissing(this, "features/crafting.yml");
        FeaturePluginSupport.saveResourceIfMissing(this, "features/cooking.yml");
    }
}

