package com.leet.interaction;

import com.leet.core.CoreApi;
import com.leet.core.feature.AbstractFeature;
import com.leet.core.feature.MessagingFeature;
import com.leet.interaction.chest.ChestRegistry;
import com.leet.core.reactor.Definition;
import com.leet.core.reactor.ItemSpec;import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The 'interaction' hub feature: signs, NPCs, bound blocks, remote chests and
 * quests, all driven by trigger -> engine -> action definitions. The classic
 * text signs ([Sell], [Warp], ...) parse their lines into action parameters and
 * run through the same engine as bound definitions.
 */
public final class InteractionFeature extends AbstractFeature implements MessagingFeature {

    private static final String FEATURE_ID = "interaction";
    private static final UUID ZERO = new UUID(0, 0);
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final List<String> SIGN_TYPES = List.of(
        "sell", "buy", "free", "enchant", "repair", "kit", "warp", "disposal", "chest", "quest", "interact",
        "weather", "time", "heal", "balance");

    private final LeetInteraction plugin;

    private boolean signsEnabled;
    private boolean npcsEnabled;
    private boolean blocksEnabled;
    private boolean questsEnabled;
    private boolean chestsEnabled;
    private String createPermissionDefault;
    private String usePermissionDefault;
    private final Map<String, Integer> signCooldowns = new HashMap<>();

    private final Map<String, Location> warps = new HashMap<>();

    public InteractionFeature(CoreApi core, LeetInteraction owner) {
        super(core, owner);
        this.plugin = owner;
    }

    private com.leet.core.reactor.Reactor reactor() {
        return plugin.core().reactor();
    }

    @Override
    public String featureId() {
        return FEATURE_ID;
    }

    /**
     * Loads the definitions and chest bindings together with the feature config
     * so /leeta reload interact (which re-enables the feature) picks up edits
     * to definitions/*.yml and chest bindings without a restart.
     */
    @Override
    public void enable() {
        plugin.definitions().load();
        plugin.chests().load();
        super.enable();
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        signsEnabled = cfg.getBoolean("feature.signs.enabled", true);
        createPermissionDefault = cfg.getString("feature.signs.create-permission-default", "op");
        usePermissionDefault = cfg.getString("feature.signs.use-permission-default", "true");
        registerSignPermissions();
        npcsEnabled = cfg.getBoolean("feature.npcs.enabled", true);
        blocksEnabled = cfg.getBoolean("feature.blocks.enabled", true);
        questsEnabled = cfg.getBoolean("feature.quests.enabled", true);
        chestsEnabled = cfg.getBoolean("feature.chests.enabled", true);
        signCooldowns.clear();
        for (String type : SIGN_TYPES) {
            signCooldowns.put(type, Math.max(0, cfg.getInt("feature.signs.cooldowns." + type, 1)));
        }

        warps.clear();
        ConfigurationSection warpSection = cfg.getConfigurationSection("feature.warps");
        if (warpSection != null) {
            for (String name : warpSection.getKeys(false)) {
                Location loc = parseWarp(warpSection.getConfigurationSection(name));
                if (loc != null) {
                    warps.put(name.toLowerCase(Locale.ROOT), loc);
                }
            }
        }
    }

    private static String createNode(String type) {
        return "leet.interaction.sign.create." + type;
    }

    private static String useNode(String type) {
        return "leet.interaction.sign.use." + type;
    }

    private void registerSignPermissions() {
        var createDefault = parseDefault(createPermissionDefault);
        var useDefault = parseDefault(usePermissionDefault);
        for (String type : SIGN_TYPES) {
            addPermission(createNode(type), createDefault);
            addPermission(useNode(type), useDefault);
        }
    }

    private static org.bukkit.permissions.PermissionDefault parseDefault(String def) {
        return switch (def.toLowerCase(Locale.ROOT)) {
            case "true" -> org.bukkit.permissions.PermissionDefault.TRUE;
            case "false" -> org.bukkit.permissions.PermissionDefault.FALSE;
            default -> org.bukkit.permissions.PermissionDefault.OP;
        };
    }

    private void addPermission(String node, org.bukkit.permissions.PermissionDefault pd) {
        try {
            Bukkit.getPluginManager().addPermission(new org.bukkit.permissions.Permission(node, pd));
        } catch (IllegalArgumentException ignored) {
            // Already registered on a prior load.
        }
    }

