package com.leet.interaction.action;

import com.leet.core.reactor.Action;
import com.leet.core.reactor.Params;
import com.leet.interaction.LeetInteraction;
import org.bukkit.entity.Player;

import java.util.Map;

/** type: open-chest — id: <chest-id> from the chest registry ([Chest] bindings). */
public final class ChestAction implements Action {

    private final LeetInteraction plugin;

    public ChestAction(LeetInteraction plugin) {
        this.plugin = plugin;
    }

    @Override
    public String type() {
        return "open-chest";
    }

    @Override
    public void execute(Player player, Map<String, Object> params) {
        plugin.feature().openBoundChest(player, Params.str(params.get("id")));
    }
}
