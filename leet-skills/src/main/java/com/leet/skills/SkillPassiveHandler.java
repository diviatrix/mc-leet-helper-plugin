package com.leet.skills;

import com.leet.core.feature.AutoCropUtil;
import com.leet.core.feature.TreeFellerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The skills passive engine: every passive skill effect as an event handler and
 * scheduler. Extracted from {@link SkillsFeature} so the feature stays a
 * state/gating/leveling hub and the passive logic is a separate cohesive concern.
 *
 * <p>It needs shared state from the feature (which skills are owned, their levels,
 * toggles, wrappers/overlay) and is wired to that via the feature handle. Registering
 * this listener is the feature's job (it owns enable/disable).
 */
public final class SkillPassiveHandler implements Listener {

    private final SkillsFeature feature;

    /** A skill + the player's level, when owned and active. */
    private record Owned(SkillConfig cfg, int level) {
        boolean active() {
            return cfg != null && level > 0;
        }
    }

    // per-use passives state / config
    private final Random rng = new Random();
    private boolean felling;    // reentrancy guard for lumberjack tree felling
    private boolean harvesting; // reentrancy guard for farmer auto-crop
    private int treeFellerMaxBlocks = 100;
    private boolean autoCropRequireMature = true;
    private boolean autoCropRequireHoe = true;
    private double doubleJumpHorizontal = 0.25;
    private double doubleJumpVertical = 1.0;

    /** Swimmer: remembered WATER_MOVEMENT_EFFICIENCY base to undo on reset. */
    private final Map<UUID, Double> swimBase = new HashMap<>();
    /** Diver: fractional air carried between ticks so breathing can extend fractionally. */
    private final Map<UUID, Double> diverAir = new HashMap<>();

    SkillPassiveHandler(SkillsFeature feature) {
        this.feature = feature;
    }

    /**
     * The skill + the player's level, or null when (a) the feature gates the
     * player out, or (b) a standalone feature bound to this skill is already
     * acting for the player (so the skill's passive must not double-fire).
     */
    private Owned owned(Player player, String skillId) {
        if (!feature.appliesTo(player)) return null;
        if (feature.hasFeaturePermission(player, skillId)) return null;
        return new Owned(feature.skill(skillId), feature.getState(player).level(skillId));
    }

    void configure(int treeFellerMaxBlocks, boolean autoCropRequireMature, boolean autoCropRequireHoe,
                   double doubleJumpHorizontal, double doubleJumpVertical) {
        this.treeFellerMaxBlocks = treeFellerMaxBlocks;
        this.autoCropRequireMature = autoCropRequireMature;
        this.autoCropRequireHoe = autoCropRequireHoe;
        this.doubleJumpHorizontal = doubleJumpHorizontal;
        this.doubleJumpVertical = doubleJumpVertical;
    }

    /** Clears per-player engine state (on entity quit). */
    void resetPlayer(Player player) {
        resetSpeed(player);
        resetWaterSpeed(player);
        diverAir.remove(player.getUniqueId());
    }

    /** Re-applies level-dependent attributes (walker/swimmer) after a level-up. */
    void reapply(Player player) {
        applySpeed(player);
        applyWaterSpeed(player);
    }

    // --- events ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!feature.appliesTo(player)) return;

        Block broken = event.getBlock();
        Material type = broken.getType();

        SkillConfig lumber = feature.skill("lumberjack");
        int lumberLevel = feature.levelOf(player, "lumberjack");
        boolean isLog = lumber != null && lumber.logs().contains(type);

        SkillConfig miner = feature.skill("miner");
        int minerLevel = feature.levelOf(player, "miner");
        boolean isMined = miner != null && miner.minerBlocks().contains(type);

        SkillConfig farmer = feature.skill("farmer");
        int farmerLevel = feature.levelOf(player, "farmer");
        boolean isCrop = farmer != null && farmer.crops().contains(type);

        boolean fellingSweep = TreeFellerUtil.isFelling(player);
        if (isLog && lumberLevel > 0 && !fellingSweep
            && !feature.hasFeaturePermission(player, "lumberjack")) {
            if (roll(lumber.valueAt("extra-block", lumberLevel))) drop(broken, type, 1);
        }
        SkillConfig gardener = feature.skill("gardener");
        int gardenerLevel = feature.levelOf(player, "gardener");
        if (isLog && gardener != null && gardenerLevel > 0 && !fellingSweep
            && !feature.hasFeaturePermission(player, "gardener")) {
            if (roll(gardener.valueAt("apple", gardenerLevel))) drop(broken, Material.APPLE, 1);
            if (roll(gardener.valueAt("golden-apple", gardenerLevel))) drop(broken, Material.GOLDEN_APPLE, 1);
        }
        if (isMined && minerLevel > 0 && !feature.hasFeaturePermission(player, "miner")) {
            if (roll(miner.valueAt("extra-block", minerLevel))) {
                ItemStack tool = player.getInventory().getItemInMainHand();
                for (ItemStack dropItem : broken.getDrops(tool)) {
                    broken.getWorld().dropItemNaturally(broken.getLocation().add(0.5, 0.5, 0.5), dropItem);
                }
            }
        }
        if (isCrop && farmerLevel > 0 && !feature.hasFeaturePermission(player, "farmer")) {
            if (roll(farmer.valueAt("extra-drop", farmerLevel))) drop(broken, type, 1);
            if (roll(farmer.valueAt("seed", farmerLevel))) drop(broken, seedFor(type), 1);
        }