    private Location parseWarp(ConfigurationSection s) {
        if (s == null) return null;
        World world = Bukkit.getWorld(s.getString("world", ""));
        if (world == null) return null;
        return new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
            (float) s.getDouble("yaw", 0), (float) s.getDouble("pitch", 0));
    }

    // --- engine ---

    /** Runs a bound definition for the player after the feature's own gating. */
    public void runDefinition(Player player, Definition def) {
        if (def == null) return;
        if (!check(player)) return;
        reactor().run(player, def);
    }

    // --- runtime cooldown helpers (in-memory, keyed) ---

    private long remainingSeconds(UUID uuid, String key, int seconds) {
        if (seconds <= 0) return 0;
        long last = plugin.storage().getRuntime(FEATURE_ID, key, uuid, -1);
        if (last < 0) return 0;
        long ms = seconds * 1000L - (System.currentTimeMillis() - last);
        return Math.max(0, ms / 1000);
    }

    private void recordUse(UUID uuid, String key) {
        plugin.storage().setRuntime(FEATURE_ID, key, uuid, System.currentTimeMillis());
    }

    public long cooldownRemaining(Player player, String key, int seconds) {
        return remainingSeconds(player.getUniqueId(), key, seconds);
    }

    public void setCooldown(Player player, String key, int seconds) {
        recordUse(player.getUniqueId(), key);
    }

    // --- helpers used by actions and signs ---

    public void message(Player player, String key, String... placeholders) {
        sendMessage(player, key, placeholders);
    }

    public Location warp(String name) {
        return name == null ? null : warps.get(name.toLowerCase(Locale.ROOT));
    }

    public ConfigurationSection kit(String name) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(
            new java.io.File(owner.getDataFolder(), "features/interaction.yml"));
        return cfg.getConfigurationSection("feature.kits." + name);
    }

    public void giveKit(Player player, String kitName) {
        giveKit(player, kitName, null);
    }

    private void giveKit(Player player, String kitName, String priceText) {
        if (kitName == null) return;
        ConfigurationSection kit = kit(kitName);
        if (kit == null) {
            message(player, "definition-unknown", "<id>", kitName);
            return;
        }
        int cooldown = kit.getInt("cooldown", 0);
        String key = "kit:" + kitName.toLowerCase(Locale.ROOT);
        long remaining = cooldownRemaining(player, key, cooldown);
        if (remaining > 0) {
            message(player, "kit-cooldown", "<kit>", kitName, "<seconds>", String.valueOf(remaining));
            return;
        }
        if (!chargePrice(player, priceText)) return;
        for (Map<?, ?> entry : kit.getMapList("items")) {
            Object spec = entry.get("item");
            if (spec == null) continue;
            int amount = entry.get("amount") instanceof Number n ? n.intValue() : 1;
            ItemStack stack = ItemSpec.build(String.valueOf(spec), amount);
            if (stack != null) {
                ItemSpec.give(player, stack);
            }
        }
        if (cooldown > 0) {
            setCooldown(player, key, cooldown);
        }
        message(player, "kit-given", "<kit>", kitName);
    }

    public void openBoundChest(Player player, String id) {
        openBoundChest(player, id, null);
    }

    private void openBoundChest(Player player, String id, String priceText) {
        if (id == null) return;
        ChestRegistry chests = plugin.chests();
        Block block = chests.chest(id.toLowerCase(Locale.ROOT));
        if (block == null) {
            if (chests.isBound(id.toLowerCase(Locale.ROOT))) {
                message(player, "chest-gone");
            } else {
                message(player, "chest-unknown", "<id>", id);
            }
            return;
        }
        if (!(block.getState() instanceof org.bukkit.block.Chest chest)) {
            message(player, "chest-gone");
            return;
        }
        if (!chargePrice(player, priceText)) return;
        player.openInventory(chest.getInventory());
    }

    // --- triggers: blocks and signs ---

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block.getState() instanceof Sign) {
            if (!signsEnabled || !check(player)) return;
            if (handleSign(player, block)) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) return;
        if (!blocksEnabled || !check(player)) return;

        String bound = plugin.storage().getPersistent(
            FEATURE_ID, com.leet.interaction.command.BindSubcommand.blockKey(block), ZERO);
        if (bound == null) return;
        Definition def = plugin.definitions().get(bound);
        if (def == null) return;
        event.setCancelled(true);
        runDefinition(player, def);
    }

    /** Routes a clicked sign; true when the sign was a functional one. */
    private boolean handleSign(Player player, Block block) {
        Sign sign = (Sign) block.getState();
        List<String> lines = sign.getSide(Side.FRONT).lines().stream()
            .map(PLAIN::serialize).map(String::trim).toList();
        String type = tag(lines.isEmpty() ? "" : lines.get(0));
        if (type == null) return false;

        if (!player.hasPermission(useNode(type))) {
            message(player, "sign-no-permission");
            return true;
        }
        if (!applySignCooldown(player, type, block)) {
            return true;
        }

        switch (type) {
            case "sell" -> trade(player, lines, true);
            case "buy" -> trade(player, lines, false);
            case "free" -> free(player, lines, block);
            case "enchant" -> enchant(player, lines);
            case "repair" -> repair(player, arg(lines, 2), arg(lines, 3));
            case "kit" -> {
                String name = arg(lines, 1);
                if (name != null) giveKit(player, name, arg(lines, 3));
            }
            case "warp" -> warp(player, arg(lines, 1), arg(lines, 3));
            case "weather" -> weather(player, arg(lines, 1), arg(lines, 3));
            case "time" -> time(player, arg(lines, 1), arg(lines, 3));
            case "heal" -> heal(player, arg(lines, 1), arg(lines, 3));
            case "balance" -> showBalance(player);
            case "disposal" -> {
                if (chargePrice(player, arg(lines, 3))) reactor().execute(player, "open-disposal", Map.of());
            }
            case "chest" -> openBoundChest(player, chestId(arg(lines, 1)), arg(lines, 3));
            case "quest" -> {
                if (questsEnabled) {
                    if (chargePrice(player, arg(lines, 3))) plugin.quests().handle(player, arg(lines, 1));
                }
            }
            case "interact" -> {
                Definition def = plugin.definitions().get(arg(lines, 1));
                if (def == null) {
                    message(player, "definition-unknown", "<id>", String.valueOf(arg(lines, 1)));
                } else {
                    if (chargePrice(player, arg(lines, 3))) runDefinition(player, def);
                }
            }
            default -> { }
        }
        return true;
    }

    /** "[Sell]" -> "sell" when the bracket tag matches a known sign type. */
    private String tag(String line0) {
        if (line0 == null || line0.length() < 3) return null;
        String t = line0.toLowerCase(Locale.ROOT);
        if (!t.startsWith("[") || !t.endsWith("]")) return null;
        String inner = t.substring(1, t.length() - 1).trim();
        if (inner.equals("weater")) inner = "weather";
        return SIGN_TYPES.contains(inner) ? inner : null;
    }

    private static String arg(List<String> lines, int index) {
        return index < lines.size() && !lines.get(index).isBlank() ? lines.get(index) : null;
    }

    private static String chestId(String line) {
        if (line == null) return null;
        return line.startsWith("#") ? line.substring(1) : line;
    }

    private boolean applySignCooldown(Player player, String type, Block block) {
        int cooldown = signCooldowns.getOrDefault(type, 1);
        if (cooldown <= 0) return true;
        String key = "sign:" + type + ":" + block.getWorld().getName() + ":"
            + block.getX() + ":" + block.getY() + ":" + block.getZ();
        long remaining = cooldownRemaining(player, key, cooldown);
        if (remaining > 0) {
            message(player, "cooldown", "<seconds>", String.valueOf(remaining));
            return false;
        }
        setCooldown(player, key, cooldown);
        return true;
    }

    private void trade(Player player, List<String> lines, boolean sell) {
        String item = arg(lines, 1);
        if (item == null) return;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("item", item);
        params.put("amount", arg(lines, 2));
        params.put("price", arg(lines, 3));
        reactor().execute(player, sell ? "sell" : "buy", params);
    }

    private void free(Player player, List<String> lines, Block signBlock) {
        String item = arg(lines, 1);
        if (item == null) return;
        Object amount = arg(lines, 2) == null ? 64 : arg(lines, 2);
        ItemStack stack = ItemSpec.build(item, parseAmount(amount));
        if (stack == null) return;
        if (!chargePrice(player, arg(lines, 3))) return;
        org.bukkit.inventory.Inventory inventory = Bukkit.createInventory(player, 54,
            MM.deserialize("<dark_green>Free Items"));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, stack.clone());
        }
        player.openInventory(inventory);
    }

    private int parseAmount(Object value) {
        if (value instanceof Number number) return Math.max(1, Math.min(64, number.intValue()));
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(String.valueOf(value))));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void enchant(Player player, List<String> lines) {
        String enchantment = arg(lines, 1);
        if (enchantment == null) return;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("enchantment", enchantment);
        params.put("level", arg(lines, 2));
        params.put("price", arg(lines, 3));
        reactor().execute(player, "enchant", params);
    }

    private void repair(Player player, String scopeText, String priceText) {
        String scope = scopeText == null || scopeText.isBlank() ? "hand" : scopeText.toLowerCase(Locale.ROOT);
        if (!scope.equals("hand") && !scope.equals("all")) {
            player.sendMessage(MM.deserialize("<yellow>Usage: [Repair] line 3 hand|all, line 4 [price]"));
            return;
        }
        int available = scope.equals("all") ? repairableAll(player) : repairableHand(player);
        if (available == 0) {
            player.sendMessage(MM.deserialize("<red>No damaged items to repair."));
            return;
        }
        double price = chargePriceAmount(player, priceText);
        if (price < 0) return;
        int repaired = scope.equals("all") ? repairAll(player) : repairHand(player);
        player.sendMessage(MM.deserialize("<gray>Repaired <white>" + repaired + " <gray>item(s)."));
    }

    private int repairableHand(Player player) {
        return repairable(player.getInventory().getItemInMainHand()) ? 1 : 0;
    }

    private int repairableAll(Player player) {
        int repairable = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (repairable(item)) repairable++;
        }
        return repairable;
    }

    private boolean repairable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !(item.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }
        return damageable.getDamage() > 0;
    }

    private int repairHand(Player player) {
        return repairItem(player.getInventory().getItemInMainHand()) ? 1 : 0;
    }

    private int repairAll(Player player) {
        int repaired = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (repairItem(item)) repaired++;
        }
        return repaired;
    }

    private boolean repairItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !(item.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }
        if (damageable.getDamage() <= 0) {
            return false;
        }
        damageable.setDamage(0);
        item.setItemMeta(damageable);
        return true;
    }

    private boolean chargePrice(Player player, String priceText) {
        return chargePriceAmount(player, priceText) >= 0;
    }

    private double chargePriceAmount(Player player, String priceText) {
        if (priceText == null || priceText.isBlank()) return 0;
        double price;
        try {
            price = Double.parseDouble(priceText);
        } catch (NumberFormatException e) {
            player.sendMessage(MM.deserialize("<yellow>Line 4 must be a price."));
            return -1;
        }
        if (price <= 0) return 0;
        Economy economy = plugin.core().economy();
        if (economy == null) {
            player.sendMessage(MM.deserialize("<red>No economy is available for this transaction."));
            return -1;
        }
        if (!economy.has(player, price)) {
            player.sendMessage(MM.deserialize("<red>Insufficient funds."));
            return -1;
        }
        if (!economy.withdrawPlayer(player, price).transactionSuccess()) {
            player.sendMessage(MM.deserialize("<red>Transaction failed."));
            return -1;
        }
        return price;
    }

    private void warp(Player player, String name, String priceText) {
        if (name == null) return;
        Location loc = warps.get(name.toLowerCase(Locale.ROOT));
        if (loc == null) {
            message(player, "warp-unknown", "<warp>", name);
            return;
        }
        if (!chargePrice(player, priceText)) return;
        player.teleport(loc);
        message(player, "warped", "<warp>", name);
    }

    private void weather(Player player, String mode, String priceText) {
        if (mode == null) return;
        World world = player.getWorld();
        switch (mode.toLowerCase(Locale.ROOT)) {
            case "clear", "sun", "sunny" -> {
                world.setStorm(false);
                world.setThundering(false);
            }
            case "rain", "storm" -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case "thunder", "thunderstorm" -> {
                world.setStorm(true);
                world.setThundering(true);
            }
            default -> {
                player.sendMessage(MM.deserialize("<yellow>Usage: [Weather] <clear|rain|thunder>"));
                return;
            }
        }
        if (!chargePrice(player, priceText)) return;
        player.sendMessage(MM.deserialize("<gray>Weather set to <white>" + mode.toLowerCase(Locale.ROOT)));
    }

    private void time(Player player, String value, String priceText) {
        if (value == null) return;
        Long ticks = switch (value.toLowerCase(Locale.ROOT)) {
            case "day" -> 1000L;
            case "noon" -> 6000L;
            case "sunset" -> 12000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            case "sunrise" -> 23000L;
            default -> parseTicks(value);
        };
        if (ticks == null) {
            player.sendMessage(MM.deserialize("<yellow>Usage: [Time] <day|noon|sunset|night|midnight|sunrise|ticks>"));
            return;
        }
        if (!chargePrice(player, priceText)) return;
        player.getWorld().setTime(ticks);
        player.sendMessage(MM.deserialize("<gray>Time set to <white>" + value.toLowerCase(Locale.ROOT)));
    }

    private Long parseTicks(String value) {
        try {
            return Math.floorMod(Long.parseLong(value), 24000L);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void heal(Player player, String value, String priceText) {
        double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double amount = max;
        if (value != null && !value.equalsIgnoreCase("full")) {
            try {
                amount = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                player.sendMessage(MM.deserialize("<yellow>Usage: [Heal] [amount|full]"));
                return;
            }
        }
        if (!chargePrice(player, priceText)) return;
        player.setHealth(Math.min(max, player.getHealth() + amount));
        player.setFireTicks(0);
        player.sendMessage(MM.deserialize("<gray>Healed."));
    }

    private void showBalance(Player player) {
        Economy economy = plugin.core().economy();
        if (economy == null) {
            message(player, "no-economy");
            return;
        }
        message(player, "balance-shown", "<balance>", economy.format(economy.getBalance(player)));
    }

    // --- triggers: NPCs ---

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!enabled || !npcsEnabled) return;
        Player player = event.getPlayer();
        if (!check(player)) return;

        String id = event.getRightClicked().getPersistentDataContainer()
            .get(new NamespacedKey(plugin, "interaction-id"),
                org.bukkit.persistence.PersistentDataType.STRING);
        if (id == null) return;

        event.setCancelled(true);
        runDefinition(player, plugin.definitions().get(id));
    }

    // --- sign creation + chest binding ---

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSignChange(SignChangeEvent event) {
        if (!enabled || !signsEnabled) return;
        Player player = event.getPlayer();
        List<String> lines = event.lines().stream()
            .map(PLAIN::serialize).map(String::trim).toList();
        String type = tag(lines.isEmpty() ? "" : lines.get(0));
        if (type == null) return;

        if (!player.hasPermission(createNode(type))) {
            return;
        }

        event.line(0, MM.deserialize("<dark_aqua>[" + type.substring(0, 1).toUpperCase(Locale.ROOT)
            + type.substring(1) + "]"));

        if (type.equals("chest")) {
            bindChestSign(event, player, chestId(arg(lines, 1)));
        }
    }

    private void bindChestSign(SignChangeEvent event, Player player, String id) {
        if (id == null || id.isBlank() || !chestsEnabled) return;
        ChestRegistry chests = plugin.chests();
        if (chests.isBound(id)) {
            message(player, "chest-already-bound", "<id>", id);
            return;
        }
        Block below = event.getBlock().getRelative(BlockFace.DOWN);
        if (below.getType() == Material.CHEST || below.getType() == Material.TRAPPED_CHEST) {
            chests.bind(id, below);
            message(player, "chest-bound", "<id>", id);
        }
    }

    // --- chest binding cleanup ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled() || !chestsEnabled) return;
        Block block = event.getBlock();
        ChestRegistry chests = plugin.chests();

        if ((block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST)
            && chests.isBoundLocation(block)) {
            chests.unbindLocation(block);
            return;
        }

        if (block.getState() instanceof Sign) {
            List<String> lines = ((Sign) block.getState()).getSide(Side.FRONT).lines().stream()
                .map(PLAIN::serialize).map(String::trim).toList();
            if (tag(lines.isEmpty() ? "" : lines.get(0)) != null && "chest".equals(tag(lines.get(0)))) {
                String id = chestId(arg(lines, 1));
                if (id != null && chests.isBound(id)) {
                    chests.unbindId(id);
                }
            }
        }
    }
}
