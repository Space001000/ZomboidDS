-- The Command deck (B42/Deck.lua): the game's clock, and its commands run the way their key
-- bindings do. Fakes stand in for the game's globals, the player and its items.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

local calls = {}
local function called(name) table.insert(calls, name) end
local paused = false
local clock24 = true
local clockVisible, dateVisible = true, true

function isGamePaused() return paused end
function getText(key)
  local texts = {
    ["UI_optionscreen_binding_Zoom in"] = "Zoom In",
    ["UI_optionscreen_binding_Toggle Search Mode"] = "Toggle Search Mode",
    ["Sandbox_StartMonth_option7"] = "July",
  }
  return texts[key] or key
end
local zoomed = {}
function getCore() return {
  doZoomScroll = function(_, playerIndex, step) table.insert(zoomed, playerIndex .. ":" .. step) end,
  getOptionClock24Hour = function() return clock24 end,
  getKey = function(_, name) return name == "Equip/Turn On/Off Light Source" and 33 or 0 end,
} end
function getGameTime() return {
  getHour = function() return 14 end, getMinutes = function() return 5 end,
  getMonth = function() return 6 end, getDay = function() return 8 end,
} end
UIManager = { getClock = function() return {
  isVisible = function() return clockVisible end, isDateVisible = function() return dateVisible end,
} end }
-- Empty in 42.20: the Deck must not rely on them.
function screenZoomIn() end
function screenZoomOut() end

local search = { isSearchMode = false }
function search:toggleSearchMode() self.isSearchMode = not self.isSearchMode; called("toggleSearch") end
function search:bringToTop() end
-- Like the game's: players[character] only once made; getManager(character) (a dot call) makes it.
local made = 0
ISSearchManager = { players = {} }
function ISSearchManager.getManager(character)
  assert(character ~= ISSearchManager, "getManager called with a colon: the manager table became the character")
  if not ISSearchManager.players[character] then made = made + 1; ISSearchManager.players[character] = search end
  return ISSearchManager.players[character]
end

local lightKey
ItemBindingHandler = { toggleLight = function(key) lightKey = key; called("toggleLight") end }
ISWorldMap = { IsAllowed = function() return true end, ToggleWorldMap = function(n) called("map" .. n) end }
local dropped
ISInventoryPaneContextMenu = { dropItem = function(item, n) dropped = item; called("drop" .. n) end }

-- Items: a lit flashlight in the hands, a watch with its alarm set, a bag on the back.
local classes = {}
function instanceof(obj, class) return classes[obj] == class end
local flashlight = {
  getType = function() return "Torch" end, canEmitLight = function() return true end,
  isActivated = function() return true end,
}
local watch = { isAlarmSet = function() return true end, getHour = function() return 7 end, getMinute = function() return 0 end }
classes[watch] = "AlarmClockClothing"
local bag = { isFavorite = function() return false end }
local function list(items) return { size = function() return #items end, getItemByIndex = function(_, i) return items[i + 1] end,
  get = function(_, i) return items[i + 1] end } end
local backBag = bag
local player = {
  getPlayerNum = function() return 0 end,
  getSecondaryHandItem = function() return nil end,
  getPrimaryHandItem = function() return flashlight end,
  getAttachedItems = function() return list({}) end,
  getInventory = function() return { getItems = function() return list({}) end, getFirstEvalRecurse = function() return nil end } end,
  getWornItems = function() return list({ watch }) end,
  getClothingItem_Back = function() return backBag end,
  getVehicle = function() return nil end,
  isSitOnGround = function() return false end,
}

local Deck = require("ZomboidDS/Adapters/B42/Deck")

-- The snapshot ----------------------------------------------------------------------------------
local snap = Deck.snapshot(player)
check(made == 0, "a snapshot never makes a search manager (the game's getManager does)")
check(snap.clock.time == "14:05" and snap.clock.date == "July 9" and snap.clock.alarm == "07:00",
  "the clock as the game shows it: time, date, and the watch's alarm")
clock24 = false
check(Deck.snapshot(player).clock.time == "2:05 PM" and Deck.snapshot(player).clock.alarm == "7:00 AM",
  "the game's 12-hour clock option")
clock24 = true
dateVisible = false
check(Deck.snapshot(player).clock.date == nil, "no date when the game's clock doesn't show it")
clockVisible = false
check(Deck.snapshot(player).clock == nil, "no clock without one showing (no watch)")
clockVisible, dateVisible = true, true

local byId = {}
for _, c in ipairs(snap.commands) do byId[c.id] = c end
check(byId.zoom_in.name == "Zoom In" and byId.zoom_in.icon == "ZoomIn" and byId.zoom_in.on == nil,
  "a one-off command: the game's own name, an icon, no on/off")
check(byId.search_mode.on == false and byId.flashlight.on == true, "modes say whether they're on")
check(byId.drop_bag.available == true, "a bag on the back can be dropped")
for _, id in ipairs({ "zoom_in", "zoom_out", "search_mode", "flashlight", "map", "sit", "drop_bag", "shout" }) do
  check(byId[id] ~= nil, "offers " .. id)
