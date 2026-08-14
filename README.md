HELPER PLUGIN — BLUEPRINT
=========================


СТРУКТУРА ПРОЕКТА
-----------------

plugins/HelperPlugin/
  build.gradle.kts
  src/main/
    java/com/yourname/helper/
      HelperPlugin.java
      feature/
        Feature.java
        FeatureManager.java
        DoubleJumpFeature.java
        DurabilityFeature.java
        AutoCropFeature.java
        BackFeature.java
      command/
        HelperCommand.java
        BackCommand.java
      util/
        ColorUtil.java
    resources/
      plugin.yml
      features/
        _double_jump.yml
        _durability.yml
        _auto_crop.yml
        _back.yml


КОНФИГИ ФИЧ
------------

features/_double_jump.yml

  id: double_jump
  enabled: true
  permission: helper.feature.double_jump
  horizontal-multiplier: 1.5
  vertical-multiplier: 0.8
  cooldown: 0.0
  sound: ENTITY_BAT_TAKEOFF
  sound-volume: 1.0
  sound-pitch: 1.0
  allow-creative: false


features/_durability.yml

  id: durability
  enabled: true
  permission: helper.feature.durability
  multiplier: 0.5
  min-damage: 1
  whitelist: []
  blacklist: []


features/_auto_crop.yml

  id: auto_crop
  enabled: true
  permission: helper.feature.auto_crop
  radius: 3
  require-mature: true
  materials:
    - WHEAT
    - CARROTS
    - POTATOES
    - BEETROOTS
    - NETHER_WART
    - SWEET_BERRY_BUSH


features/_back.yml

  id: back
  enabled: true
  permission: helper.feature.back
  cooldown: 60
  max-age: 300
  message: "&7Телепортация на место смерти..."
  death-location-saved: "&7Место смерти сохранено. /back"
  cooldown-active: "&7Подожди ещё &c{seconds} &7сек."
  expired: "&7Место смерти устарело."


PLUGIN.YML
----------

  name: HelperPlugin
  version: '1.0.0'
  main: com.yourname.helper.HelperPlugin
  api-version: '1.21'
  description: Double jumps, durability, utilities

  commands:
    helper:
      description: Helper management
      usage: /helper <list|toggle|info>
      permission: helper.admin
    back:
      description: Teleport to death location
      usage: /back
      permission: helper.back

  permissions:
    helper.feature:
      description: All features
      default: true
      children:
        helper.feature.double_jump: true
        helper.feature.durability: true
        helper.feature.auto_crop: true
        helper.feature.back: true
    helper.feature.double_jump:
      description: Double jump
      default: true
    helper.feature.durability:
      description: Reduced durability loss
      default: true
    helper.feature.auto_crop:
      description: Auto crop harvest
      default: true
    helper.feature.back:
      description: Teleport to death location
      default: true
    helper.back:
      description: /back command
      default: true
    helper.admin:
      default: op
      children:
        helper.admin.reload: true
        helper.admin.toggle: true
    helper.admin.reload:
      default: op
    helper.admin.toggle:
      default: op


BUILD.GRADLE.KTS
----------------

  plugins {
      java
      id("io.papermc.paperweight.userdev") version "1.7.4"
  }

  group = "com.yourname"
  version = "1.0.0"

  java {
      toolchain.languageVersion.set(JavaLanguageVersion.of(21))
  }

  dependencies {
      paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
  }


FEATURE.JAVA
------------

  public interface Feature {
      String id();
      String permission();
      void enable(HelperPlugin plugin);
      void disable();
      void reload();
      boolean isEnabled();
  }


