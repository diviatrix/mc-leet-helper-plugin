package com.leet.core.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tiny Material-loading helpers shared by every feature that whitelists
 * materials by name from its YAML config. Invalid names are logged at WARNING
 * with the {@code tag} prefix and skipped (or replaced by {@code fallback}
 * when supplied — the legacy {@code SkillConfig} behavior, which silently
 * substitutes {@link Material#STONE} for typos so a misconfigured block
 * doesn't break a skill's runtime). The 5 features that whitelist materials
 * (auto_crop, tree_feller, durability, xp, skills) all funnel through here.
 */
public final class MaterialSets {

    private MaterialSets() {
    }

    /**
     * Parses a YAML list of Material names into a Set. Invalid entries are
     * logged and skipped.
     */
    public static Set<Material> readSet(Logger logger, List<String> names, String tag) {
        Set<Material> set = new HashSet<>();
        for (String name : names) {
            Material m = parseMaterial(logger, name, tag, null);
            if (m != null) set.add(m);
        }
        return set;
    }

/**
 * Parses a YAML {@code <name>: <int>} section into a Material -> int map.
 * Invalid material names are logged and skipped.
 */
    public static Map<Material, Integer> readMap(Logger logger, ConfigurationSection section, String tag) {
        Map<Material, Integer> map = new HashMap<>();
        if (section == null) return map;
        for (String name : section.getKeys(false)) {
            Material m = parseMaterial(logger, name, tag, null);
            if (m != null) map.put(m, section.getInt(name));
        }
        return map;
    }

    /**
     * Resolves a single YAML material name to a {@link Material}. The caller
     * supplies a {@code fallback} for legacy call sites that silently substituted
     * {@link Material#STONE} for typos so a misconfigured block would not break
     * runtime; new code should pass {@code null} so invalid entries are dropped.
     */
    public static Material readOne(Logger logger, String name, String tag, Material fallback) {
        return parseMaterial(logger, name, tag, fallback);
    }

    /**
     * Parses a YAML list of Material names into a List (preserving order).
     * Invalid entries either log + skip (fallback = null) or substitute the
     * supplied fallback (the {@code SkillConfig} legacy behavior).
     */
    public static java.util.List<Material> readList(Logger logger, List<String> names, String tag, Material fallback) {
        java.util.List<Material> list = new java.util.ArrayList<>();
        for (String name : names) {
            Material m = parseMaterial(logger, name, tag, fallback);
            if (m != null) list.add(m);
        }
        return list;
    }

    private static Material parseMaterial(Logger logger, String name, String tag, Material fallback) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid material in " + tag + ": " + name);
            return fallback;
        }
    }
}