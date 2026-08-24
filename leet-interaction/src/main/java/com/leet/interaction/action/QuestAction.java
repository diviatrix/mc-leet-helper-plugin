package com.leet.interaction.action;

import com.leet.core.reactor.Action;
import com.leet.core.reactor.Params;
import com.leet.interaction.LeetInteraction;
import org.bukkit.entity.Player;

import java.util.Map;

/** type: quest — quest: <id>. Runs the accept / turn-in flow for the quest. */
public final class QuestAction implements Action {

    private final LeetInteraction plugin;

    public QuestAction(LeetInteraction plugin) {
        this.plugin = plugin;
    }

    @Override
    public String type() {
        return "quest";
    }

    @Override
    public void execute(Player player, Map<String, Object> params) {
        plugin.quests().handle(player, Params.str(params.get("quest")));
    }
}