FEATUREMANAGER.JAVA
-------------------

  public class FeatureManager {

      private final Map<String, Feature> features = new LinkedHashMap<>();
      private final HelperPlugin plugin;

      public FeatureManager(HelperPlugin plugin) {
          this.plugin = plugin;
      }

      public void register(Feature feature) {
          features.put(feature.id(), feature);
      }

      public void enableAll() {
          for (Feature f : features.values()) {
              try {
                  f.enable(plugin);
              } catch (Exception e) {
                  plugin.getLogger().severe(f.id() + ": " + e.getMessage());
              }
          }
      }

      public void disableAll() {
          features.values().forEach(Feature::disable);
      }

      public void reloadAll() {
          for (Feature f : features.values()) {
              f.disable();
              f.reload();
              if (f.isEnabled()) {
                  f.enable(plugin);
              }
          }
      }

      public boolean toggle(String id) {
          Feature f = features.get(id);
          if (f == null) return false;
          if (f.isEnabled()) {
              f.disable();
          } else {
              f.enable(plugin);
          }
          return f.isEnabled();
      }

      public Optional<Feature> get(String id) {
          return Optional.ofNullable(features.get(id));
      }

      public Collection<Feature> all() {
          return features.values();
      }
  }


DOUBLEJUMPFEATURE.JAVA
----------------------

  public class DoubleJumpFeature implements Feature, Listener {

      private final HelperPlugin plugin;
      private final Map<UUID, Boolean> used = new HashMap<>();
      private boolean enabled;
      private String permission;
      private double horizontal;
      private double vertical;
      private boolean allowCreative;
      private Sound sound;
      private float soundVolume, soundPitch;

      public DoubleJumpFeature(HelperPlugin plugin) {
          this.plugin = plugin;
      }

      @Override public String id() { return "double_jump"; }
      @Override public String permission() { return permission; }
      @Override public boolean isEnabled() { return enabled; }

      @Override
      public void reload() {
          FileConfiguration cfg = plugin.getFeatureConfig("double_jump");
          enabled = cfg.getBoolean("enabled", true);
          permission = cfg.getString("permission", "helper.feature.double_jump");
          horizontal = cfg.getDouble("horizontal-multiplier", 1.5);
          vertical = cfg.getDouble("vertical-multiplier", 0.8);
          allowCreative = cfg.getBoolean("allow-creative", false);
          sound = Sound.valueOf(cfg.getString("sound", "ENTITY_BAT_TAKEOFF"));
          soundVolume = (float) cfg.getDouble("sound-volume", 1.0);
          soundPitch = (float) cfg.getDouble("sound-pitch", 1.0);
      }

      @Override
      public void enable(HelperPlugin plugin) {
          reload();
          if (enabled) Bukkit.getPluginManager().registerEvents(this, plugin);
      }

      @Override
      public void disable() {
          HandlerList.unregisterAll(this);
          enabled = false;
      }

      @EventHandler
      public void onToggleFlight(PlayerToggleFlightEvent event) {
          Player player = event.getPlayer();
          if (!player.hasPermission(permission)) return;
          if (!allowCreative && player.getGameMode() == GameMode.CREATIVE) return;

          event.setCancelled(true);
          player.setFlying(false);
          player.setAllowFlight(false);

          Vector dir = player.getLocation().getDirection()
              .multiply(horizontal).setY(vertical);
          player.setVelocity(dir);
          player.playSound(player.getLocation(), sound, soundVolume, soundPitch);

          used.put(player.getUniqueId(), true);
      }

      @EventHandler
      public void onMove(PlayerMoveEvent event) {
          Player player = event.getPlayer();
          if (!player.hasPermission(permission)) return;
          if (!allowCreative && player.getGameMode() == GameMode.CREATIVE) return;

          if (player.isOnGround() || player.isInsideVehicle()) {
              player.setAllowFlight(true);
              used.put(player.getUniqueId(), false);
          }
      }
  }


