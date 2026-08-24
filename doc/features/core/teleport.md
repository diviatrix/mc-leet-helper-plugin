# Feature: Teleport

**Owning plugin:** LeetCore · **Commands:** `/spawn`, `/setspawn`, `/home`, `/sethome`, `/warp <name>`.

Teleport commands cover one global spawn, one personal home per player, and named administrator warps.

## Permissions

| Command | Permission | Default |
|---|---|---|
| `/spawn` | `leet.spawn` | `true` |
| `/setspawn` | `leet.admin` | `op` |
| `/home` | `leet.home` | `true` |
| `/sethome` | `leet.sethome` | `true` |
| `/warp <name>` | `leet.warp` | `true` |
| `/leeta warp add/del` | `leet.admin` | `op` |

## Setup

Set the global spawn as an administrator:

```text
/setspawn
```

Create an administrator warp from the desired location:

```text
/leeta warp add market
```

Players use:

```text
/spawn
/warp market
```

Warp names are normalized to lowercase letters, numbers, `_` and `-`. Delete a warp with:

```text
/leeta warp del market
```

## Personal Homes

Players set and use their own home:

```text
/sethome
/home
```

Each player has one home. `/sethome` replaces the previous home. The saved value includes world, coordinates, yaw and pitch.

## Storage

Global spawn and administrator warps are stored in `plugins/LeetCore/config.yml` under `spawn.*` and `warps.*`. Personal homes are stored by player UUID in `plugins/LeetCore/data.db`.

## Reset and Reload

The global spawn and warps are part of `LeetCore/config.yml`; deleting that file resets them along with other Core global settings. No reload is required for command-created spawn/warp changes because the commands save them immediately. A restart is required after changing static permissions.
