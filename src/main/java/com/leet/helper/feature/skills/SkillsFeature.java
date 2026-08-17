package com.leet.helper.feature.skills;

import com.leet.helper.Core;
import com.leet.helper.feature.AbstractFeature;
import com.leet.helper.feature.AutoCropUtil;
import com.leet.helper.feature.TreeFellerUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The skills feature: a per-player skill tree (stamina at the center, eight
 * passive skills around it once stamina reaches its max level) that spends
 * vanilla XP points to level up. Leveling happens through the /skills GUI
 * ({@link SkillsGui}); every skill effect here is a passive.
 */
public class SkillsFeature extends AbstractFeature {

    public static final String STAMINA = "stamina";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /**
     * Skills that are also granted by a standalone feature. Holding that
     * feature's permission counts as already owning the skill.
     */
    private static final Map<String, String> FEATURE_BOUND_SKILLS = Map.of(
        "tree-feller", "tree_feller",
        "auto-crop", "auto_crop",
        "fall-nullify", "fall_damage",
        "double-jump", "double_jump",
        "smith", "durability"
    );

    private final Map<String, SkillConfig> skills = new LinkedHashMap<>();
    private final Map<UUID, SkillState> states = new HashMap<>();
    private final Random rng = new Random();

    private SkillTreeConfig tree;

    private SkillsGui gui;
    private String guiTitle = "Skills";
    private int guiRows = 6;

    private int treeFellerMaxBlocks = 100;
    private boolean autoCropRequireMature = true;
    private double doubleJumpHorizontal = 0.25;
    private double doubleJumpVertical = 1.0;

    private boolean felling;    // reentrancy guard for lumberjack tree felling
    private boolean harvesting; // reentrancy guard for farmer auto-crop
    private int schedulerTask;
    private int diverTask = -1;

    /** Swimmer: the default WATER_MOVEMENT_EFFICIENCY base we added to per player, to undo on reset. */
    private final Map<UUID, Double> swimBase = new HashMap<>();
    /** Diver: fractional air carried between ticks so breathing can extend by a non-integer %. */
    private final Map<UUID, Double> diverAir = new HashMap<>();

    public SkillsFeature(Core plugin) {
        super(plugin);
    }

    @Override
    public String featureId() {
        return "skills";
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        skills.clear();
        guiTitle = cfg.getString("feature.gui.title", "Skills");
        guiRows = Math.max(4, Math.min(9, cfg.getInt("feature.gui.rows", 6)));

        YamlConfiguration treeCfg = loadAndMerge("skill-tree.yml");
        tree = treeCfg == null ? null : SkillTreeConfig.read(plugin, treeCfg);

        treeFellerMaxBlocks = Math.max(1, cfg.getInt("feature.tree-feller.max-blocks", 100));
        autoCropRequireMature = cfg.getBoolean("feature.auto-crop.require-mature", true);
        doubleJumpHorizontal = cfg.getDouble("feature.double-jump.horizontal-multiplier", 0.25);
        doubleJumpVertical = cfg.getDouble("feature.double-jump.vertical-multiplier", 1.0);

        ConfigurationSection skillsSection = cfg.getConfigurationSection("feature.skills");
        if (skillsSection != null) {
            for (String key : skillsSection.getKeys(false)) {
                ConfigurationSection section = skillsSection.getConfigurationSection(key);
                if (section != null) {
                    skills.put(key.toLowerCase(), SkillConfig.read(plugin, section));
                }
            }
        }

        gui = new SkillsGui(plugin, this);
    }

