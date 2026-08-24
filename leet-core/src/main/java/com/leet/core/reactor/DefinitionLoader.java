package com.leet.core.reactor;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses a definition YAML file (id / triggers / conditions / actions) into a
 * {@link Definition}. Shared by core's rules folder and any plugin that owns
 * definition files (LeetInteraction's definitions/ folder).
 */
public final class DefinitionLoader {

    private DefinitionLoader() {
    }

    /** Parses the definition, or null when the file has no usable id. */
    public static Definition parse(YamlConfiguration cfg) {
        String id = cfg.getString("id");
        if (id == null || id.isBlank()) return null;
        String key = id.toLowerCase(Locale.ROOT);

        List<String> triggers = cfg.getStringList("triggers").stream()
            .map(t -> t.toLowerCase(Locale.ROOT)).toList();

        Map<String, Object> conditions = new LinkedHashMap<>();
        ConfigurationSection cond = cfg.getConfigurationSection("conditions");
        if (cond != null) {
            for (String name : cond.getKeys(false)) {
                conditions.put(name.toLowerCase(Locale.ROOT), cond.get(name));
            }
        }

        List<Map.Entry<String, Map<String, Object>>> actions = new ArrayList<>();
        ConfigurationSection actionSection = cfg.getConfigurationSection("actions");
        if (actionSection != null) {
            for (String name : actionSection.getKeys(false)) {
                ConfigurationSection entry = actionSection.getConfigurationSection(name);
                if (entry == null) continue;
                String type = entry.getString("type");
                if (type == null) continue;
                Map<String, Object> params = new LinkedHashMap<>(entry.getValues(false));
                params.remove("type");
                actions.add(Map.entry(type.toLowerCase(Locale.ROOT), params));
            }
        }

        return new Definition(key, triggers, conditions, actions);
    }
}
