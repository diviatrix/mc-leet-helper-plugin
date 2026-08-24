package com.leet.interaction.reputation;

import com.leet.interaction.LeetInteraction;

import java.util.UUID;

/** Per-player reputation score, persisted in the plugin's own SQLite store. */
public final class ReputationManager {

    private final LeetInteraction plugin;

    public ReputationManager(LeetInteraction plugin) {
        this.plugin = plugin;
    }

    public int get(UUID uuid) {
        String value = plugin.storage().getPersistent("interaction", "reputation", uuid);
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void add(UUID uuid, int delta) {
        set(uuid, get(uuid) + delta);
    }

    public void set(UUID uuid, int value) {
        plugin.storage().setPersistent("interaction", "reputation", uuid, String.valueOf(value));
    }
}
