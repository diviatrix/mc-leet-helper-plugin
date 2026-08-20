package com.leet.core.feature;

import com.leet.core.CoreApi;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Standalone fall-damage immunity, decoupled from Double Jump. When the
 * feature is on and the player passes the checks (permission / personal
 * /leet toggle / world), ALL fall damage is negated for that player.
 */
public class FallDamageFeature extends AbstractFeature implements CostedFeature {

    public FallDamageFeature(CoreApi core, JavaPlugin owner) {
        super(core, owner);
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

        if (!chargeUse(player)) return;

        event.setCancelled(true);
    }
}