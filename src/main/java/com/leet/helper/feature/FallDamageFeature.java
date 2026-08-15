package com.leet.helper.feature;

import com.leet.helper.HelperPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Standalone fall-damage immunity, decoupled from Double Jump. When the
 * feature is on and the player passes the checks (permission / personal
 * /leet toggle / world), ALL fall damage is negated for that player.
 */
public class FallDamageFeature extends AbstractFeature {

    public FallDamageFeature(HelperPlugin plugin) {
        super(plugin);
    }

    @Override
    public String featureId() {
        return "fall_damage";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        // No feature-specific options; the feature on/off switch is enough.
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!check(player)) return;

        event.setCancelled(true);
    }
}