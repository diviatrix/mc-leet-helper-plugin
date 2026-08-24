# Feature: Vanity

**Owning plugin:** LeetVanity · Config file `plugins/LeetVanity/features/vanity.yml`.

**Feature id:** `vanity` · **Parent permission:** `leet.feat.vanity` (default `false`) · **Commands:** `/dance`, `/dance list`, `/dance <name>`, `/dance stop`.

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../../Admin.md)

Vanity is a **hub feature**: one feature id that groups several distinct, unrelated gameplay capabilities. Each capability is driven by its own section of `features/vanity.yml` and can be enabled/disabled independently, but all share the single permission/per-player-toggle/world gate owned by the `vanity` feature.

## Setup

1. Grant `leet.feat.vanity` and the capability permission needed by the player.
2. Confirm the hub with `/leeta info vanity`.
3. Edit `plugins/LeetVanity/features/vanity.yml` for doors, seats and capability permissions.
4. Edit `plugins/LeetVanity/config.yml` to change the `/dance` names.
5. Restart the server after configuration or permission-default changes.

**Permissions**

See [Feature permissions](../../ARCHITECTURE.md#feature-permissions) for the gating rules and restart caveat.

| Node | Default | Notes |
|---|---|---|
| `leet.feat.vanity` | `false` | Parent gate for every capability. Turning the feature off (`base.enabled`/`/leeta toggle vanity`, or player `/leet`) disables **every** capability at once. |
| `leet.vanity.connected` | `false` | Use connected openings and their redstone synchronization. |
| `leet.vanity.sit` | `false` | Sit on configured blocks. |
| `leet.vanity.dance` | `false` | Use `/dance`. |

## Capabilities

### Connected openings (`feature.connected`)

When either half of a **double door** is opened or closed, the other half is moved to match — both swing together, whether the trigger is a player right-clicking one half or a redstone signal. (Trapdoors and fence gates are deliberately not part of this capability: they don't form the hinged pair this mechanic targets, and vanilla already links powered gates.)

**Behavior**
1. A player right-clicks one door half.
2. The clicked half opens/closes normally.
3. The partner half is set to match the clicked half's resulting state.

A redstone signal on either door (from a button, lever, pressure plate, or wire) flips its OPEN state without a player click; the plugin reacts to that power change and mirrors it onto the partner, so wire-driven pairs move together too.

**How it's controlled**

| Key | Effect |
|---|---|
| `feature.connected.enabled` | `true`/`false` toggles just this capability (default `true`). |
| `feature.connected.openable-types` | The list of `Material` names that participate as either half of a connected pair. A door only syncs when it is on this list, so door types can be excluded selectively. |

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
| `feature.connected.permission` | permission | `leet.vanity.connected` | Permission for connected openings |
| `feature.connected.default-permission` | `true`, `false`, or `op` | `false` | Default state for the capability permission |
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

| Key | Effect |
|---|---|
| `feature.sit.enabled` | `true`/`false` toggles just this capability (default `true`). |
| `feature.sit.seat-blocks` | The list of `Material` names you can sit on. The default is every **stair** and **slab** material (each facing variant's base material). Add other blocks (fences, walls, logs) freely; blocks not on the list are ignored. |
| `feature.sit.seat-height` | A per-block-lift (in blocks) added on top of the seating surface. `0` uses the default height; use positive/negative small values if a particular seat looks too high or sinks the player. |

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
| `feature.sit.permission` | permission | `leet.vanity.sit` | Permission for sitting |
| `feature.sit.default-permission` | `true`, `false`, or `op` | `false` | Default state for the capability permission |
| `feature.sit.seat-blocks` | list of Material names | all stairs & slabs | Materials the player can sit on |
| `feature.sit.seat-height` | double | `0` | Extra lift above the seat surface (blocks) |

> **Note:** any entry in `seat-blocks` that is not a valid Bukkit `Material` name is skipped with an `Invalid material in sit seat-blocks:` warning at load and has no effect. Use exact enum names (e.g. `OAK_STAIRS`, `STONE_SLAB`).

> **Limitation:** sitting triggers the whole `vanity` gate (permission + player toggle + world). If the block's type is shared with an interactive block (crafting table, chest) it will not be in the seat list — seating only applies to blocks you choose.

**Cooldown / cost:** none.

---

### Dancing (`/dance`)

Players with the Vanity permission can use `/dance` to start a cosmetic animation. Use `/dance list` to show available dances and `/dance stop` to stop the current animation.

| Command | Description |
|---|---|
| `/dance` or `/dance list` | Lists available dances |
| `/dance groove` | Starts the groove animation |
| `/dance bounce` | Starts the bounce animation |
| `/dance spin` | Starts the spin animation |
| `/dance stop` | Stops the active animation |

The default dance names are configured in `LeetVanity/config.yml` under `dances`. Existing installations with no `dances` key use `groove`, `bounce`, and `spin` automatically. The command uses the shared `leet.feat.vanity` feature gate, including the base permission, player toggle, enabled state, and world list.

The dance capability also requires `leet.vanity.dance`. Its config is under `feature.dance` in `features/vanity.yml`:

```yaml
feature:
  dance:
    enabled: true
    permission: leet.vanity.dance
    default-permission: false
```

**Cooldown / cost:** none.

## Future capabilities

New, unrelated features are added as sibling sections under `feature:` (for example `feature.some-other-thing:`) and handled in `VanityFeature.loadFeatureConfig`. They share the `leet.feat.vanity` gate and the single `/leet` toggle; give them their own `enabled` sub-switch so admins can tune them independently.
