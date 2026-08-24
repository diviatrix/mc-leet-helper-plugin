package com.leet.skills;

import com.leet.core.CoreApi;
import com.leet.core.plugin.FeaturePluginSupport;
import com.leet.core.storage.StorageManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * LeetSkills — the skill-tree plugin. It soft-depends on LeetCore, looks up the
 * shared {@link CoreApi} service, and contributes the {@code skills} feature
 * into core's shared feature registry. Without LeetCore it disables itself
 * gracefully (the server keeps running without the skill tree). It keeps its own
 * SQLite {@link StorageManager} for skill-level persistence — plugin data stays
 * in this plugin, not in core's folder.
 */
public final class LeetSkills extends JavaPlugin {

    private CoreApi core;
    private StorageManager storage;

    @Override
    public void onEnable() {
        saveFeatureConfigs();
        core = FeaturePluginSupport.requireCore(this);
        if (core == null) return;

        storage = new StorageManager(getDataFolder(), getLogger());
        SkillsFeature skills = new SkillsFeature(core, this, storage);
        if (!core.registerFeature(skills)) {
            getLogger().severe("Failed to register skills feature with LeetCore. LeetSkills will not enable.");
            org.bukkit.Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        getCommand("skills").setExecutor(new SkillsCommand(this));

        registerReactorAdapters(skills);
        getLogger().info("LeetSkills registered the 'skills' feature with LeetCore.");
    }

    /**
     * Contributes the plugin's domain seam into core's reactor: a
     * {@code skill-level} condition ({@code skill: <id>, level: <min>}) and a
     * {@code skill-level-up} action ({@code skill: <id>}, spends the player's XP).
     */
    private void registerReactorAdapters(SkillsFeature skills) {
        if (core.reactor() == null) return;
        core.reactor().conditions().register(new com.leet.core.reactor.Condition() {
            @Override
            public String type() {
                return "skill-level";
            }

            @Override
            public boolean passes(org.bukkit.entity.Player player, java.util.Map<String, Object> params) {
                String skill = com.leet.core.reactor.Params.str(params.get("skill"));
                int min = com.leet.core.reactor.Params.intVal(params.get("level"), 1);
                return skill != null && skills.levelOf(player, skill) >= min;
            }
        });
        core.reactor().actions().register(new com.leet.core.reactor.Action() {
            @Override
            public String type() {
                return "skill-level-up";
            }

            @Override
            public void execute(org.bukkit.entity.Player player, java.util.Map<String, Object> params) {
                String skill = com.leet.core.reactor.Params.str(params.get("skill"));
                if (skill != null) {
                    skills.levelUp(player, skill);
                }
            }
        });
    }

    @Override
    public void onDisable() {
        FeaturePluginSupport.disableFeature(core, "skills");
        if (storage != null) {
            storage.close();
        }
        core = null;
        storage = null;
    }

    /** Writes this plugin's default feature configs on first run. */
    private void saveFeatureConfigs() {
        FeaturePluginSupport.saveResourceIfMissing(this, "features/skills.yml");
        FeaturePluginSupport.saveResourceIfMissing(this, "features/skill-tree.yml");
    }

    /** The bound CoreApi (valid only after onEnable). Used by SkillsCommand. */
    CoreApi core() {
        return core;
    }
}
