-- Devices (B42/Devices.lua): TVs, radios, walkie-talkies and CD players as the game's Device
-- Options window shows them, its controls, the drawer of things that fit, and Device Options opening
-- the app's panel. Fakes stand in for the game's DeviceData, the action queue and walking.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
function instanceof(obj, class) return type(obj) == "table" and obj.classes ~= nil and obj.classes[class] == true end
function getText(key)
  return ({ IGUI_RadioPowerNearby = "Has power.", IGUI_RadioRequiresPowerNearby = "No power.",
    IGUI_media_notape = "- NO TAPE -", IGUI_media_nocd = "- NO DISC -" })[key] or key
end
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end
local function list(t) return { size = function() return #t end, get = function(_, i) return t[i + 1] end } end

-- What the player queued: walks, transfers and the game's device actions, in order.
local queued = {}
local walkable = true
luautils = { walkAdj = function(_, square) table.insert(queued, "walk " .. square.name) return walkable end }
ISTimedActionQueue = { add = function(action) table.insert(queued, action.label) end }
ISRadioAction = { new = function(_, mode, _, _, value)
  return { label = mode .. (value ~= nil and (" " .. (type(value) == "table" and value.name or tostring(value))) or "") }
end }
ISDeviceMediaAction = { new = function(_, _, remove, item) return { label = remove and "EjectMedia" or ("InsertMedia " .. item.name) } end }
ISDeviceBatteryAction = { new = function(_, _, remove, item) return { label = remove and "RemoveBattery" or ("AddBattery " .. item.name) } end,
  getDeviceDataParameter = function() return "param" end }
ISInventoryPaneContextMenu = { transferIfNeeded = function(_, item) table.insert(queued, "transfer " .. item.name) end }
getZomboidRadio = function() return { getChannelName = function(_, f)
  return ({ [200] = "Triple-N", [201] = "WBLN News", [93200] = "LBMW - Kentucky Radio", [101200] = "KnoxTalk Radio" })[f] end } end
ScriptManager = { instance = { getItem = function(_, fullType)
  local names = { ["Base.Headphones"] = { "Headphones", "Headphones" }, ["Base.Earbuds"] = { "Earbuds", "Earbuds" } }
  local n = names[fullType]
  return n and { getDisplayName = function() return n[1] end, getIcon = function() return n[2] end }
end } }

-- Items
local nextId = 100
local function item(o)
  nextId = nextId + 1
  local it = { classes = { InventoryItem = true }, name = o.name, id = o.id or nextId }
  function it:getID() return self.id end
  function it:getFullType() return o.type end
  function it:getName() return o.name end
  function it:getDisplayName() return o.name end
  function it:getScriptItem() return { getIcon = function() return o.icon end } end
  function it:isRecordedMedia() return o.media ~= nil end
  function it:getMediaType() return o.media end
  function it:getCurrentUsesFloat() return o.charge or 0 end
  function it:getWorldItem() return o.world end
  return it
end
local tape1 = item({ name = "VHS: Pleistocene Land", type = "Base.VHS_Retail", icon = "Cassette3", media = 1 })
local tape2 = item({ name = "VHS: Eagle Down", type = "Base.VHS_Retail", icon = "Cassette3", media = 1 })
local cd = item({ name = "CD: Over", type = "Base.Disc_Retail", icon = "Disc", media = 0 })
local weak = item({ name = "Battery", type = "Base.Battery", icon = "Battery", charge = 0.2 })
local full = item({ name = "Battery", type = "Base.Battery", icon = "Battery", charge = 0.9 })
local buds = item({ name = "Earbuds", type = "Base.Earbuds", icon = "Earbuds" })
local axe = item({ name = "Axe", type = "Base.Axe", icon = "Axe" })
local shelfTape = item({ name = "VHS: Home Invaders 2", type = "Base.VHS_Retail", icon = "Cassette3", media = 1 })
local shelfBattery = item({ name = "Battery", type = "Base.Battery", icon = "Battery", charge = 1 })

local function container(items, name)
  return { name = name, getItems = function() return list(items) end, getType = function() return name end,
    getItemWithIDRecursiv = function(_, id) for _, it in ipairs(items) do if it:getID() == id then return it end end end }
end
local bagInv = container({ tape2, full })
local bag = item({ name = "Backpack", type = "Base.Bag_Schoolbag", icon = "Bag" })
bag.classes.InventoryContainer = true
bag.getInventory = function() return bagInv end
local inventory = container({ tape1, cd, weak, buds, axe, bag })
local baseLookup = inventory.getItemWithIDRecursiv
inventory.getItemWithIDRecursiv = function(self, id) return baseLookup(self, id) or bagInv:getItemWithIDRecursiv(id) end
local shelves = container({ shelfTape, shelfBattery }, "Shelves")

local hand
local player = { classes = {}, getPlayerNum = function() return 0 end, getInventory = function() return inventory end,
  getX = function() return 10 end, getY = function() return 10 end,
  getPrimaryHandItem = function() return hand end, getSecondaryHandItem = function() return nil end,
  getClothingItem_Back = function() return nil end, getVehicle = function() return nil end, vars = {} }
function player:setVariable(name, value) self.vars[name] = value end

-- The game's loot window lists the shelves (Containers.nearby comes from its snapshot).
getPlayerInventory = function() return { backpacks = { { inventory = inventory, name = "Inventory", onclick = function() end } } } end
getPlayerLoot = function() return { backpacks = { { inventory = shelves, name = "Shelves", onclick = function() end } } } end

-- Devices: DeviceData with what the window reads.
local function deviceData(o)
  local d = { on = o.on or false, volume = o.volume or 0.5, channel = o.channel, media = o.media, playing = o.playing,
    headphones = o.headphones or -1, presets = o.presets or {}, power = o.power, hasBattery = o.hasBattery, mic = false }
  function d:getDeviceName() return o.name end
  function d:getIsTurnedOn() return self.on end
  function d:getIsTelevision() return o.tv == true end
  function d:getIsBatteryPowered() return o.battery == true end
  function d:getPower() return self.power end
  function d:getHasBattery() return self.hasBattery end
  function d:canBePoweredHere() return o.grid == true end
  function d:getDeviceVolume() return self.volume end
  function d:getChannel() return self.channel end
  function d:getMinChannelRange() return o.min end
  function d:getMaxChannelRange() return o.max end
  function d:getIsTwoWay() return o.twoWay == true end
  function d:getTransmitRange() return o.transmit end
  function d:getMicIsMuted() return self.mic end
  function d:getIsPortable() return o.portable == true end
  function d:getHeadphoneType() return self.headphones end
  function d:getMediaType() return o.mediaType or -1 end
  function d:hasMedia() return self.media ~= nil end
  function d:isPlayingMedia() return self.playing == true end
  function d:getMediaData() return self.media and { getTranslatedItemDisplayName = function() return self.media end } end
  function d:canPlayerRemoteInteract() return false end
  function d:transmitPresets() self.transmitted = true end
  function d:setIsTurnedOn(on) self.on = on end
  local presetList = {}
  for _, p in ipairs(d.presets) do presetList[#presetList + 1] = { getName = function() return p[1] end, getFrequency = function() return p[2] end } end
  d.presetList = presetList
  function d:getDevicePresets()
    return { getPresets = function() return list(presetList) end, getMaxPresets = function() return o.maxPresets or 10 end,
      addPreset = function(_, name, f) presetList[#presetList + 1] = { getName = function() return name end, getFrequency = function() return f end } end }
  end
  return d
end

local tvData = deviceData({ name = "ValuTech Television", tv = true, grid = true, on = true, channel = 200, mediaType = 1,
  media = "VHS: Breaking Points", playing = true, presets = { { "Triple-N", 200 }, { "WBLN News", 201 } } })
local tv = { classes = { IsoWaveSignal = true }, getDeviceData = function() return tvData end,
  getSquare = function() return { name = "tv square" } end, getX = function() return 12 end, getY = function() return 10 end,
  hasModData = function() return false end, getSprite = function() return { getName = function() return "appliances_television_01_0" end } end }

local radioData = deviceData({ name = "ValuTech Radio", battery = true, power = 0.634, hasBattery = true, on = true,
  channel = 93200, min = 88000, max = 108000, portable = true, presets = { { "LBMW - Kentucky Radio", 93200 } }, maxPresets = 2 })
local radio = item({ name = "ValuTech Radio", type = "Base.RadioBlack", icon = "RadioBlack" })
radio.classes.Radio = true
radio.getDeviceData = function() return radioData end

local walkieData = deviceData({ name = "Walkie Talkie", battery = true, power = 0.4, hasBattery = true, on = true,
  channel = 107600, min = 25000, max = 300000, portable = true, twoWay = true, transmit = 2000 })
local walkie = item({ name = "Walkie Talkie", type = "Base.WalkieTalkie3", icon = "WalkieTalkieCivilian2" })
walkie.classes.Radio = true
walkie.getDeviceData = function() return walkieData end
walkie.setActivated = function(self, on) self.activated = on end

local B42 = require("ZomboidDS/Adapters/B42")
B42.snapshotContainers(player)

-- Device Options opens the app's panel while the app is there
local shown, gameWindow = nil, nil
local appThere = true
ISRadioWindow = { activate = function(_, device) gameWindow = device return "window" end }
B42.redirectGameWindows(function(_, show) if not appThere then return false end shown = show return true end)
check(not B42.commands.device_state(player, {}), "no device open: nothing to show")
ISRadioWindow.activate(player, tv, true)
check(shown and shown.panel == "device" and gameWindow == nil, "Device Options: the app's panel, the game's window stays shut")
check(player.vars.ExerciseStarted == false and player.vars.ExerciseEnded == true, "opening Device Options ends exercising, as the game's does")

-- A TV with a tape playing
local ok, _, s = B42.commands.device_state(player, {})
check(ok and s.name == "ValuTech Television" and s.tv and s.on, "the TV, on")
check(s.icon == "appliances_television_01_0_Icon", "a TV's icon is its sprite's")
check(s.grid.power and s.grid.text == "Has power." and s.battery == nil, "mains power: the game's \"Has power.\"")
check(s.media.type == "vhs" and s.media.name == "VHS: Breaking Points" and s.media.playing, "the tape in it, playing")
check(#s.presets == 2 and s.presets[1].name == "Triple-N" and s.channel == 200 and s.channelName == "Triple-N", "the TV's channels")
check(s.range == nil and s.headphones == nil and s.twoWay == nil, "no tuning range, headphone slot or microphone on a TV")
local d = s.drawer
check(#d == 2 and d[1].onYou and d[2].name == "Shelves", "drawer: on you, then the shelves")
check(#d[1].items == 2 and d[1].items[1].name == "VHS: Pleistocene Land" and d[1].items[2].name == "VHS: Eagle Down",
  "only tapes fit a VCR, from the bags too; no CD, battery or axe")
check(d[2].items[1].name == "VHS: Home Invaders 2" and #d[2].items == 1, "tapes on the shelves")

-- Picking a channel while the tape plays stops the tape first
queued = {}
check(B42.commands.device_control(player, { control = "channel", value = 201 }), "tune to WBLN News")
check(queued[1] == "walk tv square" and queued[2] == "TogglePlayMedia" and queued[3] == "SetChannel 201", "walks over, stops the tape, tunes in")

-- Another tape: the one in comes out first; from the shelves it's taken to you first
queued = {}
check(B42.commands.device_control(player, { control = "insert", itemId = shelfTape:getID() }), "insert a tape from the shelves")
check(queued[1] == "transfer VHS: Home Invaders 2" and queued[3] == "EjectMedia" and queued[4] == "InsertMedia VHS: Home Invaders 2",
  "taken to you, the old tape out, the new one in")
check(not B42.commands.device_control(player, { control = "insert", itemId = cd:getID() }), "a CD doesn't fit a VCR")
queued = {}
B42.commands.device_control(player, { control = "volume", value = 1.7 })
check(queued[2] == "SetVolume 1", "volume clamped to the game's 0..1")

-- Walking away closes it, like the game's window
tv.getX = function() return 30 end
local okGone, _, gone = B42.commands.device_state(player, {})
check(okGone and gone.gone, "out of reach: the panel is told it's gone")
check(not B42.commands.device_state(player, {}), "and it's closed")

-- A radio in your hand on batteries
hand = radio
ISRadioWindow.activate(player, radio)
local _, _, r = B42.commands.device_state(player, {})
check(r.battery.level == 0.63 and r.battery.has and r.grid == nil, "battery level")
check(r.where == "hand" and r.icon == "Item_RadioBlack", "in your hand, the item's icon")
check(r.range.min == 88000 and r.range.max == 108000 and r.maxPresets == 2, "its tuning range")
check(r.headphones.slot and r.headphones.name == nil, "a headphone slot, empty")
check(r.media == nil and r.tv == nil, "no media slot on this radio")
local rd = r.drawer[1].items
check(#rd == 2 and rd[1].fits == "battery" and rd[1].count == 2 and rd[1].id == full:getID(), "batteries: one row, the fullest goes in")
check(rd[2].fits == "headphones" and rd[2].name == "Earbuds", "earbuds fit the headphone slot")
check(r.drawer[2].items[1].fits == "battery", "a battery on the shelves too")

queued = {}
B42.commands.device_control(player, { control = "insert", itemId = full:getID() })
check(queued[2] == "RemoveBattery" and queued[3] == "AddBattery Battery", "a new battery: the old one out first")
queued = {}
B42.commands.device_control(player, { control = "insert", itemId = buds:getID() })
check(queued[2] == "AddHeadphones Earbuds", "earbuds in")

-- Presets from the number pad
check(B42.commands.device_control(player, { control = "add_preset", value = 101200 }), "save 101.2 as a preset")
check(radioData.presetList[2].getName() == "KnoxTalk Radio" and radioData.transmitted, "named after the station the game knows there")
radioData.presetList[2] = nil
check(B42.commands.device_control(player, { control = "add_preset", value = 105500 }), "a frequency with no station")
check(radioData.presetList[2].getName() == "105.5 MHz", "named by its frequency")
check(not B42.commands.device_control(player, { control = "add_preset", value = 106000 }), "no room past the radio's maximum")
check(not B42.commands.device_control(player, { control = "add_preset", value = 120000 }), "outside the range: refused")

-- A walkie-talkie: the microphone
hand = walkie
ISRadioWindow.activate(player, walkie)
local _, _, w = B42.commands.device_state(player, {})
check(w.twoWay and w.twoWay.range == 2000 and w.twoWay.micMuted == false, "two-way: transmit range, microphone on")
queued = {}
B42.commands.device_control(player, { control = "mic", value = true })
check(queued[1] == "MuteMicrophone true", "mute the microphone")
hand = nil
check(select(3, B42.commands.device_state(player, {})).gone, "put away: gone, like the game's window closes")
check(walkieData.on == false and walkie.activated == false, "and switched off, as the game does, so it doesn't drain in a bag")

-- A car radio: open while near the car, in it or not; gone once the radio is taken out
local carData = deviceData({ name = "Car Radio", battery = false, grid = true, on = true, channel = 93200, min = 88000, max = 108000 })
local car = { name = "car" }
local carRadioItem = item({ name = "Radio", type = "Base.RadioCar", icon = "Radio" })
local part = { classes = { VehiclePart = true }, getDeviceData = function() return carData end, getVehicle = function() return car end,
  getSquare = function() return { name = "car square" } end, getX = function() return 14 end, getY = function() return 10 end,
  getItemType = function() return { isEmpty = function() return false end } end, getInventoryItem = function() return carRadioItem end }
ISRadioWindow.activate(player, part)
check(select(3, B42.commands.device_state(player, {})).where == "vehicle", "the car radio, from outside the car")
check(not B42.commands.device_control(player, { control = "power" }), "its buttons need you in the car, as in the game")
part.getInventoryItem = function() return nil end
check(select(3, B42.commands.device_state(player, {})).gone, "radio taken out of the car: gone")

-- Without the app: the game's own window
appThere = false
ISRadioWindow.activate(player, tv)
check(gameWindow == tv, "without the app: the game's window")
print("ALL LUA CHECKS PASSED")
