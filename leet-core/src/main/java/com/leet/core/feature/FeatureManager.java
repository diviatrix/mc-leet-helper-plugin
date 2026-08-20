package com.leet.core.feature;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FeatureManager implements FeatureRegistry {

    private final Map<String, AbstractFeature> features = new LinkedHashMap<>();
    private final Logger logger;

    public FeatureManager(Logger logger) {
        this.logger = logger;
    }

    public void register(AbstractFeature feature) {
        features.put(feature.id(), feature);
    }

    public void disableAll() {
        for (AbstractFeature feature : features.values()) {
            try {
                feature.disable();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to disable feature: " + feature.id(), e);
            }
        }
    }

    /**
     * Reloads a single feature from its config file: disables it (unregistering
     * listeners/recipes and stopping tasks), then re-enables it (re-running
     * enable()/loadConfig() so config edits take effect at runtime).
     */
    public boolean reload(String id) {
        if (!features.containsKey(id)) return false;
        AbstractFeature feature = features.get(id);
        try {
            feature.disable();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to disable feature during reload: " + id, e);
        }
        // Re-read the persisted base.enabled BEFORE enable() so a feature that is
        // disabled in its file stays disabled after the reload (mirrors toggle()).
        Optional<File> file = fileFor(feature);
        boolean shouldEnable = file
            .map(f -> YamlConfiguration.loadConfiguration(f).getBoolean("base.enabled", false))
            .orElse(true);
        if (shouldEnable) {
            feature.enable();
        }
        return true;
    }

    public boolean toggle(String id) {
        if (!features.containsKey(id)) return false;
        AbstractFeature feature = features.get(id);
        boolean newState = !feature.isEnabled();
        feature.disable();
        // Persist base.enabled BEFORE enable() so enable()/loadConfig() re-reads the
        // new value; otherwise a feature toggled off→on stays disabled at runtime.
        fileFor(feature).ifPresent(file -> persistToggle(file, id, newState));
        if (newState) {
            feature.enable();
        }
        return true;
    }

    private Optional<File> fileFor(AbstractFeature feature) {
        File file = new File(feature.ownerDataFolder(), "features/" + feature.id() + ".yml");
        return file.exists() ? Optional.of(file) : Optional.empty();
    }

    private void persistToggle(File file, String id, boolean newState) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.set("base.enabled", newState);
        try {
            cfg.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to persist toggle for feature: " + id, e);
        }
    }

    public Optional<AbstractFeature> get(String id) {
        return Optional.ofNullable(features.get(id));
    }

    public Collection<AbstractFeature> all() {
        return Collections.unmodifiableCollection(features.values());
    }
}
