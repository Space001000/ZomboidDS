--- Build 42 adapter: the only Lua file that calls the game API.
--- Implements the contract documented in ZomboidDS/Core/Adapters.lua.
---
--- Engine calls go through `try`, so one renamed method in a B42 update degrades one field to nil
--- instead of breaking the whole snapshot. Every call below was checked against 42.20's Lua
--- sources and projectzomboid.jar (2026-09-22).
local Adapters = require("ZomboidDS/Core/Adapters")

local B42 = {
    id = "b42",
    capabilities = { "player", "inventory", "vehicle", "cmd.equip", "cmd.wear", "cmd.unequip", "cmd.drop" },
    dirtyEvents = {
        inventory = { "OnContainerUpdate", "OnRefreshInventoryWindowContainers", "OnClothingUpdated",
                      "OnEquipPrimary", "OnEquipSecondary" },
        vehicle = { "OnEnterVehicle", "OnExitVehicle", "OnSwitchVehicleSeat" },
    },
}

function B42.matches(major, _minor)
    return major == 42
end

-- Helpers --------------------------------------------------------------------

--- obj:method(...), or nil if obj is nil, the method doesn't exist, or it throws.
local function try(obj, method, ...)
    if obj == nil then
        return nil
    end
    local ok, result = pcall(obj[method], obj, ...)
    if ok then
        return result
    end
    return nil
end

local function round(x, digits)
    if type(x) ~= "number" then
        return nil
    end
    local m = 10 ^ (digits or 2)
    return math.floor(x * m + 0.5) / m
end

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

-- Inventory ------------------------------------------------------------------

--- Texture name as the bridge's /icons endpoint knows it (e.g. "Item_Axe").
local function iconName(item)
    local name = try(try(item, "getTex"), "getName")
    if name then
        name = string.gsub(name, "^.*[/\\]", "")
        name = string.gsub(name, "%.png$", "")
        return name
    end
    local scriptIcon = try(try(item, "getScriptItem"), "getIcon")
    if scriptIcon then
        return "Item_" .. scriptIcon
    end
    return nil
end

local function equippedSlot(player, item)
    local primary = try(player, "getPrimaryHandItem")
    local secondary = try(player, "getSecondaryHandItem")
    if item == primary and item == secondary then
        return "both"
    elseif item == primary then
        return "primary"
    elseif item == secondary then
        return "secondary"
    elseif try(player, "isEquippedClothing", item) then
        return "worn"
    end
    return nil
end

local function condition(item)
    if not (instanceof(item, "HandWeapon") or instanceof(item, "Clothing")) then
        return nil
    end
    local max = try(item, "getConditionMax")
    if not max or max <= 0 then
        return nil
    end
    return round((try(item, "getCondition") or 0) / max, 2)
end

--- What the companion app may offer for this item, decided the way the game's own inventory menu
--- does (ISInventoryPaneContextMenu). The app shows exactly these; see protocol/PROTOCOL.md.
local function actionsFor(item, equipped)
    local actions = {}
    local function add(action) actions[#actions + 1] = action end

    if equipped then
        add("unequip")
    else
        local isClothing = try(item, "IsClothing") == true
        local wearable = (isClothing and try(item, "getBodyLocation") ~= nil)
            or (instanceof(item, "InventoryContainer") and try(item, "canBeEquipped") ~= nil)
        if wearable then add("wear") end
        if not isClothing then -- clothes are worn, not held
            if try(item, "isRequiresEquippedBothHands") == true then
                add("equip.both")
            else
                add("equip.primary")
                add(try(item, "isTwoHandWeapon") == true and "equip.both" or "equip.secondary")
            end
        end
    end
    if try(item, "isFavorite") ~= true then add("drop") end -- vanilla won't drop favourites
    return actions
end

local function describeItem(player, item)
    local equipped = equippedSlot(player, item)
    return {
        id = item:getID(),
        type = try(item, "getFullType"),
        name = try(item, "getDisplayName"),
        category = try(item, "getDisplayCategory") or try(item, "getCategory"),
        icon = iconName(item),
        weight = round(try(item, "getActualWeight"), 2),
        condition = condition(item),
        equipped = equipped,
        actions = actionsFor(item, equipped),
    }
end

--- Main inventory only for now. TODO: equipped bags and nearby containers (loot).
function B42.snapshotInventory(player)
    local inventory = player:getInventory()
    local items = inventory:getItems()
    local list = {}
    for i = 0, items:size() - 1 do
        list[#list + 1] = describeItem(player, items:get(i))
    end
    return {
        weight = {
            current = round(try(inventory, "getCapacityWeight"), 2),
            max = round(try(player, "getMaxWeight"), 2),
        },
        items = list,
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
    local item = try(player:getInventory(), "getItemWithIDRecursiv", id)
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

-- UI -------------------------------------------------------------------------

function B42.notify(player, text)
    player:setHaloNote(text, 255, 190, 90, 600) -- orange, ~10 s
end

function B42.hideNativeUI(playerNum)
    for _, getter in ipairs({ getPlayerInventory, getPlayerLoot }) do
        local page = getter and getter(playerNum)
        if page then
            page:setVisible(false)
        end
    end
end

Adapters.register(B42)

return B42
