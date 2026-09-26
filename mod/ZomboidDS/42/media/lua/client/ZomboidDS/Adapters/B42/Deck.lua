--- The Command deck: the game's clock, and commands the app's Deck can run, each doing what its key
--- binding does in the game (42.20 Lua, checked per command below). Names are the game's own key
--- binding texts, so they follow the player's language.
local Util = require("ZomboidDS/Adapters/B42/Util")
local try = Util.try

local Deck = {}

local function playerNum(player)
    return try(player, "getPlayerNum") or 0
end

--- A light the game's light key would switch: in the hands or attached (belt), as
--- ItemBindingHandler.toggleLight looks for one. Candles and hurricane lanterns are lit by hand.
local function isSwitchableLight(item)
    local kind = try(item, "getType")
    return item ~= nil and try(item, "canEmitLight") == true and kind ~= "CandleLit" and kind ~= "Lantern_HurricaneLit"
end

local function heldLights(player)
    local lights = {}
    -- Secondary first, as the game does; either hand may be empty (so no ipairs over them).
    for _, method in ipairs({ "getSecondaryHandItem", "getPrimaryHandItem" }) do
        local item = try(player, method)
        if isSwitchableLight(item) then
            table.insert(lights, item)
        end
    end
    local attached = try(player, "getAttachedItems")
    for i = 0, (try(attached, "size") or 0) - 1 do
        local item = attached:getItemByIndex(i)
        if isSwitchableLight(item) and not instanceof(item, "HandWeapon") then
            table.insert(lights, item)
        end
    end
    return lights
end

local function hasAnyLight(player)
    if #heldLights(player) > 0 then
        return true
    end
    -- Otherwise the key equips the best light from the inventory.
    local inventory = try(player, "getInventory")
    local found = try(inventory, "getFirstEvalRecurse", function(item)
        return try(item, "canEmitLight") == true and (try(item, "getLightStrength") or 0) > 0
    end)
    return found ~= nil
end

local function searchManager(player)
    return ISSearchManager and try(ISSearchManager, "getManager", player) or nil
end

local function mapVisible()
    return ISWorldMap_instance ~= nil and try(ISWorldMap_instance, "isVisible") == true
end

local function wornBag(player)
    local bag = try(player, "getClothingItem_Back")
    if bag ~= nil and try(bag, "isFavorite") ~= true then
        return bag
    end
    return nil
end

