package com.leet.helper.feature;

import com.leet.helper.Core;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class DoubleJumpFeature extends AbstractFeature {

    private double horizontalMultiplier;
    private double verticalMultiplier;

    public DoubleJumpFeature(Core plugin) {
        super(plugin);
    }

    @Override
    public String featureId() {
        return "double_jump";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        horizontalMultiplier = cfg.getDouble("feature.horizontal-multiplier", 0.25);
        verticalMultiplier = cfg.getDouble("feature.vertical-multiplier", 1.0);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        // Always cancel the flight-toggle and reset flight state for survival/
        // adventure players, regardless of whether the double jump is allowed
        // right now (permission / personal /leet toggle / world / cooldown).
        // If we returned early on a failed check, the game would leave the
        // player flying freely - a free-flight exploit. Only the launch below
        // is gated behind check()/checkCooldown().
        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);

        if (!check(player)) return;

        if (!checkCooldown(player.getUniqueId())) return;

        if (!chargeUse(player)) return;

        Vector direction = player.getLocation().getDirection();
        Vector velocity = new Vector(
            direction.getX() * horizontalMultiplier,
            verticalMultiplier,
            direction.getZ() * horizontalMultiplier
        );
        player.setVelocity(velocity);
        setCooldown(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!check(player)) return;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        if (isOnSolidGround(player) || player.isInsideVehicle()) {
            player.setAllowFlight(true);
        }
    }

    /**
     * Server-authoritative ground check. Unlike {@link Player#isOnGround()},
     * which reports a client-controlled flag that is unreliable and spoofable,
     * this probes the world for a solid block beneath the player's bounding
     * box. Returns true when any solid block is directly under the player's
     * feet (within a half-block tolerance).
     */
    private boolean isOnSolidGround(Player player) {
        BoundingBox box = player.getBoundingBox();
        org.bukkit.World world = player.getWorld();

        // Check the region just below the player's feet. Use a small tolerance
        // so the player registers as grounded when their feet are level with
        // (or a fraction above) the block top.
        double feetY = box.getMinY() - 0.5;
        for (double y = 0; y <= 0.5; y += 0.5) {
            int blockY = (int) Math.floor(feetY + y);
            for (int blockX = (int) Math.floor(box.getMinX()); blockX <= (int) Math.floor(box.getMaxX()); blockX++) {
                for (int blockZ = (int) Math.floor(box.getMinZ()); blockZ <= (int) Math.floor(box.getMaxZ()); blockZ++) {
                    if (world.getBlockAt(blockX, blockY, blockZ).getType().isSolid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
