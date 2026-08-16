# IMPLEMENTATION PLAN

Sequential steps. Each must be completed and verified before moving to the next.

---

## STEP 1: Project scaffold

- Create directory structure under `plugins/LeetHelper/`
- Create `build.gradle.kts` with paperweight userdev 2.0.0-beta.21, Java 25, paperDevBundle 26.2.build.+
- Create `src/main/resources/plugin.yml` with commands (helper, back) and admin permissions only
- Create `src/main/resources/config.yml` with config-version and log-level
- Create placeholder feature YAML files under `features/` (empty base/feature/messages sections)
- Verify: `./gradlew build` compiles without errors

---

## STEP 2: StorageManager

- Create `src/main/java/com/leet/helper/storage/StorageManager.java`
- Implement SQLite connection via Bukkit's built-in JDBC (`jdbc:sqlite:<dataFolder>/data.db`)
- Create `kv_store` table on init: `(feature_id TEXT, key TEXT, uuid TEXT, value TEXT, updated_at INTEGER)`
- Implement runtime methods: `setRuntime()`, `getRuntime()` — backed by nested HashMap
- Implement persistent methods: `setPersistent()`, `getPersistent()`, `deletePersistent()` — backed by SQLite
- Implement `close()` to shut down SQLite connection
- Verify: compiles, SQLite file created on plugin enable

---

## STEP 3: Core base

- Create `src/main/java/com/leet/helper/Core.java`
- Extend `JavaPlugin`
- Implement `onEnable()`: saveDefaultConfig, saveResource for feature YAMLs, init StorageManager
- Implement `onDisable()`: close StorageManager
- Implement `getFeatureConfig(id)`: load YAML from `features/<id>.yml`
- Add getters: `storageManager()`, `featureManager()` (stub null for now)
- Verify: plugin loads on server, SQLite created, configs extracted

---

## STEP 4: AbstractFeature base class

- Create `src/main/java/com/leet/helper/feature/AbstractFeature.java`
- Implement `implements Listener`
- Fields: enabled, permission, defaultPermission, worlds, cooldownSeconds, messageType, messages
- Abstract methods: `featureId()`, `loadFeatureConfig(cfg)`
- `loadConfig()`: reads `base:` and `messages:` sections, calls `loadFeatureConfig()`
- `enable()`: calls `loadConfig()`, if enabled registers events
- `disable()`: unregisters all listeners, sets enabled false
- `check(player)`: verifies enabled + permission + world
- `checkCooldown(uuid)` / `setCooldown(uuid)` / `getCooldownRemaining(uuid)`: runtime via StorageManager
- `sendMessage(player, key, placeholders...)`: reads messages map, applies placeholders, delivers via MiniMessage + messageType
- `hasBalance(player, amount)` / `withdraw(player, amount)`: Vault economy helpers
- Getters: `id()`, `permission()`, `isEnabled()`
- Verify: compiles, abstract contract clear

---

## STEP 5: FeatureManager

- Create `src/main/java/com/leet/helper/feature/FeatureManager.java`
- Fields: `Map<String, AbstractFeature>`, `Core` reference
- `register(feature)` — adds to map
- `enableAll()` — iterates, calls `enable()` on each, catches exceptions per-feature
- `disableAll()` — iterates, calls `disable()` on each
- `toggle(id)` — gets feature, toggles runtime state, calls `persistToggle()`
- `persistToggle(id, newState)` — writes `base.enabled` to feature YAML on disk
- `get(id)` — returns Optional
- `all()` — returns collection
- Verify: compiles

---

## STEP 6: Vault setup

- Add `softdepend: [Vault]` to plugin.yml (already done in step 1)
- In Core: implement `setupVault()` — look up Economy and Permission service providers
- Store as nullable fields: `economy`, `vaultPermission`
- Add getters
- Verify: plugin loads with or without Vault present, no errors

---

## STEP 7: Dynamic permission registration

- In Core `onEnable()`, after `enableAll()`: iterate features, register each permission via `Bukkit.getPluginManager().addPermission()` with PermissionDefault from config
- Verify: permissions appear in LuckPerms / PEX after startup

---

## STEP 8: DoubleJumpFeature

- Create `src/main/java/com/leet/helper/feature/DoubleJumpFeature.java`
- Extend `AbstractFeature`
- `featureId()` returns `"double_jump"`
- `loadFeatureConfig()`: reads horizontal-multiplier, vertical-multiplier from `feature:` section
- `@EventHandler onToggleFlight(PlayerToggleFlightEvent)`: cancel → disable flight → check → checkCooldown → apply velocity → setCooldown
- `@EventHandler onMove(PlayerMoveEvent)`: check → block-level optimization (skip if same block) → if onSolidGround or inVehicle → setAllowFlight(true)
- Create `features/double_jump.yml` with base/feature/messages sections (cooldown: 1)
- Verify: on server, double jump works, cooldown enforced, ground reset works

