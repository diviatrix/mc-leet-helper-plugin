package com.leet.core;

import com.leet.core.command.BackCommand;
import com.leet.core.command.HelperCommand;
import com.leet.core.command.LeetCommand;
import com.leet.core.craft.CustomItemView;
import com.leet.core.feature.AbstractFeature;
import com.leet.core.feature.AutoCropFeature;
import com.leet.core.feature.BackFeature;
import com.leet.core.feature.DoubleJumpFeature;
import com.leet.core.feature.DurabilityFeature;
import com.leet.core.feature.FallDamageFeature;
import com.leet.core.feature.FeatureManager;
import com.leet.core.feature.FeatureRegistry;
import com.leet.core.feature.TreeFellerFeature;
import com.leet.core.feature.XpFeature;
import com.leet.core.gui.GuiManager;
import com.leet.core.plugin.FeaturePluginSupport;
import com.leet.core.reactor.Reactor;
import com.leet.core.storage.StorageManager;
import com.leet.core.util.MiniMessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * LeetCore — the sharing core plugin. It owns the shared engines (storage, item
 * registry, feature registry, resource-pack service), the seven standalone
 * "shared mechanics" features, and the cross-plugin commands (/leeta, /leet,
 * /back). The skills and crafting plugins look up {@link CoreApi} and contribute
 * their own features into the shared {@link FeatureManager}.
 */
public class LeetCore extends JavaPlugin implements CoreApi {

    private StorageManager storageManager;
    private FeatureManager featureManager;
    private Economy economy;
    private GuiManager guiManager;
    private Reactor reactor;
    private final java.util.Map<String, com.leet.core.command.AdminSubcommand> adminSubcommands =
        new java.util.LinkedHashMap<>();

