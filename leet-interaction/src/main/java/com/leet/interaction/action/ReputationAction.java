package com.leet.interaction.action;

import com.leet.core.reactor.Action;
import com.leet.core.reactor.Params;
import com.leet.interaction.LeetInteraction;
import org.bukkit.entity.Player;

import java.util.Map;

/** type: reputation — amount: <n> (may be negative). */
public final class ReputationAction implements Action {

    private final LeetInteraction plugin;

    public ReputationAction(LeetInteraction plugin) {
        this.plugin = plugin;
    }

    @Override
    public String type() {
        return "reputation";
    }

    @Override
    public void execute(Player player, Map<String, Object> params) {
        plugin.reputation().add(player.getUniqueId(), Params.intVal(params.get("amount"), 0));
    }
}
