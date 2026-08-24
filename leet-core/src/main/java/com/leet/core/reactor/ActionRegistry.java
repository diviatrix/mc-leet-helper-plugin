package com.leet.core.reactor;

import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Type name -> Action. Core registers the generic built-ins; feature plugins
 * contribute their domain actions through the same registry at boot.
 */
public final class ActionRegistry {

    private final Map<String, Action> actions = new LinkedHashMap<>();

    public void register(Action action) {
        actions.put(action.type(), action);
    }

    public Action get(String type) {
        return type == null ? null : actions.get(type.toLowerCase(Locale.ROOT));
    }

    public void execute(Player player, String type, Map<String, Object> params) {
        Action action = get(type);
        if (action == null) return;
        action.execute(player, params == null ? Map.of() : params);
    }
}