---

## STEP 9: FallDamageFeature

- Create `src/main/java/com/leet/helper/feature/FallDamageFeature.java`
- Extend `AbstractFeature`
- `featureId()` returns `"fall_damage"`
- `loadFeatureConfig()`: no feature-specific options (feature on/off + permission is the only switch)
- `@EventHandler onFall(EntityDamageEvent)`: cast to Player → check cause is FALL → check → cancel event
- Create `features/fall_damage.yml` with base/feature (empty)/messages sections
- Wire into Core `onEnable()` (saveResourceIfMissing + register feature) and add `/leet fall` alias + display name in LeetCommand
- Verify: eligible players take no fall damage; decoupled from Double Jump permission/toggle

---

## STEP 10: DurabilityFeature

- Create `src/main/java/com/leet/helper/feature/DurabilityFeature.java`
- Extend `AbstractFeature`
- `featureId()` returns `"durability"`
- `loadFeatureConfig()`: reads multiplier, min-damage, whitelist from `feature:` section
- `@EventHandler onDamage(PlayerItemDamageEvent)`: check → check whitelist → apply multiplier to damage AFTER Unbreaking → set damage
- Create `features/durability.yml` with full tools & equipment whitelist
- Verify: tools take reduced/increased damage per multiplier, only whitelisted items affected

---

## STEP 11: AutoCropFeature

- Create `src/main/java/com/leet/helper/feature/AutoCropFeature.java`
- Extend `AbstractFeature`
- `featureId()` returns `"auto_crop"`
- `loadFeatureConfig()`: reads radius (capped at 5), require-mature, require-hoe, materials from `feature:` section
- `@EventHandler onBreak(BlockBreakEvent)`: check → check crop type → check maturity → check hoe held (if require-hoe) → loop radius → breakNaturally(tool) for each valid nearby crop
- Helper `isMature(block)`: checks Ageable max age
- Helper `isHoe(item)`: checks the held item is one of the six hoe materials
- Create `features/auto_crop.yml` with default materials
- Verify: breaking one mature crop harvests nearby mature crops, radius respects cap, Silk Touch works

---

## STEP 12: BackFeature

- Create `src/main/java/com/leet/helper/feature/BackFeature.java`
- Extend `AbstractFeature`
- `featureId()` returns `"back"`
- `loadFeatureConfig()`: reads max-age, cost from `feature:` section
- Implement `serializeLocation(Location)` — JSON string with world, x, y, z, yaw, pitch, timestamp
- Implement `deserializeLocation(String)` — parse JSON back to Location
- `@EventHandler onDeath(PlayerDeathEvent)`: check → serialize location → store in SQLite via storage.setPersistent → sendMessage("death-location-saved")
- `teleportBack(Player)`: check → load from SQLite → check world → check max-age → check cooldown (persistent) → check Vault balance → deduct cost → teleport → save cooldown → delete death → sendMessage("teleport")
- Create `features/back.yml` with messages in MiniMessage format
- Verify: die → get message → /back → teleport works, cooldown works, cross-world blocked, cost deducted

---

## STEP 13: TreeFellerFeature

- Create `src/main/java/com/leet/helper/feature/TreeFellerFeature.java`
- Extend `AbstractFeature`
- `featureId()` returns `"tree_feller"`
- `loadFeatureConfig()`: reads logs (list of log/stem materials), max-blocks (default 100) from `feature:` section
- `@EventHandler onBreak(BlockBreakEvent)`: check → check broken block material is in `logs` → breadth-first search (6-directional) collecting connected log blocks → stop at max-blocks cap → breakNaturally(tool) for each collected log
- Helper `findConnectedLogs(block)`: BFS over log blocks, `max-blocks` hard cap
- Create `features/tree_feller.yml` with default log/stem materials
- Wire into Core `onEnable()` (saveResourceIfMissing + register feature) and add `/leet tree` alias + display name in LeetCommand
- Verify: breaking one log fells the whole connected tree, cap respected, Silk Touch works

---

## STEP 14: XpFeature

