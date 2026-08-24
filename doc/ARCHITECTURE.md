# Architecture

How LeetHelper is structured and works internally as **five cooperating plugins**. For end-user installation see [README](../README.md); feature-specific commands, configuration and workflows live in the canonical pages under [features](features/README.md).

Contents:

- [Project Structure](#project-structure)
- [How the plugins cooperate (CoreApi)](#how-the-plugins-cooperate-coreapi)
- [Feature Model](#feature-model)
- [Config Handling](#config-handling)
- [Permission Model](#permission-model)
- [Storage](#storage)
- [Vault / Economy Integration](#vault--economy-integration)

---

## Project Structure

The codebase is a Gradle multi-project build (see [BUILDING](BUILDING.md)). Five subprojects, each a standalone Paper plugin:

```
build.gradle.kts, settings.gradle.kts   # multi-project build (version single-sourced)

leet-core/                            # LeetCore — shared infra + 7 standalone features
  src/main/java/com/leet/core/
    LeetCore.java                     # Plugin lifecycle, Vault setup, CoreApi service registration
    CoreApi.java                      # Service contract exposed to the other plugins
    feature/
      AbstractFeature.java            # Thin base: config, enable/disable, gating (check)
      FeatureManager.java             # Feature registry, enable/disable/toggle, toggle persistence
      FeatureRegistry.java            # Narrow read contract for cross-plugin consumers
      ToggleableFeature.java          # Role: per-player /leet toggle
      CostedFeature.java              # Role: Vault per-use cost
      CooldownAware.java              # Role: cooldowns (runtime or persistent)
      MessagingFeature.java           # Role: template messages (action bar/chat/title)
      BlockBreakerFeature.java        # Role: protection-aware multi-block breaking
      DoubleJumpFeature.java          # (CostedFeature + CooldownAware)
      DurabilityFeature.java          # (CostedFeature)
      AutoCropFeature.java            # (CostedFeature + BlockBreakerFeature)
      BackFeature.java                # (CostedFeature + CooldownAware + MessagingFeature, persistent cooldown)
      TreeFellerFeature.java          # (CostedFeature + BlockBreakerFeature)
      FallDamageFeature.java          # (CostedFeature)
      XpFeature.java                  # (MessagingFeature)
      TreeFellerUtil.java             # Shared whole-tree felling (Tree Feller feature + skill)
      AutoCropUtil.java               # Shared auto-harvest (Auto Crop feature + skill)
    craft/
      CustomItemView.java             # Read-only custom-item registry contract (service)
    command/
      HelperCommand.java              # /leeta list|toggle|info|give (+ tab completion)
      BackCommand.java                # /back
      LeetCommand.java                # /leet player feature toggles (+ tab completion)
      CommandUtil.java                # Shared command helpers
    gui/
      GuiManager.java                 # Generic pages/actions inventory GUI backend
    plugin/
      FeaturePluginSupport.java       # requireCore/saveResourceIfMissing/disableFeature helpers
    storage/
      StorageManager.java             # Runtime (in-memory) + persistent (SQLite) storage
    util/
      MiniMessageUtil.java            # MiniMessage helpers
    ItemStackUtil.java                # Item helpers
  src/main/resources/
    plugin.yml                        # LeetCore metadata; /leeta, /back, /leet; leet.admin permissions
    config.yml                        # Global core config
    features/                         # double_jump, durability, auto_crop, back, tree_feller,
                                      #   fall_damage, xp

leet-skills/                          # LeetSkills — the skill-tree plugin
  src/main/java/com/leet/skills/
    LeetSkills.java                   # Plugin lifecycle; binds to CoreApi; owns skills SQLite store
    SkillsFeature.java                # The 'skills' feature (gating/leveling/tree/GUI hub)
    SkillPassiveHandler.java          # Passive effect event handlers + schedulers
    SkillsGui.java                    # Thin client over core's GuiManager
    SkillsCommand.java                # /skills (gates via leet.feat.skills at runtime)
    SkillConfig.java                  # Per-skill definition (skills.yml)
    SkillTreeConfig.java              # Tree topology: ring/advanced/slots + requires (skill-tree.yml)
    SkillState.java                   # Per-player skill-level persistence
  src/main/resources/
    plugin.yml                        # LeetSkills metadata (softdepend Vault, LeetCore); /skills (no static perm)
    features/skills.yml               # Skill definitions (+ binds-feature / toggleable / effects)
    features/skill-tree.yml           # Tree topology + advanced GUI slots

leet-crafting/                        # LeetCrafting — custom items & recipes
    src/main/java/com/leet/crafting/
      LeetCrafting.java                 # Plugin lifecycle; binds to CoreApi; owns item registry + resource pack
      CraftFeature.java                 # The single crafting feature (custom items + recipes)
      craft/
        LeetItem.java                   # A single custom item (id, material, name/lore, food, leet:item model)
        LeetItemRegistry.java           # id -> item registry; registered with core as CustomItemView
        LeetRecipeRegistry.java         # Generic SHAPED/SHAPELESS/SMELT recipe parse + register/gate
      resource/
        ResourcePackService.java        # Builds + serves the additive item-texture resource pack
    src/main/resources/
      plugin.yml                        # LeetCrafting metadata (softdepend Vault, LeetCore); no commands
      config.yml                        # Global + resource-pack.* distribution settings
      features/crafting.yml             # All custom items and recipes (condiments + dishes)
      resource_pack/                    # Additive asset pack (leet: item models + textures + index)

leet-vanity/                          # LeetVanity — a hub of distinct gameplay features
    src/main/java/com/leet/vanity/
      LeetVanity.java                       # Plugin lifecycle; binds to CoreApi
      VanityFeature.java                # The 'vanity' hub feature: several capabilities under one feature id
    src/main/resources/
      plugin.yml                        # LeetVanity metadata (softdepend LeetCore); no commands
      config.yml                        # Global settings (log level)
      features/vanity.yml               # Hub config; each capability has its own section

leet-interaction/                     # LeetInteraction — signs, NPCs, blocks, chests, quests
  src/main/java/com/leet/interaction/
    LeetInteraction.java               # Plugin lifecycle; binds to CoreApi; own SQLite store
    InteractionFeature.java            # The 'interaction' hub feature + all event triggers
    definition/                        # InteractionDefinition + DefinitionRegistry (definitions/*.yml)
    engine via action/                 # ActionRegistry + stateless Action implementations
    trigger/ (inside InteractionFeature)  # sign / entity / block listeners + SignChangeEvent
    chest/ChestRegistry.java           # [Chest] #id bindings (persisted chest index)
    quest/QuestManager.java            # accept / turn-in flow + QuestDefinition
    reputation/ReputationManager.java  # per-player reputation score
    command/BindSubcommand.java        # /leeta bind|unbind (contributed admin subcommands)
  src/main/resources/
    plugin.yml                         # LeetInteraction metadata (softdepend LeetCore, Vault)
    features/interaction.yml           # Capability toggles, kits, warps
    definitions/                       # Example definition files (warp npc, shopkeeper, quest)
tools/cooking/                        # One-off Python tooling for the cooking values/textures (run from repo root)
```

**What each plugin does:**

- **LeetCore** boots first, resolves Vault, owns the shared feature registry, storage, and generic GUI, registers its **seven** standalone features, exposes itself as the **`CoreApi`** service, and wires up the `/leeta`, `/back`, and `/leet` commands. Skill, crafting, and vanity features are contributed *into* LeetCore's shared registry by the other plugins.
- **LeetSkills** soft-depends on LeetCore, looks up `CoreApi`, contributes the `skills` feature, and keeps its **own** SQLite store for skill levels/toggles.
- **LeetCrafting** soft-depends on LeetCore, contributes the single `crafting` feature (food + condiment items and recipes), and owns the item domain: `LeetItemRegistry` (registered with core as a read-only `CustomItemView`, so `/leeta give` and command/eat handling work) and the `ResourcePackService`.
- **LeetVanity** soft-depends on LeetCore, contributes the single `vanity` hub feature (a group of distinct capabilities sharing one feature id), and owns no database.
- **LeetInteraction** soft-depends on LeetCore, contributes the `interaction` feature, registers interaction-specific `/leeta` subcommands, and owns its own SQLite database for bindings, quests and reputation.

---

## How the plugins cooperate (CoreApi)

Core exposes a **narrow service contract** (`CoreApi`) registered via the Bukkit ServicesManager. LeetSkills, LeetCrafting and LeetVanity look it up at enable time through `FeaturePluginSupport.requireCore(...)`; if LeetCore is absent they log a warning and disable themselves gracefully. `CoreApi` exposes:

- `featureRegistry()` — the narrow `FeatureRegistry` (register/get/all/toggle) so the other plugins can contribute features and check registration.
- `storageManager()` — core's persistent store (holds `/leet` toggles and Back death locations).
- `itemRegistry()` — a read-only `CustomItemView` (may be null if no crafting plugin registered one).
- `guiManager()` — the generic GUI backend skills uses.
- `economy()` — the resolved Vault economy (or null).
- `registerFeature(AbstractFeature)` — the entry point the other plugins use to add a feature to the shared registry.
- `registerAdminSubcommand(name, handler)` — lets other plugins contribute `/leeta` subcommands (LeetInteraction registers `bind`/`unbind`); `adminSubcommands()` exposes them to core's command layer.
- `reactor()` — the shared trigger → conditions → actions kernel (see [The reactor](#the-reactor)).
- **`log(String)`** — console logging with the LeetCore prefix.
- `log(String)` — console logging with the LeetCore prefix.

Core never reaches into the concrete classes of the other plugins: it drives everything through `FeatureRegistry` and the role interfaces, so `/leeta`, `/leet`, and the toggle lifecycle work uniformly across features regardless of which plugin registered them.

### The reactor (shared trigger → conditions → actions kernel)

`com.leet.core.reactor` in LeetCore owns the declarative pipeline that LeetInteraction introduced, generalized for every plugin:

- **Definition** (`id` / `triggers` / `conditions` / `actions`) parsed by the shared `DefinitionLoader` from any YAML file. Core loads `plugins/LeetCore/rules/*.yml` as event-rules; LeetInteraction loads `definitions/*.yml` the same way.
- **Reactor.run(player, definition)** — the engine: pluggable conditions → extra `permission` → per-player `cooldown` (runtime store) → Vault `cost` → ordered actions. Feedback uses hardcoded MiniMessage lines so any plugin can drive it without wiring templates.
- **ActionRegistry** — core registers the generic built-ins (`teleport`, `give-items`, `take-items`, `sell`, `buy`, `enchant`, `open-disposal`, `run-command`, `message`, `sound`, `give-exp`); feature plugins contribute domain actions at boot (LeetInteraction: `kit`, `open-chest`, `quest`, `reputation`; LeetSkills: `skill-level-up`).
- **ConditionRegistry** — built-ins `world`, `chance`, `has-item`; contributed: LeetInteraction's `reputation`, LeetSkills' `skill-level`. Item specs (`material:X` / `item:<custom-id>`) resolve vanilla or LeetCrafting custom items uniformly.
- **ReactorTriggers** — core listener firing rule definitions on generic events (`join`, `death`, `block-break`, `consume-item`). Interaction surfaces (signs, NPCs, bound blocks) call `reactor().run` directly from their own listeners.

### Crafting engine & resource pack

The crafting feature builds its items and recipes from the generic machinery in `com.leet.crafting.craft` (owned by **LeetCrafting**, not core):

- **`LeetItemRegistry`** — one `id → item` map. The crafting feature's `feature.items` section is loaded into it once, so any recipe in the same config can reference any item id by name. The `ci` PersistentData tag + `leet:item/<id>` item model are set here. The registry is registered with core as a `CustomItemView`.
- **`LeetRecipeRegistry`** — parses a `feature.recipes` section into Bukkit recipes. Supports **SHAPELESS** (a flat list), **SHAPED** (3 rows of 3 + a letter→ingredient map), and **SMELT** (a single furnace `ingredient` → `result`, with optional `experience`/`cooking-time`). Results are a custom item id or `material:<MATERIAL>`.
- **`ResourcePackService`** — one instance owned by **LeetCrafting**, built in `onEnable` and stopped in `onDisable`. It builds and serves the additive `leet:` item pack (nothing in `assets/minecraft` is overridden), configured under `resource-pack.*` in LeetCrafting's `config.yml`.

---

## Feature Model

Features come in two tiers:

- **Tier 1 — `AbstractFeature`** (a thin base): owns the genuinely common denominator for player-gated mechanics — config loading, `enable`/`disable` event lifecycle, and the gating `check(player)` (permission + per-player `/leet` toggle + world whitelist). It alone does **not** force any Vault/cooldown/messaging machinery on a feature.
- **Tier 2 — opt-in role interfaces**, each with default methods, implemented only as needed:
  - `CostedFeature` — Vault per-use cost (extends `MessagingFeature` for the `insufficient-funds` message).
  - `CooldownAware` — cooldowns; `persistentCooldown()` switches between runtime and persistent (SQLite) storage.
  - `MessagingFeature` — template messages delivered via `base.message-type`.
  - `BlockBreakerFeature` — protection-aware multi-block breaking (`breakIfAllowed`).
  - `ToggleableFeature` — the per-player `/leet` off-switch.

Mapping (see the tree above): Double Jump / Back use cost + cooldown; Auto Crop / Tree Feller use cost + block-breaking; Durability / Fall Damage use cost only; XP uses messages only; Skills uses cooldown + messaging + block-breaking.

- **Config loading** — `loadConfig()` reads `base.*` and per-feature settings, then calls the subclass's `loadFeatureConfig(cfg)` for its own keys. `enable()`/`disable()` register/unregister the event listeners.
- **Gating** — `check(player)` enforces, in order: server `base.enabled` → the feature's `leet.feat.<id>` permission (`player.hasPermission`) → the player's personal `/leet` toggle → the `base.worlds` whitelist. All must pass.
- **Cooldowns** — `CooldownAware` uses runtime storage for most features; `BackFeature` is a `CooldownAware` with `persistentCooldown() = true`.
- **Messages** — `MessagingFeature.sendMessage(player, key, ...)` resolves a template from the feature's `messages` and delivers it per `base.message-type` (action bar, chat, or title) using MiniMessage.
- **Per-use cost** — `CostedFeature.chargeUse(player)` applies the optional Vault `feature.cost`; all features except XP charge per use. Cost semantics: multi-block features charge once per trigger action (Auto Crop / Tree Feller), passive features per event (Durability / Fall Damage); Back charges per `/back`.

`FeatureManager.toggle()` disables a feature, re-enables it if it was off, and persists `base.enabled` back to the feature's on-disk YAML (survives a restart). It does **not** reload the rest of the config. `toggle()` persists `base.enabled` **before** calling `enable()`, so an OFF→ON toggle works at runtime.

### Multi-block breakers and protection plugins

`AutoCropFeature` and `TreeFellerFeature` break many blocks per action. They run at `EventPriority.MONITOR` (so a protection plugin's cancellation of the original break is already visible) and route each additional block through `BlockBreakerFeature.breakIfAllowed(player, block, tool)`, which fires a per-block `BlockBreakEvent` so claim/region plugins (GriefPrevention, WorldGuard, ...) are consulted per block. Protected blocks are skipped rather than force-broken.

The two gather loops are extracted into `TreeFellerUtil` and `AutoCropUtil` (same package, so they can call `breakIfAllowed`) and are reused by the advanced **tree-feller** and **auto-crop** skills — both the standalone features and the skills use the same helpers and the same protection-respecting `breakIfAllowed` path. A skill that duplicates a standalone feature keys off the feature's **binding**: a skill with `binds-feature: <id>` shows as already acquired when the player holds the feature's permission (see [Skills vs. the standalone features](features/skills/skills.md#skills-vs-the-standalone-features)), so the two never both fire.

### SkillsFeature is GUI-driven

`SkillsFeature` (in **LeetSkills**) does most of its player-facing work through an inventory GUI rather than a command response. `/skills` (via `SkillsCommand`) calls `SkillsFeature.openTree`, which delegates to `SkillsGui` — a thin client over LeetCore's generic `GuiManager`. `SkillsFeature` implements the passive effects and the leveling (spending vanilla XP points via `player.getTotalExperience()` / `giveExp(-cost)`). A per-player `SkillState` caches levels in memory and persists them to the skills plugin's own SQLite store.

The skills feature loads **two configs**: `loadFeatureConfig` reads the skill definitions from `features/skills.yml` into `SkillConfig`, then loads `features/skill-tree.yml` into a `SkillTreeConfig`. The tree config owns the topology — the ordered `ring`/`advanced`/`slots` lists (driving both the GUI tiers and the tier ordering) and the `requires` map (each skill's prerequisite, enforced for the GUI lock and the `levelUp` guard). Definitions are pure "what a skill is"; the tree decides "where it sits and what gates it".

---

## Config Handling

### Common feature config layout

Each feature config (`features/<id>.yml` in the owning plugin's data folder) shares the same layout:

```yaml
base:
  enabled: true                    # Kill switch. false = feature fully off (no listeners).
  permission: leet.feat.<id>       # Permission node controlling access (omit for server-wide features)
  default-permission: false        # true | op | false  (Bukkit permission default)
  worlds: []                       # Empty = all worlds. Non-empty = whitelist of world names.
  cooldown: 0                      # Seconds between uses. 0 = no cooldown.
  message-type: ACTION_BAR         # ACTION_BAR | CHAT | TITLE

feature:
  # Feature-specific settings (see per-feature sections)
  cost: 0                          # Optional per-use Vault cost (all features except XP)

messages:
  # key: "MiniMessage formatted string"
```

**Crafting omits `base.permission`/`default-permission`** — it's server-wide (open to all when `base.enabled` is true). `registerPermission()` skips registering a node when the config lacks the `base.permission` key.

#### Three-level control per feature

Each player-gated feature has three independent on/off controls:

1. **`base.enabled`** — server-wide kill switch. When `false`, the feature's event listeners are **not registered** at all.
2. **`base.default-permission`** — the Bukkit default for the configured permission. Defaults to `false` (nobody can use the feature until you grant the node in your permission plugin, e.g. LuckPerms). `true` = everyone, `op` = ops only.
3. **`base.worlds`** — per-world whitelist. If non-empty, the feature only works in the listed world names. Empty list = works everywhere.

All three are checked by `check(player)` at the start of every relevant event or command. *All* must pass for the feature to act.

<a id="feature-permissions"></a>
#### Feature permissions

Every gated feature follows the same permission lifecycle; this is the canonical place for it — feature docs only link here:

- **Node:** `leet.feat.<id>` · **default:** `false` (nobody can use the feature until granted). The node unlocks the feature's effect and (where applicable) the matching `/leet <alias>` personal off-toggle.
- **Grant the node** in your permission plugin (LuckPerms, PEX, ...) to allow the feature. The node alone is not enough — `base.enabled`, the permission, and `base.worlds` must all pass.
- **Set `base.default-permission`** in the feature's YAML to `true` (everyone) or `op` (ops only) to change the out-of-box default. The runtime permission is registered once at startup, so changing `base.permission` or `base.default-permission` requires a **restart**.
- **Server-wide features** (Crafting) declare no `base.permission` key, so no runtime node is registered and `check(player)` skips the permission step — they apply to everyone whenever enabled.

### Message Delivery Types

Messages are rendered with [MiniMessage](https://docs.advntr.dev/minimessage/format.html) and delivered according to `base.message-type`:

| Value | Delivery |
|---|---|
| `ACTION_BAR` | Sent to the player's action bar (default) |
| `CHAT` | Sent to chat |
| `TITLE` | Shown as a title (200ms fade-in, 2s stay, 500ms fade-out) |

A missing or empty message template silently produces no message.

### Per-Feature Configs

Each feature's config file (path shown relative to the owning plugin's data folder, e.g. `plugins/LeetCore/features/`) and its reference doc:

| Plugin | Feature | Config file | Reference |
|---|---|---|---|
| LeetCore | Double Jump | `features/double_jump.yml` | [features/core/double-jump](features/core/double-jump.md) |
| LeetCore | Durability | `features/durability.yml` | [features/core/durability](features/core/durability.md) |
| LeetCore | Auto Crop | `features/auto_crop.yml` | [features/core/auto-crop](features/core/auto-crop.md) |
| LeetCore | Back | `features/back.yml` | [features/core/back](features/core/back.md) |
| LeetCore | Tree Feller | `features/tree_feller.yml` | [features/core/tree-feller](features/core/tree-feller.md) |
| LeetCore | Fall Damage | `features/fall_damage.yml` | [features/core/fall-damage](features/core/fall-damage.md) |
| LeetCore | XP | `features/xp.yml` | [features/core/xp](features/core/xp.md) |
| LeetSkills | Skills | `features/skills.yml` + `features/skill-tree.yml` | [features/skills/skills](features/skills/skills.md) |
| LeetCrafting | Crafting | `features/crafting.yml` | [features/crafting/crafting](features/crafting/crafting.md) |
| LeetVanity | Vanity | `features/vanity.yml` | [features/vanity/vanity](features/vanity/vanity.md) |

### Automatic config merging (backfill)

Every config — each plugin's global `config.yml` **and** each feature file — is merged against the bundled default at startup (`mergeMissingKeys` / `saveResourceIfMissing`). Any default keys missing from the on-disk file are added (and the file saved), while the server admin's existing values are preserved. Consequences:

- Updating the jars automatically brings new options (e.g. `require-hoe`, `cost`) into existing configs.
- Deleting a key yourself will **not** persist — it is restored from the default on the next start.
- Removing a key is done by overriding its value, not by deleting it.

---

## Permission Model

- **Admin permissions** (`leet.admin`, `leet.admin.list|toggle|info`) are declared statically in **LeetCore's** `plugin.yml` and gate `/leeta`. (The `/back` command permission `leet.feat.back` is also declared statically there.)
- **Feature permissions** (`leet.feat.<id>`) are **not** in any `plugin.yml`. The owning plugin's feature registers them at runtime on every startup via `Bukkit.getPluginManager().addPermission()`, using the node from `base.permission` and the default from `base.default-permission`. **Crafting declares no permission at all** (server-wide). **Skills** registers a single `leet.feat.skills` node (default `false`).
- **`/skills`** has **no static command permission** — access is gated entirely at runtime by the same `leet.feat.skills` node (a single source of truth, default-denied; group-scoped so `AuthMe` default-group denial applies).
- **Checks** use Bukkit's `player.hasPermission(permission)` everywhere. Even with Vault installed, the plugins do **not** route permission lookups through Vault's `Permission` provider — that provider is resolved at startup but unused.

**Restart required for permission changes:** because feature permissions are registered once at startup, editing `base.permission` or `base.default-permission` requires a server restart to take effect. Because feature permissions are denied by default and gated by `leet.feat.<id>`, every gated feature needs an explicit grant (e.g. LuckPerms) before it does anything. See [Permissions](permissions.md) for the per-feature table and `/leet` model.

---

## Storage

Storage is **owned by each plugin** — no cross-plugin borrowing.

### LeetCore (`plugins/LeetCore/data.db`)

`StorageManager` provides two layers. Core's DB legitimately owns the cross-plugin control state.

- **Runtime (in-memory)** — nested map `Map<featureId, Map<key, Map<uuid, Long>>>`; lost on restart. Used for **Double Jump** cooldowns.
- **Persistent (SQLite)** — single table:
  ```
  kv_store(feature_id TEXT, key TEXT, uuid TEXT, value TEXT, updated_at INTEGER,
           PRIMARY KEY (feature_id, key, uuid))
  ```
  Survives restarts. Holds the Back feature's death locations (JSON payloads) and persistent cooldowns, and the per-player `/leet` feature toggles (`feature_id` = feature, `key` = `user-toggle`; absent = enabled). Uses Bukkit's bundled SQLite JDBC (`jdbc:sqlite:...`).

### LeetSkills (`plugins/LeetSkills/data.db`)

Its own `StorageManager`, holding per-player skill **levels** and skill **toggles** keyed by skill id. Skill data is isolated from every standalone feature's `/leet` rows.

### LeetCrafting

No database — it owns only the item domain and the served resource pack.

### LeetVanity

No database — its capabilities are stateless, driven entirely by `features/vanity.yml`.

### LeetInteraction (`plugins/LeetInteraction/data.db`)

Its own `StorageManager`, holding block/chest bindings (zero-UUID rows), per-player quest state (`quest:<id>` = active/done/cooldown timestamp) and per-player reputation.

> **Backups:** backing up each plugin's `data.db` preserves saved death locations and player toggle preferences (core) and skill levels (skills). Deleting a DB clears that plugin's state.

---

## Vault / Economy Integration

Vault is a **soft dependency** (`softdepend: [Vault]`), resolved by **LeetCore** at startup; non-economy features work without it. Core passes the resolved economy to features via `CoreApi.economy()`.

**LeetCore is also an economy provider.** When Vault is present, `LeetCore.onLoad()` registers `LeetEconomy` — a minimal SQLite-backed `Economy` implementation (balances in `kv_store` under feature id `economy`, integer cents, no bank support) — with `ServicePriority.Lowest`, so it only fills the gap when no dedicated economy plugin (EssentialsX, etc.) is installed. LeetCore loads at `STARTUP` so the provider is available before regular plugins enable (e.g. plugins that disable themselves when no economy is found).

| Area | Without Vault | With Vault (economy provider) |
|---|---|---|
| Economy (`feature.cost`) | Cost is silently skipped — no charges, no balance checks | Cost checked and deducted per use for any feature with `feature.cost > 0` |
| `[Sell]` / `[Buy]` / `[Enchant]` signs and rules-engine money actions | Transaction blocked: "No economy is available for this transaction." | Money is deposited/withdrawn normally |
| `/bal`, `/pay`, `/leeta eco` | Command refuses: "No economy is installed..." | Commands operate on the resolved economy |
| Permissions | Uses Bukkit `player.hasPermission()` | Still uses Bukkit `player.hasPermission()` (the Vault `Permission` provider is resolved but **not used**) |

Notes:

- Any feature can declare a per-use `feature.cost` (default `0` = free); all features except XP do.
- `cost` is charged only when `feature.cost > 0`.
- If the player lacks funds, the `insufficient-funds` message is shown and the use is blocked.

---

## Related docs

- [BUILDING.md](BUILDING.md) — how to compile and package the five plugins
- [README](../README.md) — configuration, permissions, commands, and operational usage
- [CHANGELOG.md](CHANGELOG.md) — release history