--- The commands, in the order the app offers them when adding. `key`: the game's key binding
--- (its name is the command's name). `available` and `on` get the player; `run` does it.
local COMMANDS = {
    {
        id = "zoom_in", key = "Zoom in", icon = "ZoomIn",
        -- ISPlayerDataObject.onKeyPressed
        run = function() screenZoomIn() end,
    },
    {
        id = "zoom_out", key = "Zoom out", icon = "ZoomOut",
        run = function() screenZoomOut() end,
    },
    {
        id = "search_mode", key = "Toggle Search Mode", icon = "Search_Icon_Off",
        -- ISSearchManager.handleKeyPressed: not while paused
        available = function(player) return not isGamePaused() and searchManager(player) ~= nil end,
        on = function(player)
            local manager = searchManager(player)
            return manager ~= nil and manager.isSearchMode == true
        end,
        run = function(player)
            local manager = searchManager(player)
            manager:toggleSearchMode()
            manager:bringToTop()
        end,
    },
    {
        id = "flashlight", key = "Equip/Turn On/Off Light Source", icon = "Item_Flashlight",
        -- ISLightSourceRadialMenu: a short press calls ItemBindingHandler.toggleLight
        available = function(player) return not isGamePaused() and hasAnyLight(player) end,
        on = function(player)
            for _, light in ipairs(heldLights(player)) do
                if try(light, "isActivated") == true then
                    return true
                end
            end
            return false
        end,
        run = function()
            ItemBindingHandler.toggleLight(getCore():getKey("Equip/Turn On/Off Light Source"))
        end,
    },
    {
        id = "map", key = "Map", icon = "Item_Map",
        -- ISWorldMap.checkKey: allowed by the sandbox, and not opened while paused
        available = function()
            return ISWorldMap ~= nil and ISWorldMap.IsAllowed() and (not isGamePaused() or mapVisible())
        end,
        on = function() return mapVisible() end,
        run = function(player) ISWorldMap.ToggleWorldMap(playerNum(player)) end,
    },
    {
        id = "sit", key = "SitOnGround", icon = "furniture_seating_indoor_01_0_Icon",
        -- ISSitOnGround.lua's key handler: sit down, or get up again
        available = function(player) return try(player, "getVehicle") == nil end,
        on = function(player)
            return try(player, "isSitOnGround") == true or try(player, "isSittingOnFurniture") == true
        end,
        run = function(player)
            if player:isSitOnGround() or try(player, "isSittingOnFurniture") == true then
                player:StopAllActionQueue()
                player:setVariable("forceGetUp", true)
            else
                player:setAutoWalk(false)
                player:reportEvent("EventSitOnGround")
            end
        end,
    },
    {
        id = "drop_bag", key = "DropWornBag", icon = "Item_Backpack",
        -- CFarming_Interact.FastDropItem: the bag on your back, unless it's a favourite
        available = function(player) return wornBag(player) ~= nil end,
        run = function(player) ISInventoryPaneContextMenu.dropItem(wornBag(player), playerNum(player)) end,
    },
    {
        id = "shout", key = "Shout", icon = "Item_Whistle",
        -- ISEmoteRadialMenu.onKeyReleased: a short press shouts, not from a vehicle
        available = function(player) return not isGamePaused() and try(player, "getVehicle") == nil end,
        run = function(player) player:Callout(true) end,
    },
}

local BY_ID = {}
for _, command in ipairs(COMMANDS) do
    BY_ID[command.id] = command
end

local function check(fn, player)
    if fn == nil then
        return nil
    end
    local ok, result = pcall(fn, player)
    return ok and result == true
end

--- 14:25, or 2:25 PM with the game's 12-hour clock option.
local function formatTime(hour, minute)
    if try(getCore(), "getOptionClock24Hour") == false then
        local suffix = hour < 12 and "AM" or "PM"
        local h = hour % 12
        if h == 0 then h = 12 end
        return string.format("%d:%02d %s", h, minute, suffix)
    end
    return string.format("%02d:%02d", hour, minute)
end

--- The alarm of the player's watch or clock, as the game's clock finds it (worn first).
local function alarm(player)
    local candidates = {}
    local worn = try(player, "getWornItems")
    for i = 0, (try(worn, "size") or 0) - 1 do
        table.insert(candidates, worn:getItemByIndex(i))
    end
    local items = try(try(player, "getInventory"), "getItems")
    for i = 0, (try(items, "size") or 0) - 1 do
        table.insert(candidates, items:get(i))
    end
    for _, item in ipairs(candidates) do
        if (instanceof(item, "AlarmClock") or instanceof(item, "AlarmClockClothing")) and try(item, "isAlarmSet") == true then
            local hour, minute = try(item, "getHour"), try(item, "getMinute")
            if hour and minute then
                return formatTime(hour, minute)
            end
        end
    end
    return nil
end

--- What the game's clock (top left) shows, or nil when it doesn't: without a watch there's none.
local function clock(player)
    local gameClock = UIManager and try(UIManager, "getClock") or nil
    if gameClock == nil or try(gameClock, "isVisible") ~= true then
        return nil
    end
    local time = getGameTime()
    local result = { time = formatTime(time:getHour(), time:getMinutes()), alarm = alarm(player) }
    if try(gameClock, "isDateVisible") == true then
        result.date = getText("Sandbox_StartMonth_option" .. (time:getMonth() + 1)) .. " " .. (time:getDay() + 1)
    end
    return result
end

function Deck.snapshot(player)
    local commands = {}
    for _, command in ipairs(COMMANDS) do
        local entry = {
            id = command.id,
            name = getText("UI_optionscreen_binding_" .. command.key),
            icon = command.icon,
            available = command.available == nil or check(command.available, player) == true,
        }
        if command.on then
            entry.on = check(command.on, player) == true
        end
        table.insert(commands, entry)
    end
    return { clock = clock(player), commands = commands }
end

--- Runs a command by id. Returns true, or false and why not.
function Deck.run(player, id)
    local command = BY_ID[id]
    if command == nil then
        return false, "unknown deck command '" .. tostring(id) .. "'"
    end
    if command.available ~= nil and check(command.available, player) ~= true then
        return false, "Can't do that right now"
    end
    local ok, err = pcall(command.run, player)
    if not ok then
        return false, "That didn't work in this game version (" .. tostring(err) .. ")"
    end
    return true
end

return Deck
