package com.leet.helper.feature.skills;

import com.leet.helper.Core;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The skill-tree topology read from {@code features/skill-tree.yml}. It holds
 * which skills appear in the outer ring vs the advanced band (in display order)
 * and each skill's prerequisite. This is deliberately separate from the skill
 * *definitions* ({@code features/skills.yml}, name/effects/XP per skill) so an
 * admin can compose their own tree — reorder or drop skills, add customs,
 * rewire prerequisites — without touching a skill's definition.
 */
public final class SkillTreeConfig {

    /** A skill's prerequisite: the required skill id and its minimum level. */
    public record Prerequisite(String skill, int level) {
        /** The "no prerequisite" marker (null skill, no required level). */
        public static Prerequisite none() {
            return new Prerequisite(null, 0);
        }

        public boolean isPresent() {
            return skill != null && !skill.isEmpty();
        }
    }

    private final List<String> ring;
    private final List<String> advanced;
    private final Map<String, Prerequisite> requires;

    private SkillTreeConfig(List<String> ring, List<String> advanced, Map<String, Prerequisite> requires) {
        this.ring = ring;
        this.advanced = advanced;
        this.requires = requires;
    }

    public static SkillTreeConfig read(Core plugin, YamlConfiguration cfg) {
        List<String> ring = new ArrayList<>(cfg.getStringList("tree.ring"));
        List<String> advanced = new ArrayList<>(cfg.getStringList("tree.advanced"));
        Map<String, Prerequisite> requires = new LinkedHashMap<>();

        ConfigurationSection section = cfg.getConfigurationSection("tree");
        if (section != null) {
            ConfigurationSection req = section.getConfigurationSection("requires");
            if (req != null) {
                for (String skillId : req.getKeys(false)) {
                    ConfigurationSection entry = req.getConfigurationSection(skillId);
                    if (entry == null) continue;
                    String prereq = entry.getString("skill");
                    requires.put(skillId.toLowerCase(),
                        new Prerequisite(prereq == null ? null : prereq.toLowerCase(),
                            Math.max(1, entry.getInt("level", 1))));
                }
            }
        }
        return new SkillTreeConfig(ring, advanced, requires);
    }

    /** Ordered ids of the ring skills shown around the center; empty if unset. */
    public List<String> ring() {
        return ring;
    }

    /** Ordered ids of the advanced skills shown in the lower band. */
    public List<String> advanced() {
        return advanced;
    }

    /** The prerequisite for a skill (absent = no prerequisite, i.e. open). */
    public Prerequisite requirementFor(String skillId) {
        return skillId == null ? Prerequisite.none() : requires.getOrDefault(skillId.toLowerCase(), Prerequisite.none());
    }
}