# Changelog

All notable changes to **LeetHelper**. Entries are newest first.

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