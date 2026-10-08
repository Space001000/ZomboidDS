--- Build 42 adapter: this file and the modules next to it (B42Menu.lua, B42/) are the only Lua that
--- calls the game API. Implements the contract documented in ZomboidDS/Core/Adapters.lua.
---
--- Each area has its own module in B42/ (items, containers, health, moodles, time, Here, crafting, building, deck); this file
--- puts them together, with the player, vehicle and item commands. Engine calls go through Util.try
--- (see there). Every call was checked against 42.20's Lua sources and projectzomboid.jar.
local Adapters = require("ZomboidDS/Core/Adapters")
local B42Menu = require("ZomboidDS/Adapters/B42Menu")
local Util = require("ZomboidDS/Adapters/B42/Util")
local Items = require("ZomboidDS/Adapters/B42/Items")
local Containers = require("ZomboidDS/Adapters/B42/Containers")
local Health = require("ZomboidDS/Adapters/B42/Health")
local Moodles = require("ZomboidDS/Adapters/B42/Moodles")
local Time = require("ZomboidDS/Adapters/B42/Time")
local Here = require("ZomboidDS/Adapters/B42/Here")
local Deck = require("ZomboidDS/Adapters/B42/Deck")
local Crafting = require("ZomboidDS/Adapters/B42/Crafting")
local Building = require("ZomboidDS/Adapters/B42/Building")
local Map = require("ZomboidDS/Adapters/B42/Map")
local try, round = Util.try, Util.round

local B42 = {
    id = "b42",
    capabilities = { "player", "inventory", "vehicle", "cmd.equip", "cmd.wear", "cmd.unequip", "cmd.drop", "item_menu",
                     "containers", "transfer", "time", "world_menu", "here", "select_container", "health", "moodles", "craft", "build", "deck", "map" },
    dirtyEvents = {
        inventory = { "OnContainerUpdate", "OnRefreshInventoryWindowContainers", "OnClothingUpdated",
                      "OnEquipPrimary", "OnEquipSecondary" },
        vehicle = { "OnEnterVehicle", "OnExitVehicle", "OnSwitchVehicleSeat" },
        -- The game refreshes its container lists when you move or turn (OnRefreshInventoryWindowContainers).
        containers = { "OnContainerUpdate", "OnRefreshInventoryWindowContainers" },
    },
}

function B42.matches(major, _minor)
    return major == 42
end

B42.snapshotInventory = Items.snapshotInventory
B42.snapshotContainers = Containers.snapshot
B42.reachableContainer = Containers.reachable
B42.snapshotHealth = Health.snapshot
B42.snapshotMoodles = Moodles.snapshot
B42.snapshotTime = Time.snapshot
B42.snapshotHere = Here.snapshot
B42.snapshotDeck = Deck.snapshot
B42.snapshotMap = Map.snapshot
B42.snapshotBuilding = Building.snapshot

-- Player ---------------------------------------------------------------------

--- 42.20 only has stats:get(CharacterStat.X); the per-stat getters (getHunger, ...) are gone.
--- The getter fallback is for earlier B42 builds that still had them.
local function stat(stats, getter, enumName)
    local okEnum, key = pcall(function() return CharacterStat[enumName] end)
    if okEnum and key ~= nil then
        local value = try(stats, "get", key)
        if value ~= nil then
            return value
        end
    end
    return try(stats, getter)
end

function B42.snapshotPlayer(player)
    local body = try(player, "getBodyDamage")
    local stats = try(player, "getStats")
    return {
        health = round(try(body, "getOverallBodyHealth"), 1),
        bleeding = (try(body, "getNumPartsBleeding") or 0) > 0,
        stats = {
            hunger = round(stat(stats, "getHunger", "HUNGER"), 3),
            thirst = round(stat(stats, "getThirst", "THIRST"), 3),
            fatigue = round(stat(stats, "getFatigue", "FATIGUE"), 3),
            endurance = round(stat(stats, "getEndurance", "ENDURANCE"), 3),
        },
    }
end

-- Vehicle --------------------------------------------------------------------

function B42.isInVehicle(player)
    return try(player, "getVehicle") ~= nil
end

