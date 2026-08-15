package com.leet.helper;

import com.leet.helper.command.BackCommand;
import com.leet.helper.command.HelperCommand;
import com.leet.helper.command.LeetCommand;
import com.leet.helper.feature.*;
import com.leet.helper.storage.StorageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;

public class HelperPlugin extends JavaPlugin {

    private StorageManager storageManager;
    private FeatureManager featureManager;
    private Economy economy;
    private Permission vaultPermission;

    @Override
    public void onEnable() {
        log("Initializing LeetHelper v" + getPluginMeta().getVersion());

        saveDefaultConfig();
        mergeConfigDefaults();
        saveResourceIfMissing("features/_double_jump.yml");
        saveResourceIfMissing("features/_durability.yml");
        saveResourceIfMissing("features/_auto_crop.yml");
        saveResourceIfMissing("features/_back.yml");
        saveResourceIfMissing("features/_tree_feller.yml");
        saveResourceIfMissing("features/_fall_damage.yml");
        saveResourceIfMissing("features/_xp.yml");

        storageManager = new StorageManager(getDataFolder(), getLogger());

        setupVault();

        featureManager = new FeatureManager(this);
        featureManager.register(new DoubleJumpFeature(this));
        featureManager.register(new DurabilityFeature(this));
        featureManager.register(new AutoCropFeature(this));
        featureManager.register(new BackFeature(this));
        featureManager.register(new TreeFellerFeature(this));
        featureManager.register(new FallDamageFeature(this));
        featureManager.register(new XpFeature(this));
        featureManager.enableAll();

        getCommand("leeta").setExecutor(new HelperCommand(this));
        getCommand("leeta").setTabCompleter(new HelperCommand(this));
        getCommand("back").setExecutor(new BackCommand(this));
        getCommand("leet").setExecutor(new LeetCommand(this));
        getCommand("leet").setTabCompleter(new LeetCommand(this));

        registerFeaturePermissions();

        int enabled = 0;
        for (AbstractFeature f : featureManager.all()) {
            if (f.isEnabled()) enabled++;
        }
        log("Enabled " + enabled + "/" + featureManager.all().size() + " feature(s).");
        if (economy != null) {
            log("<aqua>Vault economy connected: " + economy.getName() + "<reset>");
        } else {
            log("<yellow>Vault not installed \u2014 economy features disabled. <reset>");
        }
    }

    @Override
    public void onDisable() {
        if (featureManager != null) {
            featureManager.disableAll();
        }
        if (storageManager != null) {
            storageManager.close();
        }
    }

    private void saveResourceIfMissing(String path) {
        File file = new File(getDataFolder(), path);
        if (!file.exists()) {
            saveResource(path, false);
        }
    }

    private void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        }
        RegisteredServiceProvider<Permission> prsp = Bukkit.getServicesManager().getRegistration(Permission.class);
        if (prsp != null) {
            vaultPermission = prsp.getProvider();
        }
    }

    private void registerFeaturePermissions() {
        for (AbstractFeature feature : featureManager.all()) {
            try {
                PermissionDefault pd;
                String def = feature.getDefaultPermission();
                if ("op".equalsIgnoreCase(def)) {
                    pd = PermissionDefault.OP;
                } else if ("false".equalsIgnoreCase(def)) {
                    pd = PermissionDefault.FALSE;
                } else {
                    pd = PermissionDefault.TRUE;
                }
                Bukkit.getPluginManager().addPermission(
                    new org.bukkit.permissions.Permission(feature.permission(), pd)
                );
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to register permission for feature: " + feature.id(), e);
            }
        }
    }

    /**
     * Adds any keys present in the bundled config.yml that are missing from the
     * on-disk file, preserving the server admin's existing values. This lets old
     * configs gain new options on plugin update (mirrors the per-feature merge).
     */
    public void mergeConfigDefaults() {
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        InputStream defaultStream = getResource("config.yml");
        if (defaultStream == null) return;
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            Object value = defaults.get(key);
            if (value instanceof org.bukkit.configuration.ConfigurationSection) {
                continue; // parent map node, not a leaf to merge
            }
            if (!cfg.contains(key)) {
                cfg.set(key, value);
                changed = true;
            }
        }
        if (!changed) return;
        try {
            cfg.save(file);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to merge config.yml defaults", e);
        }
    }

    public StorageManager storageManager() {
        return storageManager;
    }

    public FeatureManager featureManager() {
        return featureManager;
    }

    public Economy economy() {
        return economy;
    }

    public Permission vaultPermission() {
        return vaultPermission;
    }

    /**
     * Logs a message to the server console with a green <green>[LeetHelper]</green>
     * prefix via the console sender (color is shown on Paper/console).
     */
    public void log(String message) {
        Bukkit.getConsoleSender().sendMessage(
            MiniMessage.miniMessage().deserialize("<green>[LeetHelper]</green> " + message));
    }
}
