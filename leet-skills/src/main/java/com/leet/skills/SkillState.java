package com.leet.skills;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leet.core.storage.StorageManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A player's skill levels, persisted for featureId {@code skills} as a single
 * Gson JSON map (skill id -> level) under the {@code levels} key in the skills
 * plugin's own {@link StorageManager} (SQLite in this plugin's data folder,
 * survives restarts).
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
    public static SkillState load(StorageManager storage, UUID uuid) {
        SkillState state = new SkillState();
        String json = storage.getPersistent("skills", STORE_KEY, uuid);
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
            // Corrupt payload: start the player fresh rather than crash.
        }
        return state;
    }

    public void save(StorageManager storage, UUID uuid) {
        JsonObject obj = new JsonObject();
        for (var entry : levels.entrySet()) {
            obj.addProperty(entry.getKey().toLowerCase(), entry.getValue());
        }
        storage.setPersistent("skills", STORE_KEY, uuid, obj.toString());
    }
}