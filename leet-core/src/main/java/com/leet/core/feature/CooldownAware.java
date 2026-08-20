package com.leet.core.feature;

import com.leet.core.storage.StorageManager;

import java.util.UUID;

/**
 * Opt-in role: a feature with a per-player cooldown between uses.
 *
 * <p>Default implementation stores the last-use timestamp either in the runtime
 * store (default) or, when {@link #persistentCooldown()} returns true, in the
 * persistent SQLite store — keyed by {@code featureId()}, supplied via
 * {@link #storage()} and {@link #cooldownSeconds()}.
 */
public interface CooldownAware {

    StorageManager storage();

    int cooldownSeconds();

    String featureId();

    /** True to persist cooldowns across restarts (SQLite) instead of in-memory. */
    default boolean persistentCooldown() {
        return false;
    }

    /** The stored last-use timestamp, -1 when none recorded. */
    private long lastUse(UUID uuid) {
        if (persistentCooldown()) {
            String v = storage().getPersistent(featureId(), "cooldown", uuid);
            if (v == null) return -1;
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return storage().getRuntime(featureId(), "cooldown", uuid, -1);
    }

    /** True when the player is off cooldown (or no cooldown is configured). */
    default boolean checkCooldown(UUID uuid) {
        if (cooldownSeconds() <= 0) return true;
        long lastUse = lastUse(uuid);
        if (lastUse < 0) return true;
        long now = System.currentTimeMillis();
        return (now - lastUse) >= cooldownSeconds() * 1000L;
    }

    /** Records a use (no-op when no cooldown is configured). */
    default void setCooldown(UUID uuid) {
        if (cooldownSeconds() <= 0) return;
        long now = System.currentTimeMillis();
        if (persistentCooldown()) {
            storage().setPersistent(featureId(), "cooldown", uuid, String.valueOf(now));
        } else {
            storage().setRuntime(featureId(), "cooldown", uuid, now);
        }
    }

    /** Seconds remaining before the player can use again (0 = ready). */
    default long getCooldownRemaining(UUID uuid) {
        if (cooldownSeconds() <= 0) return 0;
        long lastUse = lastUse(uuid);
        if (lastUse < 0) return 0;
        long elapsed = System.currentTimeMillis() - lastUse;
        long remaining = cooldownSeconds() * 1000L - elapsed;
        return Math.max(0, remaining / 1000);
    }
}

