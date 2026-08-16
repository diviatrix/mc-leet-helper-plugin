package com.leet.helper.command;

import com.leet.helper.Core;
import com.leet.helper.feature.AbstractFeature;
import com.leet.helper.feature.skills.SkillsFeature;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Opens the /skills skill-tree GUI for a player. */
public class SkillsCommand implements CommandExecutor {

    private final Core plugin;

    public SkillsCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        AbstractFeature feature = plugin.featureManager().get("skills").orElse(null);
        if (feature instanceof SkillsFeature skillsFeature) {
            skillsFeature.openTree(player);
        } else {
            player.sendMessage("Skills are not available.");
        }
        return true;
    }
}