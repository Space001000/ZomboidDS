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

--- The player's search manager if the game has made one; never creates it (getManager does, and a
--- snapshot runs twice a second). Its functions are called with a dot, not a colon.
local function existingSearchManager(player)
    return ISSearchManager and ISSearchManager.players and ISSearchManager.players[player] or nil
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

--- The game's hotbar for this player (ISHotbar: availableSlot[i], attachedItems[i]), or nil.
local function hotbar(player)
    return getPlayerHotbar and getPlayerHotbar(playerNum(player)) or nil
end

--- The hotbar's slots in its order: { slot, name, item = { name, icon }, inHand }. Slot names as the
--- game shows them (IGUI_HotbarAttachment_<type>, else the slot's own name).
local function hotbarSlots(player)
    local bar = hotbar(player)
    if bar == nil or bar.availableSlot == nil then
        return nil
    end
    local primary, secondary = try(player, "getPrimaryHandItem"), try(player, "getSecondaryHandItem")
    local slots = {}
    for index, slot in ipairs(bar.availableSlot) do
        local name = getTextOrNull and getTextOrNull("IGUI_HotbarAttachment_" .. tostring(slot.slotType)) or nil
        local entry = { slot = index, name = name or tostring(slot.name) }
        local item = bar.attachedItems and bar.attachedItems[index]
        if item ~= nil then
            entry.item = { name = try(item, "getDisplayName"),
                           icon = Util.textureFileName(try(try(item, "getTex"), "getName")) }
            entry.inHand = item == primary or item == secondary
        end
        slots[#slots + 1] = entry
    end
    return slots
end

local function hasHotbarItem(player)
    for _, slot in ipairs(hotbarSlots(player) or {}) do
        if slot.item ~= nil then return true end
    end
    return false
end

--- The watch or clock the game's alarm uses: worn first, then carried (as zombie.ui.Clock looks).
local function alarmClock(player)
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
        if instanceof(item, "AlarmClock") or instanceof(item, "AlarmClockClothing") then
            return item
        end
    end
    return nil
end

--- Nothing queued and not mid-swing: when the game lets its hotbar keys act (ISHotbar:isAllowedToActivateSlot).
local function freeHands(player)
    if isGamePaused() or try(player, "isAttacking") == true then
        return false
    end
    local queue = ISTimedActionQueue and ISTimedActionQueue.queues and ISTimedActionQueue.queues[player]
    return queue == nil or queue.queue == nil or #queue.queue == 0
end

--- The commands, in the order the app offers them when adding. `key`: the game's key binding
--- (its name is the command's name). `available` and `on` get the player; `run` does it.
local COMMANDS = {
    {
        -- One mouse-wheel step, as the game zooms (Core.doZoomScroll; wheel up = -1 = closer). Not
        -- screenZoomIn/Out: in 42.20 those are empty (seen on the Thor: nothing happened).
        id = "zoom_in", key = "Zoom in", icon = "ZoomIn",
        run = function(player) getCore():doZoomScroll(playerNum(player), -1) end,
    },
    {
        id = "zoom_out", key = "Zoom out", icon = "ZoomOut",
        run = function(player) getCore():doZoomScroll(playerNum(player), 1) end,
    },
    {
        id = "search_mode", key = "Toggle Search Mode", icon = "Search_Icon_Off",
        -- ISSearchManager.handleKeyPressed: not while paused
        available = function() return not isGamePaused() and ISSearchManager ~= nil end,
        on = function(player)
            local manager = existingSearchManager(player)
            return manager ~= nil and manager.isSearchMode == true
        end,
        run = function(player)
            -- As the key does: the manager is made on first use.
            local manager = ISSearchManager.getManager(player)
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
        -- The app shows the hotbar and sends the slot to draw; drawing the one in hand puts it away.
        id = "weapons", name = "Weapons", icon = "Item_Axe",
        available = function(player) return hasHotbarItem(player) end,
        run = function(player, args)
            local slot = tonumber(args and args.slot)
            local bar = hotbar(player)
            if slot == nil or bar == nil or bar.attachedItems == nil or bar.attachedItems[slot] == nil then
                error("nothing in that hotbar slot", 0)
            end
            if not freeHands(player) then
                error("busy", 0)
            end
            bar:activateSlot(slot)
        end,
    },
    {
        -- As the game's alarm dialog (ISAlarmClockDialog OK): on/off, hour, minute, then sync.
        id = "alarm", name = "Alarm", icon = "ClockAlarmLargeSet",
        available = function(player) return alarmClock(player) ~= nil end,
        run = function(player, args)
            local clock = alarmClock(player)
            local hour, minute = tonumber(args and args.hour), tonumber(args and args.minute)
            if hour == nil or minute == nil or hour < 0 or hour > 23 or minute < 0 or minute > 59 then
                error("not a time", 0)
            end
            clock:setAlarmSet(args.on ~= false)
            clock:setHour(math.floor(hour))
            clock:setMinute(math.floor(minute))
            if clock.syncAlarmClock then clock:syncAlarmClock() end
        end,
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

--- The alarm time of the player's watch or clock when it's set, as the game's clock finds it.
local function alarm(player)
    local watch = alarmClock(player)
    if watch ~= nil and try(watch, "isAlarmSet") == true then
        local hour, minute = try(watch, "getHour"), try(watch, "getMinute")
        if hour and minute then
            return formatTime(hour, minute)
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
            name = command.key and getText("UI_optionscreen_binding_" .. command.key) or command.name,
            icon = command.icon,
            available = command.available == nil or check(command.available, player) == true,
        }
        if command.on then
            entry.on = check(command.on, player) == true
        end
        table.insert(commands, entry)
    end
    local result = { clock = clock(player), commands = commands, hotbar = hotbarSlots(player) }
    local watch = alarmClock(player)
    if watch ~= nil then
        result.alarmClock = { name = try(watch, "getDisplayName"), hour = try(watch, "getHour"),
                              minute = try(watch, "getMinute"), on = try(watch, "isAlarmSet") == true }
    end
    return result
end

--- Runs a command by id (with its args, e.g. the hotbar slot or the alarm time). Returns true, or false and why not.
function Deck.run(player, id, args)
    local command = BY_ID[id]
    if command == nil then
        return false, "unknown deck command '" .. tostring(id) .. "'"
    end
    if command.available ~= nil and check(command.available, player) ~= true then
        return false, "Can't do that right now"
    end
    local ok, err = pcall(command.run, player, args)
    if not ok then
        local text = tostring(err)
        if text:find("busy", 1, true) then
            return false, "Not while you're busy (finish or cancel what you're doing)"
        elseif text:find("hotbar slot", 1, true) or text:find("not a time", 1, true) then
            return false, "That changed in the game; try again"
        end
        return false, "That didn't work in this game version (" .. tostring(err) .. ")"
    end
    return true
end

return Deck
