package com.leet.core.reactor;

import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Map;

/** Shared parameter coercion helpers for actions and conditions. */
public final class Params {

    private Params() {
    }

    public static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public static int intVal(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static double doubleVal(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (Exception e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    public static java.util.List<Map<String, Object>> itemMaps(Object o) {
        if (o instanceof java.util.List<?> list) {
            return list.stream()
                .filter(e -> e instanceof Map<?, ?>)
                .map(e -> (Map<String, Object>) e)
                .toList();
        }
        return java.util.List.of();
    }

    public static void send(CommandSender to, String miniMessage) {
        to.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(miniMessage));
    }
}
