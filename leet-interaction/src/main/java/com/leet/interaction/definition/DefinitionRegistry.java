package com.leet.interaction.definition;

import com.leet.core.reactor.Definition;
import com.leet.core.reactor.DefinitionLoader;
import com.leet.interaction.LeetInteraction;
import com.leet.interaction.quest.QuestDefinition;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads every definitions/*.yml from the plugin's data folder using core's
 * shared {@link DefinitionLoader}, plus the interaction-specific {@code quest:}
 * sections layered on top of the same files.
 */
public final class DefinitionRegistry {

    private final LeetInteraction plugin;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<String, QuestDefinition> quests = new LinkedHashMap<>();

    public DefinitionRegistry(LeetInteraction plugin) {
        this.plugin = plugin;
    }

    public void load() {
        definitions.clear();
        quests.clear();
        File dir = new File(plugin.getDataFolder(), "definitions");
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                Definition def = DefinitionLoader.parse(cfg);
                if (def == null) {
                    plugin.getLogger().warning("Definition file " + file.getName() + " has no 'id'; skipped.");
                    continue;
                }
                definitions.put(def.id, def);
                QuestDefinition quest = QuestDefinition.parse(def.id, cfg.getConfigurationSection("quest"));
                if (quest != null) {
                    quests.put(def.id, quest);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load definition " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public Definition get(String id) {
        return id == null ? null : definitions.get(id.toLowerCase());
    }

    public QuestDefinition quest(String id) {
        return id == null ? null : quests.get(id.toLowerCase());
    }

    public java.util.Collection<String> ids() {
        return definitions.keySet();
    }

    public int size() {
        return definitions.size();
    }
}
