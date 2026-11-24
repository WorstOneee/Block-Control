# SerpensBlock

Player block list for Paper 1.21.11 that complements CarbonChat. A player who blocks someone stops seeing:

- **Public chat**: hooks `CarbonChatEvent` and removes the blocker from the recipients. Falls back to Paper's `AsyncChatEvent` when CarbonChat is not installed.
- **Private messages** (`/tell`, `/msg`, `/r` from CarbonChat): hooks `CarbonPrivateChatEvent`. Blocked in both directions.
- **Teleport requests**: intercepts the commands listed in `teleport.commands` (`/tpa`, `/tpahere`, ...) before the teleport plugin runs. Tested with ZwoTeleport 3.0.0 (`/tpa`, `/tpahere`, also as `/zwoteleport:tpa`); also works with EssentialsX, HuskHomes, CMI, and others. Blocked in both directions.
- **Voice chat** (Simple Voice Chat): drops every audio packet from the blocked player to the blocker (proximity, groups, spectator). With `voice.mutual: true`, the blocked player stops hearing the blocker too.

## Commands

| Command | Aliases | Description |
|---|---|---|
| `/block` | `/bloquear` | Open the panel |
| `/block <player>` | `/bloquear` | Block a player (online, or one who has joined before) |
| `/unblock <player>` | `/desbloquear` | Unblock a player |
| `/blocklist` | `/bloqueados`, `/blocks` | Open the panel (a text list when `panel.enabled: false`) |
| `/serpensblock reload` | | Reload `config.yml` |

## Player panel

`/block` with no name (or `/blocklist`) opens the panel:

- **Bloqueios** (6 rows): the header shows what a block covers on this server (chat, private messages, teleport, voice, each marked active or off) and a summary with the count. Below it are the heads of blocked players, 28 per page, with online status. The player who was just blocked comes first, with a glint. Clicking a head asks to confirm the unblock.
- **Bloquear jogador**: online players on this server who can be blocked (not yourself, not vanished, not `serpensblock.exempt`). Clicking one asks to confirm. Players on other servers or offline are blocked with `/block <name>`.
- **Confirmation** (3 rows): the head with confirm and cancel buttons.

The heads appear in a diagonal wave when a menu opens (`panel.reveal-ticks`, 0 turns it off). The wave task only runs for those few ticks. Buttons play sounds (`panel.sounds`). Clicks are rate limited and nothing can be taken out of the menus. Every text, title and fallback material is in `config.yml` under `panel`.

### Resource pack

`resourcepack/craftengine/blockcontrol/` is a CraftEngine pack in the same format as AuctionHouseSerp's:

- `items/gui/<icon>.json` + `models/item/gui/<icon>.json` + `textures/item/gui/<icon>.png`: 16 icons at 64x64, used through the `item_model` component (`blockcontrol:gui/<icon>`), with no `custom_model_data`. Seven come from the images in `tools/icones/` (help, cancel, confirm, chat, private message, teleport, shield); the rest are drawn by the generator in the same style.
- Seven icons have a light animation through `.png.mcmeta` (12 frames, 4 ticks each): a shine across the shield, a slow spin on the teleport galaxy, a 1 px bob on both chat bubbles, a soft pulse on the block sign, the voice waves, and the empty-list silhouette floating. The client animates them, so the server does no work for it.
- `font/gui.json` and `textures/font/gui/fundo_3.png` / `fundo_6.png`: light, vanilla-style menu backgrounds with a wine-red title band with gold trim, and thin wine lines around the header and navigation rows. They use the private font `blockcontrol:gui` (`U+E000`, `U+E001`, and spaces `U+F000` = -8 and `U+F001` = -165, which starts the title 4 px inside the band), so they cannot clash with other fonts. They stop above the "Inventário" label.

Install it like this:

1. Copy `resourcepack/craftengine/blockcontrol/` to `plugins/CraftEngine/resources/` on the server that builds the pack.
2. Run `/ce reload all`.
3. Once players have the pack, set `panel.resource-pack: true` and run `/serpensblock reload`.

Without the pack (`resource-pack: false`, the default), the panel uses the vanilla items from `panel.materials` and a plain title.

The background position is calculated, not yet checked in a client. If it looks shifted, adjust `ascent` in `font/gui.json` (vertical) or the space advances (horizontal). To change the art, edit `tools/gerar_assets.py` and run `python tools/gerar_assets.py [heads folder]` (Python 3 + Pillow with AVIF support). It also redraws the previews in `docs/previas/`; the optional folder holds real head PNGs used only in the previews. In game, every head always shows the player's own skin.

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

The jar is written to `target/BlockControl-1.0.0.jar`. CarbonChat and Simple Voice Chat are optional (`softdepend`).
