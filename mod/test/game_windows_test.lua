-- The controller's Loot/Inventory button (B42.redirectGameWindows) against fakes of the game's
-- button prompt and windows. Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
function instanceof() return false end

local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

local function list(t) return { size = function() return #t end, get = function(_, i) return t[i + 1] end } end
local function container(kind)
  local c = {}
  function c:getType() return kind end
  function c:getItems() return list({}) end
  function c:getCapacityWeight() return 0 end
  return c
end

local mainInventory = container("none")
local drawer = container("drawer")
local player = { getPlayerNum = function() return 0 end, getInventory = function() return mainInventory end }
function getSpecificPlayer() return player end

local lootPage = { backpacks = { { inventory = drawer, name = "Drawer", capacity = 10, onclick = function() end } },
  inventoryPane = { inventory = drawer } }
function getPlayerLoot() return lootPage end
function getPlayerInventory() return { backpacks = { { inventory = mainInventory, name = "Inventory", capacity = 8, onclick = function() end } } } end

-- The game's prompt: its commands open the windows.
local opened = {}
ISButtonPrompt = {}
function ISButtonPrompt:cmdShowLoot() table.insert(opened, "loot") end
function ISButtonPrompt:cmdShowInventory() table.insert(opened, "inventory") end
local prompt = setmetatable({ player = 0 }, { __index = ISButtonPrompt })

-- Opening a car's trunk: the game's door action shows the trunk in its loot window when done.
function isServer() return false end
local trunk = container("TruckBed")
local forced
lootPage.setForceSelectedContainer = function(_, c) forced = c end
local car = { getPartById = function(_, id) return id == "TruckBed" and
    { getItemContainer = function() return trunk end, getIndex = function() return 3 end } or nil end,
  canAccessContainer = function() return true end }
local function door(id) return { getId = function() return id end } end
local walker = { getPlayerNum = function() return 0 end, getVehicle = function() return nil end }
ISOpenVehicleDoor = {}
function ISOpenVehicleDoor:selectContainerInLootWindow() table.insert(opened, "trunk:" .. self.part:getId()) end
local function openDoor(id) return setmetatable({ vehicle = car, part = door(id), character = walker }, { __index = ISOpenVehicleDoor }) end

-- A tap of A at a car door: the prompt's door command on the press, OnUseVehicle on the release.
local now = 0
function getTimestampMs() return now end
local used = {}
VehicleUtils = { OnUseVehicle = function(character, vehicle) table.insert(used, vehicle) end }
local useHandlers = {}
Events = { OnUseVehicle = {
  Add = function(f) useHandlers[f] = true end,
  Remove = function(f) useHandlers[f] = nil end } }
Events.OnUseVehicle.Add(VehicleUtils.OnUseVehicle)
local function fireUse(character, vehicle) for f in pairs(useHandlers) do f(character, vehicle) end end
local doorCommands = {}
function ISButtonPrompt:cmdCloseVehicleDoor(playerObj, part) table.insert(doorCommands, "close") end
function ISButtonPrompt:cmdOpenVehicleDoor(playerObj, part) table.insert(doorCommands, "open") end

local B42 = require("ZomboidDS/Adapters/B42")
local ids = {}
for _, c in ipairs(B42.snapshotContainers(player).containers) do ids[c.name] = c.id end

local shown, handle = {}, true
B42.redirectGameWindows(function(playerNum, show)
  table.insert(shown, show)
  return handle
end)
local closeDoor, openDoorCmd = ISButtonPrompt.cmdCloseVehicleDoor, ISButtonPrompt.cmdOpenVehicleDoor

-- The prompt looks the command up on ISButtonPrompt each time, so the wrapper is what runs.
ISButtonPrompt.cmdShowLoot(prompt)
check(#opened == 0 and shown[1].panel == "inventory" and shown[1].container == ids["Drawer"],
  "Loot shows the container the game's loot window has selected, with the snapshot's id")
ISButtonPrompt.cmdShowInventory(prompt)
check(#opened == 0 and shown[2].container == ids["Inventory"], "Inventory shows the main inventory")

openDoor("TrunkDoor"):selectContainerInLootWindow()
check(#opened == 0 and shown[3].container == require("ZomboidDS/Adapters/B42/Containers").idOf(trunk),
  "opening a trunk shows it in the app instead of the game's loot window, with the snapshot's id")
check(forced == trunk, "and the game's loot window still selects it")
openDoor("DoorFrontLeft"):selectContainerInLootWindow()
check(opened[1] == "trunk:DoorFrontLeft" and #shown == 3, "other doors keep the game's behaviour (a quiet pre-select)")
table.remove(opened, 1)

handle = false
openDoor("DoorRear"):selectContainerInLootWindow()
check(opened[1] == "trunk:DoorRear", "not handled: the game shows its loot window as usual")
table.remove(opened, 1)
ISButtonPrompt.cmdShowLoot(prompt)
check(opened[1] == "loot", "not handled (no app connected): the game's window opens as usual")

handle = nil
B42.redirectGameWindows(function() error("boom") end)
ISButtonPrompt.cmdShowInventory(prompt)
check(opened[2] == "inventory", "installing twice is a no-op, and the first handler still applies")

-- A handler that fails must never leave the player without an inventory.
ISButtonPrompt = {}
function ISButtonPrompt:cmdShowLoot() table.insert(opened, "loot-after-error") end
function ISButtonPrompt:cmdShowInventory() end
B42.redirectGameWindows(function() error("boom") end)
ISButtonPrompt.cmdShowLoot(prompt)
check(opened[3] == "loot-after-error", "an error in the handler falls back to the game's window")
-- Door taps (installed by redirectGameWindows above).
local van = { name = "van" }
local lid = { getVehicle = function() return van end }
local me = { getPlayerNum = function() return 0 end }
now = 1000
closeDoor(prompt, me, lid)
now = 1200
fireUse(me, van)
check(doorCommands[1] == "close" and #used == 0, "a quick tap: the prompt closes the door, the release's toggle is dropped")
now = 3000
fireUse(me, van)
check(#used == 1, "using the car later (E, or a tap with no prompt command): the game's use as usual")
now = 4000
openDoorCmd(prompt, me, lid)
now = 4100
fireUse(me, { name = "other car" })
check(doorCommands[2] == "open" and #used == 2, "another car: not the same tap")
print("ALL LUA CHECKS PASSED")
