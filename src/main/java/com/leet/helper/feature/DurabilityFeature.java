package com.leet.helper.feature;

import com.leet.helper.HelperPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemDamageEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DurabilityFeature extends AbstractFeature {

    private double multiplier;
    private int minDamage;
    private Set<Material> whitelist;

    public DurabilityFeature(HelperPlugin plugin) {
        super(plugin);
    }

    @Override
    public String featureId() {
        return "durability";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        multiplier = cfg.getDouble("feature.multiplier", 0.5);
        minDamage = cfg.getInt("feature.min-damage", 1);
        whitelist = new HashSet<>();
        List<String> whitelistNames = cfg.getStringList("feature.whitelist");
        for (String name : whitelistNames) {
            try {
                whitelist.add(Material.valueOf(name));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in durability whitelist: " + name);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (!check(player)) return;

        Material itemType = event.getItem().getType();
        if (!whitelist.contains(itemType)) return;

        if (!chargeUse(player)) return;

        int newDamage = (int) Math.round(event.getDamage() * multiplier);
        if (newDamage < minDamage) {
            newDamage = minDamage;
        }
        event.setDamage(newDamage);
    }
}
