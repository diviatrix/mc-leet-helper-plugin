package com.leet.skills;

import com.leet.core.feature.AbstractFeature;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Opens the /skills skill-tree GUI for a player. */
public class SkillsCommand implements CommandExecutor {

    private final LeetSkills plugin;

    public SkillsCommand(LeetSkills plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        AbstractFeature feature = plugin.core().featureRegistry().get("skills").orElse(null);
        if (feature instanceof SkillsFeature skillsFeature) {
            // The single group-accessible node (leet.feat.skills, default-denied)
            // gates the menu exactly like the passive effects — one switch for a
            // registered group, none for an unregistered default-group player.
            if (!skillsFeature.appliesTo(player)) {
                player.sendMessage(com.leet.core.util.MiniMessageUtil.deserialize("<red>You don't have access to skills."));
                return true;
            }
            skillsFeature.openTree(player);
        } else {
            player.sendMessage("Skills are not available.");
        }
        return true;
    }
}
