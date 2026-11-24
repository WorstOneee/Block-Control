# SerpensBlock

Player block list for Paper 1.21.11 that complements CarbonChat. A player who blocks someone stops seeing:

- **Public chat**: hooks `CarbonChatEvent` and removes the blocker from the recipients. Falls back to Paper's `AsyncChatEvent` when CarbonChat is not installed.
- **Private messages** (`/tell`, `/msg`, `/r` from CarbonChat): hooks `CarbonPrivateChatEvent`. Blocked in both directions.
- **Teleport requests**: intercepts the commands listed in `teleport.commands` (`/tpa`, `/tpahere`, ...) before the teleport plugin runs. Tested with ZwoTeleport 3.0.0 (`/tpa`, `/tpahere`, also as `/zwoteleport:tpa`); also works with EssentialsX, HuskHomes, CMI, and others. Blocked in both directions.
- **Voice chat** (Simple Voice Chat): drops every audio packet from the blocked player to the blocker (proximity, groups, spectator). With `voice.mutual: true`, the blocked player stops hearing the blocker too.

## Commands

| Command | Aliases | Description |
|---|---|---|
| `/block <player>` | `/bloquear` | Block a player (online, or one who has joined before) |
| `/unblock <player>` | `/desbloquear` | Unblock a player |
| `/blocklist` | `/bloqueados`, `/blocks` | List blocked players, with a click-to-unblock button |
| `/serpensblock reload` | | Reload `config.yml` |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `serpensblock.use` | everyone | Use the block commands |
| `serpensblock.exempt` | false | Staff: cannot be blocked, and blocks never apply to them |
| `serpensblock.admin` | op | `/serpensblock reload` |

## Storage

Set `storage.type` in `config.yml`:

- `yaml`: `plugins/SerpensBlock/blocks.yml`, for a single server.
- `mysql`: shared MySQL/MariaDB, for a network (Velocity + several Paper servers). Use the same `storage.mysql` settings on every server, and a block made on one server applies on all of them. It uses the MySQL driver bundled with Paper.

With MySQL, the tables are `<prefix>blocks (owner, target, created_at)` and `<prefix>meta`, which holds a revision counter. Every server keeps all lists in memory, so chat, voice and command checks never wait on the database. Changes are written on a background thread and bump the revision. Every `sync-seconds` (default 3), each server reads that one row off the main thread and reloads the lists only when the number changed. If MySQL is unreachable at startup, the plugin disables itself instead of running with empty lists.

On the first start with `mysql`, an existing `blocks.yml` is merged into the database and renamed to `blocks.yml.imported`.

## Notes

- Apart from the MySQL revision check, everything is event driven.
- Vanished players are never revealed: if the sender cannot see the target, the teleport command is passed through untouched.
- This list is separate from CarbonChat's own `/ignore`.

## Build

```
./mvnw package
```

The jar is written to `target/SerpensBlock-1.0.0.jar`. CarbonChat and Simple Voice Chat are optional (`softdepend`).
