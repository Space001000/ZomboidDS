#!/usr/bin/env python3
"""Checks that an APK carries no development-only parts (test kits), or, with --dev, that it does.

    python tools/check-release-apk.py companion-app/build/outputs/apk/release/companion-app-release.apk
    python tools/check-release-apk.py --dev companion-app/build/outputs/apk/dev/companion-app-dev.apk

Looks at the bundled mod (assets/ZomboidDS.zip: no ZomboidDS/.../Dev/ files, no "-dev" mod version)
and the app's code (no "dev_kit" command in any classes*.dex). Exit code 1 when it doesn't match.
"""
import io
import sys
import zipfile


def inspect(path):
    apk = zipfile.ZipFile(path)
    mod = zipfile.ZipFile(io.BytesIO(apk.read("assets/ZomboidDS.zip")))
    dev_files = [n for n in mod.namelist() if "/Dev/" in n]
    version = next(l for l in mod.read("ZomboidDS/42/mod.info").decode().splitlines() if l.startswith("modversion="))
    dex_kits = any(b"dev_kit" in apk.read(n) for n in apk.namelist() if n.startswith("classes") and n.endswith(".dex"))
    return dev_files, version, dex_kits


def main():
    dev = "--dev" in sys.argv
    path = [a for a in sys.argv[1:] if a != "--dev"][0]
    dev_files, version, dex_kits = inspect(path)
    print(f"{path}\n  mod {version}\n  mod Dev/ files: {len(dev_files)}\n  test kits in the app code: {dex_kits}")
    has_dev = bool(dev_files) or version.endswith("-dev") or dex_kits
    if dev and not (dev_files and version.endswith("-dev") and dex_kits):
        sys.exit("FAIL: a dev build should carry the test kits in the mod and the app")
    if not dev and has_dev:
        sys.exit("FAIL: development parts in a release build")
    print("OK")


if __name__ == "__main__":
    main()
