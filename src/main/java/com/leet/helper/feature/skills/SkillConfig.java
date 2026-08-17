package com.leet.helper.feature.skills;

import com.leet.helper.Core;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Data-driven, generic description of a single skill read from
 * {@code features/skills.yml}. A skill is a named body of generic
 * {@link Effect}s plus the material collections its passives work on — there is
 * no per-skill class. The passive engine and the skill-tree UI both read the
 * effects list, so "current modifier" and the detail screens stay in sync.
 */
public final class SkillConfig {

    /** A single passive effect. Either a per-level increment or a level-gated unlock. */
    public static final class Effect {
        private final String id;
        private final String name;
        private final String desc;
        private final Material icon;
        private final double perLevel;
        private final int unlockAt;
        private final double unlockValue;

        Effect(String id, String name, String desc, Material icon, double perLevel, int unlockAt, double unlockValue) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.icon = icon;
            this.perLevel = perLevel;
            this.unlockAt = unlockAt;
            this.unlockValue = unlockValue;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        /** Short, value-free phrase describing the effect (shown with its current % on the tree). */
        public String desc() {
            return desc;
        }

        public Material icon() {
            return icon;
        }

        /** Per-level increment for numeric effects (0 for unlock effects). */
        public double perLevel() {
            return perLevel;
        }

        /** Level at which an unlock effect becomes active (0 = never, it's numeric). */
        public int unlockAt() {
            return unlockAt;
        }

        public double unlockValue() {
            return unlockValue;
        }
    }

    private final String id;
    private final String name;
    private final Material icon;
    private final int maxLevel;
    private final List<Integer> exp; // [i] = cost to go from level i to i+1
    private final List<String> lore;
    private final List<Effect> effects;

    // --- material / item collections the passives act on ---
    private final Set<Material> logs;
    private final Set<Material> minerBlocks;
    private final Set<Material> crops;
    private final List<Material> bonusItems;
    private final List<Material> qualityItems;

    private SkillConfig(Core plugin, String id, ConfigurationSection s) {
        this.id = id;
        this.name = s.getString("name", id);
        this.icon = parseMaterial(plugin, s.getString("icon", "STONE"), id, "icon", Material.STONE);
        this.maxLevel = s.getInt("max-level", 10);
        this.exp = s.getIntegerList("exp").isEmpty()
            ? defaultExp()
            : s.getIntegerList("exp");
        this.lore = s.getStringList("lore");

        this.effects = readEffects(plugin, s, id);

        this.logs = readMaterials(plugin, s, "logs", id);
        this.minerBlocks = readMaterials(plugin, s, "blocks", id);
        this.crops = readMaterials(plugin, s, "crops", id);
        this.bonusItems = readMaterialsList(plugin, s, "bonus-items", id);
        this.qualityItems = readMaterialsList(plugin, s, "quality-items", id);
    }

    public static SkillConfig read(Core plugin, ConfigurationSection section) {
        return new SkillConfig(plugin, section.getName(), section);
    }

    private static List<Effect> readEffects(Core plugin, ConfigurationSection s, String id) {
        List<Effect> list = new ArrayList<>();
        ConfigurationSection section = s.getConfigurationSection("effects");
        if (section == null) return list;
        for (String effectId : section.getKeys(false)) {
            ConfigurationSection e = section.getConfigurationSection(effectId);
            if (e == null) continue;
            String name = e.getString("name", effectId);
            String desc = e.getString("desc", name);
            Material icon = parseMaterial(plugin, e.getString("icon", "STONE"), id,
                "effects." + effectId + ".icon", Material.STONE);
            double perLevel = e.getDouble("per-level", 0);
            int unlockAt = e.getInt("unlock-at", 0);
            double unlockValue = e.getDouble("unlock-value", 0);
            list.add(new Effect(effectId, name, desc, icon, perLevel, unlockAt, unlockValue));
        }
        return list;
    }

    // --- effect lookups used by the passive engine and the GUI ---

    public Effect effect(String effectId) {
        for (Effect e : effects) {
            if (e.id.equals(effectId)) return e;
        }
        return null;
    }

    public List<Effect> effects() {
        return effects;
    }

    /** True when the effect is currently positive/active at the given level. */
    public boolean unlocked(String effectId, int level) {
        Effect e = effect(effectId);
        return e != null && e.unlockAt > 0 && level >= e.unlockAt;
    }

    /** The current modifier at the given level: {@code level * per-level} (numeric) or the flat unlock value (unlock). */
    public double valueAt(String effectId, int level) {
        Effect e = effect(effectId);
        if (e == null) return 0;
        if (e.perLevel > 0) return e.perLevel * Math.max(0, level);
        if (e.unlockAt > 0) return level >= e.unlockAt ? e.unlockValue : 0;
        return 0;
    }

    private static Material parseMaterial(Core plugin, String material, String id, String key, Material fallback) {
        try {
            return Material.valueOf(material);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material in skills." + id + "." + key + ": " + material);
            return fallback;
        }
    }

    private static Set<Material> readMaterials(Core plugin, ConfigurationSection s,
                                               String key, String id) {
        Set<Material> set = new HashSet<>();
        for (String name : s.getStringList(key)) {
            set.add(parseMaterial(plugin, name, id, key, Material.STONE));
        }
        return set;
    }

    private static List<Material> readMaterialsList(Core plugin, ConfigurationSection s,
                                                    String key, String id) {
        List<Material> list = new ArrayList<>();
        for (String name : s.getStringList(key)) {
            list.add(parseMaterial(plugin, name, id, key, Material.STONE));
        }
        return list;
    }

    /** Graceful fallback so an empty exp table still gives levelable skills. */
    private List<Integer> defaultExp() {
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            list.add(20 * i * i);
        }
        return list;
    }

    // --- accessors ---
    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Material icon() {
        return icon;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public List<Integer> exp() {
        return exp;
    }

    /** XP required to advance from {@code level} to {@code level+1}, or -1 if capped. */
    public int costForNext(int level) {
        if (level >= maxLevel) return -1;
        if (level >= exp.size()) return -1;
        return exp.get(level);
    }

    public List<String> lore() {
        return lore;
    }

    public Set<Material> logs() {
        return logs;
    }

    public Set<Material> minerBlocks() {
        return minerBlocks;
    }

    public Set<Material> crops() {
        return crops;
    }

    public List<Material> bonusItems() {
        return bonusItems;
    }

    public List<Material> qualityItems() {
        return qualityItems;
    }
}