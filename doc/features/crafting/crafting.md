# Feature: Crafting

**Owning plugin:** LeetCrafting · Config files `plugins/LeetCrafting/features/crafting.yml` and `plugins/LeetCrafting/config.yml`.

**Feature id:** `crafting` · **Permission:** none; this is a server-wide feature when enabled. Use `/leeta toggle crafting` or edit `base.enabled`.

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../../Admin.md)

Adds **custom crafting items** — non-food condiments (e.g. Salt) and **food dishes** (Croissant, Ramen, Borsh, Jerky, Chocolate, …). All of it is one server-level feature: when `base.enabled` is `true`, every recipe is available to **all** players — no skill, no permission, no per-player toggle. Dish items are placed on an already-edible base material and reapply custom hunger/saturation when eaten, so the combined dishes restore more than their raw parts.

**Command:** `/leeta toggle crafting` enables or disables all custom recipes. There is no player command and no crafting permission.

## Setup

1. Ensure LeetCrafting and LeetCore are installed.
2. Confirm the feature with `/leeta info crafting`.
3. Edit `plugins/LeetCrafting/features/crafting.yml` to change items or recipes.
4. Run `/leeta reload craft` or restart the server.
5. Use the recipe book or crafting table in game.

**How it's controlled**

| Control | Effect |
|---|---|
| `base.enabled` | Enabled/disabled at the **server** level. Turning it on gives everyone every recipe; there is no per-recipe or per-player unlock. |
| Permission | None required. There is **no** `/leet` subcommand for crafting — toggle it with `/leeta toggle crafting` or by editing `base.enabled` in `crafting.yml` (restart required). |
| `base.worlds` | If set, restricts the feature to those worlds. |

**Behavior**

