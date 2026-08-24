package com.leet.interaction;

import com.leet.core.CoreApi;
import com.leet.core.plugin.FeaturePluginSupport;
import com.leet.core.storage.StorageManager;
import com.leet.interaction.chest.ChestRegistry;
import com.leet.interaction.command.BindSubcommand;
import com.leet.interaction.definition.DefinitionRegistry;
import com.leet.interaction.quest.QuestManager;
import com.leet.interaction.reputation.ReputationManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class LeetInteraction extends JavaPlugin {

    private CoreApi core;
    private StorageManager storage;
    private DefinitionRegistry definitions;
    private ChestRegistry chests;
    private QuestManager quests;
    private ReputationManager reputation;
    private InteractionFeature feature;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FeaturePluginSupport.saveResourceIfMissing(this, "features/interaction.yml");
        saveDefinitions();

        core = FeaturePluginSupport.requireCore(this);
        if (core == null) return;

        storage = new StorageManager(getDataFolder(), getLogger());
        definitions = new DefinitionRegistry(this);
        chests = new ChestRegistry(this);
        reputation = new ReputationManager(this);
        quests = new QuestManager(this, definitions, reputation);

        feature = new InteractionFeature(core, this);
        if (!core.registerFeature(feature)) {
            getLogger().severe("Failed to register the 'interaction' feature with LeetCore. LeetInteraction will not enable.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        BindSubcommand bind = new BindSubcommand(this, true);
        BindSubcommand unbind = new BindSubcommand(this, false);
        core.registerAdminSubcommand("bind", bind);
        core.registerAdminSubcommand("unbind", unbind);
        core.registerAdminSubcommand("bindings", new com.leet.interaction.command.BindingsSubcommand(this));

        var reactor = core.reactor();
        reactor.actions().register(new com.leet.interaction.action.KitAction(this));
        reactor.actions().register(new com.leet.interaction.action.ChestAction(this));
        reactor.actions().register(new com.leet.interaction.action.QuestAction(this));
        reactor.actions().register(new com.leet.interaction.action.ReputationAction(this));
        reactor.conditions().register(new com.leet.core.reactor.Condition() {
            @Override
            public String type() {
                return "reputation";
            }

            @Override
            public boolean passes(org.bukkit.entity.Player player, java.util.Map<String, Object> params) {
                int min = com.leet.core.reactor.Params.intVal(
                    params.getOrDefault("value", params.get("min")), 0);
                return reputation.get(player.getUniqueId()) >= min;
            }
        });

        getLogger().info("LeetInteraction registered the 'interaction' feature with LeetCore ("
            + definitions.size() + " definition(s)).");
    }

    @Override
    public void onDisable() {
        if (feature != null) {
            feature.disable();
        }
        if (storage != null) {
            storage.close();
        }
    }

    private void saveDefinitions() {
        for (String name : new String[] {"warp_npc.yml", "shopkeeper.yml", "quest_blacksmith.yml"}) {
            FeaturePluginSupport.saveResourceIfMissing(this, "definitions/" + name);
        }
    }

    public CoreApi core() {
        return core;
    }

    public StorageManager storage() {
        return storage;
    }

    public DefinitionRegistry definitions() {
        return definitions;
    }

    public ChestRegistry chests() {
        return chests;
    }

    public QuestManager quests() {
        return quests;
    }

    public ReputationManager reputation() {
        return reputation;
    }

    public InteractionFeature feature() {
        return feature;
    }
}
