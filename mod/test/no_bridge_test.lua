-- The mod's Lua without its Java side (ZombieBuddy missing): it must not break the game, and it
-- should tell the player once, a few seconds into the game. Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

local now = 0
function getTimestampMs() return now end
local version = { getMajor = function() return 42 end, getMinor = function() return 20 end }
setmetatable(version, { __tostring = function() return "42.20" end })
function getCore() return { getGameVersion = function() return version end } end

Events = {}
for _, name in ipairs({ "OnGameStart", "OnTick" }) do
  local listeners = {}
  Events[name] = {
    Add = function(fn) table.insert(listeners, fn) end,
    Remove = function(fn) for i, f in ipairs(listeners) do if f == fn then table.remove(listeners, i) return end end end,
    fire = function(...) for _, fn in ipairs({ unpack(listeners) }) do fn(...) end end,
    count = function() return #listeners end,
  }
end

local notes = {}
local player = { setHaloNote = function(_, text) table.insert(notes, text) end }
function getSpecificPlayer() return player end

ZomboidDSBridge = nil -- ZombieBuddy didn't load our Java class

require("ZomboidDS/Adapters/B42")
require("ZomboidDS/Main")

local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

Events.OnGameStart.fire()
Events.OnTick.fire()
check(#notes == 0, "no notice right at game start")

now = 6000
Events.OnTick.fire()
check(#notes == 1 and notes[1]:find("ZombieBuddy"), "notice shown a few seconds in: " .. tostring(notes[1]))

now = 20000
Events.OnTick.fire()
Events.OnTick.fire()
check(#notes == 1, "shown only once")
check(Events.OnTick.count() == 0, "no per-tick work left without the bridge")
print("ALL LUA CHECKS PASSED")
