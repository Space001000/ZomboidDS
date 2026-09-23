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
| `containers`     | Lua      | `containers[]`: your bags and everything within reach, see below |
| `here`           | Lua      | "Here", while the app watches (`watch_here`): `watching`; then either `menuId` + `options` (the world menu for where the player stands, same shape as `item_menu`'s) or `unavailable` (the reason, e.g. paused). `{ "watching": false }` otherwise. |
| `time`           | Lua      | `speed`: the game's speed button, 0 pause, 1 play, 2 fast forward (×5), 3 faster (×20), 4 wait (×40); `canChange` (false in multiplayer); `gameMenuOpen` (true while the game's pause menu is open: `set_speed` is refused then, as the game's own buttons are) |
| `command_result` | Lua      | `id`, `ok`, `error` (optional), `data` (optional, command-specific). Event, never replayed. |
| `show`           | Lua      | `panel` (`inventory`), `container` (optional container id). The player asked the game for that panel, e.g. pressed the controller's Loot/Inventory button: the app shows it. Event, never replayed. |

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

### Containers

The containers the player can use right now, exactly as the game's own inventory window (your
inventory, bags, key rings) and loot window (everything within reach) list them, so the game's
rules apply: walls, safehouses, locks, corpses, vehicles, bags on the floor, containers from mods.

```json
{ "containers": [
  { "id": "c1", "kind": "inventory", "name": "Inventory", "icon": "Icon_InventoryBasic", "weight": 3.0, "capacity": 12 },
  { "id": "c2", "kind": "bag", "name": "School Bag", "icon": "Item_Schoolbag", "weight": 1.0, "capacity": 13, "items": [ ] },
  { "id": "c3", "kind": "nearby", "name": "Shelves", "icon": "Container_Shelf", "weight": 1.1, "capacity": 50, "items": [ ] },
  { "id": "c4", "kind": "nearby", "name": "Crate", "icon": "lock", "weight": 5, "capacity": 50, "locked": true },
  { "id": "c5", "kind": "floor", "name": "Floor", "icon": "Container_Floor", "weight": 2, "capacity": 50, "items": [ ] } ] }
```

- `kind`: `inventory` (the main inventory: its items are in the `inventory` message, it's listed here
  as a place to move things to), `bag`, `nearby`, `floor`.
- `items` has the same shape as in `inventory`. Omitted for the main inventory and for `locked`
  containers (they can't be looked into).
- `id` stays the same while the container exists; commands refer to containers by it.
- `icon` is a texture name for the icon endpoint, like item icons.

### Capabilities

`session.capabilities` tells the app what the running adapter supports, so the app can hide UI
the game side can't back. v1 values: `player`, `inventory`, `vehicle`, `cmd.equip`, `cmd.unequip`,
`cmd.drop`, `cmd.wear`, `item_menu`, `containers`, `transfer`, `time`, `world_menu`, `here`.

## Client → server

```json
{ "v": 1, "type": "command", "id": "c-17", "name": "equip", "args": { "itemId": 10234, "slot": "primary" } }
```

The game answers every command with a `command_result` carrying the same `id`. Commands are
executed on the game thread on the next tick (also while the game is paused), usually as timed actions, so `ok: true` means
"queued", not "finished". The effect shows up in the next state update.

| name      | args |
|-----------|------|
| `equip`   | `itemId`, `slot`: `primary` \| `secondary` \| `both` (from the `equip.<slot>` actions) |
| `wear`    | `itemId` |
| `unequip` | `itemId` |
| `drop`    | `itemId` |
| `item_menu` | `itemId`. Result `data`: the game's own context menu for the item, see below |
| `watch_here` | `on` (default true): the app shows "Here". Lasts 10 s, so the app repeats it every few seconds while it's shown; `on: false` stops it. |
| `world_menu` | none. Result `data`: the game's world menu for where the player stands ("Here"), same shape as `item_menu` |
| `menu_select` | `menuId`, `optionId`: runs that option of the menu, like clicking it in the game |
| `transfer` | `itemId`, `to` (container id): moves the item there from wherever it is (inventory, bag, or a container within reach) |
| `transfer_all` | `from`, `to` (container ids): moves everything, with the filters of the game's Take All / Transfer All buttons |
| `set_speed` | `speed` (0–4, as in `time`): presses the game's own speed button. Refused in multiplayer, like in the game. Works while paused. |

### Moving items

`transfer` and `transfer_all` use the game's own transfer code (what its Take All / Transfer All
buttons do): timed actions with animation and the game's capacity checks; the character walks to a
container first if needed; moving to the floor is a normal drop. They fail with a reason when the
destination is out of reach or locked, the item can't go there, or it's already there.
`transfer_all` skips what the game's buttons skip: from your inventory equipped items, key rings,
hotbar items and favourites; from elsewhere items you marked unwanted and heavy items (corpses,
generators).

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

- Works for items you carry and for items in containers within reach (not locked ones). For the
  latter it's the menu the game's loot window shows: Grab, plus options like Read or Eat that take
  the item first. An option only runs while the item is still within reach.
- Options with `children` are submenus; only options without children can be selected.
- `enabled: false` options are shown greyed out, with the game's reason in `tooltip` if it gives one.
- Only the latest menu is valid, and only for one `menu_select` within a minute. Otherwise the result
  is `ok: false` and the app should request a fresh menu.
- No menu while the game is paused (`ok: false`, "The game is paused"), same as in the game.
- Options that open a window (renaming, crafting, maps) open it on the game's screen.

`world_menu` is what the controller's interact button opens: the game's world right-click menu for
the objects on the player's tile and the three tiles they face (not through walls), including options
other mods add. None while paused or in a vehicle. An option only runs while the player still stands
and faces the same way as when the menu was built; otherwise `ok: false`.

While watched, the mod sends `here` by itself: rebuilt when the player steps onto another tile or
turns, when their action finishes, shortly after a `menu_select`, and every few seconds, but never
while the game's own context menu is open on the game's screen (the game has one per player, so
building ours would close it). A rebuild with the same options keeps the same `menuId` and isn't re-sent.

A command the bridge can't parse is answered directly by the bridge with `ok: false`.

## Rules for evolving the protocol

- Adding optional fields or new message types: fine, stays v1. Clients ignore unknown fields and types.
- Renaming/removing fields or changing their meaning: bump `v` and handle both on the client
  (the app maps wire DTOs to its domain model in one place, `ProtocolV1`).