        if (!felling && !harvesting) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (isLog && feature.skillEnabled(player, "tree-feller")
                && !feature.hasFeaturePermission(player, "tree-feller") && feature.levelOf(player, "tree-feller") >= 1) {
                felling = true;
                try {
                    TreeFellerUtil.fell(feature, player, broken, lumber.logs(), treeFellerMaxBlocks, tool);
                } finally {
                    felling = false;
                }
            } else if (isCrop && feature.skillEnabled(player, "auto-crop")
                && !feature.hasFeaturePermission(player, "auto-crop") && feature.levelOf(player, "auto-crop") >= 1) {
                if (autoCropRequireHoe && !AutoCropUtil.isHoe(tool)) return;
                harvesting = true;
                try {
                    int radius = Math.min(3, Math.max(1, feature.levelOf(player, "auto-crop")));
                    AutoCropUtil.harvestRadius(feature, player, broken, type, radius, autoCropRequireMature, tool);
                } finally {
                    harvesting = false;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        var sm = owned(player, "smith");
        if (sm == null || feature.hasFeaturePermission(player, "smith") || !sm.active()) return;
        double pct = sm.cfg().valueAt("durability", sm.level());
        if (pct <= 0) return;
        if (roll(pct)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (owned(player, "fall-nullify") == null) return;
        if (feature.hasFeaturePermission(player, "fall-nullify")) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (feature.skillEnabled(player, "fall-nullify") && feature.levelOf(player, "fall-nullify") >= 1) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (owned(player, "double-jump") == null) return;
        if (feature.hasFeaturePermission(player, "double-jump")) return;
        if (!feature.skillEnabled(player, "double-jump")) return;
        if (feature.levelOf(player, "double-jump") < 1) return;

        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);

        if (!feature.checkCooldown(player.getUniqueId())) return;

        Vector direction = player.getLocation().getDirection();
        player.setVelocity(new Vector(
            direction.getX() * doubleJumpHorizontal,
            doubleJumpVertical,
            direction.getZ() * doubleJumpHorizontal
        ));
        feature.setCooldown(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (owned(player, "double-jump") == null) return;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (feature.hasFeaturePermission(player, "double-jump")) return;
        if (!feature.skillEnabled(player, "double-jump")) return;
        if (feature.levelOf(player, "double-jump") < 1) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        if (isOnSolidGround(player) || player.isInsideVehicle()) {
            player.setAllowFlight(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var st = owned(player, SkillsFeature.STAMINA);
        if (st == null || !st.active()) return;
        int current = player.getFoodLevel();
        int next = event.getFoodLevel();
        if (next >= current) return;
        double mult = 1.0 / (1.0 + st.cfg().valueAt("hunger", st.level()) / 100.0);
        int reduced = (int) Math.round((current - next) * mult);
        if (reduced > 0 && reduced < (current - next)) {
            event.setFoodLevel(current - reduced);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        var wa = owned(player, "warrior");
        if (wa == null || !wa.active()) return;
        double chance = wa.cfg().valueAt("crit", wa.level());
        if (chance <= 0) return;
        if (roll(chance)) {
            event.setDamage(event.getDamage() * 2.0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        var fi = owned(player, "fisherman");
        if (fi == null || !fi.active()) return;
        SkillConfig cfg = fi.cfg();
        if (roll(cfg.valueAt("extra-catch", fi.level())) && !cfg.bonusItems().isEmpty()) {
            giveItem(player, cfg.bonusItems().get(rng.nextInt(cfg.bonusItems().size())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLuckyCatch(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        var lu = owned(player, "lucky-catch");
        if (lu == null || !lu.active()) return;
        SkillConfig cfg = lu.cfg();
        if (roll(cfg.valueAt("quality", lu.level())) && !cfg.qualityItems().isEmpty()) {
            giveItem(player, cfg.qualityItems().get(rng.nextInt(cfg.qualityItems().size())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShear(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        var an = owned(player, "animalist");
        if (an == null || !an.active()) return;
        if (event.getEntity() instanceof Sheep sheep && roll(an.cfg().valueAt("extra-gather", an.level()))) {
            giveItem(player, Material.valueOf(sheep.getColor().name() + "_WOOL"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMilk(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof Cow)) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.BUCKET) return;
        var an = owned(player, "animalist");
        if (an == null || !an.active()) return;
        if (roll(an.cfg().valueAt("extra-gather", an.level()))) {
            giveItem(player, Material.MILK_BUCKET);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreed(EntityBreedEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getBreeder() instanceof Player player)) return;
        var br = owned(player, "breeder");
        if (br == null || !br.active()) return;
        double chance = br.cfg().valueAt("breed", br.level());
        if (chance <= 0 || !roll(chance)) return;
        LivingEntity offspring = event.getEntity();
        Class<? extends Entity> clazz = offspring.getType().getEntityClass();
        if (clazz == null) return;
        offspring.getWorld().spawn(offspring.getLocation(), clazz,
            entity -> { if (entity instanceof Ageable ageable) ageable.setAge(-24000); });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applySpeed(event.getPlayer());
        applyWaterSpeed(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        resetSpeed(player);
        resetWaterSpeed(player);
        diverAir.remove(player.getUniqueId());
        feature.dropState(player);
    }

    // --- schedulers ---

    /** Every second, grant the stamina bonus regeneration on top of vanilla. */
    void tickRegen() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!feature.appliesTo(player)) continue;
            SkillConfig stamina = feature.skill(SkillsFeature.STAMINA);
            int level = feature.levelOf(player, SkillsFeature.STAMINA);
            if (stamina == null || level <= 0) continue;
            double regen = stamina.valueAt("regen", level) / 100.0;
            if (regen <= 0) continue;
            if (player.getFoodLevel() < 18) continue;
            double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (player.getHealth() >= max) continue;
            player.setHealth(Math.min(max, player.getHealth() + regen));
        }
    }

    /** Diver: slow the vanilla air drain while underwater. */
    void tickDiver() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!player.isInWater() || !feature.appliesTo(player)) {
                diverAir.remove(uuid);
                // If the skills feature was toggled off for this player while online,
                // tear down any attribute boosts so they don't persist.
                if (!feature.appliesTo(player)) {
                    resetSpeed(player);
                    resetWaterSpeed(player);
                }
                continue;
            }
            SkillConfig diver = feature.skill("diver");
            int level = feature.levelOf(player, "diver");
            if (diver == null || level <= 0) {
                diverAir.remove(uuid);
                continue;
            }
            double pct = diver.valueAt("breathing", level);
            if (pct <= 0) {
                diverAir.remove(uuid);
                continue;
            }
            int air = player.getRemainingAir();
            if (air <= 0) continue;
            double addBack = 1.0 - 1.0 / (1.0 + pct / 100.0);
            double acc = diverAir.merge(uuid, addBack, Double::sum);
            int whole = (int) acc;
            if (whole > 0) {
                player.setRemainingAir(Math.min(player.getMaximumAir(), air + whole));
                diverAir.put(uuid, acc - whole);
            }
        }
    }

    // --- attribute helpers ---

    private void applyWaterSpeed(Player player) {
        SkillConfig swimmer = feature.skill("swimmer");
        AttributeInstance swim = player.getAttribute(Attribute.WATER_MOVEMENT_EFFICIENCY);
        if (!feature.appliesTo(player) || swimmer == null || swim == null) {
            resetWaterSpeed(player);
            return;
        }
        int level = feature.levelOf(player, "swimmer");
        double pct = swimmer.valueAt("swim-speed", level);
        if (level <= 0 || pct <= 0) {
            resetWaterSpeed(player);
            return;
        }
        swimBase.putIfAbsent(player.getUniqueId(), swim.getBaseValue());
        swim.setBaseValue(swimBase.get(player.getUniqueId()) + pct / 100.0);
    }

    private void resetWaterSpeed(Player player) {
        AttributeInstance swim = player.getAttribute(Attribute.WATER_MOVEMENT_EFFICIENCY);
        Double base = swimBase.remove(player.getUniqueId());
        if (swim != null && base != null) {
            swim.setBaseValue(base);
        }
    }

    private void applySpeed(Player player) {
        SkillConfig explorer = feature.skill("explorer");
        if (!feature.appliesTo(player) || explorer == null) {
            resetSpeed(player);
            return;
        }
        int level = feature.levelOf(player, "explorer");
        double pct = explorer.valueAt("speed", level);
        if (level <= 0 || pct <= 0) {
            resetSpeed(player);
            return;
        }
        float speed = (float) Math.min(1.0, 0.2 * (1.0 + pct / 100.0));
        if (player.isOnline() && !player.isDead()) {
            player.setWalkSpeed(speed);
        }
    }

    private void resetSpeed(Player player) {
        try {
            player.setWalkSpeed(0.2f);
        } catch (IllegalArgumentException ignored) {
            // server already set a valid speed; nothing to reset
        }
    }

    // --- helpers ---

    private boolean roll(double pct) {
        return pct >= 100 || rng.nextDouble() * 100.0 < pct;
    }

    private void drop(Block block, Material material, int amount) {
        ItemStack item = new ItemStack(material, amount);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item);
    }

    private void giveItem(Player player, Material material) {
        for (ItemStack leftover : player.getInventory().addItem(new ItemStack(material)).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private static Material seedFor(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            case SWEET_BERRY_BUSH, MELON, PUMPKIN -> Material.valueOf(crop.name() + "_SEEDS");
            default -> Material.WHEAT_SEEDS;
        };
    }

    private boolean isOnSolidGround(Player player) {
        BoundingBox box = player.getBoundingBox();
        org.bukkit.World world = player.getWorld();
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
