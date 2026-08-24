package com.leet.core.reactor;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Fires the reactor's rule definitions on generic player events: join, death,
 * block-break and consume-item. Definitions opt in with a {@code triggers:}
 * list (e.g. {@code triggers: [join]}). Interaction surfaces (signs, NPCs,
 * bound blocks) fire definitions directly through their own listeners.
 */
public final class ReactorTriggers implements Listener {

    /** The trigger tag names recognized by this listener. */
    public static final String JOIN = "join";
    public static final String DEATH = "death";
    public static final String BLOCK_BREAK = "block-break";
    public static final String CONSUME_ITEM = "consume-item";

    private final Reactor reactor;

    public ReactorTriggers(Reactor reactor) {
        this.reactor = reactor;
    }

    private void fire(String trigger, Player player) {
        for (Definition def : reactor.definitions()) {
            if (def.triggersOn(trigger)) {
                reactor.run(player, def);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        fire(JOIN, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        fire(DEATH, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        fire(BLOCK_BREAK, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        fire(CONSUME_ITEM, event.getPlayer());
    }
}
