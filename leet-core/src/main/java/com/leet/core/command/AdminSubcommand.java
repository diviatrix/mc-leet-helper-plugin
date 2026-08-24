package com.leet.core.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * A subcommand contributed into /leeta by another plugin (e.g. LeetInteraction's
 * bind/unbind). Registered through {@code CoreApi.registerAdminSubcommand}.
 */
public interface AdminSubcommand {

    /** Handles /leeta <name> [args...]. */
    void handle(CommandSender sender, String[] args);

    /** Tab completion for the args after the subcommand name (may return empty). */
    default List<String> tab(CommandSender sender, String[] args) {
        return List.of();
    }
}
