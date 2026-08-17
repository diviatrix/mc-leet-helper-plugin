# Changelog

All notable changes to **LeetHelper**. Entries are newest first.

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