| Behavior | Detail |
|---|---|
| Recipe registration | Recipes are registered as vanilla **crafting** recipes, so they show up in the recipe book like any other recipe. The engine supports **SHAPELESS**, **SHAPED** (3x3) and **SMELT** (furnace) recipes. |
| Gating | While the feature is enabled the recipe crafts for anyone; when it is disabled (or a world is excluded), the recipe simply shows **no result** in the crafting matrix — it is uncraftable. |
| Eating | Dish items carry a plugin tag that the feature reads on eat: vanilla consumption is cancelled and the configured `hunger`/`saturation` are applied instead (base materials are chosen to be edible so the item can be eaten at all). |
| Icons | Each item shows a **custom icon** on the client from the resource pack owned by LeetCrafting — see [Resource Pack](#resource-pack). |

## Recipes & items

All custom items live in `feature.items`; `feature.recipes` defines how to craft them. `amount` sets how many pieces each craft produces (a batch's total food value is spread across the pieces). Some dishes require **Salt**, a non-food custom item also defined here.

### Non-food items

| Item | Screenshot base | Recipe | Yields | Notes |
|---|---|---|---|---|
| Salt | SUGAR | Smelt `WATER_BUCKET` (SMELT) | 9 | Smelting a water bucket yields salt; Minecraft returns the empty bucket |

### Dishes (food)

| Item | Base | Recipe | Yields | Hunger / Sat |
|---|---|---|---|---|
| Dough | WHEAT | WHEAT ×2 + EGG | 1 | — (ingredient) |
| Bread * | BREAD (vanilla) | DOUGH | 1 | vanilla |
| Croissant | BREAD | `DDD/SMS/...` (D=dough, S=sugar, M=milk) | 6 | 2 / 2 |
| Borsh | BREAD | `BRB/PPP/CWA` (B=beef, R=beetroot, P=potato, C=carrot, W=bowl, A=allium) | 6 | 6 / 9 |
| Pelmeni | BREAD | `DDD/BPB/AWA` (D=dough, B=beef, P=pork, A=allium, W=bowl) | 8 | 3 / 4 |
| Instant Noodle | BREAD | `DED/.../...` (D=dough, E=egg) | 3 | 2 / 1 |
| Ramen | BREAD | `IEI/PKC/.W.` (I=instant noodle, E=egg, P=pork, K=dried kelp, C=raw chicken, W=bowl) | 8 | 4 / 6 |
| Banh Mi | BREAD | `BCA/.../...` (B=bread, C=cooked chicken, A=allium) | 6 | 4 / 6 |
| Milk Porridge | BREAD | `MWS/.B./...` (M=milk, W=wheat, S=sugar, B=bowl) | 4 | 4 / 6 |
| Creamy Mushroom Soup | BREAD | `BRK/.W./...` (B=brown mushroom, R=red mushroom, K=milk, W=bowl) | 4 | 4 / 6 |
| Chicken Skewers | COOKED_CHICKEN | `CCS/.../...` (C=cooked chicken, S=stick) | 4 | 4 / 4 |
| Charlotte | BREAD | `DAD/SES/...` (D=dough, A=apple, S=sugar, E=egg) | 2 | 6 / 6 |
| Pretzel | BREAD | DOUGH ×2 + SALT | 2 | 3 / 3 |
| Beef Jerky | COOKED_BEEF | RAW BEEF ×2 + SALT | 3 | 5 / 5 |
| Chicken Jerky | COOKED_CHICKEN | RAW CHICKEN ×2 + SALT | 3 | 4 / 4 |
| Jamón | BREAD | `PPP/SSS/PPP` (P=raw pork, S=salt) | 8 | 4 / 6 |
| Potato Chips | BREAD | POTATO ×3 + SALT | 4 | 3 / 2 |
| Dry Salmon | COOKED_SALMON | RAW SALMON ×3 + SALT | 4 | 5 / 2 |
| Dry Cod | COOKED_COD | RAW COD ×3 + SALT | 4 | 4 / 2 |
| Chocolate Bar | BREAD | `CCC/SMS/CCC` (C=cocoa, S=sugar, M=milk) | 1 | 16 / 16 |
| Chocolate Piece | BREAD | CHOCOLATE BAR (break it) | 8 | 2 / 2 |

\* The Dough→BREAD recipe is a **second** (advanced) way to make normal vanilla bread, in addition to vanilla's wheat grid.

## Config layout

```yaml
base:
  enabled: true                         # server-level switch: ON = recipes for everyone
  worlds: []                            # optional world whitelist
  cooldown: 0
  message-type: ACTION_BAR
  # No `permission`/`default-permission`: crafting needs no permission node.

feature:
  items:            # custom items the recipes produce / consume
    salt:
      material: SUGAR
      name: "Salt"
      lore: [ "White crystalline seasoning." ]
    dough:
      material: WHEAT
      name: "Dough"
      lore: [ "Flour and egg, mixed and ready." ]
    croissant:
      material: BREAD
      name: "Croissant"
      hunger: 2      # only dish items set hunger/saturation (food)
      saturation: 2
      lore: [ "Flaky, buttery bakery fresh." ]
  recipes:
    salt:             # SMELT (furnace) recipes use a single `ingredient`
      type: SMELT
      ingredient: WATER_BUCKET
      result: salt
      amount: 9
      experience: 0.35
    dough:
      type: SHAPELESS
      ingredients:
        - WHEAT
        - WHEAT
        - EGG
      result: dough
      amount: 1
    bread:
      type: SHAPELESS
      ingredients: [ dough ]
      result: material:BREAD        # ... or `material:<MATERIAL>` for a vanilla output
      amount: 1
    croissant:        # SHAPED: 3 rows of exactly 3 chars + a letter->ingredient map
      type: SHAPED
      shape: [ "DDD", "SMS", "..." ]
      ingredients:
        D: dough
        S: SUGAR
        M: MILK_BUCKET
      result: croissant
      amount: 6
```

A recipe ingredient is either a vanilla `Material` name (matched by material) or a **custom item id** (matched exactly by tag + display name, so e.g. `dough` is distinct from `WHEAT`, and `salt` is distinct from `SUGAR`). Invalid material/item names are skipped with a warning. `result` is a custom item id (built from `feature.items.<id>`) or `material:<MATERIAL>` for a vanilla output.

| Key | Type | Default | Description |
|---|---|---|---|
| `items.<id>.material` | Material | — | Base material the item is shown as (and eaten as). Dishes should be an edible material. |
| `items.<id>.name` | string | id | Display name. |
| `items.<id>.hunger` | int | `0` | Hunger points restored on eat (`> 0` marks the item as a dish; `0` = plain ingredient like Dough or Salt). |
| `items.<id>.saturation` | int | `0` | Saturation restored on eat (only meaningful for dishes). |
| `items.<id>.lore` | string list | — | Description lines (shown in grey). |
| `recipes.<id>.type` | `SHAPELESS`/`SHAPED`/`SMELT` | `SHAPELESS` | Crafting recipe kind. |
| `recipes.<id>.ingredients` | list / map | — | SHAPELESS: a flat list; SHAPED: a `shape` (3 rows of 3) plus a letter→ingredient map. |
| `recipes.<id>.ingredient` | id / material | — | SMELT: the single ingredient smelted in a furnace. |
| `recipes.<id>.result` | id or `material:<M>` | — | Custom item id to output, or a vanilla material for a vanilla output. |
| `recipes.<id>.amount` | int | `1` | How many of `result` the recipe yields. |
| `recipes.<id>.experience` | double | `0.35` | Furnace XP granted (SMELT only). |
| `recipes.<id>.cooking-time` | int | `200` | Furnace cook ticks (SMELT only). |

## Notes & limits

| Note | Detail |
|---|---|
| Item matching | Crafting items carry the same `ci` plugin tag, so they're matched exactly (e.g. `salt` is distinct from vanilla `SUGAR`, `dough` from `WHEAT`). |
| Disabling | Disabling `base.enabled` removes the recipes and hides the items in any recipe that references them. |
| Registration | Recipes registered when the feature enables are unregistered when it disables; `base.enabled` fully controls registration. |
| Permission | There is no permission to grant or revoke — disabling `base.enabled` is the only way to switch crafting off. |

**Cooldown:** none (by default).

---

## Resource Pack

LeetCrafting builds a tiny, **additive** resource pack from `resource_pack/index` (every file lives under `assets/leet/` — nothing in `assets/minecraft/` is overridden) and pushes it to each joining client during the configuration phase, before the player joins the world. The pack provides the `leet:item/<id>` item model for every custom dish/condiment, so the base material keeps its vanilla look while the icon changes.

The pack is **optional** by default — declining it leaves everyone else's textures intact and only affects the declining player's view of custom items.

### Config

```yaml
resource-pack:
  enabled: true
  port: 8043
  url: ""
  require: false
```

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | bool | `true` | Master switch. When `false`, the embedded server does not start and no pack is offered. |
| `port` | int | `8043` | TCP port the embedded HTTP server binds to. Must be reachable by the client. |
| `url` | string | `""` | Optional override. When set, the client downloads from this URL. The path **must end with `/craft-pack.zip`**. When empty, the client uses `http://<server-ip>:<port>/craft-pack.zip`. |
| `require` | bool | `false` | `true` makes the pack mandatory. `false` lets the player decline. |

### How it's served

LeetCrafting **always** starts the embedded HTTP server when `resource-pack.enabled` is `true`, regardless of whether `resource-pack.url` is set. The embedded server binds to `server-ip:port` (or `0.0.0.0:port` when `server-ip` is empty) and serves a single endpoint: **`/craft-pack.zip`**.

The client-facing URL is decided like this:

1. If `resource-pack.url` is **set and non-blank**, the client uses that URL verbatim.
2. Otherwise, the client uses `http://<server-ip>:<port>/craft-pack.zip`, falling back to `localhost` when `server-ip` is empty.

The embedded server **only** serves `/craft-pack.zip`. If you set `resource-pack.url` to a different path, the embedded server returns **404** and the client fails to download.

### Reverse-proxy / FRPC setup

```yaml
resource-pack:
  enabled: true
  port: 8043
  url: "http://your-public-host:8043/craft-pack.zip"
  require: false
```

- Configure your tunnel/proxy to **forward TCP port 8043 → 127.0.0.1:8043** on the server host.
- Test from a browser: `http://your-public-host:8043/craft-pack.zip` must return a zip file.
- The `url` value **must** end in `/craft-pack.zip`.

### Verifying a successful download

When a player joins, look for these log lines under the `[LeetCrafting]` prefix:

| Log line | Meaning |
|---|---|
| `Serving item icons from <url>` | Plugin started, the client will be offered this URL. |
| `[RP] Sent resource pack, waiting for callback...` | The pack was offered to the joining player. |
| `[RP-HTTP] GET /craft-pack.zip -> 200 OK (<bytes>b)` | The embedded server returned the pack. |
| `[RP-HTTP] <other-path> -> 404` | A client requested a path the server doesn't serve — the `url` doesn't match `/craft-pack.zip`. |
| `[RP] Callback fired: <status> (intermediate=true)` | An intermediate status from the client. |
| `[RP] Callback fired: <status> (intermediate=false)` | The pack was applied or rejected. |
| `[RP] Callback timed out after 10s, proceeding anyway.` | The client never sent a final callback. |

A **healthy** sequence:

```
[RP] Sent resource pack, waiting for callback...
[RP-HTTP] GET /craft-pack.zip -> 200 OK (<bytes>b)
[RP] Callback fired: ACCEPTED (intermediate=true)
[RP] Callback fired: SUCCESSFULLY_LOADED (intermediate=false)
[RP] completeReconfiguration() called for <uuid>
```

### Resource Pack Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `[RP-HTTP] <other-path> -> 404` | The `resource-pack.url` path doesn't match `/craft-pack.zip`. Change `url` so its path is exactly `/craft-pack.zip`. |
| `Callback timed out after 10s` and no `[RP-HTTP]` line | The client can't reach the URL — wrong host/port, FRPC not forwarding, firewall blocking, or `server-ip` empty in `server.properties`. |
| `Callback timed out after 10s` but `[RP-HTTP] -> 200 OK` is present | The client downloaded the pack but never sent the final callback. Check for `FAILED_RELOAD` or `DECLINED`. |
| `No resource-pack URL available` | `url` was empty AND `server-ip` is blank in `server.properties`. Set `url` or `server-ip`. |
| `Could not start resource-pack server on port <port>` | The embedded server failed to bind — port in use, no permission, or `server-ip` set to an address this host can't bind. |
| Players see no custom icons even though the server logged `200 OK` | The client declined the pack (`require: false`). Set `require: true` or have the client accept the prompt. |
| Texture icons look wrong / fallback to vanilla | The client cached an older SHA1 — usually resolves on next login after a server restart. |

### Resource Pack Caveats

- The pack is regenerated **in memory on every server start** from `resource_pack/index` inside the LeetCrafting jar. Restart the server to pick up changes.
- A copy is written to `plugins/LeetCrafting/resource-pack/craft.zip` for debugging.
- The download is initiated in the **configuration phase**; the player cannot connect until the handshake completes or the 10-second timeout fires.
- Only one endpoint is served (`/craft-pack.zip`). Any other request gets a `404`.
