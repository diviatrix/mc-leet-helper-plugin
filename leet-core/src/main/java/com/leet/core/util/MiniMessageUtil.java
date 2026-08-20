package com.leet.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

public class MiniMessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /** Shared {@link MiniMessage} instance — use this instead of {@code MiniMessage.miniMessage()}. */
    public static MiniMessage miniMessage() {
        return MINI_MESSAGE;
    }

    public static Component deserialize(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        return MINI_MESSAGE.deserialize(input);
    }

    public static void sendActionBar(Player player, String miniMessage) {
        player.sendActionBar(deserialize(miniMessage));
    }

    public static void sendChat(Player player, String miniMessage) {
        player.sendMessage(deserialize(miniMessage));
    }
}