    @Override
    public void onLoad() {
        storageManager = new StorageManager(getDataFolder(), getLogger());
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            Bukkit.getServicesManager().register(Economy.class, new com.leet.core.economy.LeetEconomy(storageManager),
                this, org.bukkit.plugin.ServicePriority.Lowest);
        }
    }

    @Override
    public void onEnable() {
        log("Initializing LeetCore v" + getPluginMeta().getVersion());

        saveDefaultConfig();
        mergeConfigDefaults();
        FeaturePluginSupport.saveResourceIfMissing(this, "features/double_jump.yml");
        FeaturePluginSupport.saveResourceIfMissing(this, "features/durability.yml");
        FeaturePluginSupport.saveResourceIfMissing(this, "features/auto_crop.yml");
        FeaturePluginSupport.saveResourceIfMissing(this, "features/back.yml");
        FeaturePluginSupport.saveResourceIfMissing(this, "features/tree_feller.yml");
        FeaturePluginSupport.saveResourceIfMissing(this, "features/fall_damage.yml");
        FeaturePluginSupport.saveResourceIfMissing(this, "features/xp.yml");
        FeaturePluginSupport.saveResourceIfMissing(this, "rules/welcome.yml");

        if (storageManager == null) {
            storageManager = new StorageManager(getDataFolder(), getLogger());
        }
        setupVault();

        reactor = new Reactor(storageManager, economy);
        com.leet.core.reactor.BuiltInActions.registerAll(reactor, economy);
        Bukkit.getPluginManager().registerEvents(
            new com.leet.core.reactor.ReactorTriggers(reactor), this);
        loadRules();

        featureManager = new FeatureManager(getLogger());
        guiManager = new GuiManager(this);
        guiManager.start();

        // Register core's own "shared mechanics" features as the Service API.
        registerFeature(new DoubleJumpFeature(this, this));
        registerFeature(new DurabilityFeature(this, this));
        registerFeature(new AutoCropFeature(this, this));
        registerFeature(new BackFeature(this, this));
        registerFeature(new TreeFellerFeature(this, this));
        registerFeature(new FallDamageFeature(this, this));
        registerFeature(new XpFeature(this, this));

        getCommand("leeta").setExecutor(new HelperCommand(this));
        getCommand("leeta").setTabCompleter(new HelperCommand(this));
        getCommand("back").setExecutor(new BackCommand(this));
        var economyCommand = new com.leet.core.command.EconomyCommand(this);
        getCommand("bal").setExecutor(economyCommand);
        getCommand("bal").setTabCompleter(economyCommand);
        getCommand("pay").setExecutor(economyCommand);
        getCommand("pay").setTabCompleter(economyCommand);
        var spawnCommand = new com.leet.core.command.SpawnCommand(this);
        getCommand("spawn").setExecutor(spawnCommand);
        getCommand("spawn").setTabCompleter(spawnCommand);
        getCommand("setspawn").setExecutor(spawnCommand);
        var homeCommand = new com.leet.core.command.HomeCommand(this);
        getCommand("home").setExecutor(homeCommand);
        getCommand("sethome").setExecutor(homeCommand);
        var warpCommand = new com.leet.core.command.WarpCommand(this);
        getCommand("warp").setExecutor(warpCommand);
        getCommand("warp").setTabCompleter(warpCommand);
        registerAdminSubcommand("eco", new com.leet.core.command.EconomyAdminSubcommand(this));
        registerAdminSubcommand("warp", new com.leet.core.command.WarpAdminSubcommand(this, warpCommand));
        getCommand("leet").setExecutor(new LeetCommand(this));
        getCommand("leet").setTabCompleter(new LeetCommand(this));

        // Register this plugin as the CoreApi service so LeetSkills/LeetCrafting
        // can bind to it.
        Bukkit.getServicesManager().register(CoreApi.class, this, this, org.bukkit.plugin.ServicePriority.Normal);

        int enabled = (int) featureManager.all().stream().filter(AbstractFeature::isEnabled).count();
        log("Enabled " + enabled + "/" + featureManager.all().size() + " core feature(s).");
        if (economy != null) {
            log("<aqua>Vault economy connected: " + economy.getName() + "<reset>");
        } else {
            log("<yellow>Vault not installed \u2014 economy features disabled. <reset>");
        }
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.stop();
        }
        if (featureManager != null) {
            featureManager.disableAll();
        }
        if (storageManager != null) {
            storageManager.close();
        }
    }

    @Override
    public Reactor reactor() {
        return reactor;
    }

    /** (Re)loads every rule definition from plugins/LeetCore/rules/*.yml. */
    public void reloadRules() {
        reactor.clearDefinitions();
        loadRules();
    }

    private void loadRules() {
        File dir = new File(getDataFolder(), "rules");
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            try {
                var def = com.leet.core.reactor.DefinitionLoader.parse(
                    YamlConfiguration.loadConfiguration(file));
                if (def != null) {
                    reactor.register(def);
                }
            } catch (Exception e) {
                getLogger().warning("Failed to load rule " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public boolean registerAdminSubcommand(String name, com.leet.core.command.AdminSubcommand subcommand) {
        if (adminSubcommands.containsKey(name)) return false;
        adminSubcommands.put(name, subcommand);
        return true;
    }

    @Override
    public java.util.Map<String, com.leet.core.command.AdminSubcommand> adminSubcommands() {
        return java.util.Collections.unmodifiableMap(adminSubcommands);
    }

    @Override
    public boolean registerFeature(AbstractFeature feature) {
        if (featureManager == null) return false;
        featureManager.register(feature);
        // The feature registers its own permission node during loadConfig(); here we
        // only register it into the shared manager and hand it its owner for
        // listener registration. enable() loads the config (which registers the
        // permission and populates the gating fields) then gates the listeners.
        feature.enable();
        return true;
    }

    private void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        }
    }

    /**
     * Adds any keys present in the bundled config.yml that are missing from the
     * on-disk file, preserving the server admin's existing values.
     */
    public void mergeConfigDefaults() {
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        FeaturePluginSupport.mergeMissingKeys(this, cfg, file, "config.yml");
    }

    @Override
    public StorageManager storageManager() {
        return storageManager;
    }

    @Override
    public CustomItemView itemRegistry() {
        RegisteredServiceProvider<CustomItemView> rsp =
            Bukkit.getServicesManager().getRegistration(CustomItemView.class);
        return rsp == null ? null : rsp.getProvider();
    }

    /** Narrow contract for cross-plugin consumers (CoreApi). */
    @Override
    public FeatureRegistry featureRegistry() {
        return featureManager;
    }

    /** Concrete manager for core's own command layer (toggle, etc.). */
    public FeatureManager featureManager() {
        return featureManager;
    }

    @Override
    public GuiManager guiManager() {
        return guiManager;
    }

    @Override
    public Economy economy() {
        return economy;
    }

    /** Logs a prefixed message to the server console (color is shown on Paper). */
    @Override
    public void log(String message) {
        Bukkit.getConsoleSender().sendMessage(
            MiniMessageUtil.deserialize("<green>[LeetCore]</green> " + message));
    }
}