function B42.snapshotVehicle(player)
    local vehicle = try(player, "getVehicle")
    if vehicle == nil then
        return { inVehicle = false }
    end

    local gasTank = try(vehicle, "getPartById", "GasTank")
    local capacity = try(gasTank, "getContainerCapacity")
    local amount = try(gasTank, "getContainerContentAmount")
    local fuel = nil
    if capacity and amount and capacity > 0 then
        fuel = round(amount / capacity, 3)
    end

    local scriptName = try(try(vehicle, "getScript"), "getName")
    return {
        inVehicle = true,
        name = scriptName and getText("IGUI_VehicleName" .. scriptName) or "Vehicle",
        speedKmh = round(math.abs(try(vehicle, "getCurrentSpeedKmHour") or 0), 1),
        engineRunning = try(vehicle, "isEngineRunning") == true,
        fuel = fuel,
        isDriver = try(vehicle, "isDriver", player) == true,
    }
end

-- Commands -------------------------------------------------------------------
-- These use the vanilla context-menu helpers, which queue the proper timed actions (animations,
-- moving the item out of a bag first, ...) exactly like clicking in the inventory would.
-- Signatures as in 42.20's ISInventoryPaneContextMenu.lua. Note they can silently do nothing:
-- unequipItem ignores items that aren't equipped, onDropItems skips favourites. So "ok" means
-- "queued"; the next inventory update shows what actually happened.

local function findItem(player, args)
    local id = tonumber(args.itemId)
    if id == nil then
        return nil, "missing itemId"
    end
    local item = Items.inInventory(player, id)
    if item == nil then
        return nil, "item not found"
    end
    return item
end

local function contextMenu(functionName, ...)
    local fn = ISInventoryPaneContextMenu and ISInventoryPaneContextMenu[functionName]
    if fn == nil then
        return false, functionName .. " is not available in this build"
    end
    fn(...)
    return true
end

--- Wraps a handler so it receives the resolved item instead of raw args.
local function withItem(handler)
    return function(player, args)
        local item, err = findItem(player, args)
        if item == nil then
            return false, err
        end
        return handler(player, item, args)
    end
end

B42.commands = {
    -- The app's Deck is open (on = true, repeated every few seconds) or closed.
    watch_here = function(_player, args)
        Here.watch(args.on ~= false)
        return true
    end,

    -- The game's speed buttons (see B42/Time.lua).
    set_speed = function(_player, args)
        return Time.setSpeed(tonumber(args.speed))
    end,

    -- The Command deck: zoom, search mode, flashlight, ... as their key bindings (see B42/Deck.lua).
    deck_run = function(player, args)
        return Deck.run(player, tostring(args.id), args)
    end,

    -- The game's own right-click menu for an item (see B42Menu.lua): for items you carry, and for
    -- items in containers within reach, which get the loot window's menu (Grab, and options like
    -- Read or Eat that take the item first).
    -- `itemIds`: several items picked together (the first leads); or one `itemId`.
    item_menu = function(player, args)
        local ids = type(args.itemIds) == "table" and args.itemIds or { args.itemId }
        local items = {}
        for _, id in ipairs(ids) do
            local item = Containers.findItem(player, tonumber(id))
            if item == nil then
                return false, "item not found"
            end
            items[#items + 1] = item
        end
        if #items == 0 then
            return false, "item not found"
        end
        return B42Menu.open(player, items)
    end,

    -- The game's treatment menu for a body part from `health` (see B42Menu.openHealth).
    health_menu = function(player, args)
        return B42Menu.openHealth(player, tostring(args.part))
    end,

    -- The game's world menu for where the player stands (see B42Menu.openWorld).
    world_menu = function(player, _args)
        return B42Menu.openWorld(player)
    end,

    menu_select = function(player, args)
        local ok, reason = B42Menu.select(player, args, function(id) return Containers.findItem(player, id) end)
        Here.lookAgainSoon() -- whatever ran may change what's here (a door opens)
        return ok, reason
    end,

    equip = withItem(function(player, item, args)
        local slot = args.slot or "primary"
        local playerNum = player:getPlayerNum()
        if slot == "both" then
            return contextMenu("equipWeapon", item, true, true, playerNum)
        elseif slot == "secondary" then
            return contextMenu("equipWeapon", item, false, false, playerNum)
        elseif slot == "primary" then
            return contextMenu("equipWeapon", item, true, false, playerNum)
        end
        return false, "bad slot '" .. tostring(slot) .. "'"
    end),

    wear = withItem(function(player, item)
        return contextMenu("onWearItems", { item }, player:getPlayerNum())
    end),

    unequip = withItem(function(player, item)
        return contextMenu("unequipItem", item, player:getPlayerNum())
    end),

    drop = withItem(function(player, item)
        return contextMenu("onDropItems", { item }, player:getPlayerNum())
    end),
}

