package com.leet.helper.feature;

import com.leet.helper.Core;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Grants bonus vanilla XP (via player.giveExp) for mining, woodcutting, crops,
 * fishing, building and killing. Per-material/per-mob amounts come from the
 * feature config; feedback uses the generic messages + message-type system.
 */
public class XpFeature extends AbstractFeature {

    /** How long a recorded placement is honored before it is dropped. */
    private static final long PLACED_WINDOW_MS = 60L * 60L * 1000L; // 1 hour
    /** When the in-memory tracker exceeds this many entries it is pruned. */
    private static final int PLACED_PRUNE_THRESHOLD = 5000;
    /** Prune the persistent tracker every this many placements to bound the DB. */
    private static final int PLACED_PERSISTENT_PRUNE_EVERY = 256;

    private final Map<Material, Integer> mining = new HashMap<>();
    private final Map<Material, Integer> woodcutting = new HashMap<>();
    private final Map<Material, Integer> crops = new HashMap<>();
    private int fishingAmount;
    private int buildingAmount;
    private int killFallback;
    private final Map<EntityType, Integer> kills = new HashMap<>();

    // Whether placed-block tracking survives restarts (SQLite via StorageManager)
    // instead of living only in-memory. Chosen by feature.placed-tracking.
    private boolean persistentPlaced;
    private int placedOps;

    // In-memory backend: block locations a player has placed (placement time ms).
    private final Map<Block, Long> placedBlocks = new HashMap<>();

    public XpFeature(Core plugin) {
        super(plugin);
    }

    @Override
    public String featureId() {
        return "xp";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        mining.clear();
        woodcutting.clear();
        crops.clear();
        kills.clear();

        readMaterials(cfg, "feature.mining", mining);
        readMaterials(cfg, "feature.woodcutting", woodcutting);
        readMaterials(cfg, "feature.crops", crops);

        fishingAmount = cfg.getInt("feature.fishing.amount", 3);
        buildingAmount = cfg.getInt("feature.building.amount", 1);
        killFallback = cfg.getInt("feature.killing.amount", 2);

        String tracking = cfg.getString("feature.placed-tracking", "memory");
        persistentPlaced = "persistent".equalsIgnoreCase(tracking);
        if (persistentPlaced && !plugin.storageManager().persistentAvailable()) {
            plugin.getLogger().warning("feature.placed-tracking is 'persistent' but SQLite is unavailable; "
                + "falling back to in-memory placed-block tracking.");
            persistentPlaced = false;
        }
        placedBlocks.clear();
        placedOps = 0;
        if (persistentPlaced) {
            // Clear stale markers (older than the window) left from a prior run.
            prunePlaced();
        }

        ConfigurationSection mobs = cfg.getConfigurationSection("feature.killing.mobs");
        if (mobs != null) {
            for (String name : mobs.getKeys(false)) {
                try {
                    EntityType type = EntityType.valueOf(name);
                    kills.put(type, mobs.getInt(name));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid entity in xp killing mobs: " + name);
                }
            }
        }
    }

    private void readMaterials(YamlConfiguration cfg, String path, Map<Material, Integer> into) {
        ConfigurationSection section = cfg.getConfigurationSection(path + ".materials");
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            try {
                into.put(Material.valueOf(name), section.getInt(name));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in xp " + path + ": " + name);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        // MONITOR + cancel check so a break blocked by a protection plugin
        // never awards XP.
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!check(player)) return;

        Block broken = event.getBlock();
        // A block the player placed gives no mining/woodcutting/crops XP. The
        // marker is consumed regardless of material so the tracker does not grow.
        if (isPlacedAndConsume(broken)) return;

        Material type = broken.getType();
        Integer amount = crops.get(type);
        if (amount != null) {
            award(player, "Crops", amount);
            return;
        }
        amount = woodcutting.get(type);
        if (amount != null) {
            award(player, "Woodcutting", amount);
            return;
        }
        amount = mining.get(type);
        if (amount != null) {
            award(player, "Mining", amount);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (event.isCancelled()) return; // e.g. WorldGuard deny-fishing flag
        Player player = event.getPlayer();
        if (!check(player)) return;
        award(player, "Fishing", fishingAmount);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        // MONITOR + cancel check so a placement blocked by a protection plugin
        // never awards XP.
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!check(player)) return;

        // Track the placed block so a later break of it does not award XP.
        markPlaced(event.getBlockPlaced());

        award(player, "Building", buildingAmount);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof LivingEntity dead)) return;
        if (dead instanceof Player) return;

        Player killer = dead.getKiller();
        if (killer == null || !check(killer)) return;

        int amount = kills.getOrDefault(dead.getType(), killFallback);
        award(killer, "Killing", amount);
    }

    private void award(Player player, String action, int amount) {
        if (amount <= 0) return;
        player.giveExp(amount);
        sendMessage(player, "xp-gained", "<amount>", String.valueOf(amount), "<action>", action);
    }

    /** Bounds the tracker to ~1 hour, on whichever backend is active. */
    private void prunePlaced() {
        long cutoff = System.currentTimeMillis() - PLACED_WINDOW_MS;
        if (persistentPlaced) {
            plugin.storageManager().prunePersistent(featureId(), "placed", cutoff);
        } else {
            placedBlocks.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
    }

    /** Records that the player placed this block on the active backend. */
    private void markPlaced(Block block) {
        if (persistentPlaced) {
            plugin.storageManager().setPersistent(featureId(), "placed", blockUuid(block),
                String.valueOf(System.currentTimeMillis()));
            if (++placedOps % PLACED_PERSISTENT_PRUNE_EVERY == 0) {
                prunePlaced();
            }
        } else {
            placedBlocks.put(block, System.currentTimeMillis());
            if (placedBlocks.size() > PLACED_PRUNE_THRESHOLD) {
                prunePlaced();
            }
        }
    }

    /** True if the block is a tracked placement; consumes (removes) the marker. */
    private boolean isPlacedAndConsume(Block block) {
        if (persistentPlaced) {
            UUID id = blockUuid(block);
            String stored = plugin.storageManager().getPersistent(featureId(), "placed", id);
            if (stored == null) return false;
            plugin.storageManager().deletePersistent(featureId(), "placed", id);
            return true;
        }
        return placedBlocks.remove(block) != null;
    }

    /** Stable identifier for a specific world+block location. */
    private static UUID blockUuid(Block block) {
        String s = block.getWorld().getUID() + "|" + block.getX() + "|" + block.getY() + "|" + block.getZ();
        return UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}