    @Override
    public void enable() {
        super.enable();
        if (enabled) {
            schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRegen, 20L, 20L).getTaskId();
            // Run often enough that the diver's slowed air drain stays smooth on the bubble bar.
            diverTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDiver, 2L, 2L).getTaskId();
        }
    }

    @Override
    public void disable() {
        if (schedulerTask >= 0) {
            Bukkit.getScheduler().cancelTask(schedulerTask);
            schedulerTask = -1;
        }
        if (diverTask >= 0) {
            Bukkit.getScheduler().cancelTask(diverTask);
            diverTask = -1;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetSpeed(player);
            resetWaterSpeed(player);
            diverAir.remove(player.getUniqueId());
        }
        states.clear();
        super.disable();
    }

    // --- state / leveling API (also used by SkillsGui) ---

    SkillState getState(Player player) {
        SkillState state = states.get(player.getUniqueId());
        if (state == null) {
            state = SkillState.load(plugin, player.getUniqueId());
            states.put(player.getUniqueId(), state);
        }
        return state;
    }

    public int currentLevel(Player player, String skillId) {
        SkillConfig skill = skill(skillId);
        if (skill != null && hasFeaturePermission(player, skillId)) return skill.maxLevel();
        return getState(player).level(skillId);
    }

    public int nextCost(Player player, String skillId) {
        SkillConfig skill = skill(skillId);
        if (skill == null) return -1;
        if (hasFeaturePermission(player, skillId)) return -1; // already acquired via the feature permission
        return skill.costForNext(getState(player).level(skillId));
    }

    public boolean hasXp(Player player, int cost) {
        return cost <= 0 || player.getTotalExperience() >= cost;
    }

    public boolean levelUp(Player player, String skillId) {
        SkillConfig skill = skill(skillId);
        if (skill == null) return false;
        if (hasFeaturePermission(player, skillId)) return false; // feature permission already provides it
        SkillTreeConfig.Prerequisite requirement = requirementFor(skillId);
        if (!prerequisiteSatisfied(player, skill)) {
            sendMessage(player, "locked",
                "<skill>", skill.name(),
                "<required>", nameOf(requirement.skill()),
                "<require-level>", String.valueOf(requirement.level()));
            return false;
        }
        SkillState state = getState(player);
        int level = state.level(skillId);
        int cost = skill.costForNext(level);
        if (cost < 0) {
            sendMessage(player, "max-level", "<skill>", skill.name());
            return false;
        }
        if (!hasXp(player, cost)) {
            sendMessage(player, "insufficient-xp",
                "<cost>", String.valueOf(cost), "<needed>", String.valueOf(cost));
            return false;
        }
        player.giveExp(-cost);
        state.setLevel(skillId, level + 1);
        state.save(plugin, player.getUniqueId());
        sendMessage(player, "level-up",
            "<skill>", skill.name(), "<level>", String.valueOf(level + 1), "<cost>", String.valueOf(cost));
        applySpeed(player);
        applyWaterSpeed(player);
        return true;
    }

    public void openTree(Player player) {
        if (!check(player)) {
            sendMessage(player, "feature-off");
            return;
        }
        gui.openTree(player);
    }

    Inventory newInventory(Player player) {
        return Bukkit.createInventory(null, guiRows * 9, MM.deserialize(guiTitle));
    }

    public int rows() {
        return guiRows;
    }

    public SkillConfig skill(String id) {
        return skills.get(id);
    }

    public List<String> ringSkillIds() {
        return tree == null ? List.of() : tree.ring();
    }

    public List<String> advancedSkillIds() {
        return tree == null ? List.of() : tree.advanced();
    }

    /**
     * Whether the player holds the matching standalone feature's permission for
     * this skill. When true the skill is treated as already acquired and the
     * feature provides the effect, so the skill's own passive does not also fire
     * (no double invocation). The feature's enabled state is not consulted here.
     */
    public boolean hasFeaturePermission(Player player, String skillId) {
        String featureId = FEATURE_BOUND_SKILLS.get(skillId);
        if (featureId == null) return false;
        return plugin.featureManager().get(featureId)
            .map(f -> player.hasPermission(f.permission())).orElse(false);
    }

    /** The configured prerequisite for a skill (absent = open). */
    public SkillTreeConfig.Prerequisite requirementFor(String skillId) {
        return tree == null ? SkillTreeConfig.Prerequisite.none() : tree.requirementFor(skillId);
    }

    /**
     * Whether the player currently meets this skill's prerequisite (another
     * skill at its minimum required level), defined in the skill-tree config.
     * Skills without a configured prerequisite are always satisfied.
     */
    public boolean prerequisiteSatisfied(Player player, SkillConfig skill) {
        if (hasFeaturePermission(player, skill.id())) return true; // the feature already provides it
        SkillTreeConfig.Prerequisite requirement = requirementFor(skill.id());
        if (!requirement.isPresent()) return true;
        SkillConfig requirementSkill = skill(requirement.skill());
        if (requirementSkill == null) return true; // unknown prerequisite -> treat as open
        return currentLevel(player, requirement.skill()) >= requirement.level();
    }

    public String nameOf(String skillId) {
        SkillConfig skill = skill(skillId);
        return skill == null ? skillId : skill.name();
    }

    // --- event handlers: passive engine ---

    /**
     * Lumberjack / miner / farmer bonus drops, plus the level-10 unlock actions
     * (lumberjack leaves a whole tree, farmer auto-harvests the field). Runs
     * late and checks cancellation so a protection-plugin block is respected.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!check(player)) return;

        Block broken = event.getBlock();
        Material type = broken.getType();
        SkillState state = getState(player);

        SkillConfig lumber = skill("lumberjack");
        int lumberLevel = state.level("lumberjack");
        boolean isLog = lumber != null && lumber.logs().contains(type);

        SkillConfig miner = skill("miner");
        int minerLevel = state.level("miner");
        boolean isMined = miner != null && miner.minerBlocks().contains(type);

        SkillConfig farmer = skill("farmer");
        int farmerLevel = state.level("farmer");
        boolean isCrop = farmer != null && farmer.crops().contains(type);

        // Lumberjack bonus log + Gardener drops roll once per felled tree (the
        // initiating root break), not once per log. During a whole-tree felling
        // sweep TreeFellerUtil reports the player as felling, so these are
        // suppressed on the synthetic per-log breaks and a 20-log tree grants
        // one bonus log / one apple roll instead of twenty.
        boolean fellingSweep = TreeFellerUtil.isFelling(player);
        if (isLog && lumberLevel > 0 && !fellingSweep) {
            if (roll(lumber.valueAt("extra-block", lumberLevel))) drop(broken, type, 1);
        }

        // Gardener: when felling logs, a chance to drop an apple / golden apple.
        SkillConfig gardener = skill("gardener");
        int gardenerLevel = state.level("gardener");
        if (isLog && gardener != null && gardenerLevel > 0 && !fellingSweep) {
            if (roll(gardener.valueAt("apple", gardenerLevel))) drop(broken, Material.APPLE, 1);
            if (roll(gardener.valueAt("golden-apple", gardenerLevel))) drop(broken, Material.GOLDEN_APPLE, 1);
        }
        if (isMined && minerLevel > 0) {
            if (roll(miner.valueAt("extra-block", minerLevel))) {
                // Drop the block's natural drops (e.g. raw copper from COPPER_ORE),
                // matching what vanilla mining gives, instead of the block itself.
                ItemStack tool = player.getInventory().getItemInMainHand();
                for (ItemStack dropItem : broken.getDrops(tool)) {
                    broken.getWorld().dropItemNaturally(broken.getLocation().add(0.5, 0.5, 0.5), dropItem);
                }
            }
        }
        if (isCrop && farmerLevel > 0) {
            if (roll(farmer.valueAt("extra-drop", farmerLevel))) drop(broken, type, 1);
            if (roll(farmer.valueAt("seed", farmerLevel))) drop(broken, seedFor(type), 1);
        }

        // Felling/harvesting only on the real (non-synthetic) break, driven by
        // the dedicated 1-level advanced skills.
        if (!felling && !harvesting) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (isLog && !hasFeaturePermission(player, "tree-feller") && state.level("tree-feller") >= 1) {
                felling = true;
                try {
                    TreeFellerUtil.fell(this, player, broken, lumber.logs(), treeFellerMaxBlocks, tool);
                } finally {
                    felling = false;
                }
            } else if (isCrop && !hasFeaturePermission(player, "auto-crop") && state.level("auto-crop") >= 1) {
                harvesting = true;
                try {
                    // Radius grows with the skill's level: level = radius (1-3).
                    int radius = Math.min(3, Math.max(1, state.level("auto-crop")));
                    AutoCropUtil.harvestRadius(this, player, broken, type, radius, autoCropRequireMature, tool);
                } finally {
                    harvesting = false;
                }
            }
        }
    }

    /** Smith: a % chance the tool/armor takes no durability damage from this hit. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (!check(player)) return;
        if (hasFeaturePermission(player, "smith")) return; // durability feature provides it
        SkillConfig smith = skill("smith");
        int level = getState(player).level("smith");
        if (smith == null || level <= 0) return;
        double pct = smith.valueAt("durability", level);
        if (pct <= 0) return;
        if (roll(pct)) event.setCancelled(true);
    }

    /** Fall Nullify: nullify fall damage once the 1-level skill is held. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!check(player)) return;
        if (hasFeaturePermission(player, "fall-nullify")) return; // fall damage feature provides it
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (getState(player).level("fall-nullify") >= 1) {
            event.setCancelled(true);
        }
    }

    /**
     * Double Jump: consume a mid-air jump when the skill is held. The flight
     * toggle is always cancelled and flight reset for the skill's own launches,
     * so a player using this skill can never be left flying freely.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (!check(player)) return;
        if (hasFeaturePermission(player, "double-jump")) return; // double jump feature provides it
        if (getState(player).level("double-jump") < 1) return;

        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);

        if (!checkCooldown(player.getUniqueId())) return;

        Vector direction = player.getLocation().getDirection();
        player.setVelocity(new Vector(
            direction.getX() * doubleJumpHorizontal,
            doubleJumpVertical,
            direction.getZ() * doubleJumpHorizontal
        ));
        setCooldown(player.getUniqueId());
    }

    /** Double Jump: re-arm the mid-air launch when the skill's player touches solid ground. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!check(player)) return;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (hasFeaturePermission(player, "double-jump")) return; // double jump feature provides it
        if (getState(player).level("double-jump") < 1) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        if (isOnSolidGround(player) || player.isInsideVehicle()) {
            player.setAllowFlight(true);
        }
    }

    /** Stamina: slow hunger depletion so level 10 drains at half rate (2x duration). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!check(player)) return;

        SkillConfig stamina = skill(STAMINA);
        int level = getState(player).level(STAMINA);
        if (stamina == null || level <= 0) return;

        int current = player.getFoodLevel();
        int next = event.getFoodLevel();
        if (next >= current) return; // only losses are slowed

        double mult = 1.0 / (1.0 + stamina.valueAt("hunger", level) / 100.0);
        int reduced = (int) Math.round((current - next) * mult);
        if (reduced > 0 && reduced < (current - next)) {
            event.setFoodLevel(current - reduced);
        }
    }

    /** Warrior: a chance each attack deals double damage (crit). */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!check(player)) return;

        SkillConfig warrior = skill("warrior");
        int warriorLevel = getState(player).level("warrior");
        if (warrior == null || warriorLevel <= 0) return;

        double chance = warrior.valueAt("crit", warriorLevel);
        if (chance <= 0) return;
        if (roll(chance)) {
            event.setDamage(event.getDamage() * 2.0);
        }
    }

    /** Fisherman: bonus catch. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!check(player)) return;

        SkillConfig fisherman = skill("fisherman");
        int level = getState(player).level("fisherman");
        if (fisherman == null || level <= 0) return;

        if (roll(fisherman.valueAt("extra-catch", level)) && !fisherman.bonusItems().isEmpty()) {
            giveItem(player, fisherman.bonusItems().get(rng.nextInt(fisherman.bonusItems().size())));
        }
    }

    /** Lucky Catch: a higher-tier catch. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLuckyCatch(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!check(player)) return;

        SkillConfig lucky = skill("lucky-catch");
        int level = getState(player).level("lucky-catch");
        if (lucky == null || level <= 0) return;

        if (roll(lucky.valueAt("quality", level)) && !lucky.qualityItems().isEmpty()) {
            giveItem(player, lucky.qualityItems().get(rng.nextInt(lucky.qualityItems().size())));
        }
    }

    /** Animalist: extra wool when shearing sheep. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onShear(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        if (!check(player)) return;

        SkillConfig animalist = skill("animalist");
        int level = getState(player).level("animalist");
        if (animalist == null || level <= 0) return;

        if (event.getEntity() instanceof Sheep sheep && roll(animalist.valueAt("extra-gather", level))) {
            giveItem(player, Material.valueOf(sheep.getColor().name() + "_WOOL"));
        }
    }

    /** Animalist: extra bucket of milk when milking a cow. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMilk(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!check(player)) return;
        if (!(event.getRightClicked() instanceof Cow)) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.BUCKET) return;

        SkillConfig animalist = skill("animalist");
        int level = getState(player).level("animalist");
        if (animalist == null || level <= 0) return;

        if (roll(animalist.valueAt("extra-gather", level))) {
            giveItem(player, Material.MILK_BUCKET);
        }
    }

    /** Breeder: a chance to drop an extra baby when breeding. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreed(EntityBreedEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getBreeder() instanceof Player player)) return;
        if (!check(player)) return;

        SkillConfig breeder = skill("breeder");
        int level = getState(player).level("breeder");
        if (breeder == null || level <= 0) return;

        double chance = breeder.valueAt("breed", level);
        if (chance <= 0 || !roll(chance)) return;

        LivingEntity offspring = event.getEntity();
        Class<? extends Entity> clazz = offspring.getType().getEntityClass();
        if (clazz == null) return;
        offspring.getWorld().spawn(offspring.getLocation(), clazz,
            entity -> { if (entity instanceof Ageable ageable) ageable.setAge(-24000); });
    }

    /** Explorer: apply run-speed on join; keep the cache tidy on quit. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applySpeed(event.getPlayer());
        applyWaterSpeed(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        resetSpeed(event.getPlayer());
        resetWaterSpeed(event.getPlayer());
        diverAir.remove(event.getPlayer().getUniqueId());
        gui.playerLeft(event.getPlayer());
        states.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        gui.handleClick(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            gui.onClose(player, event.getView().getTopInventory());
        }
    }

    // --- stamina regen scheduler ---

    /** Every second, grant the stamina bonus regeneration on top of vanilla. */
    private void tickRegen() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!check(player)) continue;
            SkillConfig stamina = skill(STAMINA);
            int level = getState(player).level(STAMINA);
            if (stamina == null || level <= 0) continue;
            double regen = stamina.valueAt("regen", level) / 100.0;
            if (regen <= 0) continue;
            if (player.getFoodLevel() < 18) continue;
            double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (player.getHealth() >= max) continue;
            player.setHealth(Math.min(max, player.getHealth() + regen));
        }
    }

    /**
     * Diver: while underwater, slow the vanilla air drain so a fully-leveled
     * skill lets you hold your breath 100% longer (drain drops to half; 10%
     * at level 1). The air bar is integer but the drain slow-down is fractional,
     * so the leftover is accumulated per player and handed out as whole bubbles.
     */
    private void tickDiver() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!player.isInWater() || !check(player)) {
                diverAir.remove(uuid);
                continue;
            }
            SkillConfig diver = skill("diver");
            int level = getState(player).level("diver");
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
            if (air <= 0) continue; // already drowning; don't fight it
            double addBack = 1.0 - 1.0 / (1.0 + pct / 100.0);
            double acc = diverAir.merge(uuid, addBack, Double::sum);
            int whole = (int) acc;
            if (whole > 0) {
                player.setRemainingAir(Math.min(player.getMaximumAir(), air + whole));
                diverAir.put(uuid, acc - whole);
            }
        }
    }

    /**
     * Swimmer: raise the WATER_MOVEMENT_EFFICIENCY attribute (the Depth Strider
     * swim speed) by `skill level%`. Added on top of the player's natural base
     * (which we remember once) so it composes with Depth Strider's own modifier
     * instead of clobbering it.
     */
    private void applyWaterSpeed(Player player) {
        SkillConfig swimmer = skill("swimmer");
        AttributeInstance swim = player.getAttribute(Attribute.WATER_MOVEMENT_EFFICIENCY);
        if (!check(player) || swimmer == null || swim == null) {
            resetWaterSpeed(player);
            return;
        }
        int level = getState(player).level("swimmer");
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

    // --- helpers ---

    private boolean roll(double pct) {
        return pct >= 100 || rng.nextDouble() * 100.0 < pct;
    }

    /**
     * Server-authoritative ground check (mirrors the standalone Double Jump
     * feature). Unlike {@link Player#isOnGround()}, which reports a
     * client-controlled flag, this probes the world for a solid block below the
     * player's bounding box so the relaunch can't be spoofed mid-air.
     */
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

    private void drop(Block block, Material material, int amount) {
        ItemStack item = new ItemStack(material, amount);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item);
    }

    private void giveItem(Player player, Material material) {
        for (ItemStack leftover : player.getInventory().addItem(new ItemStack(material)).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /** Item seed a harvested crop type replants with (the "random seed" bonus). */
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

    private void applySpeed(Player player) {
        SkillConfig explorer = skill("explorer");
        if (!check(player) || explorer == null) {
            resetSpeed(player);
            return;
        }
        int level = getState(player).level("explorer");
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
}