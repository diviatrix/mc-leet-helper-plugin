# Architecture Rework (historical plan & record)

Status: **IMPLEMENTED**. This document records the post-review rework that split the
monolith into the current three-plugin bundle. The **current** architecture is
described in [ARCHITECTURE.md](ARCHITECTURE.md) — read that for how things work now;
this page is the plan and its completion record.

## Why the original single-plugin split was wrong

Before the rework the codebase was a single plugin whose files had been repackaged
into three jars while preserving its class structure. It was a **repackaging, not an
architecture** — proved by two runtime bugs: permission registration depended on
enable-order, and skill levels vs. skill toggles lived in two different SQLite DBs.

Root causes found by the reviews:

1. **`CoreApi` was a facade over concrete classes**, not a contract. Consumers
   imported concrete types directly.
2. **`AbstractFeature` was a ~9-responsibility god class** every feature compiled
   against (config, permissions, Vault cost, cooldowns, messages, world gating,
   event lifecycle, block-breaking).
3. **`FeatureManager` + core commands hard-coded cross-plugin feature ids** and
   reached into concrete classes.
4. **`ResourcePackService` was hosted in core** but only crafting had assets.
5. **Crafting's domain engine lived in core**, with two `CraftFeature`-like instances
   duplicating handlers and a hidden load-order contract.

## Agreed target architecture

- **LeetCore = infrastructure only** (storage, item/feature registry, GUI, Vault) plus
  the composition site for the seven standalone features — no fourth jar. Narrow role
  interfaces instead of concrete classes.
- **Two coexisting implementations** for the doubled mechanics (standalone features +
  skills), with config-driven binding (`binds-feature`/`toggleable` in `skills.yml`).
- **Each plugin owns its domain data** (skills → own DB; crafting → owns the item
  registry + resource pack).
- **Composable feature roles** replace the god class: thin `AbstractFeature` +
  opt-in `CostedFeature` / `CooldownAware` / `MessagingFeature` / `BlockBreakerFeature`
  / `ToggleableFeature`.

## Implementation status — all done, runtime-verified by the user

1. **Composable roles — done.** `AbstractFeature` is now a thin gated-feature base;
   cost/cooldown/messages/block-breaking moved to opt-in role interfaces.
   `TreeFellerUtil`/`AutoCropUtil` now take `BlockBreakerFeature`.
2. **Storage split-brain — fixed.** Skill levels AND skill toggles persist in the
   skills plugin's own DB; no writes into core's toggle namespace. `/leet` toggles and
   cooldowns legitimately stay in core's DB (core owns the `/leet` gating mechanic).
3. **Resource pack ownership — fixed.** `ResourcePackService` moved into the crafting
   plugin, constructed/started/stopped by `LeetCrafting`. Crafting has its own
   `config.yml` with `resource-pack.*`. Kept cooking + crafting as TWO visible feature
   ids (`/leet cook`, `/leeta toggle cooking|crafting`) so the command surface is
   preserved.
4. **Registry-driven commands — done.** `/leeta` and `/leet` **browse the shared
   registry**, so skills/cooking features registered by their owning plugins appear
   and toggleable uniformly; absent plugins degrade gracefully (presence-guarded).
5. **Config-driven skill<->feature binding — done.** Each skill declares
   `binds-feature` and (for overlap skills) `toggleable` in `skills.yml`; a
   `validateBindings()` load-time check warns if a bound core feature isn't registered.
   **Advanced GUI slots** are config-driven too, via `tree.slots` in `skill-tree.yml`
   (a missing slot logs a warning rather than silently dropping the skill).

**Deployment note:** skill levels/toggles live in the new skills DB on first run with
the current jars, so previously-earned levels won't be present until re-earned. Requires
deploying **all three jars** together (LeetCrafting, LeetSkills soft-depend on LeetCore),
with LeetCore loaded first.