DURABILITYFEATURE.JAVA
----------------------

  public class DurabilityFeature implements Feature, Listener {

      private final HelperPlugin plugin;
      private boolean enabled;
      private String permission;
      private double multiplier;
      private int minDamage;
      private Set<Material> whitelist;
      private Set<Material> blacklist;

      public DurabilityFeature(HelperPlugin plugin) {
          this.plugin = plugin;
      }

      @Override public String id() { return "durability"; }
      @Override public String permission() { return permission; }
      @Override public boolean isEnabled() { return enabled; }

      @Override
      public void reload() {
          FileConfiguration cfg = plugin.getFeatureConfig("durability");
          enabled = cfg.getBoolean("enabled", true);
          permission = cfg.getString("permission", "helper.feature.durability");
          multiplier = cfg.getDouble("multiplier", 0.5);
          minDamage = cfg.getInt("min-damage", 1);
          whitelist = parseMaterials(cfg.getStringList("whitelist"));
          blacklist = parseMaterials(cfg.getStringList("blacklist"));
      }

      @Override
      public void enable(HelperPlugin plugin) {
          reload();
          if (enabled) Bukkit.getPluginManager().registerEvents(this, plugin);
      }

      @Override
      public void disable() {
          HandlerList.unregisterAll(this);
          enabled = false;
      }

      @EventHandler
      public void onDamage(PlayerItemDamageEvent event) {
          if (!event.getPlayer().hasPermission(permission)) return;

          Material mat = event.getItem().getType();
          if (!whitelist.isEmpty() && !whitelist.contains(mat)) return;
          if (blacklist.contains(mat)) return;

          int newDamage = Math.max(minDamage,
              (int) Math.ceil(event.getDamage() * multiplier));
          event.setDamage(newDamage);
      }

      private Set<Material> parseMaterials(List<String> list) {
          return list.stream()
              .map(s -> Material.valueOf(s.toUpperCase()))
              .collect(Collectors.toSet());
      }
  }


AUTOCROPFEATURE.JAVA
--------------------

  public class AutoCropFeature implements Feature, Listener {

      private final HelperPlugin plugin;
      private boolean enabled;
      private String permission;
      private int radius;
      private boolean requireMature;
      private Set<Material> crops;

      public AutoCropFeature(HelperPlugin plugin) {
          this.plugin = plugin;
      }

      @Override public String id() { return "auto_crop"; }
      @Override public String permission() { return permission; }
      @Override public boolean isEnabled() { return enabled; }

      @Override
      public void reload() {
          FileConfiguration cfg = plugin.getFeatureConfig("auto_crop");
          enabled = cfg.getBoolean("enabled", true);
          permission = cfg.getString("permission", "helper.feature.auto_crop");
          radius = cfg.getInt("radius", 3);
          requireMature = cfg.getBoolean("require-mature", true);
          crops = cfg.getStringList("materials").stream()
              .map(s -> Material.valueOf(s.toUpperCase()))
              .collect(Collectors.toSet());
      }

      @Override
      public void enable(HelperPlugin plugin) {
          reload();
          if (enabled) Bukkit.getPluginManager().registerEvents(this, plugin);
      }

      @Override
      public void disable() {
          HandlerList.unregisterAll(this);
          enabled = false;
      }

      @EventHandler
      public void onBreak(BlockBreakEvent event) {
          Player player = event.getPlayer();
          if (!player.hasPermission(permission)) return;

          Block block = event.getBlock();
          if (!crops.contains(block.getType())) return;
          if (requireMature && !isMature(block)) return;

          for (int dx = -radius; dx <= radius; dx++) {
              for (int dz = -radius; dz <= radius; dz++) {
                  if (dx == 0 && dz == 0) continue;

                  Block nearby = block.getRelative(dx, 0, dz);
                  if (!crops.contains(nearby.getType())) continue;
                  if (requireMature && !isMature(nearby)) continue;

                  nearby.breakNaturally(player.getInventory().getItemInMainHand());
              }
          }
      }

      private boolean isMature(Block block) {
          BlockData data = block.getBlockData();
          if (data instanceof Ageable ageable) {
              return ageable.getAge() == ageable.getMaximumAge();
          }
          return true;
      }
  }


