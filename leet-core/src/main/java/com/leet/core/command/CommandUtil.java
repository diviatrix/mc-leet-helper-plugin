package com.leet.core.command;

import java.util.ArrayList;
import java.util.List;

/** Small shared helpers for command tab completion. */
public final class CommandUtil {

    private CommandUtil() {
    }

    /** Returns only the options that start with {@code prefix} (case-insensitive). */
    public static List<String> filterPrefix(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        String p = prefix == null ? "" : prefix.toLowerCase();
        for (String option : options) {
            if (option.toLowerCase().startsWith(p)) {
                result.add(option);
            }
        }
        return result;
    }
}
