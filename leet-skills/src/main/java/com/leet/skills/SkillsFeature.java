package com.leet.skills;

import com.leet.core.CoreApi;
import com.leet.core.feature.AbstractFeature;
import com.leet.core.feature.BlockBreakerFeature;
import com.leet.core.feature.CooldownAware;
import com.leet.core.feature.MessagingFeature;
import com.leet.core.storage.StorageManager;
import com.leet.core.util.MiniMessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The skills feature: a per-player skill tree (stamina at the center, ring
 * skills around it, advanced skills below) leveled by spending vanilla XP points,
 * driven through the /skills GUI. This class is the state / gating / leveling /
 * tree hub. All passive skill effects live in {@link SkillPassiveHandler}, which
 * this feature wires into the owner-plugin's listener lifecycle.
 */
public class SkillsFeature extends AbstractFeature implements CooldownAware, MessagingFeature, BlockBreakerFeature {

    public static final String STAMINA = "stamina";

    private static final MiniMessage MM = MiniMessageUtil.miniMessage();

    private final Map<String, SkillConfig> skills = new LinkedHashMap<>();
    private final Map<UUID, SkillState> states = new HashMap<>();
    private final StorageManager storage;

    private SkillTreeConfig tree;

    private SkillsGui gui;
    private String guiTitle = "Skills";
    private int guiRows = 6;

    private final SkillPassiveHandler passive;
    private int schedulerTask = -1;
    private int diverTask = -1;

    public SkillsFeature(CoreApi core, JavaPlugin owner, StorageManager storage) {
        super(core, owner);
        this.storage = storage;
        this.passive = new SkillPassiveHandler(this);
    }

    @Override
    public String featureId() {
        return "skills";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        skills.clear();
        guiTitle = cfg.getString("feature.gui.title", "Skills");
        guiRows = Math.max(4, Math.min(9, cfg.getInt("feature.gui.rows", 6)));

        YamlConfiguration treeCfg = loadAndMerge("skill-tree.yml");
        tree = treeCfg == null ? null : SkillTreeConfig.read(owner, treeCfg);

        passive.configure(
            Math.max(1, cfg.getInt("feature.tree-feller.max-blocks", 100)),
            cfg.getBoolean("feature.auto-crop.require-mature", true),
            cfg.getDouble("feature.double-jump.horizontal-multiplier", 0.25),
            cfg.getDouble("feature.double-jump.vertical-multiplier", 1.0)
        );

        ConfigurationSection skillsSection = cfg.getConfigurationSection("feature.skills");
        if (skillsSection != null) {
            for (String key : skillsSection.getKeys(false)) {
                ConfigurationSection section = skillsSection.getConfigurationSection(key);
                if (section != null) {
                    skills.put(key.toLowerCase(), SkillConfig.read(owner, section));
                }
            }
        }
        validateBindings();

        gui = new SkillsGui(this);
    }

    @Override
    public void enable() {
        super.enable();
        if (enabled) {
            Bukkit.getPluginManager().registerEvents(passive, owner);
            schedulerTask = Bukkit.getScheduler().runTaskTimer(owner, passive::tickRegen, 20L, 20L).getTaskId();
            diverTask = Bukkit.getScheduler().runTaskTimer(owner, passive::tickDiver, 2L, 2L).getTaskId();
        }
    }

    @Override
    public void disable() {
        if (schedulerTask >= 0) {
            Bukkit.getScheduler().cancelTask(schedulerTask);
            schedulerTask = -1;
        }
        if (diverTask >= 0) {
            Bukkit.getScheduler().cancelTask(diverTask);
            diverTask = -1;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            passive.resetPlayer(player);
        }
        states.clear();
        org.bukkit.event.HandlerList.unregisterAll(passive);
        super.disable();
    }

    // --- state / leveling API (also used by SkillsGui) ---

    SkillState getState(Player player) {
        SkillState state = states.get(player.getUniqueId());
        if (state == null) {
            state = SkillState.load(storage, player.getUniqueId());
            states.put(player.getUniqueId(), state);
        }
        return state;
    }

    /** Drops cached per-player state (on quit) so the cache does not grow unbounded. */
    void dropState(Player player) {
        states.remove(player.getUniqueId());
    }

    /** The player's current level of a skill (handles the feature-permission overlay). */
    public int levelOf(Player player, String skillId) {
        return currentLevel(player, skillId);
    }

    public int currentLevel(Player player, String skillId) {
        SkillConfig skill = skill(skillId);
        if (skill != null && hasFeaturePermission(player, skillId)) return skill.maxLevel();
        return getState(player).level(skillId);
    }

    public int nextCost(Player player, String skillId) {
        SkillConfig skill = skill(skillId);
        if (skill == null) return -1;
        if (hasFeaturePermission(player, skillId)) return -1; // already acquired via the feature permission
        return skill.costForNext(getState(player).level(skillId));
    }

    public boolean hasXp(Player player, int cost) {
        return cost <= 0 || player.getTotalExperience() >= cost;
    }