BACKFEATURE.JAVA
----------------

  public class BackFeature implements Feature, Listener {

      private final HelperPlugin plugin;
      private boolean enabled;
      private String permission;
      private int cooldown;
      private int maxAge;

      private final Map<UUID, Location> deathLocations = new HashMap<>();
      private final Map<UUID, Long> deathTimes = new HashMap<>();
      private final Map<UUID, Long> useTimes = new HashMap<>();

      private String msgSaved, msgTeleport, msgCooldown, msgExpired;

      public BackFeature(HelperPlugin plugin) {
          this.plugin = plugin;
      }

      @Override public String id() { return "back"; }
      @Override public String permission() { return permission; }
      @Override public boolean isEnabled() { return enabled; }

      @Override
      public void reload() {
          FileConfiguration cfg = plugin.getFeatureConfig("back");
          enabled = cfg.getBoolean("enabled", true);
          permission = cfg.getString("permission", "helper.feature.back");
          cooldown = cfg.getInt("cooldown", 60);
          maxAge = cfg.getInt("max-age", 300);
          msgSaved = colorize(cfg.getString("death-location-saved", ""));
          msgTeleport = colorize(cfg.getString("message", ""));
          msgCooldown = colorize(cfg.getString("cooldown-active", ""));
          msgExpired = colorize(cfg.getString("expired", ""));
      }

      @Override
      public void enable(HelperPlugin plugin) {
          reload();
          if (enabled) Bukkit.getPluginManager().registerEvents(this, plugin);
      }

      @Override
      public void disable() {
          HandlerList.unregisterAll(this);
          enabled = false;
      }

      @EventHandler
      public void onDeath(PlayerDeathEvent event) {
          Player player = event.getEntity();
          if (!player.hasPermission(permission)) return;

          deathLocations.put(player.getUniqueId(), player.getLocation().clone());
          deathTimes.put(player.getUniqueId(), System.currentTimeMillis());
          player.sendMessage(msgSaved);
      }

      public boolean teleportBack(Player player) {
          UUID uid = player.getUniqueId();
          if (!player.hasPermission(permission)) return false;

          Location loc = deathLocations.get(uid);
          if (loc == null) return false;

          long ageSec = (System.currentTimeMillis() - deathTimes.get(uid)) / 1000;
          if (ageSec > maxAge) {
              deathLocations.remove(uid);
              deathTimes.remove(uid);
              player.sendMessage(msgExpired);
              return false;
          }

          Long lastUse = useTimes.get(uid);
          if (lastUse != null) {
              long elapsed = (System.currentTimeMillis() - lastUse) / 1000;
              if (elapsed < cooldown) {
                  player.sendMessage(msgCooldown
                      .replace("{seconds}", String.valueOf(cooldown - elapsed)));
                  return false;
              }
          }

          player.teleport(loc);
          useTimes.put(uid, System.currentTimeMillis());
          deathLocations.remove(uid);
          deathTimes.remove(uid);
          player.sendMessage(msgTeleport);
          return true;
      }
  }


HELPERPLUGIN.JAVA
-----------------

  public class HelperPlugin extends JavaPlugin {

      private FeatureManager featureManager;

      @Override
      public void onEnable() {
          saveResource("features/_double_jump.yml", false);
          saveResource("features/_durability.yml", false);
          saveResource("features/_auto_crop.yml", false);
          saveResource("features/_back.yml", false);

          featureManager = new FeatureManager(this);
          featureManager.register(new DoubleJumpFeature(this));
          featureManager.register(new DurabilityFeature(this));
          featureManager.register(new AutoCropFeature(this));
          featureManager.register(new BackFeature(this));
          featureManager.enableAll();

          getCommand("helper").setExecutor(new HelperCommand(this));
          getCommand("back").setExecutor(new BackCommand(this));

          long count = featureManager.all().stream()
              .filter(Feature::isEnabled).count();
          getLogger().info("HelperPlugin: " + count + " features enabled");
      }

      @Override
      public void onDisable() {
          featureManager.disableAll();
      }

      public FileConfiguration getFeatureConfig(String id) {
          File file = new File(getDataFolder(), "features/" + id + ".yml");
          return YamlConfiguration.loadConfiguration(file);
      }

      public FeatureManager featureManager() { return featureManager; }
  }


