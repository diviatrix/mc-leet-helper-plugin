# HELPER PLUGIN — BLUEPRINT

## PROJECT STRUCTURE

```
plugins/HelperPlugin/
  build.gradle.kts
  src/main/
    java/com/leet/helper/
      HelperPlugin.java
      feature/
        AbstractFeature.java
        FeatureManager.java
        DoubleJumpFeature.java
        DurabilityFeature.java
        AutoCropFeature.java
        BackFeature.java
        TreeFellerFeature.java
        FallDamageFeature.java
      command/
        HelperCommand.java
        BackCommand.java
      storage/
        StorageManager.java
      util/
        MiniMessageUtil.java
    resources/
      plugin.yml
      config.yml
      features/
        _double_jump.yml
        _durability.yml
        _auto_crop.yml
        _back.yml
        _tree_feller.yml
        _fall_damage.yml
```

**Package:** `com.leet.helper`

---

## ARCHITECTURE: GENERIC FEATURE PATTERN

All features extend `AbstractFeature`. The base class handles everything common.
Concrete features only implement: `featureId()`, `loadFeatureConfig()`, and event handlers.

### Base class responsibilities

1. **Config loading** — reads `base:` and `messages:` sections from YAML
2. **Permission check** — `check(player)` verifies enabled + permission + world
3. **Listener lifecycle** — `enable()` registers events, `disable()` unregisters
4. **Cooldown** — runtime (in-memory) via `checkCooldown()` / `setCooldown()`
5. **Messages** — `sendMessage(player, key, placeholders...)` with per-feature delivery type
6. **Vault helpers** — `hasBalance()`, `withdraw()`

### Feature config structure (all features)

```yaml
base:
  enabled: true
  permission: leet.feat.<id>
  default-permission: true       # true | op | false
  worlds: []                     # empty = all worlds
  cooldown: 0                    # seconds, 0 = no cooldown
  message-type: ACTION_BAR       # ACTION_BAR | CHAT | TITLE

feature:
  # feature-specific fields

messages:
  # key: "MiniMessage formatted string"
```

### Concrete feature contract

Each feature implements only:
- `featureId()` — returns string ID matching the YAML filename
- `loadFeatureConfig(cfg)` — reads `feature:` section into local fields
- `@EventHandler` methods — calls `check(player)`, `sendMessage()`, `setCooldown()`

---

## PERMISSIONS MODEL

Three-level control per feature:

1. **`enabled`** — server-wide kill switch. `false` = unregister all listeners
2. **`default-permission`** — Bukkit permission default (true/op/false)
3. **`worlds`** — world whitelist. Empty = all worlds

Permission checks via `check(player)` in every event handler:
- `enabled` must be true
- `player.hasPermission(permission)` must pass
- If `worlds` not empty, player's world must be in list

Feature permissions registered dynamically at runtime via Bukkit Permission API.
plugin.yml only contains `helper.admin` and command permissions.

---

## STORAGE

### StorageManager — dual-layer

**Runtime (in-memory):** `Map<featureId, Map<key, Map<uuid, Long>>>`. Lost on restart.
Used for DoubleJump cooldowns.

**Persistent (SQLite):** Single `kv_store` table with composite key `(feature_id, key, uuid)`.
Value is TEXT (JSON for complex data like death locations). Survives restarts.
Used for Back death locations and cooldowns.

### Schema

```
kv_store(feature_id TEXT, key TEXT, uuid TEXT, value TEXT, updated_at INTEGER)
PRIMARY KEY (feature_id, key, uuid)
```

---

## FEATURE DETAILS

### DoubleJump

**Logic chain:** Player toggles flight → cancel event → disable flight → check permission + world + cooldown → apply velocity (horizontal * direction, vertical on Y) → set cooldown → Player moves (block-level check) → if on solid ground or in vehicle → re-enable flight.

**Config fields:** horizontal-multiplier, vertical-multiplier
**Cooldown:** Runtime (in-memory), default 1 second
**Messages:** none

### Durability

**Logic chain:** Player item takes damage → check permission + world → check whitelist → apply multiplier to damage AFTER Unbreaking → set damage.

**Config fields:** multiplier (0.1–10.0), min-damage, whitelist (tools & equipment only)
**Whitelist-only model:** only listed items are affected. Default: all tools & equipment.
**Durability + Unbreaking:** Plugin multiplier applied AFTER Unbreaking reduces damage.
**Cooldown:** none
**Messages:** none

### AutoCrop

**Logic chain:** Player breaks block → check permission + world → check if block is a crop → check maturity (if require-mature) → check hoe held (if require-hoe) → loop radius (hard cap 5) around broken block → for each nearby crop of same type + maturity → breakNaturally(tool) — respects Silk Touch.

**Config fields:** radius (default 3, hard cap 5), require-mature, require-hoe, materials
**Cooldown:** none
**Messages:** none

### Back

**Logic chain:** Player dies → check permission + world → serialize location to JSON → store in SQLite → send "death-location-saved" message → Player runs /back → load death from SQLite → check world (must match) → check max-age → check cooldown (persistent, SQLite) → check Vault balance → deduct cost → teleport → save cooldown to SQLite → delete death from SQLite → send "teleport" message.

