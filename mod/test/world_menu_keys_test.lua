-- "Here": the world menu's options get a key per object (so the app keeps each card in place), a
-- tray flag for lists of objects (Disassemble), and front for what the interact button acts on.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
UIManager = { getSpeedControls = function() return { getCurrentGameSpeed = function() return 1 end } end }
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

-- World objects: on a square, at an index among its objects.
local function object(x, y, index)
  local square = { getX = function() return x end, getY = function() return y end, getZ = function() return 0 end }
  return { iso = true, getSquare = function() return square end, getObjectIndex = function() return index end }
end
function instanceof(o, class) return type(o) == "table" and o.iso == true and class == "IsoObject" end

local window, curtain, oven, door = object(10, 19, 2), object(10, 19, 3), object(11, 20, 1), object(9, 20, 4)
local counter, toaster, sink = object(11, 20, 2), object(11, 20, 3), object(10, 21, 1)

local square = { getX = function() return 10 end, getY = function() return 20 end, getZ = function() return 0 end }
local doorInFront = nil
local player = { getPlayerNum = function() return 0 end, getCurrentSquare = function() return square end,
  getDir = function() return "N" end, getVehicle = function() return nil end,
  getContextDoorOrWindowOrWindowFrame = function() return doorInFront end }
function isoToScreenX() return 0 end
function isoToScreenY() return 0 end

local prompt = { aPrompt = "Turn on", aParams = { oven } }
function getButtonPrompts()
  local p = { aPrompt = prompt.aPrompt, aParams = prompt.aParams }
  function p:getInteractOptionsButtonObjects() return { items = { window }, isEmpty = function() return false end } end
  return p
end

local function fn() end
ISDisassembleMenu = { disassemble = function() end }
local onWindow, onCurtain, onStove, onDrink, onDoor = fn, function() end, function() end, function() end, function() end

-- A kitchen, the way the game builds it: object submenus, Disassemble with greyed objects, a loose action.
local order = "kitchen"
local function sub(options) return { options = options } end
local function opt(name, f, ...)
  local o = { name = name, onSelect = f, target = "worldobjects" }
  for i, p in ipairs({ ... }) do o["param" .. i] = p end
  return o
end
ISContextManager = { getInstance = function() return { createWorldMenu = function()
  local subs = {}
  local menu = { options = {} }
  local function addSub(name, options)
    subs[name] = sub(options)
    table.insert(menu.options, { name = name, subOption = name })
  end
  function menu:getSubMenu(name) return subs[name] end
  function menu:hideAndChildren() end
  local window = function() addSub("Window", { opt("Open Window", onWindow, window), opt("Close Curtains", onCurtain, curtain) }) end
  local oven = function() addSub("Green Oven", { opt("Turn on", onStove, oven) }) end
  if order == "kitchen" then window() oven() else oven() window() end
  addSub("Chrome Sink", { opt("Drink", onDrink, sink) })
  local dis = { opt("Green Oven", ISDisassembleMenu.disassemble, { object = oven }),
                opt("Rough Wooden Corner Counter", ISDisassembleMenu.disassemble, { object = counter }),
                opt("Chrome Toaster", ISDisassembleMenu.disassemble, { object = toaster }) }
  dis[1].notAvailable, dis[2].notAvailable = true, true
  addSub("Disassemble", dis)
  if doorInFront then addSub("Door", { opt("Open Door", onDoor, door) }) end
  table.insert(menu.options, opt("Sit on ground", fn))
  return menu
end } end }

local B42Menu = require("ZomboidDS/Adapters/B42Menu")
local function byName(options)
  local t = {}
  for _, o in ipairs(options) do t[o.name] = o end
  return t
end

local ok, _, data = B42Menu.openWorld(player)
local o = byName(data.options)
check(ok and o["Window"].key == "10,19,0#2", "an object's card is keyed by where its object is")
check(o["Green Oven"].key == "11,20,0#1" and o["Chrome Sink"].key == "10,21,0#1", "each object its own key")
check(o["Disassemble"].tray and o["Disassemble"].key == "list:Disassemble", "Disassemble is a list of objects: tray")
check(not o["Window"].tray and not o["Chrome Sink"].tray, "an object's actions are not a tray (even on several objects, the curtains)")
check(o["Sit on ground"].key == "name:Sit on ground" and not o["Sit on ground"].tray, "a loose action is keyed by its name")
check(o["Green Oven"].front and not o["Window"].front, "the prompt's object (the stove) is in front")

order = "turned"
local _, _, data2 = B42Menu.openWorld(player)
check(data2.options[1].name == "Green Oven" and data2.options[1].key == o["Green Oven"].key,
  "the game reorders its menu, the keys stay with their objects")

prompt.aPrompt, prompt.aParams = "Open Door", {}
doorInFront = door
local _, _, data3 = B42Menu.openWorld(player)
local o3 = byName(data3.options)
check(o3["Door"].front and not o3["Green Oven"].front, "a door's prompt has no object: the door the game picks is in front")
prompt.aPrompt = nil
local _, _, data4 = B42Menu.openWorld(player)
local anyFront = false
for _, x in ipairs(data4.options) do anyFront = anyFront or x.front end
check(not anyFront, "no prompt: nothing is in front")

-- One object to disassemble is still Disassemble's list.
ISContextManager = { getInstance = function() return { createWorldMenu = function()
  local menu = { options = { { name = "Disassemble", subOption = 1 } } }
  function menu:getSubMenu() return sub({ opt("Chrome Toaster", ISDisassembleMenu.disassemble, { object = toaster }) }) end
  function menu:hideAndChildren() end
  return menu
end } end }
local _, _, data5 = B42Menu.openWorld(player)
check(data5.options[1].tray, "Disassemble with one object is a tray too")
print("ALL LUA CHECKS PASSED")