HELPERCOMMAND.JAVA
------------------

  public class HelperCommand implements CommandExecutor {

      private final HelperPlugin plugin;

      public HelperCommand(HelperPlugin plugin) {
          this.plugin = plugin;
      }

      @Override
      public boolean onCommand(CommandSender sender, Command cmd,
                                String label, String[] args) {
          if (args.length == 0) {
              sendHelp(sender);
              return true;
          }

          return switch (args[0]) {
              case "list"   -> handleList(sender);
              case "toggle" -> handleToggle(sender, args);
              case "info"   -> handleInfo(sender, args);
              default       -> { sendHelp(sender); yield true; }
          };
      }

      private boolean handleList(CommandSender sender) {
          if (!sender.hasPermission("helper.admin")) return true;

          for (Feature f : plugin.featureManager().all()) {
              String status = f.isEnabled() ? "ON" : "OFF";
              sender.sendMessage(Component.text(
                  "  " + f.id() + " [" + status + "] — " + f.permission()));
          }
          return true;
      }

      private boolean handleToggle(CommandSender sender, String[] args) {
          if (!sender.hasPermission("helper.admin.toggle")) return true;
          if (args.length < 2) {
              sender.sendMessage(Component.text("/helper toggle <feature_id>",
                  NamedTextColor.RED));
              return true;
          }

          String id = args[1];
          Feature f = plugin.featureManager().get(id).orElse(null);
          if (f == null) {
              sender.sendMessage(Component.text("Not found: " + id,
                  NamedTextColor.RED));
              return true;
          }

          boolean now = plugin.featureManager().toggle(id);
          String state = now ? "enabled" : "disabled";
          sender.sendMessage(Component.text(f.id() + " " + state,
              now ? NamedTextColor.GREEN : NamedTextColor.RED));
          return true;
      }

      private boolean handleInfo(CommandSender sender, String[] args) {
          if (args.length < 2) return false;

          Feature f = plugin.featureManager().get(args[1]).orElse(null);
          if (f == null) {
              sender.sendMessage(Component.text("Not found.", NamedTextColor.RED));
              return true;
          }

          sender.sendMessage(Component.text("ID: " + f.id()));
          sender.sendMessage(Component.text("Permission: " + f.permission()));
          sender.sendMessage(Component.text("Enabled: " + f.isEnabled()));
          return true;
      }

      private void sendHelp(CommandSender sender) {
          sender.sendMessage(Component.text("/helper list"));
          sender.sendMessage(Component.text("/helper toggle <id>"));
          sender.sendMessage(Component.text("/helper info <id>"));
      }
  }


BACKCOMMAND.JAVA
----------------

  public class BackCommand implements CommandExecutor {

      private final HelperPlugin plugin;

      public BackCommand(HelperPlugin plugin) {
          this.plugin = plugin;
      }

      @Override
      public boolean onCommand(CommandSender sender, Command cmd,
                                String label, String[] args) {
          if (!(sender instanceof Player player)) return true;

          plugin.featureManager().get("back").ifPresent(f -> {
              if (f instanceof BackFeature back) {
                  back.teleportBack(player);
              }
          });
          return true;
      }
  }


ОТКЛЮЧАЕМОСТЬ И PERMISSIONS
----------------------------

Уровень 1: enabled: true/false в конфиге фичи
  disable() снимает HandlerList, слушатели мертвы, конфликтов нет

Уровень 2: permission в конфиге фичи
  hasPermission() в каждом EventHandler, конкретный игрок не использует

Управление:
  /helper list          — все фичи, статус, permission
  /helper toggle <id>   — вкл/выкл фичу
  /helper info <id>     — детали


СХЕМА ЗАПУСКА
-------------

  HelperPlugin.onEnable()
    копирует дефолтные конфиги фич
    создаёт FeatureManager
    регистрирует все фичи
    enableAll()
      для каждой фичи:
        reload() — читает конфиг
        если enabled: registerEvents(this)
    регистрирует команды
