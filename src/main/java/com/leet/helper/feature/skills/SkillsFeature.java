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
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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

    private final Map<String, SkillConfig> skills = new LinkedHashMap<>();
    private final Map<UUID, SkillState> states = new HashMap<>();
    private final Random rng = new Random();

    private final List<String> ringSkillIds = List.of(
        "lumberjack", "miner", "builder", "farmer", "animalist", "fisherman", "warrior", "explorer");

    private SkillsGui gui;
    private String guiTitle = "Skills";
    private int guiRows = 6;

    private int treeFellerMaxBlocks = 100;
    private int autoCropRadius = 3;
    private boolean autoCropRequireMature = true;
    private int defenseMaxPct = 50;

    private boolean felling;    // reentrancy guard for lumberjack tree felling
    private boolean harvesting; // reentrancy guard for farmer auto-crop
    private int schedulerTask;

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

        treeFellerMaxBlocks = Math.max(1, cfg.getInt("feature.tree-feller.max-blocks", 100));
        autoCropRadius = Math.min(5, Math.max(1, cfg.getInt("feature.auto-crop.radius", 3)));
        autoCropRequireMature = cfg.getBoolean("feature.auto-crop.require-mature", true);
        defenseMaxPct = Math.max(0, Math.min(100, cfg.getInt("feature.defense.max-pct", 50)));

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
        }
    }

    @Override
    public void disable() {
        if (schedulerTask >= 0) {
            Bukkit.getScheduler().cancelTask(schedulerTask);
            schedulerTask = -1;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetSpeed(player);
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
        return getState(player).level(skillId);
    }

    public int nextCost(Player player, String skillId) {
        SkillConfig skill = skill(skillId);
        if (skill == null) return -1;
        return skill.costForNext(getState(player).level(skillId));
    }

    public boolean hasXp(Player player, int cost) {
        return cost <= 0 || player.getTotalExperience() >= cost;
    }

    public boolean levelUp(Player player, String skillId) {
        SkillConfig skill = skill(skillId);
        if (skill == null) return false;
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
        return ringSkillIds;
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

        if (isLog && lumberLevel > 0) {
            if (roll(lumber.valueAt("extra-block", lumberLevel))) drop(broken, type, 1);
            if (roll(lumber.valueAt("apple", lumberLevel))) drop(broken, Material.APPLE, 1);
            if (roll(lumber.valueAt("golden-apple", lumberLevel))) drop(broken, Material.GOLDEN_APPLE, 1);
        }
        if (isMined && minerLevel > 0) {
            if (roll(miner.valueAt("extra-block", minerLevel))) drop(broken, type, 1);
        }
        if (isCrop && farmerLevel > 0) {
            if (roll(farmer.valueAt("extra-drop", farmerLevel))) drop(broken, type, 1);
            if (roll(farmer.valueAt("seed", farmerLevel))) drop(broken, seedFor(type), 1);
        }

        // Level-10 unlock actions only for the real (non-synthetic) break.
        if (!felling && !harvesting) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (isLog && lumber != null && lumber.unlocked("tree-feller", lumberLevel) && !treeFellerActive(player)) {
                felling = true;
                try {
                    TreeFellerUtil.fell(this, player, broken, lumber.logs(), treeFellerMaxBlocks, tool);
                } finally {
                    felling = false;
                }
            } else if (isCrop && farmer != null && farmer.unlocked("auto-crop", farmerLevel) && !autoCropActive(player)) {
                harvesting = true;
                try {
                    AutoCropUtil.harvestRadius(this, player, broken, type, autoCropRadius, autoCropRequireMature, tool);
                } finally {
                    harvesting = false;
                }
            }
        }
    }

    /** Builder: a % chance a placed block is not consumed from the inventory. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!check(player)) return;

        SkillConfig builder = skill("builder");
        int level = getState(player).level("builder");
        if (builder == null || level <= 0) return;
        if (!builder.builderBlocks().contains(event.getBlock().getType())) return;
        if (!roll(builder.valueAt("no-consume", level))) return;

        // Replenish the single block that was just consumed by the placement.
        for (ItemStack leftover : player.getInventory().addItem(new ItemStack(event.getItemInHand().getType(), 1)).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
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

    /** Warrior: flat damage reduction (ignores configured causes). Explorer: nullify fall at level 10. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!check(player)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();

        // Explorer's level-10 fall immunity is independent of the warrior skill.
        SkillConfig explorer = skill("explorer");
        int explorerLevel = getState(player).level("explorer");
        if (cause == EntityDamageEvent.DamageCause.FALL && explorer != null
                && explorer.unlocked("fall-nullify", explorerLevel)) {
            event.setCancelled(true);
            return;
        }

        SkillConfig warrior = skill("warrior");
        int warriorLevel = getState(player).level("warrior");
        if (warrior == null || warriorLevel <= 0) return;
        if (warrior.ignoredCauses().contains(cause)) return;

        double pct = Math.min(defenseMaxPct, warrior.valueAt("damage-reduction", warriorLevel));
        if (pct <= 0) return;
        event.setDamage(Math.max(0, event.getDamage() * (1.0 - pct / 100.0)));
    }

    /** Fisherman: bonus catch + loot-quality upgrade chances. */
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
        if (roll(fisherman.valueAt("quality", level)) && !fisherman.qualityItems().isEmpty()) {
            giveItem(player, fisherman.qualityItems().get(rng.nextInt(fisherman.qualityItems().size())));
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

    /** Animalist: at level 10, a chance to drop an extra baby when breeding. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreed(EntityBreedEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getBreeder() instanceof Player player)) return;
        if (!check(player)) return;

        SkillConfig animalist = skill("animalist");
        int level = getState(player).level("animalist");
        if (animalist == null || level <= 0) return;

        double chance = animalist.valueAt("breed", level);
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
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        resetSpeed(event.getPlayer());
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

    // --- helpers ---

    private boolean treeFellerActive(Player player) {
        return plugin.featureManager().get("tree_feller").map(f -> f.isEnabled() && f.appliesTo(player)).orElse(false);
    }

    private boolean autoCropActive(Player player) {
        return plugin.featureManager().get("auto_crop").map(f -> f.isEnabled() && f.appliesTo(player)).orElse(false);
    }

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