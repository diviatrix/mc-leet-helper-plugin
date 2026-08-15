# LeetHelper

A **Paper 26.2** plugin providing modular gameplay features. Each feature has its own on-disk YAML config, its own permission node, per-world whitelisting, optional cooldowns, and (for the Back feature) optional Vault economy integration.

Licensed under **CC0 1.0** (public domain) — see [LICENSE](LICENSE).

---

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [Building from Source](#building-from-source)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
  - [config.yml](#configyml)
  - [Common Feature Config Structure](#common-feature-config-structure)
  - [Log Levels](#log-levels)
  - [Message Delivery Types](#message-delivery-types)
  - [Per-Feature Configs](#per-feature-configs)
- [Permissions](#permissions)
  - [Admin Permissions](#admin-permissions)
  - [Feature Permissions](#feature-permissions)
- [Commands](#commands)
  - [/helper](#helper)
  - [/back](#back)
- [Features](#features)
  - [Double Jump](#feature-double-jump)
  - [Durability](#feature-durability)
  - [Auto Crop](#feature-auto-crop)
  - [Back](#feature-back)
  - [Tree Feller](#feature-tree-feller)
  - [Fall Damage](#feature-fall-damage)
- [Storage](#storage)
- [Vault / Economy Integration](#vault--economy-integration)
- [Troubleshooting](#troubleshooting)
- [Known Limitations](#known-limitations)
- [License](#license)

---

## Overview

LeetHelper registers six gameplay features plus one admin command.

| Feature | ID | Description |
|---|---|---|
| Double Jump | `double_jump` | Mid-air double jump with configurable velocity and cooldown |
| Durability | `durability` | Configurable durability multiplier for whitelisted tools/equipment |
| Auto Crop | `auto_crop` | Auto-harvest nearby mature crops when breaking one |
| Back | `back` | Teleport back to your death location, with optional cost and cooldown |
| Tree Feller | `tree_feller` | Felling a log drops the whole connected tree |
| Fall Damage | `fall_damage` | Negates all fall damage for eligible players |

Admin features are managed with the `/helper` command (`list`, `toggle`, `info`).

---

## Requirements

| Requirement | Version |
|---|---|
| Server software | Paper **26.2**+ (Bundled API jar is compiled against `26.2`). Spigot/CraftBukkit are **not** supported. |
| Java | **26+** (the build toolchain targets Java 26). Run your server on a JVM that supports the compiled bytecode. |
| Vault | Optional. Only needed for the Back economy cost. The plugin works fully without it. |

> Java runtime vs. build JDK: the Gradle build uses a Java 26 toolchain, and the plugin bytecode targets Java 26. Use a Java 26 (or later) runtime on your server when running the plugin.

---

## Installation

1. **Build or obtain the jar** — see [Building from Source](#building-from-source). The build produces `build/libs/mc-leet-helper-plugin-1.0.0.jar`.
2. **Copy the jar** into your server's `plugins/` folder:

   ```bash
   cp build/libs/mc-leet-helper-plugin-1.0.0.jar /path/to/server/plugins/
   ```

3. **Start the server.** On first launch the plugin creates its data folder and writes default configuration files:

   ```
   plugins/LeetHelper/
   ├── config.yml                 # Global settings (log level, schema version)
   ├── data.db                    # SQLite database (Back feature persistence)
   └── features/
       ├── _double_jump.yml
       ├── _durability.yml
       ├── _auto_crop.yml
       ├── _back.yml
       ├── _tree_feller.yml
       └── _fall_damage.yml
   ```

4. **Configure to taste** — edit the files inside `plugins/LeetHelper/features/`. Restart the server for changes to take effect (there is **no reload command**; `base.enabled` toggles are the only thing that can be changed live, via `/helper toggle`).

> **Updating the plugin:** on startup the global `config.yml` and every feature config are merged against the bundled defaults. Any **new key** introduced by a newer plugin version (e.g. `require-hoe`) is automatically added to your existing configs while all your other values are preserved. No manual copying needed.

---

## Building from Source

### Prerequisites

- **JDK 26+** (the Gradle toolchain requires it)
- **Gradle 9.7** (a wrapper is included — you only need to invoke `./gradlew`)
- **Internet connection** on the first build (downloads the Paper dev bundle)

### Commands

```bash
# Full build (compiles and produces the jar)
./gradlew build

# Clean + build
./gradlew clean build

# Just compile, skip packaging
./gradlew compileJava
```

**Output artifact:** `build/libs/mc-leet-helper-plugin-1.0.0.jar`

> **First build note:** the paperweight plugin downloads and runs a Paper server JAR to produce the remapped API (~40s). Subsequent builds are cached and faster.

**What the build does:** the `build.gradle.kts` uses `io.papermc.paperweight.userdev` (v2.0.0-beta.21) with `paperDevBundle("26.2.build.+")`. The Vault API is included as a `compileOnly` dependency (JitPack `com.github.MilkBowl:VaultAPI:1.7.1`).

### Expectations & Verification

There is no unit-test suite or test plugin wired into the Gradle build (`gradle.properties` enables Gradle configuration-cache only). Verification is manual on a Paper 26.2 server — see the per-feature behavior notes and [Troubleshooting](#troubleshooting).

---

## Project Structure

```
src/main/
  java/com/leet/helper/
    HelperPlugin.java            # Plugin lifecycle, Vault setup, dynamic permission registration
    feature/
      AbstractFeature.java       # Base class: config, permissions, cooldowns, messages
      FeatureManager.java        # Feature registry, enable/disable/toggle, toggle persistence
      DoubleJumpFeature.java     # Double jump implementation
      DurabilityFeature.java     # Durability multiplier implementation
      AutoCropFeature.java       # Auto crop harvest implementation
      BackFeature.java           # Death-back teleport implementation
      TreeFellerFeature.java     # Whole-tree felling implementation
      FallDamageFeature.java     # Fall-damage immunity implementation
    command/
      HelperCommand.java         # /helper list|toggle|info (+ tab completion)
      BackCommand.java           # /back
      LeetCommand.java           # /leet player feature toggles (+ tab completion)
    storage/
      StorageManager.java        # Runtime (in-memory) + persistent (SQLite) storage
    util/
      MiniMessageUtil.java       # MiniMessage helpers
  resources/
    plugin.yml                   # Plugin metadata, command & admin permission declarations
    config.yml                   # Global config
    features/
      _double_jump.yml
      _durability.yml
      _auto_crop.yml
      _back.yml
      _tree_feller.yml
      _fall_damage.yml
```

---

## Configuration

### config.yml

Global, top-level settings.

```yaml
config-version: 1
log-level: INFO
```

| Key | Type | Description |
|---|---|---|
| `config-version` | integer | Schema version of `config.yml`. On startup, any keys missing from the on-disk file are auto-added from the bundled default while preserving existing values (see [Automatic config merging on update](#automatic-config-merging-on-update)) for `config.yml`). |
| `log-level` | `OFF`, `INFO`, `DEBUG` | Logging verbosity. See [Log Levels](#log-levels). |

### Log Levels

| Level | What is logged |
|---|---|
| `OFF` | Only critical errors (SEVERE), e.g. storage failures, feature-enable exceptions |
| `INFO` | Startup, no-Vault notice, feature enable failures, invalid-whitelist warnings, config errors |
| `DEBUG` | Reserved for fine-grained diagnostics; currently no extra DEBUG output is emitted beyond INFO |

The `log-level` is read from `config.yml`, though most feature-related messages are logged at the `INFO`/`WARNING`/`SEVERE` level regardless.

> **Console prefix & color:** startup and status messages (e.g. `[LeetHelper] Initializing LeetHelper v1.0.0`, `[LeetHelper] Enabled 4/4 feature(s).`, the Vault status) are sent to the console via the console sender with a green `[LeetHelper]` prefix. These colored lines appear in the live console but color codes are stripped from `logs/latest.log`. The automatically-printed Paper line `[LeetHelper] Enabling LeetHelper v1.0.0` and the plugin-logger `[LeetHelper]` WARN/SEVERE lines come from Paper's logger and are not recolored.

> **Renaming a plugin (`name` in `plugin.yml`):** the data folder and all file paths follow the plugin's display name (now `plugins/LeetHelper/`). If you previously ran under the old name (`plugins/HelperPlugin/`), move those files across to keep existing configs and the SQLite `data.db`.

### Common Feature Config Structure

All features use the same layout. Feature configs live in `features/_<id>.yml`.

```yaml
base:
  enabled: true                    # Kill switch. false = feature fully off (no listeners).
  permission: leet.feat.<id>  # Permission node controlling access
  default-permission: false        # true | op | false  (Bukkit permission default)
  worlds: []                       # Empty = all worlds. Non-empty = whitelist of world names.
  cooldown: 0                      # Seconds between uses. 0 = no cooldown.
  message-type: ACTION_BAR         # ACTION_BAR | CHAT | TITLE

feature:
  # Feature-specific settings (see per-feature sections)

messages:
  # key: "MiniMessage formatted string"
```

#### Three-level control per feature

Each feature has three independent on/off controls:

1. **`base.enabled`** — server-wide kill switch. When `false`, the feature's event listeners are **not registered** at all.
2. **`base.default-permission`** — the Bukkit default for the configured permission. Defaults to `false` (nobody can use the feature until you grant the node in your permission plugin, e.g. LuckPerms). `true` = everyone, `op` = ops only.
3. **`base.worlds`** — per-world whitelist. If non-empty, the feature only works in the listed world names. Empty list = works everywhere.

All three are checked by `check(player)` at the start of every relevant event or command. *All* must pass for the feature to act.

### Message Delivery Types

Messages are rendered with [MiniMessage](https://docs.advntr.dev/minimessage/format.html) and delivered according to `base.message-type`:

| Value | Delivery |
|---|---|
| `ACTION_BAR` | Sent to the player's action bar (default) |
| `CHAT` | Sent to chat |
| `TITLE` | Shown as a title (200ms fade-in, 2s stay, 500ms fade-out) |

A missing or empty message template silently produces no message.

#### Automatic config merging on update

Every config — the global `config.yml` **and** each feature file under `features/` — is merged against the bundled default at startup. Any default keys that are missing from the on-disk file are added (and the file saved), while the server admin's existing values are left untouched. Consequences:

- Updating the jar automatically brings new options (e.g. `require-hoe`) into existing configs.
- Deleting a key yourself will NOT persist — it is restored from the default on the next start.
- Removing a key is done by overriding its value (or by setting it to a value equivalent to the default), not by deleting it.

---

### Per-Feature Configs

#### Feature: Double Jump

Allows a mid-air double jump. Config file `features/_double_jump.yml`.

**Behavior**
1. Player on the ground → flight is enabled for them automatically.
2. Player double-taps space (`PlayerToggleFlightEvent`) → the flight toggle is cancelled, flight disabled, and a velocity vector is applied in the player's look direction.
   - Horizontal velocity = look direction × `horizontal-multiplier`.
   - Vertical velocity = fixed `vertical-multiplier`.
3. The runtime cooldown starts.
4. When the player lands (or enters a vehicle), flight is re-enabled.

**Fall damage is no longer part of Double Jump** — it has its own feature and `/leet` toggle (see [Feature: Fall Damage](#feature-fall-damage)).

**Limits:** skipped entirely for Creative and Spectator game modes. The movement check is **block-level only** — it only re-enables flight when the player's block position changes (a performance optimization).

```yaml
base:
  enabled: true
  permission: leet.feat.double_jump
  default-permission: false
  worlds: []
  cooldown: 1
  message-type: ACTION_BAR

feature:
  horizontal-multiplier: 0.25  # Forward/sideways velocity multiplier
  vertical-multiplier: 1.0     # Upward velocity

messages: {}
```

| Key | Type | Default | Description |
|---|---|---|---|
| `horizontal-multiplier` | double | `0.25` | Horizontal (look-direction) velocity multiplier |
| `vertical-multiplier` | double | `1.0` | Fixed upward velocity on jump |

**Cooldown:** runtime only (in-memory), lost on restart. Default `1` second. When on cooldown, the jump is skipped (no velocity) but the flight-toggle event is still cancelled.

---

#### Feature: Durability

Modifies durability damage for **whitelisted** items. Config file `features/_durability.yml`.

**Behavior**
1. A held/broken item takes durability damage (`PlayerItemDamageEvent`).
2. If the item's material is in `whitelist`, the damage is multiplied by `multiplier`, then clamped to at least `min-damage`.
3. Non-whitelisted items are unaffected.

**Ordering:** the multiplier applies **after** the Unbreaking enchantment has already reduced the damage value presented by the event (i.e. it multiplies the post-Unbreaking damage).

```yaml
base:
  enabled: true
  permission: leet.feat.durability
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature:
  multiplier: 0.5     # 0.1 – 10.0
  min-damage: 1        # Minimum damage applied per hit
  whitelist:
    - WOODEN_SWORD
    - WOODEN_SHOVEL
    - WOODEN_PICKAXE
    - WOODEN_AXE
    - WOODEN_HOE
    - STONE_SWORD
    - STONE_SHOVEL
    - STONE_PICKAXE
    - STONE_AXE
    - STONE_HOE
    - IRON_SWORD
    - IRON_SHOVEL
    - IRON_PICKAXE
    - IRON_AXE
    - IRON_HOE
    - GOLDEN_SWORD
    - GOLDEN_SHOVEL
    - GOLDEN_PICKAXE
    - GOLDEN_AXE
    - GOLDEN_HOE
    - DIAMOND_SWORD
    - DIAMOND_SHOVEL
    - DIAMOND_PICKAXE
    - DIAMOND_AXE
    - DIAMOND_HOE
    - NETHERITE_SWORD
    - NETHERITE_SHOVEL
    - NETHERITE_PICKAXE
    - NETHERITE_AXE
    - NETHERITE_HOE
    - TRIDENT
    - BOW
    - CROSSBOW
    - SHIELD
    - LEATHER_HELMET
    - IRON_HELMET
    - GOLDEN_HELMET
    - DIAMOND_HELMET
    - NETHERITE_HELMET
    - LEATHER_CHESTPLATE
    - IRON_CHESTPLATE
    - GOLDEN_CHESTPLATE
    - DIAMOND_CHESTPLATE
    - NETHERITE_CHESTPLATE
    - LEATHER_LEGGINGS
    - IRON_LEGGINGS
    - GOLDEN_LEGGINGS
    - DIAMOND_LEGGINGS
    - NETHERITE_LEGGINGS
    - LEATHER_BOOTS
    - IRON_BOOTS
    - GOLDEN_BOOTS
    - DIAMOND_BOOTS
    - NETHERITE_BOOTS
    - TURTLE_HELMET
    - ELYTRA

messages: {}
```

| Key | Type | Default | Description |
|---|---|---|---|
| `multiplier` | double | `0.5` | Damage multiplier. `0.5` = half damage (items last ~2×), `1.0` = vanilla, `2.0` = double damage |
| `min-damage` | int | `1` | Minimum damage per event (prevents 0/infinite-durability items) |
| `whitelist` | list of Material names | all tools & equipment | Only these materials are affected |

> **Note:** any entry that is not a valid Bukkit `Material` name is skipped with a `Invalid material in durability whitelist:` warning at load and has no effect. Only use exact enum names (e.g. `WOODEN_SWORD`, `DIAMOND_PICKAXE`) — generic names like `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS` (unprefixed armor slots) are not valid Materials and were removed from the defaults; use the material-specific forms (`LEATHER_HELMET`, `IRON_CHESTPLATE`, etc.) instead.

**Multiplier examples**
- `0.5` — items last ~2× longer
- `1.0` — vanilla behavior
- `2.0` — items break ~2× faster

**Cooldown:** none.

---

#### Feature: Auto Crop

Auto-harvests nearby mature crops when a player breaks one. Config file `features/_auto_crop.yml`.

**Behavior**
1. A player breaks a block (`BlockBreakEvent`).
2. If the broken block is in `materials` (and, if `require-mature` is `true`, it is fully grown), the feature scans a cube.
3. The cube spans `-radius`..`+radius` on all three axes around the broken block (excluding the source block itself).
4. Every nearby block of the **same material** (and, if enabled, the **same maturity**) is broken with `breakNaturally(tool)`, using the player's main-hand item.

If `require-hoe` is `true`, the cube scan only happens when the player is harvesting with a hoe in their hand — otherwise only the single broken crop is removed (default vanilla behavior).

**Silk Touch** is respected (with a Silk Touch tool, crops drop as blocks rather than items).

```yaml
base:
  enabled: true
  permission: leet.feat.auto_crop
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature:
  radius: 3              # 1 – 5 (hard-capped at 5)
  require-mature: true   # Only harvest fully grown crops
  require-hoe: false     # Only scan/break nearby crops when holding a hoe
  materials:
    - WHEAT
    - CARROTS
    - POTATOES
    - BEETROOTS
    - NETHER_WART
    - COCOA
    - SWEET_BERRY_BUSH

messages: {}
```

| Key | Type | Default | Description |
|---|---|---|---|
| `radius` | int | `3` | Cube half-size around the broken block. Values > 5 are **clamped to 5**. |
| `require-mature` | bool | `true` | Only break fully grown crops. Maturity uses `Ageable` block data (`age == maximumAge`). |
| `require-hoe` | bool | `false` | Only run the cube scan while the player is holding a hoe (any of wooden/stone/iron/golden/diamond/netherite). With no hoe, only the single broken crop is removed. |
| `materials` | list of Material names | wheat, carrots, potatoes, etc. | Crop materials to auto-harvest. Invalid names are skipped with a warning. |

> Radius scans a cube `-radius`..`+radius` on each axis → `(2×radius+1)³ − 1` candidate blocks (e.g. radius 3 = 342 candidates). Lower the radius on lag-heavy worlds. The scan is performed on the server thread.

**Cooldown:** none.

---

#### Feature: Back

Teleports players to their last death location. **Persistent** via SQLite — survives server restarts. Config file `features/_back.yml`.

**Behavior — on death**
1. Player dies → `check()` (enabled + permission + world).
2. Death location (world, x, y, z, yaw, pitch, timestamp) is serialized to JSON.
3. Stored in SQLite.
4. `death-location-saved` message is sent.

**Behavior — on `/back`**
1. Loads the death location from SQLite.
2. Checks, **in order**:
   - A saved location exists.
   - The location has not expired (`max-age` seconds since the timestamp).
   - Taught world matches — you **must** still be in the same world as the death location.
   - The cooldown (persistent, SQLite) has elapsed.
   - If `cost > 0`, the player has sufficient funds (Vault); otherwise blocked + message.
3. If `cost > 0`, the cost is deducted via Vault.
4. Player is teleported.
5. Cooldown is saved to SQLite; the saved death location is deleted.
6. `teleport` message is sent.

```yaml
base:
  enabled: true
  permission: leet.feat.back
  default-permission: false
  worlds: []
  cooldown: 60
  message-type: ACTION_BAR

feature:
  max-age: 300     # Seconds before a death location expires
  cost: 0.0        # Vault economy cost per use (0.0 = free)

messages:
  death-location-saved: "<green>Death location saved! Use /back to return."
  teleport: "<green>Teleported to your death location."
  cooldown-active: "<red>Cooldown active! Wait <time> seconds."
  expired: "<red>Your death location has expired."
  insufficient-funds: "<red>Insufficient funds! Cost: <cost>"
  wrong-world: "<red>You must be in the same world as your death location."
  no-location: "<red>No death location found."
```

| Key | Type | Default | Description |
|---|---|---|---|
| `max-age` | int | `300` | Seconds before a death location expires. Expired locations are deleted. |
| `cost` | double | `0.0` | Vault economy cost per use. `0` or any value `≤ 0` = free (cost is only applied when `> 0`). |

| Message | Placeholders | Sent when |
|---|---|---|
| `death-location-saved` | — | Death location stored |
| `teleport` | — | Teleport succeeded |
| `cooldown-active` | `<time>` | Cooldown still active (remaining seconds) |
| `expired` | — | Death location older than `max-age` |
| `insufficient-funds` | `<cost>` | Cost set and player lacks balance |
| `wrong-world` | — | Player in a different world than death location |
| `no-location` | — | No saved location, or permission/world blocked |

**Restrictions & notes**
- **Cross-world teleportation is not allowed** — you must be in the world where you died.
- Cooldown is **persistent** (survives restarts) and stored in SQLite, separate from the runtime cooldown used by other features.
- There is **no admin bypass** — cooldown, cost and `max-age` apply equally to everyone.
- `cost` requires Vault with a running economy provider. Without Vault, cost is silently skipped (no charge, no check).

---

#### Feature: Tree Feller

Breaking one log automatically breaks the whole connected tree. Config file `features/_tree_feller.yml`.

**Behavior**
1. A player breaks a log (`BlockBreakEvent`).
2. If the broken block's material is in `logs`, a breadth-first search collects every adjacent log block (6-directional: up/down + 4 horizontal) connected to it.
3. Each collected log is broken with `breakNaturally(tool)`, using the player's main-hand item (so Silk Touch and the tool's drop rates apply).

The connected-component search means the whole trunk **and any branch/logs touching it** come down together, not just the single broken piece. The search stops as soon as `max-blocks` logs have been collected, capping the work so a giant or player-built log structure can't trigger an unbounded chain of block breaks (anti-lag / anti-abuse).

```yaml
base:
  enabled: true
  permission: leet.feat.tree_feller
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature:
  logs:
    - OAK_LOG
    - SPRUCE_LOG
    - BIRCH_LOG
    - JUNGLE_LOG
    - ACACIA_LOG
    - DARK_OAK_LOG
    - MANGROVE_LOG
    - CHERRY_LOG
    - PALE_OAK_LOG
    - CRIMSON_STEM
    - WARPED_STEM
  max-blocks: 100

messages: {}
```

| Key | Type | Default | Description |
|---|---|---|---|
| `logs` | list of Material names | oak/spruce/birch/jungle/acacia/dark-oak/mangrove/cherry/pale-oak logs + crimson/warped stems | Log materials treated as tree trunks. Invalid names are skipped with a warning. |
| `max-blocks` | int | `100` | Hard cap on how many logs the search will collect/break in one tree. Prevents breaking player-built log structures or giant trees from causing lag. |

**Cooldown:** none.

---

#### Feature: Fall Damage

Negates all fall damage for eligible players, as a standalone feature **independent of Double Jump**. Config file `features/_fall_damage.yml`.

**Behavior**
1. A player takes fall damage (`EntityDamageEvent`, cause `FALL`).
2. If the player passes the feature checks (enabled + `leet.feat.fall_damage` permission + personal `/leet` toggle + world), the fall damage is cancelled entirely.

There are no feature-specific config options — the feature is controlled by `base.enabled`, the `leet.feat.fall_damage` permission, and the personal `/leet fall` toggle.

```yaml
base:
  enabled: true
  permission: leet.feat.fall_damage
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature: {}

messages: {}
```

**Cooldown:** none.

---

## Permissions

> **Full reference:** see [doc/permissions.md](doc/permissions.md) for the complete list, defaults, registration logic, gate order, and `/leet` permission behavior.

### Admin Permissions

Declared statically in `plugin.yml`. They control access to the `/helper` command.

| Permission | Default | Description |
|---|---|---|
| `helper.admin` | op | Full admin access **to commands** (children included). Does **not** grant the group these permissions are all `op`. |
| `helper.admin.list` | op | Use `/helper list` |
| `helper.admin.toggle` | op | Use `/helper toggle` |
| `helper.admin.info` | op | Use `/helper info` |

`helper.admin` automatically includes the three children (`list`, `toggle`, `info`) via its `children` map.

### Command-Facing Permissions

Declared in `plugin.yml` on the commands themselves:

| Permission | Default | Found on |
|---|---|---|
| `helper.admin` | op | `helper` command (base command requires it) |
| `leet.feat.back` | (dynamic) | `back` command |

### Feature Permissions (dynamic)

Feature permissions are **not** declared in `plugin.yml`. Instead, `HelperPlugin` registers them at runtime from each feature's config: on every startup it calls `Bukkit.getPluginManager().addPermission()` with the node from `base.permission` and the default from `base.default-permission`.

| Permission | Default (from config) | Description |
|---|---|---|
| `leet.feat.double_jump` | false | Can use double jump |
| `leet.feat.durability` | false | Durability multiplier applies |
| `leet.feat.auto_crop` | false | Auto-harvest crops |
| `leet.feat.back` | false | Can have death locations saved and use `/back` |
| `leet.feat.tree_feller` | false | Whole-tree felling |
| `leet.feat.fall_damage` | false | Fall-damage immunity |

`base.default-permission` maps to a Bukkit default:
- `true` → `PermissionDefault.TRUE` (every player)
- `op` → `PermissionDefault.OP` (ops only)
- `false` → `PermissionDefault.FALSE` (nobody)

**How permission checks happen:** checks use Bukkit's `player.hasPermission(permission)` everywhere. Feature permissions are moderately standard Bukkit permission nodes, so they integrate with LuckPerms, PEX, GroupManager, etc. Even with Vault installed, the plugin does **not** route permission lookups through Vault's `Permission` provider — the Vault permission provider is resolved at startup but currently unused.

> **Restart required for permission changes:** because feature permissions are registered once at startup, editing `base.permission` or `base.default-permission` requires a server restart (or replugin) to take effect.

---

## Commands

### /helper

Admin command for managing features. Requires the `helper` command permission (`helper.admin`, op by default).

| Subcommand | Permission | Description |
|---|---|---|
| `/helper` | (base command) | Prints usage: `/helper <list\|toggle\|info>` |
| `/helper list` | `helper.admin` | Lists all features with ON/OFF status |
| `/helper toggle <id>` | `helper.admin.toggle` | Toggles a feature on/off and **persists** `base.enabled` to its YAML |
| `/helper info <id>` | `helper.admin` | Shows the feature's ID, permission node, and current status |

Tab completion is provided for subcommands and feature IDs.

**On toggle:** `FeatureManager.toggle()` disables the feature (unregisters listeners), re-enables it if it was off, and writes the new state back to `base.enabled` in the feature's YAML file — so the toggle survives a restart. A toggle does **not** reload the rest of the config; config file edits still need a restart.

### /back

| Command | Permission | Description |
|---|---|---|
| `/back` | `leet.feat.back` | Teleports the player to their last death location (see [Feature: Back](#feature-back)) |

This command is player-only (the console receives a "This command can only be used by players." message). On success/failure, feedback is delivered via the Back feature's `message-type`.

### /leet

Player-side feature toggles. Each player can turn supported features **off for themselves** (it's an off-switch — it never grants or revokes access). Persisted per-player in the SQLite `kv_store`, so preferences survive restarts.

| Subcommand | Description |
|---|---|
| `/leet` or `/leet list` | Show your current ON/OFF status for each feature you have |
| `/leet dj` | Toggle **Double Jump** on/off for yourself |
| `/leet crop` | Toggle **Auto Crop** on/off for yourself |
| `/leet tree` | Toggle **Tree Feller** on/off for yourself |
| `/leet fall` | Toggle **Fall Damage** on/off for yourself |

**Permission model** — `/leet` is permission-gated by the underlying feature permissions:
- The command is only available to players who have at least **one** `leet.feat.<id>` permission. If a player has **none**, `/leet` reports `No permission.` and does nothing (including `list`, and no tab completion).
- Tab completion and the status list only show the features the player is actually permissioned for.
- Toggling a feature still checks that feature's permission (e.g. `leet.feat.double_jump`); without it, `/leet <sub>` is declined.
- Because these features default to `false`, `/leet` is **not** available out of the box — a player must be granted at least one feature permission first (see [Feature Permissions](#feature-permissions-dynamic)). Grant e.g. `leet.feat.double_jump`, `leet.feat.auto_crop`, `leet.feat.tree_feller`, or `leet.feat.fall_damage` in your permission plugin to unlock the corresponding `/leet` subcommands.

**How the toggle applies:** a player's off-toggle adds a layer inside `AbstractFeature.check()` (server enabled → base permission → personal toggle → world whitelist). When off, the feature stops firing for that player only; other players and the rest of the config are unaffected.

---

## Features

Detailed behavior, config keys, and limitations are documented per feature in [Configuration](#per-feature-configs) above. The section above covers each fully:

- [Double Jump](#feature-double-jump) — mid-air double jump
- [Durability](#feature-durability) — durability multiplier on whitelisted items
- [Auto Crop](#feature-auto-crop) — batch crop harvesting
- [Back](#feature-back) — death teleportation
- [Tree Feller](#feature-tree-feller) — whole-tree felling
- [Fall Damage](#feature-fall-damage) — fall-damage immunity

---

## Storage

`StorageManager` provides two storage layers.

### Runtime (in-memory)

- Backed by nested map: `Map<featureId, Map<key, Map<uuid, Long>>>`.
- **Lost on server restart.**
- Used for **Double Jump** cooldowns.

### Persistent (SQLite)

- Database file: `plugins/LeetHelper/data.db`.
- Single table:

  ```
  kv_store(feature_id TEXT, key TEXT, uuid TEXT, value TEXT, updated_at INTEGER,
           PRIMARY KEY (feature_id, key, uuid))
  ```

- **Survives restarts.**
- Used for **Back** death locations (JSON payloads), persistent cooldowns, and per-player `/leet` feature toggles (`feature_id` = feature, `key` = `user-toggle`, `value` = `true`/`false`; absent = enabled).
- Uses Bukkit's bundled SQLite JDBC (`jdbc:sqlite:...`). No external driver needed.

> **Backups:** `data.db` is written by the plugin. Backing it up preserves saved death locations and player toggle preferences. Deleting it clears all of that state.

---

## Vault / Economy Integration

Vault is an **optional soft dependency** (`softdepend: [Vault]`). The plugin detects it at startup and resolves the Vault `Economy` provider; it works entirely without Vault.

| Area | Without Vault | With Vault (economy provider) |
|---|---|---|
| Economy (`/back` cost) | Cost is silently skipped — free teleports, no balance checks | Cost checked and deducted per `/back` use |
| Permissions | Uses Bukkit `player.hasPermission()` | Stills uses Bukkit `player.hasPermission()` (the Vault `Permission` provider is resolved but **not used**) |

Notes:
- Only the **Back** feature uses the economy.
- `cost` is charged only when `feature.cost > 0`.
- If the player lacks funds, the `insufficient-funds` message is shown and the teleport is blocked.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Plugin doesn't load on start | Server is not Paper 26.2+, or the JVM is older than Java 26. Check console for a version mismatch. |
| Feature config changes have no effect | Feature configs are read at startup; there is **no reload command**. Restart the server. |
| `/helper` not recognized / "unknown command" | The `helper` command permission (`helper.admin`) is `op` by default — grant it or run as op. |
| Durability whitelist warnings at startup | `Invalid material in durability whitelist:` — an entry in the on-disk `features/_durability.yml` whitelist is not a valid `Material` name (e.g. leftover `STEEL_*` or `HELMET`) and is being ignored. Remove it or use the correct enum name (see the note in [Durability](#feature-durability)). |
| `/back` cost not charged | Vault is not installed, or no economy provider is registered. Without Vault the cost feature is silently disabled. |
| Death locations reset on restart | The `data.db` file was deleted/moved, or the SQLite connection failed to initialize (SEVERE log). |
| `data.db` not created | Check the startup logs for `Failed to initialize SQLite`. The plugin degrades gracefully (Back feature won't persist). |
| DoubleJump not triggering | Check game mode (Creative/Spectator excluded), `double_jump` cooldown (1s default), or the permission/world whitelist. |

---

## Known Limitations

- **No reload command** — config file changes require a restart. Only `/helper toggle` can change `base.enabled` live.
- **`config-version` is informational only** — the merge adds missing keys regardless of the version value; it never removes or rewrites existing keys.
- **Vault permission provider is unused** — permission checks are Bukkit-native even with Vault installed.
- **No admin bypass** for Back cooldown/cost/max-age.
- **No bStats** — sends zero analytics/metrics telemetry.
- **Auto Crop scan is server-thread** — large radii can be expensive on busy worlds.
- **No unit tests** — verification is manual on a Paper server.

---

## License

Licensed under **CC0 1.0 (Creative Commons — Public Domain)**. See [LICENSE](LICENSE).