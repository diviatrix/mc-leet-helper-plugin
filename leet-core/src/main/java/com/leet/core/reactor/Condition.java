package com.leet.core.reactor;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * A predicate a player must pass before a definition's actions run. Unlike the
 * engine's built-in gating (permission / cooldown / cost), conditions are
 * pluggable: any plugin can register domain conditions (e.g. LeetInteraction's
 * {@code reputation}, LeetSkills' {@code skill-level}).
 */
public interface Condition {

    String type();

    /** Params come from the definition's {@code conditions.<type>} section. */
    boolean passes(Player player, Map<String, Object> params);
}
