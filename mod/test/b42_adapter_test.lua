-- Drives the mod's Lua (core + B42 adapter) end to end against a fake of just enough of
-- Project Zomboid and of the Java bridge. Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

local now = 0
function getTimestampMs() return now end
function getText(k) return "T:" .. k end
function instanceof(obj, cls) return obj._class == cls end
local version = { getMajor = function() return 42 end, getMinor = function() return 12 end }
setmetatable(version, { __tostring = function() return "42.12" end })
function getCore() return { getGameVersion = function() return version end } end

Events = {}
for _, name in ipairs({ "OnGameStart", "OnTickEvenPaused", "OnContainerUpdate", "OnEnterVehicle", "OnExitVehicle" }) do
  local listeners = {}
  Events[name] = { Add = function(fn) table.insert(listeners, fn) end, fire = function(...) for _, fn in ipairs(listeners) do fn(...) end end }
end

-- Java ArrayList stand-in
local function list(t) return { size = function() return #t end, get = function(_, i) return t[i + 1] end, raw = t } end
local function item(id, fullType, cls)
  return { _class = cls, getID = function() return id end, getFullType = function() return fullType end,
    getDisplayName = function() return fullType:gsub("Base.", "") end, getDisplayCategory = function() return "Misc" end,
    getTex = function() return { getName = function() return "media/textures/Item_" .. fullType:gsub("Base.", "") .. ".png" end } end,
    getActualWeight = function() return 1.23456 end, getCondition = function() return 5 end, getConditionMax = function() return 10 end,
    IsClothing = function() return cls == "Clothing" end, getBodyLocation = function() return cls == "Clothing" and "Hat" or nil end,
    isRequiresEquippedBothHands = function() return false end, isTwoHandWeapon = function() return fullType == "Base.Axe" end,
    isFavorite = function() return false end }
end
local axe = item(1, "Base.Axe", "HandWeapon")
local bandage = item(2, "Base.Bandage", "InventoryItem")
local items = { axe, bandage }
local inventory = { getItems = function() return list(items) end, getCapacityWeight = function() return 2.5 end,
  getItemWithIDRecursiv = function(_, id) for _, it in ipairs(items) do if it:getID() == id then return it end end end }
local vehicle = nil
local player = {
  getInventory = function() return inventory end, getMaxWeight = function() return 18 end, getPlayerNum = function() return 0 end,
  getPrimaryHandItem = function() return axe end, getSecondaryHandItem = function() return nil end,
  isEquippedClothing = function() return false end, getVehicle = function() return vehicle end,
  getBodyDamage = function() return { getOverallBodyHealth = function() return 87.54 end, getNumPartsBleeding = function() return 1 end } end,
  -- 42.20 style: stats:get(CharacterStat.X). ENDURANCE deliberately unknown to the fake.
  getStats = function() return { get = function(_, stat) return ({ HUNGER = 0.1234, THIRST = 0.5, FATIGUE = 0 })[stat] end } end,
}
function getSpecificPlayer() return player end
CharacterStat = { HUNGER = "HUNGER", THIRST = "THIRST", FATIGUE = "FATIGUE", ENDURANCE = "ENDURANCE" }

-- The game's speed buttons (zombie.ui.SpeedControls): ButtonClicked by name.
local speed, clicked = 1, {}
local SPEEDS = { Pause = 0, Play = 1, ["Fast Forward x 1"] = 2, ["Fast Forward x 2"] = 3, Wait = 4 }
local controls = { getCurrentGameSpeed = function() return speed end,
  ButtonClicked = function(_, name) table.insert(clicked, name); speed = SPEEDS[name] end }
UIManager = { getSpeedControls = function() return controls end }
function isClient() return false end

local calls = {}
ISInventoryPaneContextMenu = { equipWeapon = function(it, primary, two, n) table.insert(calls, "equipWeapon " .. it:getID() .. " " .. tostring(primary) .. " " .. tostring(two)) end,
  onDropItems = function(its, n) table.insert(calls, "drop " .. its[1]:getID()) end }
function getPlayerInventory() return nil end
function getPlayerLoot() return nil end

-- Fake Java bridge
local sent, queue = {}, {}
local resets = 0
ZomboidDSBridge = { emit = function(t, d) table.insert(sent, { type = t, data = d }) end,
  reset = function() resets = resets + 1 end,
  poll = function(max) if #queue == 0 then return nil end local b = queue; queue = {}; return b end,
  clients = function() return 1 end, version = function() return "test" end }

require("ZomboidDS/Adapters/B42")
require("ZomboidDS/Main")

local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end
local function last(t) for i = #sent, 1, -1 do if sent[i].type == t then return sent[i].data end end end
local function count(t) local n = 0 for _, m in ipairs(sent) do if m.type == t then n = n + 1 end end return n end

check(resets == 1, "loading the mod's Lua resets stale bridge state")

Events.OnGameStart.fire()
local s = last("session")
check(s and s.inGame == true and s.adapter == "b42" and s.gameVersion == "42.12", "session announces adapter + version")

Events.OnTickEvenPaused.fire()
local inv = last("inventory")
check(inv and #inv.items == 2, "inventory has 2 items")
check(inv.items[1].icon == "Item_Axe", "icon path+extension stripped: " .. tostring(inv.items[1].icon))
check(inv.items[1].equipped == "primary" and inv.items[2].equipped == nil, "equipped slot detection")
check(inv.items[1].condition == 0.5 and inv.items[2].condition == nil, "condition only for weapons/clothing")
check(inv.items[1].weight == 1.23, "weights rounded")
check(table.concat(inv.items[1].actions, ",") == "unequip,drop", "equipped item can be unequipped or dropped")
check(table.concat(inv.items[2].actions, ",") == "equip.primary,equip.secondary,drop", "loose item can be held in either hand")

-- Hidden items (B42 wounds are invisible worn clothing) aren't shown, like in the game's window.
local wound = item(99, "Base.Wound_Abdomen_Bite_Male", "Clothing")
wound.isHidden = function() return true end
table.insert(items, wound)
Events.OnContainerUpdate.fire(); Events.OnTickEvenPaused.fire()
local ids = {}
for _, it in ipairs(last("inventory").items) do ids[it.id] = true end
check(not ids[99] and ids[1] and ids[2], "hidden items (wounds) are left out")
table.remove(items)
Events.OnContainerUpdate.fire(); Events.OnTickEvenPaused.fire()
local p = last("player")
check(p.health == 87.5 and p.bleeding == true and p.stats.hunger == 0.123, "player snapshot")
check(p.stats.endurance == nil, "missing engine method degrades to nil, not an error")
check(last("vehicle").inVehicle == false, "on foot")

local before = count("inventory")
now = now + 50; Events.OnTickEvenPaused.fire()
check(count("inventory") == before, "unchanged inventory not re-sent")

now = now + 5000; Events.OnTickEvenPaused.fire()
check(count("inventory") == before, "interval re-sample with no change still not re-sent")

table.remove(items, 2); Events.OnContainerUpdate.fire(); Events.OnTickEvenPaused.fire()
check(count("inventory") == before + 1 and #last("inventory").items == 1, "dirty event re-sends changed inventory immediately")

vehicle = { getPartById = function() return { getContainerCapacity = function() return 50 end, getContainerContentAmount = function() return 25 end } end,
  getScript = function() return { getName = function() return "CarNormal" end } end,
  getCurrentSpeedKmHour = function() return -12.34 end, isEngineRunning = function() return true end, isDriver = function() return true end }
Events.OnEnterVehicle.fire(); Events.OnTickEvenPaused.fire()
local v = last("vehicle")
check(v.inVehicle and v.speedKmh == 12.3 and v.fuel == 0.5 and v.name == "T:IGUI_VehicleNameCarNormal", "vehicle snapshot")

queue = { { id = "c-1", name = "equip", args = { itemId = 1, slot = "both" } },
          { id = "c-2", name = "drop", args = { itemId = 99 } },
          { id = "c-3", name = "fly", args = {} },
          { id = "c-4", name = "wear", args = { itemId = 1 } } }
Events.OnTickEvenPaused.fire()
local results = {}
for _, m in ipairs(sent) do if m.type == "command_result" then results[m.data.id] = m.data end end
check(results["c-1"].ok == true and calls[1] == "equipWeapon 1 true true", "equip both -> equipWeapon(item, true, true)")
check(results["c-2"].ok == false and results["c-2"].error == "item not found", "unknown item rejected")
check(results["c-3"].ok == false and results["c-3"].error:find("unsupported"), "unknown command rejected")
check(results["c-4"].ok == false and results["c-4"].error:find("onWearItems"), "missing vanilla helper reported, not thrown")
-- Item actions for the other cases (mirrors the vanilla inventory menu).
local function withOverrides(base, overrides)
  local copy = {}
  for k, v in pairs(base) do copy[k] = v end
  for k, v in pairs(overrides) do copy[k] = v end
  return copy
end
local cap = withOverrides(item(3, "Base.Hat_BaseballCap", "Clothing"), {})
local sledge = withOverrides(item(4, "Base.Sledgehammer", "HandWeapon"), { isTwoHandWeapon = function() return true end })
local generator = withOverrides(item(5, "Base.Generator", "InventoryItem"), { isRequiresEquippedBothHands = function() return true end })
local lucky = withOverrides(item(6, "Base.Crisps", "Food"), { isFavorite = function() return true end })
local backpack = withOverrides(item(7, "Base.Bag_Schoolbag", "InventoryContainer"), { canBeEquipped = function() return "Back" end })
items = { axe, cap, sledge, generator, lucky, backpack }
Events.OnContainerUpdate.fire(); Events.OnTickEvenPaused.fire()
local byId = {}
for _, it in ipairs(last("inventory").items) do byId[it.id] = table.concat(it.actions, ",") end
check(byId[3] == "wear,drop", "clothing is worn, not held: " .. tostring(byId[3]))
check(byId[4] == "equip.primary,equip.both,drop", "two-handed weapon: main or both hands: " .. tostring(byId[4]))
check(byId[5] == "equip.both,drop", "items that need both hands: " .. tostring(byId[5]))
check(byId[6] == "equip.primary,equip.secondary", "favourites can't be dropped: " .. tostring(byId[6]))
check(byId[7] == "wear,equip.primary,equip.secondary,drop", "bags can be worn or held: " .. tostring(byId[7]))

-- Game speed
check(last("time").speed == 1 and last("time").canChange == true, "time: the game's current speed")
queue = { { id = "s-1", name = "set_speed", args = { speed = 0 } } }
Events.OnTickEvenPaused.fire()
for _, m in ipairs(sent) do if m.type == "command_result" then results[m.data.id] = m.data end end
check(results["s-1"].ok and clicked[1] == "Pause" and speed == 0, "pause presses the game's Pause button")
now = now + 1000; Events.OnTickEvenPaused.fire()
check(last("time").speed == 0, "time follows the change")
-- Paused, the mod still ticks (OnTickEvenPaused), so the app can unpause.
queue = { { id = "s-2", name = "set_speed", args = { speed = 0 } }, { id = "s-3", name = "set_speed", args = { speed = 3 } },
          { id = "s-4", name = "set_speed", args = { speed = 7 } } }
Events.OnTickEvenPaused.fire()
for _, m in ipairs(sent) do if m.type == "command_result" then results[m.data.id] = m.data end end
check(results["s-2"].ok and #clicked == 2 and clicked[2] ~= "Pause", "pausing while paused doesn't press Pause again (it would toggle)")
check(results["s-3"].ok and clicked[2] == "Fast Forward x 2" and speed == 3, "faster forward from pause")
check(results["s-4"].ok == false, "unknown speeds are refused")
-- The game's pause menu (Esc) is open: no speed changes behind it.
local screen = { inGame = true, visible = true }
function screen:isReallyVisible() return self.visible end
MainScreen = { instance = screen }
now = now + 1000; Events.OnTickEvenPaused.fire()
check(last("time").gameMenuOpen == true, "time: the game's pause menu is open")
queue = { { id = "s-6", name = "set_speed", args = { speed = 1 } } }
Events.OnTickEvenPaused.fire()
for _, m in ipairs(sent) do if m.type == "command_result" then results[m.data.id] = m.data end end
check(results["s-6"].ok == false and results["s-6"].error:find("menu") and speed == 3, "no speed change while the pause menu is open")
screen.visible = false
now = now + 1000; Events.OnTickEvenPaused.fire()
check(last("time").gameMenuOpen == nil, "menu closed: speed buttons usable again")

isClient = function() return true end
queue = { { id = "s-5", name = "set_speed", args = { speed = 1 } } }
Events.OnTickEvenPaused.fire()
for _, m in ipairs(sent) do if m.type == "command_result" then results[m.data.id] = m.data end end
check(results["s-5"].ok == false and results["s-5"].error:find("multiplayer"), "no speed changes in multiplayer, like the game")

print("ALL LUA CHECKS PASSED")
