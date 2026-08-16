package com.leet.helper.feature.skills;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leet.helper.Core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A player's skill levels, persisted for featureId {@code skills} as a single
 * Gson JSON map (skill id -> level) under the {@code levels} key in the
 * StorageManager kv_store (SQLite, survives restarts).
 */
public final class SkillState {

    private static final String STORE_KEY = "levels";

    private final Map<String, Integer> levels = new HashMap<>();

    public int level(String skillId) {
        return levels.getOrDefault(skillId, 0);
    }

    public void setLevel(String skillId, int level) {
        levels.put(skillId, level);
    }

    /** Load a player's saved levels (missing = all at level 0). */
    public static SkillState load(Core plugin, UUID uuid) {
        SkillState state = new SkillState();
        String json = plugin.storageManager().getPersistent("skills", STORE_KEY, uuid);
        if (json == null || json.isEmpty()) {
            return state;
        }
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            for (var entry : obj.entrySet()) {
                state.levels.put(entry.getKey().toLowerCase(),
                    entry.getValue().getAsInt());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse saved skill levels; resetting to 0.");
        }
        return state;
    }

    public void save(Core plugin, UUID uuid) {
        JsonObject obj = new JsonObject();
        for (var entry : levels.entrySet()) {
            obj.addProperty(entry.getKey().toLowerCase(), entry.getValue());
        }
        plugin.storageManager().setPersistent("skills", STORE_KEY, uuid, obj.toString());
    }
}