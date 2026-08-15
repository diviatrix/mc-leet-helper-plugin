# Feature: Fall Damage

> Common `base:`/`messages:` config layout and control model: [README](../README.md#common-feature-config-structure) · Permissions: [permissions.md](../permissions.md) · All features: [index](README.md)

Negates all fall damage for eligible players, as a standalone feature **independent of Double Jump**. Config file `features/_fall_damage.yml`.

**Behavior**
1. A player takes fall damage (`EntityDamageEvent`, cause `FALL`).
2. If the player passes the feature checks (enabled + `leet.feat.fall_damage` permission + personal `/leet` toggle + world), the fall damage is cancelled entirely.

There are no feature-specific config options — the feature is controlled by `base.enabled`, the `leet.feat.fall_damage` permission, and the personal `/leet fall` toggle.

```yaml
base:
  enabled: true
  permission: leet.feat.fall_damage
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature: {}

messages: {}
```

**Cooldown:** none.
