package com.leet.core.reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A declarative interaction: an id, the triggers it listens to (for event-rules
 * like join/death/consume), gating conditions, and an ordered action list.
 *
 * <p>Condition values are either scalars ({@code cooldown: 5}) or sections
 * ({@code has-item: {item: ..., amount: ...}}); the engine passes sections
 * through as condition params and wraps scalars as {@code value}.
 */
public final class Definition {

    public final String id;
    public final List<String> triggers;
    public final Map<String, Object> conditions;
    public final List<Map.Entry<String, Map<String, Object>>> actions;

    public Definition(String id, List<String> triggers,
                      Map<String, Object> conditions,
                      List<Map.Entry<String, Map<String, Object>>> actions) {
        this.id = id;
        this.triggers = triggers;
        this.conditions = conditions;
        this.actions = actions;
    }

    public boolean triggersOn(String trigger) {
        return triggers.contains(trigger);
    }

    public static Definition of(String id, List<Map.Entry<String, Map<String, Object>>> actions) {
        return new Definition(id, List.of(), Map.of(), actions == null ? new ArrayList<>() : actions);
    }
}
