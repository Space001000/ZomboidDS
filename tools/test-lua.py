#!/usr/bin/env python3
"""Runs the mod's Lua tests (mod/test/*_test.lua) in an embedded Lua 5.1, like the game's Kahlua.

    pip install lupa
    python tools/test-lua.py
"""
import pathlib
import sys

from lupa import lua51

root = pathlib.Path(__file__).resolve().parent.parent
failed = False
for test in sorted((root / "mod" / "test").glob("*_test.lua")):
    print(f"== {test.name}")
    lua = lua51.LuaRuntime()
    lua.globals().MOD_ROOT = str(root / "mod" / "ZomboidDS").replace("\\", "/")
    try:
        lua.execute(test.read_text(encoding="utf-8"))
    except Exception as e:  # a failed check() raises a LuaError
        print(f"FAILED: {e}")
        failed = True
sys.exit(1 if failed else 0)
