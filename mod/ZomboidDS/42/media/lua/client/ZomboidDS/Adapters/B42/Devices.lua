--- Devices: TVs, radios, walkie-talkies, CD players and car radios, as the game's Device Options
--- window shows them, and its controls.
---
--- 42.21's window (ISRadioWindow) is one window for every device, built from parts (RadioCom/
--- RadioWindowModules): Power (battery) or GridPower, Volume (10 steps, mute, a headphone slot on
--- portable radios), Microphone (two-way), Media (a CD or VHS slot), Channel (radio presets) or
--- ChannelTV. Every way the game opens it goes through ISRadioWindow.activate (Here's Device
--- Options, an item's menu, the car, the controller prompt, clicking the device), so the app takes
--- over there. Each control queues the game's own action, after walking to the device the way the
--- window does (RWMPanel:doWalkTo), or from a distance with a TV remote (canPlayerRemoteInteract).
local Util = require("ZomboidDS/Adapters/B42/Util")
local Items = require("ZomboidDS/Adapters/B42/Items")
local Containers = require("ZomboidDS/Adapters/B42/Containers")
local try, round = Util.try, Util.round

local Devices = {}

local BATTERY, HEADPHONES, EARBUDS = "Base.Battery", "Base.Headphones", "Base.Earbuds"
local REACH = 10 -- ISRadioWindow's `dist`: the window closes once you're further from the device

local open = {} -- playerNum -> { device, kind }: the device the app shows

--- What the window calls the device: "InventoryItem", "IsoObject" or "VehiclePart" (readFromObject).
local function kindOf(device)
    if instanceof(device, "Radio") then return "InventoryItem" end
    if instanceof(device, "IsoWaveSignal") then return "IsoObject" end
    if instanceof(device, "VehiclePart") then return "VehiclePart" end
    return nil
end

local function current(player)
    return open[player:getPlayerNum()]
end

--- Whether the window would still be open (ISRadioWindow:update): an item in your hands or on your
--- back, a car part that still has its radio, an object or car within REACH.
local function stillThere(player, entry)
    local device = entry.device
    if entry.kind == "InventoryItem" then
        return try(player, "getPrimaryHandItem") == device or try(player, "getSecondaryHandItem") == device
            or try(player, "getClothingItem_Back") == device
    end
    if entry.kind == "VehiclePart" then
        local itemType = try(device, "getItemType")
        if itemType ~= nil and not try(itemType, "isEmpty") and try(device, "getInventoryItem") == nil then
            return false
        end
    end
    if try(device, "getSquare") == nil then
        return false
    end
    local dx, dy = player:getX() - device:getX(), player:getY() - device:getY()
    return dx > -REACH and dx < REACH and dy > -REACH and dy < REACH
end

--- The device is gone (ISRadioWindow:update closing): a radio you put away is switched off, so it
--- doesn't run its battery down in your bag.
local function forget(player, entry)
    open[player:getPlayerNum()] = nil
    if entry.kind == "InventoryItem" then
        local data = try(entry.device, "getDeviceData")
        if data ~= nil then
            data:setIsTurnedOn(false)
            entry.device:setActivated(false)
        end
    end
end

--- RWMPanel:doWalkTo: true when the player can act now (queueing a walk to an object first).
local function walkTo(player, entry)
    if entry.kind == "InventoryItem" then
        return true
    elseif entry.kind == "IsoObject" then
        local square = try(entry.device, "getSquare")
        return square ~= nil and luautils.walkAdj(player, square, true)
    elseif entry.kind == "VehiclePart" then
        return try(player, "getVehicle") == try(entry.device, "getVehicle")
    end
    return false
end

--- Power, volume and channels also work from a distance with a TV remote in hand, as in the window.
local function reach(player, entry, data)
    return try(data, "canPlayerRemoteInteract", player) == true or walkTo(player, entry)
end

local function radioAction(mode, player, entry, value)
    ISTimedActionQueue.add(ISRadioAction:new(mode, player, entry.device, value))
end

--- The parameter ISDeviceBatteryAction / ISDeviceMediaAction find the device by.
local function deviceParameter(player, entry)
    return ISDeviceBatteryAction:getDeviceDataParameter(player, entry.device, entry.kind)
end

-- Reading the device -----------------------------------------------------------------

local function icon(player, entry)
    local device = entry.device
    if entry.kind == "InventoryItem" then
        return Items.describe(player, device, true).icon
    end
    if entry.kind == "VehiclePart" then
        local item = try(device, "getInventoryItem")
        return item and Items.describe(player, item, false).icon or nil
    end
    -- A radio put down is an invisible IsoRadio next to its world item (ISDropWorldItemAction).
    local itemId = try(device, "hasModData") and device:getModData().RadioItemID or nil
    if type(itemId) == "number" then
        local objects = try(try(device, "getSquare"), "getWorldObjects")
        for i = 0, (objects and objects:size() or 0) - 1 do
            local item = try(objects:get(i), "getItem")
            if item ~= nil and item:getID() == itemId then
                return Items.describe(player, item, false).icon
            end
        end
    end
    local sprite = try(try(device, "getSprite"), "getName")
    return sprite and (sprite .. "_Icon") or nil
end

local function where(player, entry)
    if entry.kind == "VehiclePart" then return "vehicle" end
    if entry.kind == "IsoObject" then
        local itemId = try(entry.device, "hasModData") and entry.device:getModData().RadioItemID or nil
        return type(itemId) == "number" and "floor" or nil
    end
    if try(player, "getClothingItem_Back") == entry.device then return "back" end
    return "hand"
end

local function channelName(frequency)
    local radio = getZomboidRadio and getZomboidRadio() or nil
    return radio and try(radio, "getChannelName", frequency) or nil
end

local function presets(data)
    local list = {}
    local all = try(try(data, "getDevicePresets"), "getPresets")
    for i = 0, (all and all:size() or 0) - 1 do
        local preset = all:get(i)
        list[#list + 1] = { name = try(preset, "getName"), frequency = try(preset, "getFrequency") }
    end
    return list
end

local function headphoneSlot(data)
    return try(data, "getIsPortable") == true and try(data, "getIsTelevision") ~= true
end

--- What fits in the device: tapes or CDs of its kind, batteries, headphones or earbuds.
local function fits(data, item)
    local fullType = try(item, "getFullType")
    if fullType == BATTERY then
        return try(data, "getIsBatteryPowered") == true and "battery" or nil
    end
    if fullType == HEADPHONES or fullType == EARBUDS then
        return headphoneSlot(data) and "headphones" or nil
    end
    local mediaType = try(data, "getMediaType") or -1
    if mediaType >= 0 and try(item, "isRecordedMedia") == true and try(item, "getMediaType") == mediaType then
        return "media"
    end
    return nil
end

--- The drawer: what fits, on the player (inventory and bags), then per container around. Same items
--- (batteries, earbuds) are one row with a count; a battery row puts in the fullest.
local function drawer(player, data)
    local groups = {}
    local function collect(containers, name, onYou)
        local rows, byKey = {}, {}
        local function walk(container)
            local items = container:getItems()
            for i = 0, items:size() - 1 do
                local item = items:get(i)
                local kind = fits(data, item)
                if kind ~= nil then
                    local described = Items.describe(player, item, onYou)
                    local key = kind == "media" and item:getID() or try(item, "getFullType")
                    local row = byKey[key]
                    if row == nil then
                        row = { id = described.id, name = described.name, icon = described.icon, fits = kind, count = 0, charge = -1 }
                        byKey[key] = row
                        rows[#rows + 1] = row
                    end
                    row.count = row.count + 1
                    local charge = kind == "battery" and (try(item, "getCurrentUsesFloat") or 0) or 0
                    if charge > row.charge then
                        row.charge, row.id = charge, described.id
                    end
                end
                local inner = onYou and instanceof(item, "InventoryContainer") and try(item, "getInventory") or nil
                if inner ~= nil then
                    walk(inner)
                end
            end
        end
        for _, container in ipairs(containers) do
            walk(container)
        end
        for _, row in ipairs(rows) do
            row.charge = nil
            if row.count == 1 then row.count = nil end
        end
        if #rows > 0 then
            groups[#groups + 1] = { name = name, onYou = onYou or nil, items = rows }
        end
    end
    collect({ player:getInventory() }, nil, true)
    for _, entry in ipairs(Containers.nearby()) do
        collect({ entry.container }, entry.name, false)
    end
    return groups
end

--- The device as the window shows it, only with the parts it has.
function Devices.state(player)
    local entry = current(player)
    if entry == nil then
        return false, "No device open"
    end
    if not stillThere(player, entry) then
        forget(player, entry)
        return true, nil, { gone = true }
    end
    local data = try(entry.device, "getDeviceData")
    if data == nil then
        return false, "That's not a device"
    end
    local tv = try(data, "getIsTelevision") == true
    local batteryPowered = try(data, "getIsBatteryPowered") == true
    local frequency = try(data, "getChannel")
    local state = {
        name = try(data, "getDeviceName"),
        icon = icon(player, entry),
        where = where(player, entry),
        on = try(data, "getIsTurnedOn") == true,
        tv = tv or nil,
        volume = round(try(data, "getDeviceVolume") or 0, 1),
        channel = frequency,
        channelName = frequency and channelName(frequency) or nil,
        presets = presets(data),
        drawer = drawer(player, data),
    }
    if batteryPowered then
        state.battery = { level = round(try(data, "getPower") or 0, 2), has = try(data, "getHasBattery") == true }
    else
        -- RWMGridPower: "Has power." / "No power."
        state.grid = { power = try(data, "canBePoweredHere") == true,
            text = getText(try(data, "canBePoweredHere") and "IGUI_RadioPowerNearby" or "IGUI_RadioRequiresPowerNearby") }
    end
    if headphoneSlot(data) then
        local kind = try(data, "getHeadphoneType") or -1
        state.headphones = { slot = true }
        if kind >= 0 then
            local item = try(ScriptManager.instance, "getItem", kind == 0 and HEADPHONES or EARBUDS)
            local iconName = try(item, "getIcon")
            state.headphones.name = try(item, "getDisplayName")
            state.headphones.icon = iconName and ("Item_" .. iconName) or nil
        end
    end
    if not tv then
        state.range = { min = try(data, "getMinChannelRange"), max = try(data, "getMaxChannelRange") }
        state.maxPresets = try(try(data, "getDevicePresets"), "getMaxPresets")
    end
    if try(data, "getIsTwoWay") == true then
        state.twoWay = { range = try(data, "getTransmitRange"), micMuted = try(data, "getMicIsMuted") == true }
    end
    local mediaType = try(data, "getMediaType") or -1
    if mediaType >= 0 then
        local media = { type = mediaType == 1 and "vhs" or "cd", playing = try(data, "isPlayingMedia") == true or nil,
            empty = getText(mediaType == 1 and "IGUI_media_notape" or "IGUI_media_nocd") }
        if try(data, "hasMedia") then
            media.name = try(try(data, "getMediaData"), "getTranslatedItemDisplayName")
        end
        state.media = media
    end
    return true, nil, state
end

-- Controls ------------------------------------------------------------------------------

--- An item from the drawer into the player's inventory first, as the window's media slot does
--- (RWMMedia:addMediaAux): walk to it when it's on the floor, then the game's transfer.
local function fetch(player, item)
    local worldItem = try(item, "getWorldItem")
    if worldItem ~= nil and not luautils.walkAdj(player, worldItem:getSquare(), true) then
        return false
    end
    ISInventoryPaneContextMenu.transferIfNeeded(player, item)
    return true
end

local controls = {}

function controls.power(player, entry, data)
    if not reach(player, entry, data) then return false, "Can't get to it" end
    radioAction("ToggleOnOff", player, entry)
    return true
end

function controls.volume(player, entry, data, args)
    local volume = tonumber(args.value)
    if volume == nil then return false, "No volume" end
    if not reach(player, entry, data) then return false, "Can't get to it" end
    radioAction("SetVolume", player, entry, math.max(0, math.min(1, volume)))
    return true
end

function controls.mute(player, entry, data)
    if not reach(player, entry, data) then return false, "Can't get to it" end
    radioAction("MuteVolume", player, entry, true)
    return true
end

--- RWMVolume:onSpeakerButton: back to the volume it had, or one step.
function controls.unmute(player, entry, data, args)
    local volume = tonumber(args.value)
    if not reach(player, entry, data) then return false, "Can't get to it" end
    radioAction("UnMuteVolume", player, entry, (volume and volume > 0) and volume or 0.1)
    return true
end

--- A channel (a frequency). A playing tape or CD keeps the TV from picking up channels, so it stops
--- first: picking a channel means watching it.
function controls.channel(player, entry, data, args)
    local frequency = tonumber(args.value)
    if frequency == nil then return false, "No channel" end
    if not reach(player, entry, data) then return false, "Can't get to it" end
    if try(data, "isPlayingMedia") then
        radioAction("TogglePlayMedia", player, entry)
    end
    radioAction("SetChannel", player, entry, frequency)
    return true
end

function controls.play(player, entry, data)
    if not reach(player, entry, data) then return false, "Can't get to it" end
    radioAction("TogglePlayMedia", player, entry)
    return true
end

function controls.eject(player, entry, data)
    if not try(data, "hasMedia") then return false, "Nothing in it" end
    if not walkTo(player, entry) then return false, "Can't get to it" end
    ISTimedActionQueue.add(ISDeviceMediaAction:new(player, true, nil, deviceParameter(player, entry)))
    return true
end

function controls.battery_out(player, entry, data)
    if not try(data, "getHasBattery") then return false, "No battery in it" end
    if not walkTo(player, entry) then return false, "Can't get to it" end
    ISTimedActionQueue.add(ISDeviceBatteryAction:new(player, true, nil, deviceParameter(player, entry)))
    return true
end

function controls.headphones_out(player, entry, data)
    if (try(data, "getHeadphoneType") or -1) < 0 then return false, "No headphones in it" end
    if not walkTo(player, entry) then return false, "Can't get to it" end
    radioAction("RemoveHeadphones", player, entry)
    return true
end

--- Something from the drawer goes in; what's in that slot comes out first.
function controls.insert(player, entry, data, args)
    local item = Containers.findItem(player, tonumber(args.itemId))
    local kind = item and fits(data, item) or nil
    if kind == nil then return false, "That doesn't fit" end
    if not fetch(player, item) then return false, "Can't get to it" end
    if not walkTo(player, entry) then return false, "Can't get to it" end
    local parameter = deviceParameter(player, entry)
    if kind == "media" then
        if try(data, "hasMedia") then
            ISTimedActionQueue.add(ISDeviceMediaAction:new(player, true, nil, parameter))
        end
        ISTimedActionQueue.add(ISDeviceMediaAction:new(player, false, item, parameter))
    elseif kind == "battery" then
        if try(data, "getHasBattery") then
            ISTimedActionQueue.add(ISDeviceBatteryAction:new(player, true, nil, parameter))
        end
        ISTimedActionQueue.add(ISDeviceBatteryAction:new(player, false, item, parameter))
    else
        if (try(data, "getHeadphoneType") or -1) >= 0 then
            radioAction("RemoveHeadphones", player, entry)
        end
        radioAction("AddHeadphones", player, entry, item)
    end
    return true
end

function controls.mic(player, entry, data, args)
    if try(data, "getIsTwoWay") ~= true then return false, "No microphone" end
    if not walkTo(player, entry) then return false, "Can't get to it" end
    radioAction("MuteMicrophone", player, entry, args.value == true)
    return true
end

--- A new preset from the number pad, named after the station the game knows there (RWMChannel's
--- add-preset panel asks for a name; there's no keyboard here), or the frequency.
function controls.add_preset(player, entry, data, args)
    local frequency = tonumber(args.value)
    local list = try(data, "getDevicePresets")
    local min, max = try(data, "getMinChannelRange"), try(data, "getMaxChannelRange")
    if frequency == nil or list == nil or min == nil or max == nil or frequency < min or frequency > max then
        return false, "Out of range"
    end
    local all = try(list, "getPresets")
    if all ~= nil and all:size() >= (try(list, "getMaxPresets") or 0) then
        return false, "No room for another preset"
    end
    for i = 0, (all and all:size() or 0) - 1 do
        if try(all:get(i), "getFrequency") == frequency then
            return true -- already there
        end
    end
    local name = channelName(frequency) or (tostring(round(frequency / 1000, 1)) .. " MHz")
    list:addPreset(name, frequency)
    try(data, "transmitPresets")
    return true
end

Devices.commands = {
    device_state = function(player, _args)
        return Devices.state(player)
    end,
    device_close = function(player, _args)
        open[player:getPlayerNum()] = nil
        return true
    end,
    device_control = function(player, args)
        local entry = current(player)
        if entry == nil then
            return false, "No device open"
        end
        if not stillThere(player, entry) then
            forget(player, entry)
            return false, "No device open"
        end
        local data = try(entry.device, "getDeviceData")
        local control = controls[tostring(args.control)]
        if data == nil or control == nil then
            return false, "Unknown control"
        end
        return control(player, entry, data, args)
    end,
}

--- The game's Device Options window opens the app's device panel instead, while the app is there.
function Devices.redirect(onShow)
    if ISRadioWindow == nil or ISRadioWindow.activate == nil or ISRadioWindow.zomboidDSRedirect then
        return
    end
    ISRadioWindow.zomboidDSRedirect = true
    local original = ISRadioWindow.activate
    ISRadioWindow.activate = function(player, device, ...)
        local kind = kindOf(device)
        local playerNum = player:getPlayerNum()
        local previous = open[playerNum]
        -- Known before the app asks for it.
        open[playerNum] = kind and { device = device, kind = kind } or previous
        local ok, handled = pcall(function()
            return kind ~= nil and onShow(playerNum, { panel = "device" })
        end)
        if ok and handled then
            -- What activate does besides the window: opening Device Options ends exercising.
            player:setVariable("ExerciseStarted", false)
            player:setVariable("ExerciseEnded", true)
            return nil
        end
        open[playerNum] = previous
        return original(player, device, ...)
    end
end

return Devices
