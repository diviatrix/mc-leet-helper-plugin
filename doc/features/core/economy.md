# Feature: Economy

**Owning plugin:** LeetCore · **Commands:** `/bal`, `/balance`, `/money`, `/pay`, `/leeta eco ...`.

Economy is exposed through Vault. LeetCore provides a low-priority SQLite-backed economy when no higher-priority provider is installed.

## Requirements

Install Vault. A separate economy plugin such as EssentialsX takes precedence when it registers a higher-priority provider.

Without Vault and an economy provider, balances, payments and paid feature actions are unavailable.

## Player Commands

| Command | Permission | Description |
|---|---|---|
| `/bal` | `leet.economy.balance` | Show your balance |
| `/balance` | `leet.economy.balance` | Alias for `/bal` |
| `/money` | `leet.economy.balance` | Alias for `/bal` |
| `/pay <player> <amount>` | `leet.economy.pay` | Pay an online player |

`/pay` rejects self-payments, non-positive amounts, invalid amounts, insufficient funds and offline players.

## Administrator Commands

| Command | Permission | Description |
|---|---|---|
| `/leeta eco give <player> <amount>` | `leet.admin` | Deposit money |
| `/leeta eco take <player> <amount>` | `leet.admin` | Withdraw money |
| `/leeta eco set <player> <amount>` | `leet.admin` | Set a balance |
| `/leeta eco balance <player>` | `leet.admin` | Inspect an online player's balance |

Example:

```text
/leeta eco give Alex 100
/leeta eco take Alex 25
/leeta eco set Alex 500
/leeta eco balance Alex
```

## Feature Costs

Core feature files can define `feature.cost`. Interaction definitions and signs can also define a price. A positive cost is charged through Vault before the action completes. Paid Interaction signs and quests require an available economy provider.

## Storage

The built-in provider stores balances in `plugins/LeetCore/data.db`. Deleting this database deletes those balances and other Core player data.
