# Changelog

All notable changes to **LeetHelper**. Entries are newest first.

## Unreleased — bindings tooling + reload fixes

**Added**
- `/leeta bindings` (contributed by LeetInteraction) — lists every NPC, block and chest binding
  with its location and definition, flagging bindings whose definition no longer exists.

**Fixed**
- `/leeta reload interact` now reloads `definitions/*.yml` and chest bindings (previously they were
  only read at startup); `/leeta reload core` also reloads `plugins/LeetCore/rules/*.yml`.
- `/leeta` usage strings now mention the contributed `bind`/`unbind` subcommands.

**Changed**
- Dead trade feedback templates removed from `features/interaction.yml` (that feedback now comes
  from the reactor's built-in actions).

## Unreleased — shared reactor kernel (triggers / conditions / actions)

**Changed**
- **Reactor in LeetCore** (`com.leet.core.reactor`) — the interaction plugin's trigger → conditions →
  actions kernel moved into core and generalized. `CoreApi.reactor()` exposes it to every plugin:
  shared `DefinitionLoader`, `ActionRegistry` (generic built-ins: teleport, give/take-items, sell,
  buy, enchant, open-disposal, run-command, message, sound, give-exp), `ConditionRegistry`
  (built-ins: world, chance, has-item) and the `Reactor.run` engine (conditions → permission →
  cooldown → Vault cost → actions).
- **Event rules** — core now loads `plugins/LeetCore/rules/*.yml`; definitions with a `triggers:`
  list fire on `join`, `death`, `block-break` and `consume-item` (bundled example: `welcome.yml`).
- **LeetInteraction slimmed** — it keeps its bindings (signs, NPCs, blocks), chests, quests and
  reputation, and contributes its domain actions/condition (`kit`, `open-chest`, `quest`,
  `reputation`) into the shared registry. Definitions and rules use the same file format.
- **LeetSkills adapters** — contributes a `skill-level` condition and a `skill-level-up` action
  into the reactor, so quests/rules can gate on and grant skill levels.

## Unreleased — LeetInteraction plugin (signs, NPCs, quests)

**Added**
- **LeetInteraction** — a fifth plugin (`leet-interaction-<v>.jar`, package `com.leet.interaction`). It
  soft-depends on LeetCore and contributes a single `interaction` hub feature: a trigger → engine →
  action system. YAML definitions in `plugins/LeetInteraction/definitions/` are bound to the world via
  classic text signs (`[interact] <id>`), vanilla entities tagged with `/leeta bind <id>` (default
  NPC behavior is cancelled for bound entities), or any block bound with `/leeta bind` (block
  bindings persist in the plugin's own SQLite store).
- **Classic signs** — `[Sell]`, `[Buy]`, `[Free]`, `[Enchant]`, `[Repair]`, `[Kit]`, `[Warp]`, `[Weather]`,
  `[Time]`, `[Heal]`, `[Disposal]`, `[Chest] #id` and `[Quest] <id>` signs, gated by runtime
  create/use permissions (`leet.interaction.sign.create.<type>` and `leet.interaction.sign.use.<type>`).
  `[Chest]` signs placed on a chest bind it (single
  or double); any other `[Chest] #id` sign opens that chest's inventory remotely.
- **Actions** — teleport (inline location or named warp), give/take items, sell, buy, enchant, kit,
  disposal GUI, open chest, run commands, message, sound, give exp, reputation, quest. Item specs
  support vanilla materials and LeetCrafting custom items (`item:<id>`).
- **Quests & reputation** — definitions may carry a `quest:` section (requirements: items/money/
  reputation; rewards: items/money/exp/reputation/commands; repeatable + cooldown). Per-player quest
  state and reputation live in `plugins/LeetInteraction/data.db`.
- **Core seam** — `CoreApi.registerAdminSubcommand` lets feature plugins contribute `/leeta`
  subcommands (used for `/leeta bind|unbind`); `/leeta reload interact` reloads definitions.

## Unreleased — LeetVanity plugin + connected openings

**Added**
- **LeetVanity** — a fourth plugin (`leet-vanity-<v>.jar`, package `com.leet.vanity`). It
  soft-depends on LeetCore and contributes a single `vanity` hub feature into core's shared
  feature registry. The hub groups several distinct, unrelated capabilities under one feature
  id and one permission node (`leet.feat.vanity`, default `false`); future capabilities are
  added as new sections of `plugins/LeetVanity/features/vanity.yml`.
- **Connected openings** (the `vanity` feature's first capability, `feature.connected`) — when
  either half of an adjacent, same-facing pair of doors is opened or closed, the other half
  moves to match. Only doors participate (trapdoors and fence gates are excluded), and state
  changes are followed both from a player right-clicking a half and from any redstone power
  change on a door (buttons, levers, pressure plates, and wire alike).
- **Sitting** (the `vanity` feature's second capability, `feature.sit`) — right-click any block
  in the seat list (default: every stair and slab) to sit on it via a hidden armor stand; sneak
  to get up. The player faces the seat's front (stairs) or keeps their own heading.

**Docs**
- `doc/features/vanity.md` added; `README`, `ARCHITECTURE`, `BUILDING`, `permissions`, and
  `Admin` updated from "three plugins" to four, and the new feature is indexed.

---

## Unreleased — Crafting feature merge

**Changed**
- **Cooking and Crafting are now a single feature.** LeetCrafting registers **one**
  `crafting` feature with `com.leet.core` instead of two (`cooking` + `crafting`).
  The custom food items (Dough, Croissant, Borsh, …) and the non-food items (Salt)
  live in the same `plugins/LeetCrafting/features/crafting.yml`. The `CraftingFeature` /
  `CookingFeature` Java stubs were deleted; `CraftFeature` now hardcodes the
  `crafting` id.
- The `/leet cook` info subcommand is gone. Crafting is server-wide and toggleable
  via `/leeta toggle crafting` (admin) or by editing `base.enabled`.
- `/leeta reload craft` reloads the single `crafting` feature.

**Migration**
- Existing servers with a pre-merge `plugins/LeetCrafting/features/cooking.yml`: on
  the first start with this version, the plugin copies `cooking.yml`'s `base.enabled`
  into `crafting.yml` (only if `crafting.yml` has no explicit value) and deletes the
  stale `cooking.yml`. Your on/off state survives the upgrade.

**Docs**
- `doc/features/cooking.md` deleted; all dish recipes + Salt + the config layout
  now live in `doc/features/crafting.md`.
- `doc/ARCHITECTURE.md`, `doc/Admin.md`, `doc/permissions.md`, `doc/features/crafting/resource-pack.md`,
  and `README.md` updated to drop the Cooking feature references and the
  `/leet cook` subcommand.

---

## Unreleased — Resource-pack ops doc + docs cleanup

**Docs**
- New **`doc/features/crafting/resource-pack.md`** — the canonical operational guide for the
  LeetCrafting item-texture resource pack: config keys, the embedded HTTP server,
  FRPC / reverse-proxy deployment, the **`/craft-pack.zip` path-routing rule**, log-line
  diagnostics (`[RP]`, `[RP-HTTP]`, `Callback fired`, `Callback timed out`), and a full
  troubleshooting matrix.
- `doc/features/cooking.md` and `doc/features/crafting.md` — the long "Dish icons &
  resource pack" section was removed (it duplicated plugin-level concerns) and replaced
  with a one-line pointer to the new guide from each feature's "Notes & limits" section.
  Both features share the same resource pack and the same icon pipeline; neither doc
  should be where the operational story lives.
- `doc/Admin.md` — Configuration section links the new guide; Troubleshooting table now
  covers "custom items show no icon", "`Callback timed out after 10s`", and
  "No resource-pack URL available" with concrete causes/fixes.
- `README.md` — added Resource Pack link to the Table of Contents and a step-5
  configuration pointer in Installation.
- Removed `doc/ARCHITECTURE-REWORK.md` — its contents were a plan/record for the
  rework that produced the current three-plugin split; the result is what
  `doc/ARCHITECTURE.md` describes today. Cross-references updated.
- Removed empty `doc/dev/`.

---

## 1.5.1 — Admin feature reload

**Added**
- `/leeta reload <core|skills|craft>` — admin command that reloads a group's feature
  configs from disk at runtime (re-reads `base.enabled` and re-registers listeners,
  passives, and recipes). Permission: `leet.admin.reload` (default op, child of
  `leet.admin`).
- Reload groups: `core` → double_jump, durability, auto_crop, back, tree_feller,
  fall_damage, xp; `skills` → skills; `craft` → crafting, cooking.

**Notes**
- Reloads only feature configs (`features/*.yml`); permission and command changes in
  `plugin.yml` still require a restart.
- A player with the `/skills` GUI open during `reload skills` should close and reopen
  it.

---

## 1.5.0 — Three-plugin split

**Added**
- The single plugin is now split into **three cooperating Paper plugins**, built by a
  multi-project Gradle build (see [BUILDING](BUILDING.md)) and deployed as three jars into
  `plugins/` together:
  - **LeetCore** (`leet-core-1.5.0.jar`) — shared infrastructure (storage, item/feature
    registry, generic GUI, Vault) + the seven standalone features + `/leeta`, `/back`,
    `/leet`.
  - **LeetSkills** (`leet-skills-1.5.0.jar`) — the Skills feature (`/skills`).
  - **LeetCrafting** (`leet-crafting-1.5.0.jar`) — the Cooking and Crafting features, plus
    the item resource pack.
- LeetSkills and LeetCrafting `softdepend` on LeetCore and disable themselves if it's
  absent. All three jars share one version, single-sourced in `build.gradle.kts`.
- **Shared service seam (CoreApi):** core exposes a narrow `CoreApi` (via the Bukkit
  ServicesManager); skills and crafting look it up to contribute features into core's
  shared feature registry. `/leeta` and `/leet` browse that registry, so they manage
  features from all three plugins uniformly.

**Changed**
- **Feature roles:** `AbstractFeature` is now a thin gated base; cost, cooldowns,
  messages, and protection-aware block-breaking are opt-in role interfaces
  (`CostedFeature`, `CooldownAware`, `MessagingFeature`, `BlockBreakerFeature`,
  `ToggleableFeature`).
- **Storage split:** each plugin owns its own data. Core keeps the `/leet` toggles and
  Back death locations (`plugins/LeetCore/data.db`); skills keeps skill levels/toggles
  (`plugins/LeetSkills/data.db`); crafting owns no DB. This fixes the earlier
  "split-brain" where skill state could collide with feature toggle rows.
- **Resource pack ownership:** the item resource pack server moved into LeetCrafting
  (`resource-pack.*` now lives in `plugins/LeetCrafting/config.yml`), constructed and
  stopped with that plugin.
- **Config-driven skill binding & layout:** each overlapping skill declares
  `binds-feature`/`toggleable` in `skills.yml`, and advanced GUI slots come from
  `tree.slots` in `skill-tree.yml` (a missing slot logs a warning rather than silently
  dropping the skill).
- **Crafting engine ownership:** `LeetItemRegistry` / `LeetRecipeRegistry` moved into
  LeetCrafting; the crafting/cooking item domains are preloaded order-independently.
- **Skills command & permissions:** `/skills` has no static command permission; access is
  gated at runtime by the single `leet.feat.skills` node (default-denied).

**Docs**
- `README`, `BUILDING`, `ARCHITECTURE`, `permissions`, `Admin`, and all feature docs
  rewritten for the three-plugin split; added the `tools/cooking/` tooling README.

**Notes**
- **Deploy all three jars together** (LeetCore first) — missing any jar removes its
  features.
- Skill levels moved to a new skills DB (`plugins/LeetSkills/data.db`, was
  `plugins/LeetHelper/data.db`). A manual, one-off migration retains player skill
  progress: run `python3 tools/migration/migrate_skills_1_4_1-1.5.0.py` from the
  server root (see the script's header). Other data is per-plugin and not carried over.

---

## 1.4.1 — Resource pack proxy fix

**Fixed**
- Embedded resource-pack HTTP server always starts, even when
  `resource-pack.url` is set. Previously, setting the URL skipped the embedded
  server entirely — so the URL pointed to a server that never started, and
  clients behind FRPC/proxies couldn't download the pack.

**Changed**
- Salt smelting recipe now yields **9** per water bucket (was 1).

---

## 1.4.0 — Custom foods, crafting engine, item give

**Added**
- **Cooking** grew from the original dishes into a 20-item food set with 21
  recipes, including salted foods: **Pretzel**, **Beef Jerky**, **Chicken Jerky**,
  **Jamón**, **Potato Chips**, **Dry Salmon**, **Dry Cod**, and **Chocolate Bar**
  / **Chocolate Piece** (bar shaped `CCC/SMS/CCC`, breaks into 8 pieces).
- **Crafting feature** (`crafting.yml`): a non-food custom-item domain, starting
  with **Salt** (smelt a water bucket). Its items join a shared custom-item
  registry that Cooking recipes reference.
- **Generic crafting engines** (`com.leet.helper.craft`): `LeetItem`,
  `LeetItemRegistry`, `LeetRecipeRegistry` — shared by Cooking and Crafting, with
  **SHAPED** / **SHAPELESS** / **SMELT** recipe support. `ResourcePackService`
  moved to a single Core-owned server that serves the additive `leet:` item pack.
- **`/leeta give <item-id> [amount] [player]`** — admin command to hand out any
  registered custom item (the supported way to obtain custom items, since a plain
  vanilla `/give` won't produce the tagged items).
- Recipe tweaks: Creamy Mushroom Soup yields ×3; Instant Noodle no longer needs a
  bowl.

**Changed**
- Custom-food `hunger`/`saturation` values and recipe `amount` yields were
  rebalanced from the server's **real vanilla food data** (values exported from
  the running server's `Foods` registry), using the vanilla cooking multiplier
  instead of arbitrary tuning. Crafted `amount` spreads large totals into several
  pieces.

**Removed**
- The experimental custom **crop** feature (soy crop + soy seed/oil/sauce) and its
  config. There are no custom plants; only items and food remain.

---

## 1.2.2 — Skill tree overhaul

The `/skills` tree was rebuilt into a full two-tier skill tree (Traveler at the
center, 8 ring skills, and a lower tier of advanced skills with attach prerequisites)
and is now driven by a separate topology config.

**Added**
- `skill-tree.yml` — new config that holds the tree layout (`ring` / `advanced`
  order) and each skill's prerequisite (`requires`). Definitions (`skills.yml`) and
  layout are now independent, so the tree can be reshaped without touching a
  skill's stats.
- **Advanced skills tier.** Nine advanced skills unlock once their ring skill hits
  level 10: Tree Feller, Auto Crop, Fall Nullify, Double Jump, **Breeder**,
  **Lucky Catch**, **Gardener**, **Swimmer**, and **Diver**.
  - **Breeder** (animalist 10) — chance of an extra baby when breeding, 10 levels (2.5 → 25%).
  - **Lucky Catch** (fisherman 10) — chance a catch is upgraded to a higher-tier item, 10 levels (1 → 10%).
  - **Gardener** (lumberjack 10) — felling a tree has a chance to drop an apple / golden apple (2.5 → 25% / 0.5 → 5%).
  - **Swimmer** (explorer 10) — move faster in water, 10 levels (5 → 50%).
  - **Diver** (swimmer 10) — hold your breath longer underwater, 10 levels (10 → 100%, i.e. 2×).
- Skill/feature dedup: when a skill matches a standalone feature (Smith, Tree
  Feller, Auto Crop, Fall Nullify, Double Jump) and the player holds that feature's
  permission, the skill shows as **already acquired** and only the feature fires —
  no double application.

**Changed**
- Stamina skill renamed to **Traveler**; the ring skills unlock once Traveler hits
  level 10.
- **Smith** durability chance lowered to 5% per level (50% at level 10, was 10–100%).
- **Fisherman** split into two skills: `fisherman` (bonus catch, 2.5 → 25%) and the
  new **Lucky Catch** (higher-tier catch), gated by fisherman level 10.
- **Lumberjack** bonus log is now **+1 per whole tree felled**, instead of per log;
  **Gardener** apple/golden-apple drops now happen **once per tree** rather than per log.
- **Auto Crop** now has 3 levels with its harvest radius equal to its level (1–3). If
  the `auto_crop` feature is permissioned, the skill counts as level 3.
- A skill that reached max level no longer shows the Level Up button.
- Advanced slots reorganized: **Swimmer** at x7y4, **Diver** at x8y4, **Double Jump**
  moved to x7y5, **Fall Nullify** moved to x8y5 (prerequisites unchanged).

**Fixed**
- **Miner** double-drop now drops the block's **natural drop** (e.g. raw copper from
  a copper ore) instead of acting like Silk Touch and dropping the ore block itself.

**Docs**
- Skills feature reference and architecture docs rewritten for the two-config tree;
- added `skill-tree.yml` to the feature layout in the README and build docs.

---

*Older releases were not tracked in this file. If you need them, see the git history.*