-- Moving items between containers, crafting and building (see B42/Containers.lua, B42/Crafting.lua,
-- B42/Building.lua).
for _, module in ipairs({ Containers, Crafting, Building }) do
    for name, command in pairs(module.commands) do
        B42.commands[name] = command
    end
end

-- UI -------------------------------------------------------------------------

function B42.notify(player, text)
    player:setHaloNote(text, 255, 190, 90, 600) -- orange, ~10 s
end

-- The game's inventory windows ---------------------------------------------------
-- With a controller the game keeps its inventory and loot windows hidden until the player presses
-- Y: "Loot" when a container is in front of them (ISButtonPrompt.cmdShowLoot), "Inventory"
-- otherwise (cmdShowInventory). Both make the window visible and give it the controller's focus.
-- We wrap those two so the app can show the container on the bottom screen instead. Hidden windows
-- keep working for us: the engine updates every window it knows about, visible or not (42.20
-- UIManager.updateUIElements), so their container lists still follow the player.

--- Calls `onShow(playerNum, { panel = "inventory", container = id })` when the player asks the game
--- for its inventory or loot window. If it returns true, the game's window stays closed.
--- The trunk the game would show after opening this door, with the checks of 42.20's
--- ISOpenVehicleDoor:selectContainerInLootWindow; nil for other doors (those only pre-select a
--- seat's container without showing the window).
local function trunkOpenedBy(action)
    if isServer() or action.vehicle == nil or action.part == nil or action.character:getVehicle() ~= nil then
        return nil
    end
    local id = action.part:getId()
    if id ~= "TrunkDoor" and id ~= "DoorRear" then
        return nil
    end
    local bed = action.vehicle:getPartById("TruckBed")
    if bed == nil or bed:getItemContainer() == nil or not action.vehicle:canAccessContainer(bed:getIndex(), action.character) then
        return nil
    end
    return bed:getItemContainer()
end

--- Opening a trunk (A at the trunk, or the vehicle menu) shows the trunk in the game's loot window
--- when the action is done (ISOpenVehicleDoor:selectContainerInLootWindow). Like Loot, it goes to
--- `onShow` instead.
local function redirectTrunk(onShow)
    if ISOpenVehicleDoor == nil or ISOpenVehicleDoor.selectContainerInLootWindow == nil or ISOpenVehicleDoor.zomboidDSRedirect then
        return
    end
    ISOpenVehicleDoor.zomboidDSRedirect = true
    local original = ISOpenVehicleDoor.selectContainerInLootWindow
    ISOpenVehicleDoor.selectContainerInLootWindow = function(self, ...)
        local ok, handled = pcall(function()
            local trunk = trunkOpenedBy(self)
            if trunk == nil then
                return false
            end
            local playerNum = self.character:getPlayerNum()
            if not onShow(playerNum, { panel = "inventory", container = Containers.idOf(trunk) }) then
                return false
            end
            -- The game's loot window still selects it, for when the player opens that. Its
            -- container list catches up with the open trunk at the next containers snapshot
            -- (Containers.lua, refreshHiddenWindows).
            getPlayerLoot(playerNum):setForceSelectedContainer(trunk, 100)
            return true
        end)
        if ok and handled then
            return
        end
        return original(self, ...)
    end
end

function B42.redirectGameWindows(onShow)
    redirectTrunk(onShow)
    if ISButtonPrompt == nil or ISButtonPrompt.zomboidDSRedirect then
        return
    end
    ISButtonPrompt.zomboidDSRedirect = true
    local function wrap(original, containerFor)
        return function(self, ...)
            local ok, handled = pcall(function()
                local container = containerFor(self.player)
                return onShow(self.player, { panel = "inventory", container = container and Containers.idOf(container) or nil })
            end)
            if ok and handled then
                return
            end
            return original(self, ...)
        end
    end
    ISButtonPrompt.cmdShowLoot = wrap(ISButtonPrompt.cmdShowLoot, Containers.selectedLoot)
    ISButtonPrompt.cmdShowInventory = wrap(ISButtonPrompt.cmdShowInventory, function(playerNum)
        local player = getSpecificPlayer(playerNum)
        return player and player:getInventory() or nil
    end)
end

Adapters.register(B42)

return B42