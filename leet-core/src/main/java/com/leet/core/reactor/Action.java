package com.leet.core.reactor;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * One executable step of a definition. Implementations are stateless and
 * registered by lowercase type name in the shared {@link ActionRegistry}.
 * Any plugin can contribute actions into core's reactor.
 */
public interface Action {

    String type();

    void execute(Player player, Map<String, Object> params);
}