**Config fields:** max-age (seconds), cost (Vault economy, 0.0 = free)
**Persistence:** Death locations and cooldowns in SQLite (survive restarts)
**Cross-world:** NOT allowed. Player must be in same world as death location.
**Vault economy:** Cost per use. Block + message if insufficient funds.
**Single permission:** `leet.feat.back` controls both death event and /back command.
**No admin bypass:** Cooldowns, costs, max-age apply equally to all players.
**Messages:** death-location-saved, teleport, cooldown-active, expired, insufficient-funds, wrong-world, no-location

### TreeFeller

**Logic chain:** Player breaks block → check permission + world → check if block material is in `logs` → breadth-first search (6-directional) collecting connected log blocks → stop at max-blocks cap → breakNaturally(tool) each collected log.

**Config fields:** logs (list of log/stem materials), max-blocks (default 100, hard cap on collected/break count)
**Connected search:** includes trunk branches and any touching logs, capped to prevent lag on player-built log structures.
**Silk Touch:** respected via breakNaturally(tool).
**Cooldown:** none
**Messages:** none

### FallDamage

**Logic chain:** Player takes fall damage → check permission + world + personal toggle → cancel event.

**Config fields:** none (feature on/off + permission is the only switch)
**Decoupled:** independent of Double Jump — own feature, own `/leet fall` toggle, own `leet.feat.fall_damage` permission.
**Cooldown:** none
**Messages:** none

---

## COMMANDS

### /helper

- `/helper list` — permission `helper.admin`, lists all features with status
- `/helper toggle <id>` — permission `helper.admin.toggle`, toggles feature, persists to YAML
- `/helper info <id>` — permission `helper.admin`, shows feature details
- Full tab completion: subcommands + feature IDs

### /back

- Permission: `leet.feat.back`
- Delegates to `BackFeature.teleportBack(player)`
- Messages sent via ActionBar from BackFeature

---

## VAULT INTEGRATION

**Soft dependency:** `softdepend: [Vault]`

**Setup:** Check for Economy and Permission service providers. Nullable if unavailable.

**Usage:**
- Permission checks: Vault Permission API if available, fallback to Bukkit `player.hasPermission()`
- Economy: BackFeature only. `hasBalance()` / `withdraw()` via base class helpers.

---

## CONFIG (config.yml)

```yaml
config-version: 1
log-level: INFO  # OFF / INFO / DEBUG
```

**Log levels:**
- OFF — only critical errors
- INFO — startup, shutdown, feature enable/disable, errors
- DEBUG — event fires, permission checks, config loads

**Config migration:** Versioned merge — on version mismatch, merge new defaults while preserving user values.

---

## HELPERPLUGIN LIFECYCLE

1. `onEnable()`:
   - Save default configs (config.yml + feature YAMLs)
   - Validate config-version, migrate if needed
   - Init StorageManager (SQLite)
   - Setup Vault (optional)
   - Create FeatureManager, register all features
   - `enableAll()` → for each: `enable()` → `loadConfig()` → if enabled → `registerEvents()`
   - Register commands + tab completers
   - Register permissions dynamically from feature configs

2. `onDisable()`:
   - `disableAll()` — unregister all listeners
   - `close()` StorageManager — close SQLite

---

## DESIGN DECISIONS

| Decision | Choice |
|---|---|
| Package | `com.leet.helper` |
| Base class | AbstractFeature — config, permissions, cooldowns, messages, Vault |
| Config structure | Nested: base / feature / messages sections |
| Permission defaults | Configurable per-feature (`default-permission`) |
| Permissions | Dynamic registration at runtime |
| Feature toggle | Persist to feature YAML (`base.enabled`) |
| Config missing | Error log + disable feature |
| Invalid config values | Error log + disable feature |
| Back persistence | SQLite via StorageManager (generic KV) |
| Cooldown (runtime) | In-memory in StorageManager |
| Cooldown (persistent) | SQLite in StorageManager |
| Back cross-world | Not allowed |
| DoubleJump perf | Block-level movement check |
| AutoCrop + Silk Touch | Respected |
| Durability + Unbreaking | After Unbreaking |
| Tab completion | Full |
| Logging | OFF / INFO / DEBUG |
| bStats | None |
| Config migration | Versioned merge |
| Event priority | Fixed NORMAL |
| Soft dependencies | Vault |
| Economy | Vault, Back only, cost-per-use |
| Insufficient funds | Block + ActionBar |
| Admin bypass | None |
| Reload command | None |
| Durability whitelist | Whitelist-only, tools & equipment default |
| AutoCrop radius | Default 3, hard cap 5 |
| TreeFeller search | BFS connected logs, max-blocks hard cap (100) |
| TreeFeller + Silk Touch | Respected (breakNaturally with tool) |
| DoubleJump cooldown | 1 second default |
| DoubleJump conditions | Ground only |
| Fall damage model | Separate FallDamage feature, own permission + /leet toggle |
| World whitelists | All features |
| Messages | MiniMessage, per-feature delivery type |
| Admin commands | Chat |
| Back permission | Single: `leet.feat.back` |
| Error handling | Per-feature isolation |
| Feature registration | Hardcoded in onEnable |
