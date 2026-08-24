package com.leet.interaction.quest;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The quest section of a definition: what the NPC expects (requirements) and
 * what the player gets back (rewards). Requirements and rewards are item lists
 * ({item, amount}) plus scalar money / exp / reputation; rewards may also run
 * console commands (%player% placeholder).
 */
public final class QuestDefinition {

    public final String id;
    public final String name;
    public final String description;
    public final boolean repeatable;
    public final int cooldownSeconds;
    public final List<Map<String, Object>> requiredItems;
    public final double requiredMoney;
    public final int requiredReputation;
    public final List<Map<String, Object>> rewardItems;
    public final double rewardMoney;
    public final int rewardExp;
    public final int rewardReputation;
    public final List<String> rewardCommands;

    private QuestDefinition(String id, String name, String description, boolean repeatable,
                            int cooldownSeconds, List<Map<String, Object>> requiredItems,
                            double requiredMoney, int requiredReputation,
                            List<Map<String, Object>> rewardItems, double rewardMoney,
                            int rewardExp, int rewardReputation, List<String> rewardCommands) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.repeatable = repeatable;
        this.cooldownSeconds = cooldownSeconds;
        this.requiredItems = requiredItems;
        this.requiredMoney = requiredMoney;
        this.requiredReputation = requiredReputation;
        this.rewardItems = rewardItems;
        this.rewardMoney = rewardMoney;
        this.rewardExp = rewardExp;
        this.rewardReputation = rewardReputation;
        this.rewardCommands = rewardCommands;
    }

    @SuppressWarnings("unchecked")
    public static QuestDefinition parse(String id, ConfigurationSection section) {
        if (section == null) return null;
        ConfigurationSection req = section.getConfigurationSection("requirements");
        ConfigurationSection rew = section.getConfigurationSection("rewards");

        List<Map<String, Object>> reqItems = items(req, "items");
        List<Map<String, Object>> rewItems = items(rew, "items");

        return new QuestDefinition(
            id,
            section.getString("name", id),
            section.getString("description", ""),
            section.getBoolean("repeatable", false),
            section.getInt("cooldown", 0),
            reqItems,
            req == null ? 0 : req.getDouble("money", 0),
            req == null ? 0 : req.getInt("reputation", 0),
            rewItems,
            rew == null ? 0 : rew.getDouble("money", 0),
            rew == null ? 0 : rew.getInt("exp", 0),
            rew == null ? 0 : rew.getInt("reputation", 0),
            rew == null ? List.of() : rew.getStringList("commands"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(ConfigurationSection section, String key) {
        if (section == null) return List.of();
        return section.getMapList(key).stream()
            .map(m -> (Map<String, Object>) m)
            .toList();
    }
}
