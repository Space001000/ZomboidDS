# ZomboidDS Protocol v1

This is the contract between the game side (bridge + Lua mod) and the companion app. It does
**not** depend on the game version: B41/B42 differences are absorbed by the game-side adapters,
so the app only ever sees this format.

The files in [`fixtures/`](fixtures) are canonical examples. They are used by:
- the companion app's unit tests (parsing must succeed),
- the mock server (which serves them as fake game state).

Change a message → update its fixture → both sides' tests tell you what broke.

## Transport

One port (default `7786`), bound to `127.0.0.1` only.

| What            | Where                                  |
|-----------------|----------------------------------------|
| WebSocket       | `ws://127.0.0.1:7786/ws`               |
| Item icons      | `GET http://127.0.0.1:7786/icons/{icon}.png` |
| Health check    | `GET http://127.0.0.1:7786/health`     |

Icons are served with `Cache-Control: max-age=31536000, immutable`; the app caches them on disk.
Icon names match `[A-Za-z0-9_.-]+` (e.g. `Item_Axe`).

## Server → client

Every message is an envelope:

```json
{ "v": 1, "type": "<type>", "seq": 42, "data": { } }
```

`seq` increases with every publish. It's for debugging and ordering, not acknowledgements.

**State messages** (`session`, `player`, `inventory`, `vehicle`) always hold the *complete* current
value, never a diff. The bridge keeps the latest one of each and replays them to a client right
after `hello`, so a (re)connecting client is immediately up to date. If the bridge falls behind,
older state messages of the same type are dropped in favour of the newest.

| type             | sent by  | data |
|------------------|----------|------|
| `hello`          | bridge   | `protocol`, `bridge` (bridge version), `adapter` (Java adapter id). Always first. |
| `session`        | Lua      | `inGame`, `gameVersion`, `adapter` (Lua adapter id), `capabilities[]`. `{ "inGame": false }` while at the main menu. |
| `player`         | Lua      | `health` (0–100), `bleeding`, `stats` { `hunger`, `thirst`, `fatigue`, `endurance` } (0–1) |
| `inventory`      | Lua      | `weight` { `current`, `max` }, `items[]` (see below) |
| `vehicle`        | Lua      | `inVehicle`; when true also `name`, `speedKmh`, `engineRunning`, `fuel` (0–1, optional), `isDriver` |
| `command_result` | Lua      | `id`, `ok`, `error` (optional), `data` (optional, command-specific). Event, never replayed. |

Inventory item:

```json
{ "id": 10234, "type": "Base.Axe", "name": "Axe", "category": "Weapon",
  "icon": "Item_Axe", "weight": 3.0, "condition": 0.85, "equipped": "primary",
  "actions": ["unequip", "drop"] }
```

- `id` is the per-instance item id (`InventoryItem:getID()`), **not** the type. Two bandages have two ids.
- `condition` is 0–1, omitted for items without meaningful condition.
- `equipped` is one of `primary`, `secondary`, `both`, `worn`, or omitted.
- `actions` lists what the app may offer for this item, decided by the game-side adapter (the app
  doesn't guess from categories): `equip.primary`, `equip.secondary`, `equip.both`, `wear`,
  `unequip`, `drop`. Each maps to one command below. Unknown actions are ignored by the app.

### Capabilities

`session.capabilities` tells the app what the running adapter supports, so the app can hide UI
the game side can't back. v1 values: `player`, `inventory`, `vehicle`, `cmd.equip`, `cmd.unequip`,
`cmd.drop`, `cmd.wear`, `item_menu`.

## Client → server

```json
{ "v": 1, "type": "command", "id": "c-17", "name": "equip", "args": { "itemId": 10234, "slot": "primary" } }
```

The game answers every command with a `command_result` carrying the same `id`. Commands are
executed on the game thread on the next tick, usually as timed actions, so `ok: true` means
"queued", not "finished". The effect shows up in the next state update.

| name      | args |
|-----------|------|
| `equip`   | `itemId`, `slot`: `primary` \| `secondary` \| `both` (from the `equip.<slot>` actions) |
| `wear`    | `itemId` |
| `unequip` | `itemId` |
| `drop`    | `itemId` |
| `item_menu` | `itemId`. Result `data`: the game's own context menu for the item, see below |
| `menu_select` | `menuId`, `optionId`: runs that option of the menu, like clicking it in the game |

### The game's item menu

`item_menu` returns the game's right-click menu for an item, built by the game itself (so it
includes everything it offers: read, eat, bandage, craft, ... and options added by other mods):

```json
{ "menuId": "m7", "options": [
  { "id": "1", "name": "Read", "enabled": true },
  { "id": "2", "name": "Eat", "enabled": true, "children": [
    { "id": "2.1", "name": "All", "enabled": true },
    { "id": "2.2", "name": "Half", "enabled": true } ] },
  { "id": "3", "name": "Rip into sheets", "enabled": false, "tooltip": "Requires a knife" } ] }
```

- Options with `children` are submenus; only options without children can be selected.
- `enabled: false` options are shown greyed out, with the game's reason in `tooltip` if it gives one.
- Only the latest menu is valid, and only for one `menu_select` within a minute. Otherwise the result
  is `ok: false` and the app should request a fresh menu.
- No menu while the game is paused (`ok: false`, "The game is paused"), same as in the game.
- Options that open a window (renaming, crafting, maps) open it on the game's screen.

A command the bridge can't parse is answered directly by the bridge with `ok: false`.

## Rules for evolving the protocol

- Adding optional fields or new message types: fine, stays v1. Clients ignore unknown fields and types.
- Renaming/removing fields or changing their meaning: bump `v` and handle both on the client
  (the app maps wire DTOs to its domain model in one place, `ProtocolV1`).