    public boolean levelUp(Player player, String skillId) {
        SkillConfig skill = skill(skillId);
        if (skill == null) return false;
        if (hasFeaturePermission(player, skillId)) return false; // feature permission already provides it
        SkillTreeConfig.Prerequisite requirement = requirementFor(skillId);
        if (!prerequisiteSatisfied(player, skill)) {
            sendMessage(player, "locked",
                "<skill>", skill.name(),
                "<required>", nameOf(requirement.skill()),
                "<require-level>", String.valueOf(requirement.level()));
            return false;
        }
        SkillState state = getState(player);
        int level = state.level(skillId);
        int cost = skill.costForNext(level);
        if (cost < 0) {
            sendMessage(player, "max-level", "<skill>", skill.name());
            return false;
        }
        if (!hasXp(player, cost)) {
            sendMessage(player, "insufficient-xp",
                "<cost>", String.valueOf(cost), "<needed>", String.valueOf(cost));
            return false;
        }
        player.giveExp(-cost);
        state.setLevel(skillId, level + 1);
        state.save(storage, player.getUniqueId());
        sendMessage(player, "level-up",
            "<skill>", skill.name(), "<level>", String.valueOf(level + 1), "<cost>", String.valueOf(cost));
        passive.reapply(player);
        return true;
    }

    public void openTree(Player player) {
        if (!check(player)) {
            sendMessage(player, "feature-off");
            return;
        }
        gui.openTree(player);
    }

    Inventory newInventory(Player player) {
        return Bukkit.createInventory(null, guiRows * 9, MM.deserialize(guiTitle));
    }

    public int rows() {
        return guiRows;
    }

    public SkillConfig skill(String id) {
        return skills.get(id);
    }

    public List<String> ringSkillIds() {
        return tree == null ? List.of() : tree.ring();
    }

    public List<String> advancedSkillIds() {
        return tree == null ? List.of() : tree.advanced();
    }

    /** The GUI slot for an advanced skill (-1 when unspecified). */
    public int advancedSlot(String skillId) {
        return tree == null ? -1 : tree.advancedSlot(skillId);
    }

    /**
     * Whether the standalone feature bound to this skill is currently acting for
     * the player (its own gating passes: enabled, permission, personal toggle,
     * world). When true the skill is treated as already acquired and the feature
     * provides the effect, so the skill's own passive does not also fire (no
     * double invocation) and the skill is not offered for leveling.
     */
    public boolean hasFeaturePermission(Player player, String skillId) {
        SkillConfig skill = skill(skillId);
        String featureId = skill == null ? null : skill.boundFeature();
        if (featureId == null) return false;
        // Treat the skill as already-acquired only when the bound feature is
        // actually acting for this player (enabled + permission + personal toggle
        // + world). If the feature is off for them, the skill stays levelable.
        return core.featureRegistry().get(featureId)
            .map(f -> f.appliesTo(player)).orElse(false);
    }

    /** The configured prerequisite for a skill (absent = open). */
    public SkillTreeConfig.Prerequisite requirementFor(String skillId) {
        return tree == null ? SkillTreeConfig.Prerequisite.none() : tree.requirementFor(skillId);
    }

    /** Whether the player currently meets this skill's prerequisite. */
    public boolean prerequisiteSatisfied(Player player, SkillConfig skill) {
        if (hasFeaturePermission(player, skill.id())) return true;
        SkillTreeConfig.Prerequisite requirement = requirementFor(skill.id());
        if (!requirement.isPresent()) return true;
        SkillConfig requirementSkill = skill(requirement.skill());
        if (requirementSkill == null) return true; // unknown prerequisite -> treat as open
        return currentLevel(player, requirement.skill()) >= requirement.level();
    }

    public String nameOf(String skillId) {
        SkillConfig skill = skill(skillId);
        return skill == null ? skillId : skill.name();
    }

    // --- per-player skill toggles (persisted in the skills plugin's own store) ---

    /** Whether this skill has a per-player on/off toggle (from the detail window). */
    public boolean skillHasToggle(String skillId) {
        SkillConfig skill = skill(skillId);
        return skill != null && skill.toggleable();
    }

    /**
     * Whether this skill's per-player toggle is currently ON (default on for
     * untoggleable skills. Stored in the skills plugin's own DB keyed by the skill
     * id — independent of the standalone feature's /leet toggle.
     */
    public boolean skillEnabled(Player player, String skillId) {
        if (!skillHasToggle(skillId)) return true;
        Boolean toggle = storage.getUserToggle(skillId, player.getUniqueId());
        return !Boolean.FALSE.equals(toggle);
    }

    /** Flips/lets the GUI set this skill's per-player toggle (in the local store). */
    public void setSkillEnabled(Player player, String skillId, boolean on) {
        if (!skillHasToggle(skillId)) return;
        storage.setUserToggle(skillId, player.getUniqueId(), on);
    }

    /** Warn if a skill's bound core feature id is not registered. */
    private void validateBindings() {
        if (tree == null) return;
        tree.validate(owner);
        for (SkillConfig skill : skills.values()) {
            String featureId = skill.boundFeature();
            if (featureId != null && core.featureRegistry().get(featureId).isEmpty()) {
                owner.getLogger().warning(
                    "Skill '" + skill.id() + "' binds to core feature '" + featureId
                    + "' which is not registered; 'already learned' overlay disabled for it.");
            }
        }
    }
}
