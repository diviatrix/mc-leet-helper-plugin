package com.leet.core.reactor;

import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared trigger -> conditions -> actions kernel. Core owns it; every
 * plugin registers its actions/conditions into it and runs definitions through
 * {@link #run(Player, Definition)}.
 *
 * <p>Built-in gating (executed in order): pluggable conditions, extra
 * {@code permission}, per-player {@code cooldown}, Vault {@code cost}; then the
 * ordered action list. Feedback is delivered as hardcoded MiniMessage chat
 * lines so any plugin can use the engine without wiring message templates.
 */
public final class Reactor {

    private final ActionRegistry actions = new ActionRegistry();
    private final ConditionRegistry conditions = new ConditionRegistry();
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final com.leet.core.storage.StorageManager storage;
    private final net.milkbowl.vault.economy.Economy economy;

    public Reactor(com.leet.core.storage.StorageManager storage,
                   net.milkbowl.vault.economy.Economy economy) {
        this.storage = storage;
        this.economy = economy;
    }

    public ActionRegistry actions() {
        return actions;
    }

    public ConditionRegistry conditions() {
        return conditions;
    }

    public java.util.Collection<Definition> definitions() {
        return definitions.values();
    }

    public void register(Definition definition) {
        definitions.put(definition.id, definition);
    }

    /** Drops every registered definition (used by the rules reload path). */
    public void clearDefinitions() {
        definitions.clear();
    }

    public Definition definition(String id) {
        return id == null ? null : definitions.get(id.toLowerCase());
    }

    /** Convenience for sign handlers and other imperative callers. */
    public void execute(Player player, String type, Map<String, Object> params) {
        actions.execute(player, type, params);
    }

    /** Runs the definition for the player; true when the actions executed. */
    public boolean run(Player player, Definition def) {
        if (def == null) return false;

        for (var cond : def.conditions.entrySet()) {
            Condition condition = conditions.get(cond.getKey());
            if (condition == null) continue;
            Map<String, Object> params = cond.getValue() instanceof Map<?, ?> m
                ? castParams(m) : Map.of("value", cond.getValue());
            if (!condition.passes(player, params)) {
                return false;
            }
        }

        Object permission = def.conditions.get("permission");
        if (permission != null && !String.valueOf(permission).isBlank()
            && !player.hasPermission(String.valueOf(permission))) {
            return false;
        }

        int cooldown = Params.intVal(def.conditions.get("cooldown"), 0);
        String key = "cooldown:" + def.id;
        if (cooldown > 0) {
            long last = storage.getRuntime("reactor", key, player.getUniqueId(), -1);
            if (last >= 0) {
                long remaining = (cooldown * 1000L - (System.currentTimeMillis() - last)) / 1000;
                if (remaining > 0) {
                    Params.send(player, "<red>Wait " + remaining + "s before using this again.");
                    return false;
                }
            }
        }

        double cost = Params.doubleVal(def.conditions.get("cost"), 0);
        if (cost > 0) {
            if (economy == null) {
                Params.send(player, "<red>No economy is available for this.");
                return false;
            }
            if (!economy.has(player, cost)) {
                Params.send(player, "<red>You need <yellow>" + cost + "</yellow> for this.");
                return false;
            }
            economy.withdrawPlayer(player, cost);
        }

        for (var action : def.actions) {
            actions.execute(player, action.getKey(), action.getValue());
        }

        if (cooldown > 0) {
            storage.setRuntime("reactor", key, player.getUniqueId(), System.currentTimeMillis());
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castParams(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }
}
