#!/usr/bin/env bash
# Development loop: build the mod zip and install it into Zomdroid on the connected device,
# through the companion app (which holds the folder permission Zomdroid's storage needs).
#
#   tools/deploy-mod.sh              build + install
#   ANDROID_SERIAL=<id> tools/deploy-mod.sh   pick a device when several are attached
#
# Needs: companion app installed and 'Grant Zomdroid access' done once. Restart the game (or
# return to the main menu) afterwards so it reloads the mod.
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash on Windows: don't rewrite /sdcard paths

cd "$(dirname "$0")/.."
ADB="${ADB:-adb}"
if ! command -v "$ADB" >/dev/null 2>&1 && [ -n "${LOCALAPPDATA:-}" ]; then
  ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
fi

APP=dev.zomboidds.companion
DEVICE_DIR=/sdcard/Android/data/$APP/files

./gradlew -q :bridge:adapter-b42:modZip
# Start the app once so its files folder exists (adb may not create it).
"$ADB" shell am start --display "${COMPANION_DISPLAY:-4}" -n "$APP/.MainActivity" >/dev/null
sleep 2
"$ADB" push bridge/adapter-b42/build/distributions/ZomboidDS.zip "$DEVICE_DIR/ZomboidDS.zip" >/dev/null

"$ADB" logcat -c
# Launch on the bottom screen (display 4 on the AYN Thor); -S restarts it so the extra is read.
"$ADB" shell am start -S --display "${COMPANION_DISPLAY:-4}" -n "$APP/.MainActivity" --es devInstall ZomboidDS.zip >/dev/null

for _ in $(seq 1 30); do
  result=$("$ADB" logcat -d -s ZomboidDS-dev | grep -E "install done|FAILED" || true)
  if [ -n "$result" ]; then
    echo "$result" | sed 's/^.*ZomboidDS-dev: //'
    echo "$result" | grep -q "install done" && exit 0 || exit 1
  fi
  sleep 1
done
echo "timed out waiting for the install result (is the app installed and access granted?)" >&2
exit 1