- Create `src/main/java/com/leet/helper/feature/XpFeature.java`
- Extend `AbstractFeature`
- `featureId()` returns `"xp"`
- `loadFeatureConfig()`: reads `feature.mining.materials`, `feature.woodcutting.materials`, `feature.crops.materials` (Material → XP maps), `feature.fishing.amount`, `feature.building.amount`, `feature.killing.amount` (fallback), and `feature.killing.mobs` (EntityType → XP map) — invalid Material/EntityType names skipped with a warning
- `@EventHandler onBlockBreak(BlockBreakEvent)`: check → route by broken Material (crops → woodcutting → mining) → award once
- `@EventHandler onFish(PlayerFishEvent)`: check → only on `CAUGHT_FISH` → award fishing amount
- `@EventHandler onPlace(BlockPlaceEvent)`: check → award building amount
- `@EventHandler onKill(EntityDeathEvent)`: victim not a Player → `getKiller()` → check killer → award per-mob (`killing.mobs`) or `killing.amount` fallback
- Helper `award(player, action, amount)`: skip if `amount <= 0`; `player.giveExp(amount)`; `sendMessage(player, "xp-gained", "<amount>", ..., "<action>", ...)` (empty template = silent)
- Create `features/xp.yml` with curated mining/woodcutting/crops material maps, fishing/building amounts, killing fallback + mobs, and an `xp-gained` message
- Wire into Core `onEnable()` (saveResourceIfMissing + register feature) and add `/leet xp` alias + display name in LeetCommand
- Verify: mining stone → +1 XP; fishing → +3; killing a zombie → +3; breaking an unlisted block gives nothing; `xp-gained` shows on the action bar; `/leet xp` knocks it off per-player

---

## STEP 15: HelperCommand

- Create `src/main/java/com/leet/helper/command/HelperCommand.java`
- Implement `CommandExecutor` + `TabCompleter`
- `onCommand()`: switch on args[0] — list, toggle, info
- `handleList()`: permission helper.admin, iterate features, send status
- `handleToggle()`: permission helper.admin.toggle, call featureManager.toggle(), persist
- `handleInfo()`: permission helper.admin, show id/permission/enabled
- `onTabComplete()`: return subcommands, feature IDs for toggle/info
- In Core: register executor + tab completer for "helper" command
- Verify: /helper list, /helper toggle, /helper info work, tab complete works

---

## STEP 16: BackCommand

- Create `src/main/java/com/leet/helper/command/BackCommand.java`
- Implement `CommandExecutor`
- `onCommand()`: cast sender to Player, get BackFeature from FeatureManager, call teleportBack()
- In Core: register executor for "back" command
- Verify: /back triggers teleportBack, console gets no response

---

## STEP 17: Final integration test

- Start Paper 26.2 server with plugin
- Test all features end-to-end:
  - DoubleJump: survival mode, double tap space, cooldown, ground reset
  - Durability: hit mob, check damage reduction, verify whitelist
  - AutoCrop: break mature wheat, verify radius harvest, Silk Touch
  - TreeFeller: break one log, verify whole tree fells, max-blocks cap, Silk Touch
  - FallDamage: eligibility negates fall damage; requires `leet.feat.fall_damage`, independent of Double Jump
  - XP: mine stone → +1 XP action bar; fishing → +3; kill zombie → +3; unlisted block gives nothing; `/leet xp` toggles off
  - Back: die, get message, /back, cooldown, cross-world block, cost
- Test admin commands: /helper list, toggle, info
- Test tab completion
- Test permission defaults (all players get features, ops get admin)
- Test world whitelist (add a world, verify feature only works there)
- Test toggle persistence: /helper toggle → restart → verify state
- Test with Vault absent: plugin loads, economy features gracefully disabled
- Verify: no console errors, all features functional

---

## STEP 18: Skills feature

- Create `src/main/java/com/leet/helper/feature/skills/` (SkillConfig, SkillState, SkillsGui) and `SkillsFeature.java` (`featureId` `"skills"`)
- Extract shared helpers: `feature/TreeFellerUtil.java` and `feature/AutoCropUtil.java`; refactor TreeFellerFeature + AutoCropFeature to use them (skills lumberjack/farmer reuse the same protection-respecting loops)
- `/skills` command (`SkillsCommand`) opens the skill-tree GUI: Stamina centered, 8 passive skills unlock around it once Stamina maxes; detail + Apply/Back confirm screens
- Leveling spends **vanilla XP points** (`getTotalExperience()`/`giveExp(-cost)`); per-skill exp tables in `features/skills.yml`; levels persisted in SQLite kv_store
- Passives: stamina (hunger drain + regen), lumberjack/miner/farmer (extra drops; L10 tree-feller/auto-crop stand down when Tree Feller/Auto Crop features are active), builder (no-consume on placement), animalist (wool/milk; L10 extra baby), fisherman (extra + quality catch), warrior (damage reduction), explorer (walk speed; L10 fall nullify)
- Wire into Core (`saveResourceIfMissing` + register feature/command), plugin.yml, LeetCommand `/leet skills` alias
- Verify: ./gradlew build; on server grant `leet.feat.skills`, `/skills` opens tree, leveling spends XP and persists across restart, ring unlocks at Stamina 10, each passive behaves, `/leet skills` toggles it off
