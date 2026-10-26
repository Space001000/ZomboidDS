-- The mod's timings (Core/Perf.lua) through the Emitter and the command pump, with a fake clock.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. package.path

local Perf = require("ZomboidDS/Core/Perf")
local Emitter = require("ZomboidDS/Core/Emitter")
local Commands = require("ZomboidDS/Core/Commands")

local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

local now = 0
local function clock() return now end
local lines = {}
local perf = Perf.new(clock, 10000, function(line) table.insert(lines, line) end)

local sent = {}
local bridge = {
    emit = function(t, d) table.insert(sent, t); now = now + 0.5 end,
    poll = function() return { { id = "1", name = "item_menu", args = {} } } end,
}
local emitter = Emitter.new(bridge, clock, perf)
local count = 0
emitter:addChannel("containers", function() now = now + 12; return { items = { count } } end, 1000)
emitter:addChannel("time", function() now = now + 0.25; return { speed = 1 } end, 250)

local adapter = { commands = { item_menu = function() now = now + 30; return true end } }
local realPrint = print
print = function() end -- the command pump's log line

-- Tick 1: a command and both channels; tick 2: nothing due.
perf:beginTick()
Commands.pump(bridge, adapter, {}, 8, perf)
emitter:update({})
perf:endTick()
perf:beginTick()
emitter:update({})
perf:endTick()
-- Tick 3: containers changed and marked dirty, time unchanged.
count = 1
emitter:markDirty("containers")
emitter:markDirty("time")
perf:beginTick()
emitter:update({})
perf:endTick()
print = realPrint

check(#lines == 0, "nothing written before the report is due")
now = 10001
perf:beginTick()
perf:endTick()

check(#lines == 4, "a summary, two channels and one command: " .. #lines)
check(lines[1]:find("4 ticks, 2 with work", 1, true) ~= nil, "counts ticks with work: " .. lines[1])
check(lines[1]:find("1 over 16 ms", 1, true) ~= nil, "counts slow ticks: " .. lines[1])
check(lines[1]:find("cmd item_menu 30.0", 1, true) ~= nil, "names what the worst tick did: " .. lines[1])
check(lines[2]:find("containers: 2 runs (2 dirty), 2 sent", 1, true) ~= nil, "channel runs, dirty runs, sends: " .. lines[2])
check(lines[2]:find("snapshot 12.0/12.0", 1, true) ~= nil, "snapshot time: " .. lines[2])
check(lines[2]:find("emit 0.5/0.5", 1, true) ~= nil, "emit time: " .. lines[2])
check(lines[3]:find("time: 2 runs (2 dirty), 1 sent", 1, true) ~= nil, "unchanged snapshots aren't counted as sent: " .. lines[3])
check(lines[4]:find("command item_menu: 1 | 30.0/30.0", 1, true) ~= nil, "command time: " .. lines[4])

lines = {}
now = 20002
perf:beginTick()
perf:endTick()
check(#lines == 0, "a quiet period writes nothing")

-- A bridge that compares in Java: the Emitter leaves the comparing to it, and a reconnect makes it
-- forget what it sent.
local javaSent, forgotten = {}, 0
local last = {}
local javaBridge = {
    emit = function() error("emit isn't used when the bridge compares") end,
    emitIfChanged = function(t, d)
        local key = tostring(d.speed)
        if last[t] == key then return false end
        last[t] = key
        table.insert(javaSent, t)
        return true
    end,
    forgetSent = function() forgotten = forgotten + 1; last = {} end,
}
local java = Emitter.new(javaBridge, clock)
local speed = 1
java:addChannel("time", function() return { speed = speed } end, 250)
java:update({})
java:markDirty("time")
java:update({})
check(#javaSent == 1, "compared by the bridge: an unchanged snapshot isn't sent")
speed = 2
java:markDirty("time")
java:update({})
check(#javaSent == 2, "a changed one is")
java:invalidate()
java:update({})
check(forgotten == 1 and #javaSent == 3, "invalidate makes the bridge forget, so it's sent again")

-- GC marking: a tick in which the game's garbage collector ran is counted apart.
local gc = 0
lines = {}
now = 0
local gcPerf = Perf.new(clock, 10000, function(line) table.insert(lines, line) end, function() return gc end)
gcPerf:beginTick()
gcPerf:channel("inventory", false, 20, 1, nil, 0)
gc = gc + 1 -- a GC pause during this tick
now = now + 20
gcPerf:endTick()
gcPerf:beginTick()
gcPerf:channel("containers", false, 9, 1, 0, 0)
now = now + 10
gcPerf:endTick()
now = 10001
gcPerf:beginTick()
gcPerf:endTick()
check(lines[1]:find("2 over 8 ms (1 with GC), 1 over 16 ms (1 with GC)", 1, true) ~= nil, "slow ticks with a GC pause are counted apart: " .. lines[1])
check(lines[1]:find("worst 20.0 ms with GC (inventory 21.0)", 1, true) ~= nil, "the worst tick says it had one: " .. lines[1])
check(lines[1]:find("worst without GC 10.0 ms (containers 10.0)", 1, true) ~= nil, "and the worst one without: " .. lines[1])
check(lines[2]:find("inventory: 1 runs (0 dirty), 0 sent | snapshot", 1, true) ~= nil, "no size when the bridge compared: " .. lines[2])

-- Without a Perf the Emitter works as before.
sent = {}
local plain = Emitter.new(bridge, clock)
plain:addChannel("time", function() return { speed = 1 } end, 250)
plain:update({})
check(#sent == 1 and sent[1] == "time", "the Emitter without timings still sends")
