-- The change check's string form (Core/Signature.lua). Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. package.path

local Signature = require("ZomboidDS/Core/Signature")

local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

-- The same content built in a different order gives the same string.
local a = { name = "Axe", weight = 3.5, icon = "Item_Axe", tags = { "tool", "weapon" } }
local b = {}
b.tags = { "tool", "weapon" }
b.icon = "Item_Axe"
b.weight = 3.5
b.name = "Axe"
check(Signature.of(a) == Signature.of(b), "key order doesn't matter")

-- Any change shows.
check(Signature.of({ items = { 1, 2 } }) ~= Signature.of({ items = { 2, 1 } }), "array order matters")
check(Signature.of({ items = { 1, 2 } }) ~= Signature.of({ items = { 1, 2, 3 } }), "an added element shows")
check(Signature.of({ x = 1 }) ~= Signature.of({ x = 2 }), "a changed value shows")
check(Signature.of({ x = 1 }) ~= Signature.of({ y = 1 }), "a renamed key shows")
check(Signature.of({ x = true }) ~= Signature.of({ x = "true", y = nil, z = 1 }), "an added key shows")
check(Signature.of({ "a" }) ~= Signature.of({ a = "a" }), "an array element isn't a key")
check(Signature.of({ {} }) ~= Signature.of({}), "an empty table inside shows")
check(Signature.of({ a = { b = 1 } }) ~= Signature.of({ a = { b = 2 } }), "nested changes show")

-- Mixed keys: the array part, then the rest; numbers outside the array and strings sorted together.
local mixed = { "first", "second", [10] = "ten", name = "x", [2.5] = "half" }
local mixed2 = { name = "x", [2.5] = "half", [10] = "ten" }
mixed2[1], mixed2[2] = "first", "second"
check(Signature.of(mixed) == Signature.of(mixed2), "mixed keys are stable")
check(Signature.of(mixed) ~= Signature.of({ "first", "second", [11] = "ten", name = "x", [2.5] = "half" }), "and a moved number key shows")

-- Constant tables: written once, then reused, and they give the same string as an unmarked copy.
local groups = { { id = "friendly", emotes = { { id = "clap" }, { id = "wave" } } } }
local copy = { { id = "friendly", emotes = { { id = "clap" }, { id = "wave" } } } }
check(Signature.constant(groups) == groups, "constant returns the table")
local first = Signature.of({ emotes = groups, clock = "8:00" })
check(first == Signature.of({ emotes = copy, clock = "8:00" }), "a constant table writes like any other")
groups[1].id = "changed" -- never done for real: shows that the string was kept
check(Signature.of({ emotes = groups, clock = "8:00" }) == first, "a constant table is written only once")
check(Signature.of({ emotes = groups, clock = "8:01" }) ~= first, "what's around it still counts")
