# Resource Pack Distribution

How LeetCrafting ships its **item-texture** resource pack to joining players. This is the canonical operational guide; the [Crafting](features/crafting/crafting.md) feature doc links it from its "Behavior" section.

> **Owning plugin:** LeetCrafting · Config: `plugins/LeetCrafting/config.yml` under the `resource-pack:` block.

## What it does

LeetCrafting builds a tiny, **additive** resource pack from `resource_pack/index` (every file lives under `assets/leet/` — nothing in `assets/minecraft/` is overridden) and pushes it to each joining client during the configuration phase, before the player joins the world. The pack provides the `leet:item/<id>` item model for every custom dish/condiment, so the base material keeps its vanilla look while the icon changes.

The pack is **optional** by default — declining it leaves everyone else's textures intact and only affects the declining player's view of custom items.

## Config

```yaml
resource-pack:
  enabled: true    # master switch for distributing the icons
  port: 8043       # embedded HTTP server port (used when url is empty)
  url: ""          # optional: external URL hosting the same pack
  require: false   # true = mandatory; false = client can decline
```

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | bool | `true` | Master switch. When `false`, the embedded server does not start and no pack is offered. |
| `port` | int | `8043` | TCP port the embedded HTTP server binds to. Must be reachable by the client (directly, or through your proxy). |
| `url` | string | `""` | Optional override. When set, the client downloads from this URL. The path component **must end with `/craft-pack.zip`** — see [Path routing](#path-routing-the-craftpackzip-footgun) below. When empty, the client is given the embedded server's URL (derived from the server's `server-ip`). |
| `require` | bool | `false` | `true` makes the pack mandatory (client cannot join without it). `false` lets the player decline. |

## How it's served

LeetCrafting **always** starts the embedded HTTP server when `resource-pack.enabled` is `true`, regardless of whether `resource-pack.url` is set. This is intentional: most deployments front the embedded server with a tunnel/proxy (FRPC, nginx, ngrok, ...) so the client reaches it via a public hostname. The embedded server binds to `server-ip:port` (or `0.0.0.0:port` when `server-ip` is empty) and serves a single endpoint.

The single served path is **`/craft-pack.zip`** — that's the file the in-process handler matches and returns, nothing else.

The client-facing URL the player is told to download from is decided like this:

1. If `resource-pack.url` is **set and non-blank**, the client uses that URL verbatim (typical for FRPC/proxy setups where `url` is your public hostname).
2. Otherwise, the client uses `http://<server-ip>:<port>/craft-pack.zip`, falling back to `localhost` when `server-ip` is empty.

### Path routing: the `/craft-pack.zip` footgun

The embedded server **only** serves `/craft-pack.zip`. If you set `resource-pack.url` to anything else — e.g. `http://selfed.top:8043/cooking-pack.zip` — the embedded server will return **404** for that path and the client will fail to download. The client never retries with a different path; the download times out and the player joins without the pack.

When you set `url`, the path must end with `/craft-pack.zip`. So the only knobs are:

- the **scheme/host/port** the client reaches (the tunnel/proxy address)
- **port** — keep the same `port` as your tunnel forwards to

The filename segment (`craft-pack.zip`) is fixed by the embedded server.

## Reverse-proxy / FRPC setup

Typical when the Minecraft server is on a private network and you expose port 8043 to a public hostname.

```yaml
# plugins/LeetCrafting/config.yml
resource-pack:
  enabled: true
  port: 8043                              # embedded server port (local)
  url: "http://your-public-host:8043/craft-pack.zip"   # what the client downloads
  require: false
```

- Configure your tunnel/proxy to **forward TCP port 8043 → 127.0.0.1:8043** on the server host.
- Test from a browser: `http://your-public-host:8043/craft-pack.zip` must return a zip file (you can save it and inspect with `unzip -l` — you should see `assets/leet/...` files).
- The `url` value above **must** end in `/craft-pack.zip` — the embedded server serves no other path.

## Verifying a successful download

When a player joins, look for these log lines under the `[LeetCrafting]` prefix:

| Log line | Meaning |
|---|---|
| `Serving item icons from <url>` | Plugin started, the client will be offered this URL. |
| `[RP] Sent resource pack, waiting for callback...` | The pack was offered to the joining player during configuration. |
| `[RP-HTTP] GET /craft-pack.zip -> 200 OK (<bytes>b)` | The embedded server returned the pack (this is what you want to see). |
| `[RP-HTTP] <other-path> -> 404` | A client requested a path the server doesn't serve. **This is the `/craft-pack.zip` footgun** — the `url` you set doesn't match the embedded server's path. |
| `[RP] Callback fired: <status> (intermediate=true)` | An intermediate status came back from the client (e.g. `ACCEPTED` when the URL is reachable, `DOWNLOADED` when the bytes are received). |
| `[RP] Callback fired: <status> (intermediate=false)` | The pack was applied (or rejected). This is the line that completes the configuration handshake. |
| `[RP] Callback timed out after 10s, proceeding anyway.` | The client never sent a **final** callback — see [Troubleshooting](#troubleshooting). |
| `[RP] completeReconfiguration() called for <uuid>` | The configuration phase was completed for this player. |

A **healthy** sequence for one joining player is:

```
[RP] Sent resource pack, waiting for callback...
[RP-HTTP] GET /craft-pack.zip -> 200 OK (<bytes>b)
[RP] Callback fired: ACCEPTED (intermediate=true)
[RP] Callback fired: SUCCESSFULLY_LOADED (intermediate=false)
[RP] completeReconfiguration() called for <uuid>
```

The exact intermediate/final `status` enum names depend on the Paper/Adventure version, but the `(intermediate=...)` flag is the source of truth: `true` = transitional, `false` = final.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `[RP-HTTP] <something-other-than-craft-pack.zip> -> 404` | The `resource-pack.url` path doesn't match the embedded server's `/craft-pack.zip`. Change `url` so its path is exactly `/craft-pack.zip` (the scheme/host/port can be whatever your tunnel exposes). |
| `Callback timed out after 10s, proceeding anyway` and no `[RP-HTTP]` line | The client can't reach the URL at all — wrong host/port, FRPC not forwarding port 8043, firewall blocking the port, or `server-ip` empty in `server.properties` so the embedded URL is `localhost` and remote clients can't reach it. Hit the same URL with `curl` from outside the server to isolate. |
| `Callback timed out after 10s` but `[RP-HTTP] -> 200 OK` is present | The client downloaded the pack but never sent the final callback. Causes: SHA1 mismatch (server-side hash doesn't match what the client computed — only happens if the bytes changed between build and serve), or the client declined (`require: false`). Check for `[RP] Callback fired: FAILED_RELOAD` or `DECLINED`. |
| `[LeetCrafting] No resource-pack URL available. Set resource-pack.url in config.yml.` | `url` was empty AND `server-ip` is blank in `server.properties`, so the embedded server fell back to `localhost`. Remote clients can't reach `localhost`. Set `url` to your public/tunnel address (path = `/craft-pack.zip`) or set `server-ip`. |
| `[LeetCrafting] Could not start resource-pack server on port <port>: <message>` | The embedded server failed to bind — port in use, no permission, or `server-ip` set to an address this host can't bind. Free the port, change `port`, or unset `server-ip`. |
| `server-ip is empty — resource pack URL uses localhost.` warning at startup | Same root cause as the "No resource-pack URL available" line above — see that row. |
| Players see no custom icons even though the server logged `200 OK` | The pack was delivered but the client's `Require Resource Pack` prompt was declined (`require: false`); the player joined without applying. Either set `require: true`, or have the client accept the prompt. |
| Texture icons look wrong / fallback to vanilla | The client cached an older SHA1 — usually resolves on next login after a server restart. Forced by reloading the data pack or by the client clearing its `resourcepacks/` running cache. |

## Caveats

- The pack is regenerated **in memory on every server start** from the files listed in `resource_pack/index` inside the LeetCrafting jar. There's no on-disk reload of the pack contents — restart the server to pick up changes to `resource_pack/index` or its asset files.
- A copy is also written to `plugins/LeetCrafting/resource-pack/craft.zip` for debugging — you can `unzip -l` it to see what's being served.
- The download is initiated in the **configuration phase**; the player cannot connect to the world until the handshake completes or the 10-second timeout fires. If your tunnel is slow or your pack grows, the timeout can become a bottleneck. There is no way to extend it from the config today; raise an issue if you need it.
- Only one endpoint is served (`/craft-pack.zip`). Any other request gets a `404` and a plain-text `not found` body. Don't expose port 8043 publicly beyond a tightly-scoped tunnel/proxy.