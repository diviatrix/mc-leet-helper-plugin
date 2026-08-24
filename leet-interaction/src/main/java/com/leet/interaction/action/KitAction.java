package com.leet.interaction.action;

import com.leet.core.reactor.Action;
import com.leet.interaction.LeetInteraction;
import org.bukkit.entity.Player;

import java.util.Map;

/** type: kit — kit: <name> from feature.kits.<name> in interaction.yml. */
public final class KitAction implements Action {

    private final LeetInteraction plugin;

    public KitAction(LeetInteraction plugin) {
        this.plugin = plugin;
    }

    @Override
    public String type() {
        return "kit";
    }

    @Override
    public void execute(Player player, Map<String, Object> params) {
        plugin.feature().giveKit(player, com.leet.core.reactor.Params.str(params.get("kit")));
    }
}
