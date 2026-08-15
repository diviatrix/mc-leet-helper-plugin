package com.leet.helper.feature;

import com.leet.helper.HelperPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class FeatureManager {

    private final Map<String, AbstractFeature> features = new LinkedHashMap<>();
    private final HelperPlugin plugin;

    public FeatureManager(HelperPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(AbstractFeature feature) {
        features.put(feature.id(), feature);
    }

    public void enableAll() {
        for (AbstractFeature feature : features.values()) {
            try {
                feature.enable();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to enable feature: " + feature.id(), e);
            }
        }
    }

    public void disableAll() {
        for (AbstractFeature feature : features.values()) {
            try {
                feature.disable();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to disable feature: " + feature.id(), e);
            }
        }
    }

    public boolean toggle(String id) {
        AbstractFeature feature = features.get(id);
        if (feature == null) return false;
        boolean newState = !feature.isEnabled();
        feature.disable();
        if (newState) {
            feature.enable();
        }
        persistToggle(id, newState);
        return true;
    }

    private void persistToggle(String id, boolean newState) {
        File file = new File(plugin.getDataFolder(), "features/_" + id + ".yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.set("base.enabled", newState);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to persist toggle for feature: " + id, e);
        }
    }

    public Optional<AbstractFeature> get(String id) {
        return Optional.ofNullable(features.get(id));
    }

    public Collection<AbstractFeature> all() {
        return Collections.unmodifiableCollection(features.values());
    }
}
