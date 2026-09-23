# ZomboidDS

Nintendo DS-style dual-screen play for Project Zomboid on handhelds like the AYN Thor. The game
runs on the top screen (through [Zomdroid](https://github.com/udarmolota/zomdroid)); the
**ZomboidDS Companion** app on the bottom screen shows your inventory with the game's own icons,
your health and stats and your vehicle, and lets you use items by tapping: quick actions (equip,
wear, drop) plus everything the game's own item menu offers (read, eat, apply, craft, ...).

Status: in development. See [PLAN.md](PLAN.md) for the plan, device findings and what's next, and
[docs/USER_GUIDE.md](docs/USER_GUIDE.md) for how players set it up.

## How it fits together

```
Top screen: Zomdroid → Project Zomboid (B42)
  ├─ ZombieBuddy (Java mod loader, installed by the user through Zomdroid)
  └─ ZomboidDS mod
       ├─ Java (bridge/): WebSocket + HTTP server on 127.0.0.1:7786
       └─ Lua (mod/):     reads game state, runs commands on the game thread
Bottom screen: ZomboidDS Companion (companion-app/), Kotlin + Compose
```

The two sides only share [the protocol](protocol/PROTOCOL.md). Everything game-version specific
lives in adapters (`bridge/adapter-b42`, `mod/ZomboidDS/42/`), so another build (e.g. B41) is an
added adapter, not a change to the core or the app.

| Folder | What |
|---|---|
| `protocol/` | Protocol spec and example messages, used as test fixtures by both sides |
| `bridge/core` | Version-neutral server: state hub, command queue, icon extraction from `.pack` files |
| `bridge/adapter-b42` | B42 adapter + ZombieBuddy entry point; builds `ZomboidDS.jar` and the mod zip |
| `bridge/mock-server` | The real bridge with a fake game, for app development and end-to-end tests |
| `mod/ZomboidDS` | The mod: Lua core in `common/`, B42 adapter and `mod.info` in `42/` |
| `mod/test` | Lua tests against a fake game |
| `companion-app` | The Android app, including the setup wizard that installs the mod |
| `tools` | Dev scripts |

## Building

Requirements:

- Android Studio (or the Android SDK) and a JDK 21+ to run Gradle.
- A **JDK 25** for the bridge: 42.20's class files need it. Gradle finds one installed by
  IntelliJ/Android Studio (`~/.jdks`), or downloads it.
- **Project Zomboid (B42)** on the build machine; the bridge compiles against its
  `projectzomboid.jar`. Put the folder that contains it in `local.properties` (not committed):

  ```properties
  pz.gameDir=C\:/Steam/steamapps/common/ProjectZomboid
  ```

Then:

```bash
./gradlew :companion-app:assembleDebug
```

This also builds the mod zip (`bridge/adapter-b42/build/distributions/ZomboidDS.zip`) and bundles
it into the APK. ZombieBuddy isn't bundled: the app downloads the version pinned in
`gradle.properties` from ZombieBuddy's GitHub when the user asks, and checks its hashes.

## Tests

```bash
./gradlew :bridge:core:test :bridge:adapter-b42:test :companion-app:testDebugUnitTest
pip install lupa && python tools/test-lua.py
```

- `bridge/*`: server, state hub, protocol codec, `.pack` reader, Kahlua conversion (against the
  game's real Kahlua classes).
- `companion-app`: protocol mapping, inventory stacking, mod list editing, ZombieBuddy packaging,
  and end-to-end tests of the app's gateway against the real bridge with the mock game.
- `mod/test`: the Lua core and B42 adapter against a fake of the game's API.

## Developing on a device

- **App against a fake game:** `./gradlew :bridge:mock-server:run` on the PC, then
  `adb reverse tcp:7786 tcp:7786` so the app on the device reaches it on `127.0.0.1`. The console
  lets you change the fake game (enter/leave a vehicle, take damage, add items). Add
  `-Pmock.args="inventory=full,gameDir=<PZ folder>"` for a 64-item inventory with the game's real
  icons. The emulator works too: set it to the Thor's bottom screen with
  `adb shell wm size 1080x1240` and `adb shell wm density 369`.
- **Mod changes on the device:** `tools/deploy-mod.sh` builds the mod and installs it into Zomdroid
  through the companion app (which holds the folder permission; grant it once in the app).
  Restart the game afterwards. The mod's log goes to logcat under the tag `zomdroid-main`
  (look for `[ZomboidDS]`).
- On the AYN Thor the bottom screen is display 4:
  `adb shell am start --display 4 -n dev.zomboidds.companion/.MainActivity`.

## Credits

- [Zomdroid](https://github.com/udarmolota/zomdroid) runs Project Zomboid on Android.
- [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) (MIT) loads the mod's Java code and
  exposes it to Lua.

Not affiliated with The Indie Stone. ZomboidDS ships no game files; icons are read from the
player's own installation on their device.
