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
Icon names match `[A-Za-z0-9_.-]+` (e.g. `Item_Axe`), or are a path of such names under the game's
`media/ui` or `media/textures` when several images share a file name (e.g. `Moodles/128/Mood_Sad`;
no `..`). Missing icons are `404` with `Cache-Control: no-store`.

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
| `health`         | Lua      | `parts[]`: the body parts the game's health panel lists (injured, bandaged, stitched, splinted, in pain), each `{ id, name, lines[] }` with `lines` = `{ text, tone }`: the game's own lines ("Scratched (Severe)", "Bandaged", ...) as the player's First Aid level lets them see, `tone` from the game's colour: `bad`, `good` (treated) or `warn` (dirty bandage, infection, stiffness). `female`: which body silhouette to draw (the game's `bps_male_*` / `bps_female_*` images). |
| `moodles`        | Lua      | `moodles[]`: what the game's moodle column shows (every moodle above level 0), most urgent first, each `{ id, name, description, level, tone, color, icon }`: `id` the game's MoodleType (`HUNGRY`, `BLEEDING`, ...), `name` and `description` the game's own texts for the level (its hover tooltip), `level` 1–4, `tone` `good`/`bad`/`neutral`, `color` `[r, g, b]` (0–1) the game's background colour for it (grey towards the player's good/bad highlight colour by level), `icon` an icon path (e.g. `Moodles/128/Status_Hunger`). `background` and `border`: the game's round moodle background (to tint with `color`) and its outline. |
| `here`           | Lua      | "Here", while the app watches (`watch_here`): `watching`; then either `menuId` + `options` (the world menu for where the player stands, same shape as `item_menu`'s) or `unavailable` (the reason, e.g. paused). `{ "watching": false }` otherwise. |
| `time`           | Lua      | `speed`: the game's speed button, 0 pause, 1 play, 2 fast forward (×5), 3 faster (×20), 4 wait (×40); `canChange` (false in multiplayer); `gameMenuOpen` (true while the game's pause menu is open: `set_speed` is refused then, as the game's own buttons are) |
| `deck`           | Lua      | The Command deck: `clock` and `commands[]`, see below. |
| `map`            | Lua      | The minimap: `x`, `y` (tiles; the car's while driving, as the game's minimap centres on it), `z` (floor), `heading` (degrees clockwise from east, 90 = south; optional, from the car's movement while driving), `miniMap` / `worldMap` (the save's sandbox Map options, `ISMiniMap.IsAllowed` / `ISWorldMap.IsAllowed`). `alwaysShow`: the player ticked "Map on every save" in the game's Options > Mods > ZomboidDS. The app shows the map when `worldMap` and (`miniMap` or `alwaysShow`). Sent when the player moves or turns (mod 0.22+). |
| `building`       | Lua      | What the player is placing with the game's build cursor after `build_place`: `{ placing: { id, name, icon, blocked, missing[] } }`, or `{}` (possibly `[]`) when the cursor is down. `blocked`: the game won't place it now (something is short); `missing`: the names of the inputs and skills that are short, when known. Sent when it changes (mod 0.25+). |
| `explored`       | bridge   | The parts of the map the player has seen, as the game remembers them (`WorldMapVisited`): `originX`, `originY` (tile of unit 0,0), `unit` (32 tiles), `width`, `height` (in units), `bits`: base64 of zlib-deflated bytes, 2 bits per unit (1 visited, 2 known from a map), 4 units per byte, unit `x` in bits `(x % 4) * 2` of byte `x / 4 + y * width / 4`. The minimap shows a unit when either bit is set. Sent when it changes, checked every 2 s. |
| `map_symbols`    | bridge   | What the game's minimap shows with its Symbols option on: the game's printed labels (town, river, building names, read from each map's `worldmap-annotations.lua`, `label: true`) and the symbols and notes the player put on their world map. `symbols[]`, each `{ kind, x, y, color, scale, rotation, anchorX, anchorY, label, minZoom, maxZoom }` with `kind` `icon` (+ `icon`, an icon path like `LootableMaps/map_star`) or `text` (+ `text`, as the game shows it). `color` `[r, g, b, a]` (0–1), `scale` the game's size (0.666 default), `rotation` degrees, `anchorX`/`anchorY` which point of it sits on the spot (0–1), `minZoom`/`maxZoom` the game zoom levels it shows between (zoom = log2(pixels per tile × 40075017 / view height in pixels)). Colour alpha 0: the map style's colour (black on the minimap). Sent when they change, checked every 2 s (mod 0.23+). |
| `command_result` | Lua      | `id`, `ok`, `error` (optional), `data` (optional, command-specific). Event, never replayed. |
| `show`           | Lua      | `panel` (`inventory` or `garment`), `container` (optional container id, `inventory`), `item` (the garment's item id, `garment`). The player asked the game for that panel, e.g. pressed the controller's Loot/Inventory button, or chose Inspect on a garment (mod 0.26+): the app shows it. Event, never replayed. |

Inventory item:

```json
{ "id": 10234, "type": "Base.Axe", "name": "Axe", "category": "Weapon",
  "icon": "Item_Axe", "weight": 3.0, "condition": 0.85, "equipped": "primary",
  "actions": ["unequip", "drop"] }
```

- `id` is the per-instance item id (`InventoryItem:getID()`), **not** the type. Two bandages have two ids.
- `category`: as the game's inventory list shows it, translated ("Cooking", not `CookingWeapon`).
- `condition` is 0–1, omitted for items without meaningful condition.
- `name`: as the game's inventory list shows it (`item:getName(player)`, mod 0.24+): food with its
  state in brackets, "Steak (Fresh, Cooked)", fluid containers with what's in them, "Water Bottle
  (Water)", "Empty Water Bottle". `shortName`: the plain name ("Steak") when it differs, for tiles.
- `freshness`: `fresh`, `stale` or `rotten` for food that goes off, as the game names it ("Bread
  (Stale)"); omitted otherwise. `freshnessText`: that word as it appears in `name` (omitted for
  burnt food, whose name says only "Burnt").
- `cooking` (food, mod 0.24+): `state` `cooked`, `uncooked` or `burnt` and `text`, the word the
  name uses ("Cooked", "Grilled", "Toasted", "Uncooked", "Burnt"; both omitted where the game's name
  leaves it out). While it heats (cookable, not frozen, heat > 1.6), `progress` 0–1 as the game's
  inventory bar draws it: cooking up to done, then with `burning: true` up to burnt.
- `read: true` (mod 0.24+): read, watched, heard or (a map) read already, where the game's inventory
  puts its tick. `unwanted: true`: the player set the item type Unwanted (the game greys it out).
- `fluid` (B42 fluid containers, mod 0.24+): `amount` and `capacity` in litres; when not empty the
  main fluid's `name` (translated) or `mixture: true`, and `color` `[r, g, b]` (0–1), the colour
  the game's tooltip draws it in.
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
- `selected: true` marks the container the game's loot window has selected: the one it outlines in the world.
- `icon` is a texture name for the icon endpoint, like item icons.

### Command deck

`deck.clock` is what the game's own clock shows, and only then: it appears when the player carries
a watch or clock, the date only with some of them. `time` (e.g. `"14:25"` or `"2:25 PM"`, in the
player's 12/24-hour setting), `date` (e.g. `"July 9"`, optional), `alarm` (the alarm time of the
player's watch or clock when it's set, optional). No `clock` without a watch.

`deck.commands[]` lists the commands this game side can run, in no particular order; the player
chooses which ones the Deck shows. Each is `{ id, name, icon, available, on }`:

- `id`: stable, the app remembers the player's choice by it. v1: `zoom_in`, `zoom_out`,
  `search_mode`, `map`, `sit`, `flashlight`, `drop_bag`, `weapons`, `alarm`, `shout`. Unknown ids
  are ignored by the app.
- `name`: the game's own (translated) name for it, as in its key bindings.
- `icon`: a texture name for the icon endpoint.
- `available`: false while it can't run now (no light source to switch, no bag on your back,
  paused where the game refuses it).
- `on`: for modes (search mode, a lit flashlight): whether it's on now. Omitted for one-off commands.

`deck_run` runs one (see below). `drop_bag` drops the bag the player wears: the app asks first.

Two commands take arguments and come with their own state in `deck`:

- `weapons`: `deck.hotbar[]` is the game's hotbar, slot by slot in its order: `{ slot, name,
  item: { name, icon }, inHand }` (`item` omitted for an empty slot; `name` as the game names the
  slot: Back, Belt Left, ...). `deck_run { id: "weapons", slot }` draws that slot's item like the
  hotbar's key (what was in hand goes back to its slot; the item already in hand is put away).
  Refused while an action is queued or mid-swing, like the hotbar keys.
- `alarm`: `deck.alarmClock` is the watch or clock the game's alarm uses (worn first, then
  carried): `{ name, hour, minute, on }`; omitted without one. `deck_run { id: "alarm", hour,
  minute, on }` sets it as the game's alarm dialog does.

### Capabilities

`session.capabilities` tells the app what the running adapter supports, so the app can hide UI
the game side can't back. v1 values: `player`, `inventory`, `vehicle`, `cmd.equip`, `cmd.unequip`,
`cmd.drop`, `cmd.wear`, `item_menu`, `containers`, `transfer`, `time`, `world_menu`, `here`, `select_container`, `health`, `moodles`, `craft`, `build`, `tailor`, `deck`, `map`.

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
| `item_menu` | `itemId`, or `itemIds` for several items picked together (the game's menu for a selection; the first leads; mod 0.21+, `itemId` is sent as well for older ones). Result `data`: the game's own context menu for the item, see below; without the entries the app has as its own buttons (Grab / Grab all, Move To, Transfer all / Loot all; recognised by the game function behind them) |
| `watch_here` | `on` (default true): the app shows "Here". Lasts 10 s, so the app repeats it every few seconds while it's shown; `on: false` stops it. |
| `health_menu` | `part` (a body part `id` from `health`). Result `data`: the game's treatment menu for it (bandage, disinfect, remove glass, splint, ... with what the player carries), same shape as `item_menu` |
| `world_menu` | none. Result `data`: the game's world menu for where the player stands ("Here"), same shape as `item_menu` |
| `menu_select` | `menuId`, `optionId`: runs that option of the menu, like clicking it in the game |
| `transfer` | `itemId`, `to` (container id): moves the item there from wherever it is (inventory, bag, or a container within reach) |
| `transfer_all` | `from`, `to` (container ids): moves everything, with the filters of the game's Take All / Transfer All buttons |
| `select_container` | `id`: selects a container around the player in the game's loot window, as clicking its tab would. The game then outlines it in the world and plays its open/close sound. Refused for your own bags and locked or out-of-reach containers. |
| `set_speed` | `speed` (0–4, as in `time`): presses the game's own speed button. Refused in multiplayer, like in the game. Works while paused. |
| `deck_run` | `id` (from `deck.commands`): runs that command as its key binding does in the game. Refused while `available` is false. `weapons` also takes `slot`; `alarm` takes `hour`, `minute`, `on` (see Command deck). |
| `craft_list` | none. Result `data`: what the game's crafting window lists for the player now: `recipes[]` `{ id, name, icon, category, canCraft }` and `categories[]` `{ id, name }` (see Crafting) |
| `craft_recipe` | `recipe` (an `id` from `craft_list`). Result `data`: `{ id, name, icon, category, seconds, canCraft, max, inputs[], outputs[], skills[] }` (see Crafting) |
| `craft` | `recipe`, `count`: crafts it `count` times (at most `max`), the way the crafting window's Craft button does |
| `build_list` | none. Result `data`: what the game's build window lists: `recipes[]` `{ id, name, icon, category, canBuild, group, level, version, groupName, skill }` and `categories[]` (see Building) |
| `build_recipe` | `recipe` (an `id` from `build_list`). Result `data`: `{ id, name, icon, category, seconds, canBuild, inputs[], skills[] }` as in `craft_recipe` |
| `build_place` | `recipe`: turns on the game's placement cursor for it on the game's screen, as the build window's Build button does. The `building` message follows. |
| `build_stop` | none: puts the cursor away, as B on the controller does |
| `tailor_list` | none. Result `data`: the player's clothes and sewing kit: `garments[]` `{ id, name, icon, condition, worn, bag, holes, patches, repairable }`, `kit` `{ needle, thread, fabrics[] { type, name, icon, count } }`, `tailoring` (skill level) (see Tailoring) |
| `tailor_garment` | `itemId`. Result `data`: `{ id, name, icon, worn, condition, blood, dirt, cantRepair, tailoring, parts[] }`, each part `{ id, name, bite, scratch, bullet, hole, blood, patch, sewing }` (see Tailoring) |
| `tailor_menu` | `itemId`, `part` (a part `id`). Result `data`: `{ menuId, options[] }` as `item_menu`: the game's tailoring menu for that part. Choose with `menu_select`. |

### Crafting

The recipes are the game crafting window's (42.20 `HandcraftLogic` with its default query
`InHandCraft;AnySurfaceCraft`, no workbench): known recipes, `canCraft` from the same check its list
uses, ingredients counted from the inventory, bags and containers around. `craft_recipe`:
`inputs[]` `{ name, icon, need, have, ok, keep, others, unit }` (`name`/`icon`: the first item the
player has for it, else the first that would do; `others`: how many other items would also do;
`keep`: a tool, not used up; `unit: "L"` for fluids), `outputs[]` `{ name, icon, amount, unit }`,
`skills[]` `{ name, level, have }`, `max`: how many times it can be made now. `craft` runs the
window's own start: it fetches the ingredients, walks to a surface when the recipe needs one,
queues the actions and puts items back; it fails with a reason when nothing can be made.

### Building

The recipes are the game build window's (42.20 `BuildLogic`, the game entities with a craft recipe),
read like Crafting's. Versions of one thing (Shoddy, Poor, Good) are entities named `<thing>_Lvl<n>`:
they share a `group` with their `level`; `version` is the bracketed suffix of the translated name
(`Wood Chair (Poor)` → `Poor`, absent when the name has none) and `groupName` the name before it.
`skill` `{ name, level }` is the first skill it needs. Placing is the game's: the player moves, turns
and places the cursor with the mouse or controller (d-pad, LB/RB, A, B to stop). After each
placement the mod re-counts what's in reach and brings the cursor back, as the window does; the
game blocks it when something is short.

### Tailoring

As the game's Inspect window (42.20 `ISGarmentUI`) shows a garment. `garments` are the clothes in
the player's inventory and bags that cover a body part, worn first; `bag` names the bag a carried
one is in; `holes` / `patches` count them; `repairable` false for clothes without a fabric (boots,
helmets). `kit` is what the window's menu looks for anywhere in the inventory: a needle, thread and
the three fabrics it patches with (types `RippedSheets`, `DenimStrips`, `LeatherStrips`, in that order: Rag, Denim Strips and Leather Strips in English, with
their item names and counts).

A garment's `parts` are the body parts it covers (`BloodBodyPartType`, `Back` included), with the
translated `name`; `bite`, `scratch`, `bullet` the defence there (0 over a hole); `hole` true;
`blood` 0–1 when bloody; `patch` the game's line ("Leather Strips patch"); `sewing`
`{ name, progress }` while the player's current action patches or unpatches that part (the game's
label, 0–1). `blood` and `dirt` of the garment are 0–1; `cantRepair` is the game's "Can't be
repaired." text. `tailor_menu` is the window's own menu for a part (Patch Hole / Add Padding with
each fabric, Patch all Holes using …, Remove Patch, or the greyed Tailoring with the game's reason);
the game moves the garment and kit into the main inventory first, as it does from the window.

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
  { "id": "1", "name": "Read", "enabled": true, "pill": "action" },
  { "id": "2", "name": "Eat", "enabled": true, "pill": "action", "children": [
    { "id": "2.1", "name": "All", "enabled": true },
    { "id": "2.2", "name": "Half", "enabled": true } ] },
  { "id": "3", "name": "Rip into sheets", "enabled": false, "tooltip": "Requires a knife" },
  { "id": "4", "name": "Drop", "enabled": true, "pill": "drop" } ] }
```

- Works for items you carry and for items in containers within reach (not locked ones). For the
  latter it's the menu the game's loot window shows: Grab, plus options like Read or Eat that take
  the item first. An option only runs while the item is still within reach.
- Options with `children` are submenus; only options without children can be selected.
- `icon` (optional): the texture name of the option's icon in the game's menu (e.g. the object's
  sprite), for the bridge's icon endpoint. Not every icon can be served; the app shows none then.
- `enabled: false` options are shown greyed out, with the game's reason in `tooltip` if it gives one.
- `pill` (optional, top-level options only): the item's main uses, which the app shows as buttons
  above the rest, recognised by the game function behind them (never by name): equip and attach
  (weapons only), eat, drink, wear, take off, read, take pills, apply a bandage, firearm loading and
  racking, turn on/off, alarm, check map, device options (`"action"`), and drop (`"drop"`). A
  submenu is a pill when all its options are (Eat > All / Half / Quarter). Unknown values count as
  `"action"`.
- Only the latest menu is valid, and only for one `menu_select` within a minute. Otherwise the result
  is `ok: false` and the app should request a fresh menu.
- No menu while the game is paused (`ok: false`, "The game is paused"), same as in the game.
- Options that open a window (renaming, crafting, maps) open it on the game's screen.

`world_menu` is what the controller's interact button opens: the game's world right-click menu for
the objects on the player's tile and the three tiles they face (not through walls), including options
other mods add. None while paused or in a vehicle. An option only runs while the player still stands
and faces the same way as when the menu was built; otherwise `ok: false`.

Its top-level options carry three optional fields, so the app can keep its own order:
- `key`: the object the option's actions act on (`"x,y,z#index"`: its square and its place among
  the square's objects), the same however the game orders its menu; `"list:<name>"` for a `tray`
  option, `"name:<name>"` for one without an object. A repeated key gets `"|2"`, `"|3"`, ...
- `tray: true`: one action over several objects (Disassemble > each object), not an object's card.
- `front: true`: the option for what the interact button would act on now (the prompt's object, or
  the door or window the game picks the same way). At most one.

While watched, the mod sends `here` by itself: rebuilt when the player steps onto another tile or
turns, when their action finishes, shortly after a `menu_select`, and every few seconds, but never
while the game's own context menu is open on the game's screen (the game has one per player, so
building ours would close it). A rebuild with the same options keeps the same `menuId` and isn't re-sent.

A command the bridge can't parse is answered directly by the bridge with `ok: false`.

## Rules for evolving the protocol

- Adding optional fields or new message types: fine, stays v1. Clients ignore unknown fields and types.
- Renaming/removing fields or changing their meaning: bump `v` and handle both on the client
  (the app maps wire DTOs to its domain model in one place, `ProtocolV1`).
