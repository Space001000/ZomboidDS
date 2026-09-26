# ZomboidDS: design

How ZomboidDS works, and why it's built this way. For players: [USER_GUIDE.md](USER_GUIDE.md). For
building and testing: [README.md](../README.md). The wire format: [PROTOCOL.md](../protocol/PROTOCOL.md).

## Goal

Nintendo DS-style play on the AYN Thor: Project Zomboid (through [Zomdroid](https://github.com/udarmolota/zomdroid))
fills the top screen, a native app on the bottom touchscreen shows and acts on what the game's side
panels would otherwise cover.

On a 6" screen the game's sidebar panels (inventory, loot, health, crafting) cover most of the game
view, so looting and treating wounds happen blind. ZomboidDS moves them to the bottom screen and
leaves the top screen to the game. Zomdroid renders the whole game into one surface, so the game's
windows can't be moved to the other display: each panel is rebuilt in the app from game data.

## Principles

1. **The game's own logic, not a copy.** Wherever the game decides something (which containers are
   in reach, what an item's menu offers, what a wound needs, what can be crafted), ZomboidDS asks
   the game's own code and mirrors the answer. Rules, translations, skills and other mods' changes
   come along for free, and nothing drifts when the game updates.
2. **Don't duplicate the controller.** The gamepad's radial menus already cover many actions (map,
   crafting window, ...). The bottom screen is for panels that would cover the game view,
   glanceable information, and gaps the controller has.
3. **The game's look.** The app uses the game's own icons, moodles and body silhouette, read from
   the player's installation.
4. **An average Thor owner can install it.** No adb, no manual file copying: the app's setup
   checklist does every step it can and explains the rest.
5. **Never break the game.** If ZombieBuddy, the bridge or the app is missing, the game runs as
   usual, and every engine call is guarded so one renamed method degrades one field, not the mod.
6. **Ship no game files.** Icons are read at runtime from the player's own copy of the game.

## Architecture

```
┌──────────────────────── TOP SCREEN: Zomdroid process ────────────────────────┐
│  Project Zomboid (JVM)                                                       │
│   ├─ ZombieBuddy (installed by the player through Zomdroid)                  │
│   └─ ZomboidDS mod                                                           │
│       ├─ Java jar (loaded by ZombieBuddy)                                    │
│       │    bridge core: WebSocket + HTTP on 127.0.0.1:7786, state hub,       │
│       │                 command inbox, icon service                          │
│       │    game adapter (B42): Lua API, icon sources                         │
│       └─ Lua                                                                 │
│            core (common/): emitter, command router, adapter registry         │
│            adapter (42/):  every call into the game API                      │
└──────────────────────────────────────────────────────────────────────────────┘
                  ws://127.0.0.1:7786/ws         http://127.0.0.1:7786/icons/*.png
┌──────────────────── BOTTOM SCREEN: ZomboidDS Companion ──────────────────────┐
│  Kotlin + Jetpack Compose, a non-focusable window (the game keeps the pad)   │
│   ├─ setup:  checklist, mod installer (folder picker → Zomdroid's provider), │
│   │          ZombieBuddy download, app updates                               │
│   ├─ domain: GameState, commands, menus, crafting (ports, no Android)        │
│   ├─ data:   WebSocketGameGateway (OkHttp), ProtocolV1                       │
│   └─ ui:     Inventory, Here (Vehicle), Status, Craft, Deck                  │
└──────────────────────────────────────────────────────────────────────────────┘
```

The two sides only share [the protocol](../protocol/PROTOCOL.md); its example messages are test
fixtures for both, and the mock server serves them.

**Version-specific code lives in adapters.** The Java core (`bridge/core`) and the Lua core
(`mod/ZomboidDS/common`) know nothing about a game build; `bridge/adapter-b42` and
`mod/ZomboidDS/42/.../Adapters/B42*.lua` do. Another build (e.g. B41) is an added adapter. The app
only speaks the protocol and reads `session.capabilities` to hide what an adapter can't do.

**Threading.** Game state and Kahlua are touched only on the game thread: commands are queued by
the bridge and drained in the game's tick (`OnTickEvenPaused`, so the app can unpause the game).
Network I/O happens only on bridge threads. State messages are full snapshots, sent only when they
change, and replayed to a client that (re)connects.

## How each feature talks to the game (42.20)

Everything below was checked against the game's Lua sources and `projectzomboid.jar`.

| Feature | The game's own code it uses |
|---|---|
| Containers | The inventory and loot windows' container lists (`ISInventoryPage.backpacks`): the game's own reachability, locks, safehouses, corpses, vehicles. Hidden windows keep updating (`UIManager.updateUIElements`), so they follow the player. |
| Moving items | `ISInventoryPane:transferItemsByWeight`, what the game's Take All / Transfer All use: timed actions, capacity checks, walking to the container. "Move all" applies the same filters as those buttons. |
| Highlighting | Opening a container's tab selects it in the hidden loot window (`selectButtonForContainer`), which outlines it in the world. |
| Item menu | `ISInventoryPaneContextMenu.createMenu`, built hidden and closed in the same tick; its options are copied and a choice runs `option.onSelect(target, params…)` exactly like a click. Entries the app has as buttons are left out by the function behind them. |
| The Y button | The controller prompt's `cmdShowLoot` / `cmdShowInventory` are wrapped: with the app connected, the container opens on the bottom screen instead of the game's window. |
| Game speed | The speed buttons' own `ButtonClicked` (like the controller's wheel); refused while the pause menu is open, as the game does. |
| "Here" | The interact prompt's objects (`getInteractOptionsButtonObjects`) and the world menu the controller's interact button opens; rebuilt when the player moves, turns or an action finishes. |
| Vehicle | In a vehicle there's no world menu: the game's vehicle radial menu (`ISVehicleMenu.showRadialMenu`) is built into a recorder instead of the real radial menu, with its sound and controller focus switched off for that moment; its slices become the Vehicle tab's buttons. |
| Command deck | Each command calls what its key binding calls (zoom: `Core.doZoomScroll`; search mode: the search manager; flashlight: `ItemBindingHandler.toggleLight`; map: `ISWorldMap.ToggleWorldMap`; ...), named with the game's key binding texts. Weapons is the game's hotbar (`ISHotbar:activateSlot`); Alarm sets the watch as the game's alarm dialog does. The clock line follows the game's own clock (only with a watch). |
| Injuries | `ISHealthPanel.getDamagedParts` and the health list's own `doDrawItem`, run with a stand-in that records its text and colours: the lines depend on the player's First Aid level, like in the game. Treatments are the game's body-part menu. |
| Body silhouette | The health panel's `bps_male_*` / `bps_female_*` layers (UI2.pack), tinted per body part. |
| Moodles | `player:getMoodles()` for levels, names and descriptions; the moodle column's rules (level > 0, "food eaten" from level 3, background grey → good/bad highlight colour by level/4). The type → icon table is Java-only (`zombie.ui.MoodleTextureSet`), so the adapter holds a copy. |
| Food freshness | The rule `Food:getName` uses for "Fresh / Stale / Rotten". |
| Crafting | Java `HandcraftLogic` with the crafting window's default query (`InHandCraft;AnySurfaceCraft`), its containers and manual input selection; crafting runs the window's own `startHandcraft` with a stand-in for its craft control (fetch ingredients, walk to a surface, queue the actions, put items back). |
| Icons | The bridge serves loose textures (`media/textures`, `media/ui`), item and UI packs, and world-object sprites from `Tiles1x.pack`, cropped to their visible pixels; a missing icon is never cached. |

## Setup and install

Findings in Zomdroid's and ZombieBuddy's sources that shape the setup:

- The game's JVM runs inside Zomdroid's process. Zomdroid supports **ZombieBuddy**, which loads a
  mod's jar and exposes its Java to Lua: the mod's bridge needs no JVM arguments.
- Zomdroid's storage is an exported, writable DocumentsProvider: through Android's folder picker
  the app gets access and installs the mod into `<instance>/Zomboid/mods/`, and adds it to
  `default.txt` (what new games start with).
- ZombieBuddy's GitHub release has only the jar, not its mod folder, so mods that require it can't
  be enabled. The app builds the complete package from the same official release and tag
  (verified against hashes in `gradle.properties`) and saves it for Zomdroid's installer.
  ZombieBuddy isn't bundled or hosted by ZomboidDS.
- Zomdroid stops reading the gamepad when its activity pauses, so the app's window never takes
  input focus; touches still arrive.
- Cocoon can launch the app on the bottom screen together with Zomdroid.

The app bundles the mod; after an app update the checklist offers the matching mod update. The app
checks this repository's releases for its own updates and hands a downloaded APK to Android's
installer (same signing key only).

## Testing

- `mod/test`: the Lua core and B42 adapter against fakes of the game's API (`tools/test-lua.py`).
- `bridge/*`: server, state hub, protocol codec, `.pack` reader, and Kahlua conversion against the
  game's real Kahlua classes.
- `companion-app`: protocol mapping, stacking, setup logic, updates, and end-to-end tests of the
  app's gateway against the real bridge with a fake game (`bridge/mock-server`).
- On the device: every feature was tried in the game on an AYN Thor (42.20.4) before it was done.

## Known issues and limits

- Zomdroid's default graphics driver can garble other apps' drawing, including this one. The Mr.
  Purple Turnip T30 driver avoids it (see the user guide).
- Game menus the game builds only while you hover them can arrive empty (seen once: "Natural Water
  Source" in "Here").
- Build 42 only for now; one Zomdroid instance at a time (the one picked in the checklist).
- The vehicle dashboard is simple (speed, engine, fuel), with the car's menu below it.

## What's next

- A fuller vehicle dashboard.
- Inventory organisation, character info (skills, protection), a map view.
- Several Zomdroid instances at once, and B41 through its own adapter.
- Upstream ideas: a Zomdroid intent to launch an instance on a chosen display; a ZombieBuddy release
  asset with the full mod for non-Steam installs.
