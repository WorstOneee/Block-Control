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

## Notes

- Data is stored in `plugins/SerpensBlock/blocks.yml` (UUIDs), saved on a background thread after each change.
- Fully event driven: no repeating tasks.
- Vanished players are never revealed: if the sender cannot see the target, the teleport command is passed through untouched.
- This list is separate from CarbonChat's own `/ignore`.

## Build

```
./mvnw package
```

The jar is written to `target/SerpensBlock-1.0.0.jar`. CarbonChat and Simple Voice Chat are optional (`softdepend`).
