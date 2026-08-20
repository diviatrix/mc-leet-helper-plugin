package com.leet.skills;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.logging.Level;

/**
 * The skill-tree topology read from {@code features/skill-tree.yml}. It holds
 * which skills appear in the outer ring vs the advanced band (in display order),
 * each skill's prerequisite, and the GUI slot each advanced skill occupies. This
 * is deliberately separate from the skill *definitions* ({@code features/skills.yml},
 * name/effects/XP per skill) so an admin can compose their own tree — reorder or
 * drop skills, add customs, rewire prerequisites and layout — without touching a
 * skill's definition.
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
    private final Map<String, Integer> slots;

    private SkillTreeConfig(List<String> ring, List<String> advanced,
                            Map<String, Prerequisite> requires, Map<String, Integer> slots) {
        this.ring = ring;
        this.advanced = advanced;
        this.requires = requires;
        this.slots = slots;
    }

    public static SkillTreeConfig read(Plugin owner, YamlConfiguration cfg) {
        List<String> ring = cfg.getStringList("tree.ring").stream().map(String::toLowerCase).toList();
        List<String> advanced = cfg.getStringList("tree.advanced").stream().map(String::toLowerCase).toList();
        Map<String, Prerequisite> requires = new LinkedHashMap<>();
        Map<String, Integer> slots = new LinkedHashMap<>();

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
            ConfigurationSection slotSection = section.getConfigurationSection("slots");
            if (slotSection != null) {
                for (String skillId : slotSection.getKeys(false)) {
                    slots.put(skillId.toLowerCase(), slotSection.getInt(skillId));
                }
            }
        }
        return new SkillTreeConfig(ring, advanced, requires, slots);
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

    /** The GUI slot for an advanced skill, or -1 when none is configured. */
    public int advancedSlot(String skillId) {
        return skillId == null ? -1 : slots.getOrDefault(skillId.toLowerCase(), -1);
    }

    /**
     * Warns when an advanced skill declared in the tree has no configured GUI slot
     * (it would silently not render). Each advanced id should have a matching
     * entry under {@code tree.slots}.
     */
    public void validate(Plugin owner) {
        for (String id : advanced) {
            if (!slots.containsKey(id.toLowerCase())) {
                owner.getLogger().log(Level.WARNING,
                    "skill-tree.yml: advanced skill '" + id + "' has no slot in tree.slots; it will not render.");
            }
        }
    }
}
