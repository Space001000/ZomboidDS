-- "Here": the game's world menu for where the player stands (B42Menu.openWorld), against fakes of
-- the controller's button prompt and the game's context manager. Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

local now = 0
function getTimestampMs() return now end
function instanceof() return false end
local paused = false
UIManager = { getSpeedControls = function() return { getCurrentGameSpeed = function() return paused and 0 or 1 end } end }

local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

local square = { getX = function() return 10 end, getY = function() return 20 end, getZ = function() return 0 end }
local dir, vehicle = "N", nil
local player = { getPlayerNum = function() return 0 end, getCurrentSquare = function() return square end,
  getDir = function() return dir end, getVehicle = function() return vehicle end, getInventory = function() return {} end }
function isoToScreenX(_, x) return x * 100 end
function isoToScreenY(_, _, y) return y * 100 end

-- The prompt collects what's in front of the player; the world menu is built from those objects.
local chair = { name = "chair" }
local nearby = { chair }
local function luaList(t) return { items = t, isEmpty = function() return #t == 0 end } end
function getButtonPrompts() return { getInteractOptionsButtonObjects = function(_, d) return luaList(nearby) end } end

local calls, built = {}, nil
ISContextManager = { getInstance = function() return { createWorldMenu = function(playerNum, object, objects, x, y)
  built = { objects = objects, x = x, y = y }
  local sit = { name = "Sit on chair", target = objects[1], onSelect = function(target) table.insert(calls, "sit " .. target.name) end,
    iconTexture = { getName = function() return "media/textures/Furniture_Chair_01.png" end } }
  local menu = { options = { sit }, hidden = false }
  function menu:hideAndChildren() self.hidden = true end
  built.menu = menu
  return menu
end } end }

local B42 = require("ZomboidDS/Adapters/B42")

local ok, _, data = B42.commands.world_menu(player, {})
check(ok and data.options[1].name == "Sit on chair", "the world menu for what's in front of the player")
check(data.options[1].icon == "Furniture_Chair_01", "an option's icon goes along as a texture name")
check(built.objects[1] == chair and built.x == 1000 and built.y == 2000, "built from the prompt's objects, at the player's screen position")
check(built.menu.hidden, "hidden again right away, never shown on the top screen")
check(B42.commands.menu_select(player, { menuId = data.menuId, optionId = "1" }) == true and calls[1] == "sit chair",
  "choosing an option runs it like in the game")

local _, _, data2 = B42.commands.world_menu(player, {})
dir = "E"
local ok2, reason2 = B42.commands.menu_select(player, { menuId = data2.menuId, optionId = "1" })
check(ok2 == false and reason2:find("moved") and #calls == 1, "after turning or moving, the old menu doesn't run")

nearby = {}
check(B42.commands.world_menu(player, {}) == false, "nothing in front of the player: no menu")
nearby = { chair }
paused = true
local ok3, reason3 = B42.commands.world_menu(player, {})
check(ok3 == false and reason3:find("paused"), "no world menu while paused, like the game")
paused = false
-- In a vehicle: the game's vehicle radial menu instead, recorded, without its sound or focus grab.
local car = { getScript = function() return { getName = function() return "CarNormal" end } end }
function getText(key) return key == "IGUI_VehicleNameCarNormal" and "Chevalier Dart" or key end
local sounds, focus = 0, 0
function getSoundManager() return { playUISound = function() sounds = sounds + 1 end } end
function setJoypadFocus() focus = focus + 1 end
local realRadial = { addSlice = function() error("the real radial menu must not be filled") end }
function getPlayerRadialMenu() return realRadial end
JoypadState = { players = { { id = "pad" } } }
local ran = {}
ISVehicleMenu = { onToggleHeadlights = function(p) table.insert(ran, "lights") end,
                  onExit = function(p) table.insert(ran, "exit") end }
function ISVehicleMenu.showRadialMenu(playerObj)
  local menu = getPlayerRadialMenu(playerObj:getPlayerNum())
  menu:clear()
  if menu:isReallyVisible() then menu:undisplay() return end
  menu:setX(menu:getWidth() / 2)
  menu:addSlice("Headlights On", { getName = function() return "media/ui/vehicles/vehicle_lightsON.png" end },
    ISVehicleMenu.onToggleHeadlights, playerObj)
  menu:addSlice("Not tired enough", nil, nil, playerObj, vehicle)
  menu:addSlice("Exit Vehicle", nil, ISVehicleMenu.onExit, playerObj)
  menu:addToUIManager()
  getSoundManager():playUISound("UIVehicleMenuOpen")
  menu.sounds.undisplay = "UIVehicleMenuClose"
  if JoypadState.players[playerObj:getPlayerNum() + 1] then setJoypadFocus(playerObj:getPlayerNum(), menu) end
end
local realSounds, realFocus = getSoundManager, setJoypadFocus

vehicle = car
local okV, _, dataV = B42.commands.world_menu(player, {})
check(okV and dataV.options[1].name == "Chevalier Dart" and #dataV.options[1].children == 3,
  "in a vehicle: one card named after it, with the vehicle menu's slices")
local slices = dataV.options[1].children
check(slices[1].name == "Headlights On" and slices[1].enabled and slices[1].icon == "vehicle_lightsON",
  "a slice: the game's text and icon")
check(slices[2].enabled == false and slices[2].tooltip == "Not tired enough", "a slice without a function: greyed, with its reason")
check(sounds == 0 and focus == 0, "no menu sound, no controller focus grab while recording it")
check(getSoundManager == realSounds and setJoypadFocus == realFocus and getPlayerRadialMenu() == realRadial
  and JoypadState.players[1].id == "pad", "the game's functions and controller state are put back")
square = { getX = function() return 11 end, getY = function() return 20 end, getZ = function() return 0 end }
check(B42.commands.menu_select(player, { menuId = dataV.menuId, optionId = slices[1].id }) == true and ran[1] == "lights",
  "choosing a slice runs it, even though the car moved")
local _, _, dataV2 = B42.commands.world_menu(player, {})
vehicle = nil
local okV2, reasonV2 = B42.commands.menu_select(player, { menuId = dataV2.menuId, optionId = "1.3" })
check(okV2 == false and reasonV2:find("no longer") and #ran == 1, "after getting out, the car's menu doesn't run")

-- "Here" follows the player while the app watches -----------------------------------
local builds = 0
local create = ISContextManager.getInstance().createWorldMenu
ISContextManager = { getInstance = function() return { createWorldMenu = function(...) builds = builds + 1 return create(...) end } end }
local gameMenu = { visible = false }
function gameMenu:isVisible() return self.visible end
function getPlayerContextMenu() return gameMenu end
local doing = false
ISTimedActionQueue = { isPlayerDoingAction = function() return doing end }

check(B42.snapshotHere(player).watching == false and builds == 0, "not watched: nothing is built")
B42.commands.watch_here(player, { on = true })
local first = B42.snapshotHere(player)
check(first.watching and first.options[1].name == "Sit on chair" and builds == 1, "watched: the menu for where you stand")
now = now + 200
check(B42.snapshotHere(player) == first and builds == 1, "standing still: nothing rebuilt")
dir = "S"
local turned = B42.snapshotHere(player)
check(builds == 2 and turned.menuId == first.menuId, "turning rebuilds; the same options keep their id (not re-sent)")
gameMenu.visible = true
dir = "W"
B42.snapshotHere(player)
check(builds == 2, "never while the game's own menu is open on the top screen (it would close it)")
gameMenu.visible = false
B42.snapshotHere(player)
check(builds == 3, "rebuilt once the game's menu is closed")
doing = true
now = now + 200; B42.snapshotHere(player)
doing = false
now = now + 200; B42.snapshotHere(player)
check(builds == 4, "rebuilt when the player's action finishes (the door is open now)")
local sel = B42.snapshotHere(player)
B42.commands.menu_select(player, { menuId = sel.menuId, optionId = "1" })
now = now + 500; B42.snapshotHere(player)
check(builds == 5, "rebuilt shortly after an option from the app ran")
now = now + 3000; B42.snapshotHere(player)
check(builds == 6, "and every few seconds as a fallback")
now = now + 11000
check(B42.snapshotHere(player).watching == false, "watching expires unless the app repeats it (app closed or crashed)")
B42.commands.watch_here(player, { on = true })
B42.commands.watch_here(player, { on = false })
check(B42.snapshotHere(player).watching == false, "the app stops watching when the Deck closes")
print("ALL LUA CHECKS PASSED")