end

-- Running ---------------------------------------------------------------------------------------
check(Deck.run(player, "zoom_in") == true and zoomed[#zoomed] == "0:-1", "zoom in: one wheel step closer for this player")
check(Deck.run(player, "zoom_out") == true and zoomed[#zoomed] == "0:1", "zoom out: one wheel step away")
check(Deck.run(player, "search_mode") == true and search.isSearchMode == true and made == 1,
  "search mode switches on (the manager is made on first use, as the key does)")
check(Deck.snapshot(player).commands[3].on == true, "... and the next snapshot says so")
check(Deck.run(player, "flashlight") == true and lightKey == 33, "flashlight: the light key's own toggle, with its key")
check(Deck.run(player, "map") == true and calls[#calls] == "map0", "map: the game's toggle for this player")
check(Deck.run(player, "drop_bag") == true and dropped == bag, "drop bag: the worn bag")

paused = true
local ok, why = Deck.run(player, "search_mode")
check(ok == false and why == "Can't do that right now", "search mode waits while paused, like its key")
check(Deck.run(player, "zoom_out") == true, "zoom still works while paused")
paused = false

backBag = nil
check(Deck.snapshot(player).commands[7].available == false and Deck.run(player, "drop_bag") == false,
  "no bag on the back: nothing to drop")
ok, why = Deck.run(player, "fly")
check(ok == false and why:find("unknown") ~= nil, "unknown commands are refused")

ISWorldMap.ToggleWorldMap = function() error("renamed in a later build") end
ok, why = Deck.run(player, "map")
check(ok == false and why:find("didn't work") ~= nil, "a game function that fails is reported, not thrown")

-- Weapons: the game's hotbar ----------------------------------------------------------------
ISWorldMap.ToggleWorldMap = function(n) called("map" .. n) end
local axe = { getDisplayName = function() return "Axe" end, getTex = function() return { getName = function() return "media/textures/Item_Axe.png" end } end }
local crowbar = { getDisplayName = function() return "Crowbar" end, getTex = function() return { getName = function() return "Item_Crowbar" end } end }
local activated = {}
local bar = {
  availableSlot = { { slotType = "Back", name = "Back" }, { slotType = "SmallBeltLeft", name = "Belt Left" }, { slotType = "SmallBeltRight", name = "Belt Right" } },
  attachedItems = { [1] = axe, [2] = crowbar },
}
function bar:activateSlot(i) table.insert(activated, i) end
function getPlayerHotbar(n) return n == 0 and bar or nil end
function getTextOrNull(key) return key == "IGUI_HotbarAttachment_Back" and "Back" or nil end
player.getPrimaryHandItem = function() return axe end
local queue = {}
ISTimedActionQueue = { queues = { [player] = { queue = queue } } }

local deck = Deck.snapshot(player)
check(#deck.hotbar == 3 and deck.hotbar[1].name == "Back" and deck.hotbar[2].name == "Belt Left",
  "the hotbar's slots in its order, named as the game names them")
check(deck.hotbar[1].item.name == "Axe" and deck.hotbar[1].item.icon == "Item_Axe" and deck.hotbar[1].inHand == true,
  "a slot's item, and whether it's in hand")
check(deck.hotbar[3].item == nil, "an empty slot")
check(Deck.run(player, "weapons", { slot = 2 }) == true and activated[1] == 2, "drawing a slot: the hotbar's own activateSlot")
table.insert(queue, "walking")
ok, why = Deck.run(player, "weapons", { slot = 1 })
check(ok == false and why:find("busy") and #activated == 1, "not while an action is queued, like the hotbar keys")
table.remove(queue)
ok, why = Deck.run(player, "weapons", { slot = 3 })
check(ok == false and #activated == 1, "an empty slot does nothing")
bar.attachedItems = {}
check(Deck.run(player, "weapons", { slot = 1 }) == false, "nothing on the hotbar: nothing to draw")

-- Alarm: the watch's alarm, as the game's alarm dialog sets it ------------------------------
local synced = false
watch.setAlarmSet = function(self, on) self.set = on end
watch.setHour = function(self, h) self.h = h end
watch.setMinute = function(self, m) self.m = m end
watch.syncAlarmClock = function() synced = true end
local alarmInfo = Deck.snapshot(player).alarmClock
check(alarmInfo ~= nil and alarmInfo.hour == 7 and alarmInfo.minute == 0 and alarmInfo.on == true, "the watch's alarm: time and on/off")
check(Deck.run(player, "alarm", { hour = 6, minute = 30, on = true }) == true and watch.h == 6 and watch.m == 30
  and watch.set == true and synced, "setting it: on, hour, minute, then synced like the game's dialog")
check(Deck.run(player, "alarm", { hour = 25, minute = 0 }) == false, "not a time: refused")
check(Deck.run(player, "alarm", { hour = 6, minute = 30, on = false }) == true and watch.set == false, "switching it off")
