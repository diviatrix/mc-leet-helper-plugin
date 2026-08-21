# Feature: Vanity

**Owning plugin:** LeetVanity · Config file `plugins/LeetVanity/features/vanity.yml`.

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../../Admin.md)

Vanity is a **hub feature**: one feature id that groups several distinct, unrelated gameplay capabilities. Each capability is driven by its own section of `features/vanity.yml` and can be enabled/disabled independently, but all share the single permission/per-player-toggle/world gate owned by the `vanity` feature.

**Permissions**
- Node: `leet.feat.vanity` · default `false`. See [Feature permissions](../../ARCHITECTURE.md#feature-permissions) for the gating rules and restart caveat.
- Turning the feature off (`base.enabled`/`/leeta toggle vanity`, or player `/leet`) disables **every** capability at once. Per-capability on/off switches live in each capability's config section.

## Capabilities

### Connected openings (`feature.connected`)

When either half of a **double door** is opened or closed, the other half is moved to match — both swing together, whether the trigger is a player right-clicking one half or a redstone signal. (Trapdoors and fence gates are deliberately not part of this capability: they don't form the hinged pair this mechanic targets, and vanilla already links powered gates.)

**Behavior**
1. A player right-clicks one door half.
2. The clicked half opens/closes normally.
3. The partner half is set to match the clicked half's resulting state.

A redstone signal on either door (from a button, lever, pressure plate, or wire) flips its OPEN state without a player click; the plugin reacts to that power change and mirrors it onto the partner, so wire-driven pairs move together too.

**How it's controlled**
- `feature.connected.enabled` — `true`/`false` toggles just this capability (default `true`).
- `feature.connected.openable-types` — the list of `Material` names that participate as either half of a connected pair. A door only syncs when it is on this list, so door types can be excluded selectively.

```yaml
base:
  enabled: true
  permission: leet.feat.vanity
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature:
  connected:
    enabled: true
    openable-types:
      - OAK_DOOR
      - SPRUCE_DOOR
      - # ... any door material names

messages: {}
```

| Key | Type | Default | Description |
|---|---|---|---|
| `feature.connected.enabled` | boolean | `true` | Enables the connected-openings capability |
| `feature.connected.openable-types` | list of Material names | all doors | Door materials treated as pairable openings |
| `base.permission` | permission | `leet.feat.vanity` | Gate for the whole hub feature |

> **Note:** any entry in `openable-types` that is not a valid Bukkit `Material` name is skipped with an `Invalid material in connected openable-types:` warning at load and has no effect. Use exact enum names (e.g. `OAK_DOOR`, `IRON_DOOR`).

**Cooldown / cost:** none.

---

### Sitting (`feature.sit`)

Right-click a block on the seat list to **sit on it**. Uses a hidden, invulnerable armor stand as the mount, so the player sits in place — no boat, armor, or entity visible. Sneak (`Shift`) to stand back up.

**Behavior**
1. A player right-clicks a block that is in `feature.sit.seat-blocks`.
2. A hidden marker armor stand spawns just above the block's top surface, and the player rides it (reads/sends as sitting).
3. The player faces the seat's front when the block is directional (stairs face forward) or keeps their own heading otherwise.
4. Sneaking dismounts normally; the hidden stand is removed on dismount.

**How it's controlled**
- `feature.sit.enabled` — `true`/`false` toggles just this capability (default `true`).
- `feature.sit.seat-blocks` — the list of `Material` names you can sit on. The default is every **stair** and **slab** material (each facing variant's base material). Add other blocks (fences, walls, logs) freely; blocks not on the list are ignored.
- `feature.sit.seat-height` — a per-block-lift (in blocks) added on top of the seating surface. `0` uses the default height; use positive/negative small values if a particular seat looks too high or sinks the player.

```yaml
feature:
  # Sitting: right-click a block to sit; sneak to get up.
  sit:
    enabled: true
    seat-height: 0
    seat-blocks:
      - OAK_STAIRS
      - OAK_SLAB
      - # ... all stair and slab material names
```

| Key | Type | Default | Description |
|---|---|---|---|
| `feature.sit.enabled` | boolean | `true` | Enables the sitting capability |
| `feature.sit.seat-blocks` | list of Material names | all stairs & slabs | Materials the player can sit on |
| `feature.sit.seat-height` | double | `0` | Extra lift above the seat surface (blocks) |

> **Note:** any entry in `seat-blocks` that is not a valid Bukkit `Material` name is skipped with an `Invalid material in sit seat-blocks:` warning at load and has no effect. Use exact enum names (e.g. `OAK_STAIRS`, `STONE_SLAB`).

> **Limitation:** sitting triggers the whole `vanity` gate (permission + player toggle + world). If the block's type is shared with an interactive block (crafting table, chest) it will not be in the seat list — seating only applies to blocks you choose.

**Cooldown / cost:** none.

## Future capabilities

New, unrelated features are added as sibling sections under `feature:` (for example `feature.some-other-thing:`) and handled in `VanityFeature.loadFeatureConfig`. They share the `leet.feat.vanity` gate and the single `/leet` toggle; give them their own `enabled` sub-switch so admins can tune them independently.
