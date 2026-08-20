package com.leet.core.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;

/**
 * Opt-in role: a feature that delivers feedback messages with a configurable
 * message type (ACTION_BAR / CHAT / TITLE) in MiniMessage format.
 *
 * Default implementation reads a {@code messageType()} and {@code messages()}
 * map supplied by the feature; a missing or empty template is a silent no-op.
 */
public interface MessagingFeature {

    Map<String, String> messages();

    String messageType();

    /**
     * Sends the template for {@code key} with {@code placeholder/value} pairs,
     * delivered per the configured message type.
     */
    default void sendMessage(Player player, String key, String... placeholders) {
        String template = messages().get(key);
        if (template == null || template.isEmpty()) return;

        String result = template;
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            result = result.replace(placeholders[i], placeholders[i + 1]);
        }

        Component component = MiniMessage.miniMessage().deserialize(result);

        switch (messageType().toUpperCase()) {
            case "CHAT":
                player.sendMessage(component);
                break;
            case "TITLE":
                player.showTitle(Title.title(
                    component, Component.empty(),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))
                ));
                break;
            case "ACTION_BAR":
            default:
                player.sendActionBar(component);
                break;
        }
    }
}
