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
vehicle = {}
check(B42.commands.world_menu(player, {}) == false, "not in a vehicle (the vehicle has its own menu)")
vehicle = nil

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
