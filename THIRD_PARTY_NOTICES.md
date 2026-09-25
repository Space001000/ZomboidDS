# Third-party notices

ZomboidDS is licensed under the GNU General Public License v3.0 (see [LICENSE](LICENSE)). It
includes or uses the open-source software below, under their own licences. All of them are
compatible with the GPL-3.0.

## Included in the companion app (APK)

| Component | Licence | Source |
|---|---|---|
| Kotlin standard library | Apache-2.0 | https://github.com/JetBrains/kotlin |
| kotlinx.coroutines | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx.serialization | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| AndroidX (Core, Activity, Lifecycle, Compose UI, Foundation, Material 3, and their dependencies) | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| JetBrains Compose Multiplatform runtime (used by Coil) | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform |
| Accompanist Drawable Painter (used by Coil) | Apache-2.0 | https://github.com/google/accompanist |
| Coil | Apache-2.0 | https://github.com/coil-kt/coil |
| OkHttp | Apache-2.0 | https://github.com/square/okhttp |
| Okio | Apache-2.0 | https://github.com/square/okio |
| Guava ListenableFuture | Apache-2.0 | https://github.com/google/guava |
| JetBrains Java Annotations | Apache-2.0 | https://github.com/JetBrains/java-annotations |
| JSpecify | Apache-2.0 | https://github.com/jspecify/jspecify |

The full text of the Apache License 2.0 is in [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt).

## Included in the mod (ZomboidDS.jar)

| Component | Licence | Source |
|---|---|---|
| Gson | Apache-2.0 | https://github.com/google/gson |
| NanoHTTPD (core and WebSocket) | BSD-3-Clause | https://github.com/NanoHttpd/nanohttpd |

NanoHTTPD:

```
Copyright (C) 2012 - 2015 nanohttpd

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the nanohttpd nor the names of its contributors
   may be used to endorse or promote products derived from this software without
   specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
OF THE POSSIBILITY OF SUCH DAMAGE.
```

## Used, not included

- **ZombieBuddy** (MIT, Copyright (c) 2025 Andrey "Zed" Zaikin,
  https://github.com/zed-0xff/ZombieBuddy): loads the mod's Java code. Not bundled: the app
  downloads the pinned release from ZombieBuddy's own GitHub page when the player asks, and checks
  its hashes.
- **Zomdroid** (MIT, https://github.com/udarmolota/zomdroid): runs Project Zomboid on Android. Not
  bundled; installed by the player.
- **Project Zomboid** is © The Indie Stone. ZomboidDS is an unofficial mod, not affiliated with or
  endorsed by The Indie Stone. It ships no game files: the item, moodle and body images the app
  shows are read at runtime from the player's own installation of the game.

Build and test tools (Gradle, the Android Gradle Plugin, Shadow, JUnit, Lupa, ...) are used to
build and test ZomboidDS but aren't part of what it ships.
