# Installation and Upgrades

## Install

LeetHelper requires Paper `26.2+` and Java `25+`. Install these matching jars:

```text
LeetCore
LeetSkills
LeetCrafting
LeetVanity
LeetInteraction
```

Copy them to `plugins/`, then start the server. LeetCore must be present because the other four plugins consume its service API. They disable themselves if it is missing.

## Optional Economy

Install Vault and an economy provider for `/bal`, `/pay`, paid signs or feature costs. LeetCore registers a low-priority SQLite economy provider when Vault is available. A higher-priority provider normally takes precedence.

## Generated Files

The first start creates:

```text
plugins/LeetCore/
  config.yml
  data.db
  features/*.yml
  rules/*.yml
plugins/LeetSkills/
  data.db
  features/skills.yml
  features/skill-tree.yml
plugins/LeetCrafting/
  config.yml
  features/crafting.yml
plugins/LeetVanity/
  config.yml
  features/vanity.yml
plugins/LeetInteraction/
  config.yml
  data.db
  features/interaction.yml
  definitions/*.yml
```

## Upgrade

1. Back up all `data.db` files and YAML configuration.
2. Stop the server.
3. Replace all five jars with matching versions.
4. Start the server and inspect the logs for enable errors.
5. Review newly merged configuration keys.

Missing keys are merged from bundled defaults. Existing values are preserved. Configuration changes normally take effect after restart.

## Reset Configuration

Deleting a generated YAML file allows its plugin to create a fresh default on the next start. This does not reset SQLite player data. Never delete a database unless you intentionally want to lose its contents.

## Migration

The historical skills migration tool is documented in [tools/migration/README.md](../tools/migration/README.md). Back up both databases before running it.
