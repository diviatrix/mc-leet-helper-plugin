package com.leet.helper.command;

import com.leet.helper.Core;
import com.leet.helper.feature.BackFeature;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BackCommand implements CommandExecutor {

    private final Core plugin;

    public BackCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        plugin.featureManager().get("back").ifPresent(feature -> {
            if (feature instanceof BackFeature backFeature) {
                backFeature.teleportBack(player);
            }
        });
        return true;
    }
}
