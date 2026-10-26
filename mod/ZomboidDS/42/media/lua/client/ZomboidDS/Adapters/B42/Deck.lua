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

local function itemIcon(item)
    return Util.textureFileName(try(try(item, "getTex"), "getName"))
end

--- The player's timed actions aren't done (something queued or running).
local function busy(player)
    local queue = ISTimedActionQueue and ISTimedActionQueue.queues and ISTimedActionQueue.queues[player]
    return queue ~= nil and queue.queue ~= nil and #queue.queue > 0
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
            entry.item = { name = try(item, "getDisplayName"), icon = itemIcon(item) }
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

local function onHotbar(player, item)
    local bar = hotbar(player)
    for _, attached in pairs(bar and bar.attachedItems or {}) do
        if attached == item then return true end
    end
    return false
end

--- The weapons in the player's hands, wherever they came from (the hotbar or the inventory; not a
--- light or a bag): { primary = { name, icon, onHotbar }, secondary = ..., bothHands }; a
--- two-handed grip is primary only.
local function hands(player)
    local primary, secondary = try(player, "getPrimaryHandItem"), try(player, "getSecondaryHandItem")
    local function entry(item)
        if item == nil or not instanceof(item, "HandWeapon") then return nil end
        return { name = try(item, "getDisplayName"), icon = itemIcon(item), onHotbar = onHotbar(player, item) }
    end
    local main = entry(primary)
    if main ~= nil and primary == secondary then
        return { primary = main, bothHands = true }
    end
    return { primary = main, secondary = entry(secondary) }
end

--- The held items the game's "Drop Both Held Items" key drops: secondary first, never a favourite
--- (CFarming_Interact.FastDropItem).
local function droppableHeld(player)
    local items = {}
    for _, method in ipairs({ "getSecondaryHandItem", "getPrimaryHandItem" }) do
        local item = try(player, method)
        -- A two-handed grip is the same item in both hands: once.
        if item ~= nil and item ~= items[1] and try(item, "isFavorite") ~= true then
            table.insert(items, item)
        end
    end
    return items
end

--- What Drop held dropped, per player, to pick up again: { item, primary, twoHands }. Forgotten when
--- the game reloads its Lua (leaving the game), and item by item once it's no longer on the floor.
local dropped = {}
local PICK_UP_RANGE = 3 -- tiles: one this close offers to pick them up again
local FETCH_RANGE = 10 -- tiles: then it fetches all of them this close (dropped while running, they spread out)
-- The drop is timed actions (per item: unequip, then onto the floor; longer for heavy items, and
-- one item after the other): until it lands the item is in the hands or the inventory. It's
-- remembered while those actions run (the player's action queue isn't empty), at most a minute.
local LANDING_MS = 60000

--- What Pick up again fetches: once one dropped item lies within PICK_UP_RANGE, all of them on the
--- floor within FETCH_RANGE, nearest first; each { item, primary, twoHands, square }. Otherwise none.
--- Also forgets what's no longer on the floor (or on its way down).
local function pickUpTargets(player)
    local nearby, close = {}, false
    local range = FETCH_RANGE
    local kept = {}
    for _, entry in ipairs(dropped[player] or {}) do
        local world = try(entry.item, "getWorldItem")
        local square = world and try(world, "getSquare")
        if square ~= nil then
            entry.landsBy = nil -- landed
            entry.square = square
            table.insert(kept, entry)
            local dx, dy = square:getX() - player:getX(), square:getY() - player:getY()
            entry.distance = dx * dx + dy * dy
            if square:getZ() == math.floor(player:getZ()) and entry.distance <= range * range then
                table.insert(nearby, entry)
                close = close or entry.distance <= PICK_UP_RANGE * PICK_UP_RANGE
            end
        end
    end
    table.sort(nearby, function(a, b) return a.distance < b.distance end)
    -- Keep remembering items that are still on their way down.
    for _, entry in ipairs(dropped[player] or {}) do
        if entry.landsBy and try(entry.item, "getWorldItem") == nil and busy(player) and getTimestampMs() < entry.landsBy then
            table.insert(kept, entry)
        end
    end
    dropped[player] = kept
    return close and nearby or {}
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
    return not isGamePaused() and try(player, "isAttacking") ~= true and not busy(player)
end

--- The emote wheel's groups and their emotes in the order the game defines them
--- (ISEmoteRadialMenu:init); its tables keep no order, so these come first and any others after.
local EMOTE_GROUPS = { "friendly", "hostile", "group", "signal" }
local EMOTE_ORDER = {
    friendly = { "wavehi", "wavebye", "clap", "thumbsup", "thankyou" },
    hostile = { "insult", "stop", "surrender", "thumbsdown" },
    group = { "followme", "comehere", "yes", "no", "shrug", "undecided" },
    signal = { "ceasefire", "signalok", "moveout", "freeze", "followbehind", "signalfire", "comefront", "salute" },
}

--- A texture's path under media/ui ("emotes/thumbsup"), the icon endpoint's name for it.
local function uiIcon(texture)
    local path = try(texture, "getName")
    if type(path) ~= "string" then return nil end
    path = string.gsub(path, "\\", "/")
    path = string.gsub(path, "^.*media/ui/", "")
    return (string.gsub(path, "%.png$", ""))
end

--- The wheel's menu (made on first use, as the wheel does when it opens).
local function emoteMenu()
    if ISEmoteRadialMenu == nil then return nil end
    if ISEmoteRadialMenu.menu == nil and ISEmoteRadialMenu.init then
        ISEmoteRadialMenu:init()
    end
    return ISEmoteRadialMenu.menu
end

local function ordered(keys, known)
    local result, seen = {}, {}
    for _, key in ipairs(known) do
        if keys[key] ~= nil then table.insert(result, key); seen[key] = true end
    end
    local rest = {}
    for key in pairs(keys) do
        if not seen[key] then table.insert(rest, key) end
    end
    table.sort(rest)
    for _, key in ipairs(rest) do table.insert(result, key) end
    return result
end

--- The wheel's groups (not Shout, a command of its own): { id, name, icon, emotes = { { id, name, icon } } }.
--- Built once per wheel menu (it only changes when the game rebuilds it).
local builtFor, builtGroups
local function emoteGroups()
    local menu = emoteMenu()
    if menu == nil then return nil end
    if menu == builtFor then return builtGroups end
    local icons = ISEmoteRadialMenu.icons or {}
    local groupKeys = {}
    for key, group in pairs(menu) do
        if group.subMenu then groupKeys[key] = true end
    end
    local groups = {}
    for _, key in ipairs(ordered(groupKeys, EMOTE_GROUPS)) do
        local group = menu[key]
        local emotes = {}
        for _, emote in ipairs(ordered(group.subMenu, EMOTE_ORDER[key] or {})) do
            table.insert(emotes, { id = emote, name = group.subMenu[emote], icon = uiIcon(icons[emote]) })
        end
        table.insert(groups, { id = key, name = group.name, icon = uiIcon(icons[key]), emotes = emotes })
    end
    builtFor, builtGroups = menu, groups
    return groups
end

--- When the wheel's key works (ISEmoteRadialMenu.checkKey): not paused, alive, not busy.
local function canEmote(player)
    local speed = UIManager and try(UIManager, "getSpeedControls")
    if speed ~= nil and try(speed, "getCurrentGameSpeed") == 0 then return false end
    if try(player, "isDead") == true then return false end
    return not (busy(player) and try(player, "isCurrentlyBusy") == true)
end

--- The commands, in the order the app offers them when adding. `key`: the game's key binding
--- (its name is the command's name); `text`: else the game text it's named with. `available` and `on` get the player; `run` does it;
--- `describe` (optional) gives the app's entry its availability, icon and extra state in one pass.
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
        -- The held items, as the "Drop Both Held Items" key (the app asks first). Remembers them: while
        -- they're on the floor nearby, the command picks them up again into the hands they were in,
        -- as equipping from the floor does (walk next to them, then ISInventoryPaneContextMenu.equipWeapon).
        id = "drop_held", key = "DropBothHeldItems", icon = "Item_Duffelbag",
        available = function(player) return #pickUpTargets(player) > 0 or #droppableHeld(player) > 0 end,
        -- For the app: what a tap fetches (`pickUp`), and its icon: what it would pick up or drop,
        -- else the bag (greyed: nothing in hand).
        describe = function(player)
            local targets = pickUpTargets(player)
            if #targets > 0 then
                local items = {}
                for _, entry in ipairs(targets) do
                    table.insert(items, { name = try(entry.item, "getDisplayName"), icon = itemIcon(entry.item) })
                end
                return { available = true, icon = items[1].icon, pickUp = items }
            end
            local held = droppableHeld(player)
            return { available = #held > 0, icon = held[1] and itemIcon(held[#held]) or nil }
        end,
        run = function(player)
            local targets = pickUpTargets(player)
            if #targets > 0 then
                -- All of them within reach of a walk, nearest first, one walk per spot: the first as
                -- the game's Grab walks (luautils.walkAdj, which clears the queue), the next ones
                -- chained the same way (a free tile next to the spot, then the game's walk).
                -- (Kahlua has no `next`: a flag says whether a walk is queued yet.)
                local picked, walkedTo, reached, walking = {}, {}, false, false
                for _, entry in ipairs(targets) do
                    local square = entry.square
                    local there = walkedTo[square]
                    if there == nil then
                        if not walking then
                            there = luautils.walkAdj(player, square) == true
                            walking = true
                        else
                            local adjacent = AdjacentFreeTileFinder.Find(square, player)
                            there = adjacent ~= nil
                            if there then ISTimedActionQueue.add(ISWalkToTimedAction:new(player, adjacent)) end
                        end
                        walkedTo[square] = there
                    end
                    if there then
                        ISInventoryPaneContextMenu.equipWeapon(entry.item, entry.primary, entry.twoHands, playerNum(player))
                        picked[entry] = true
                        reached = true
                    end
                end
                if not reached then
                    error("can't reach", 0)
                end
                -- Items still on their way down stay remembered, to pick up next.
                local rest = {}
                for _, entry in ipairs(dropped[player] or {}) do
                    if not picked[entry] then table.insert(rest, entry) end
                end
                dropped[player] = rest
                return
            end
            local primary, secondary = try(player, "getPrimaryHandItem"), try(player, "getSecondaryHandItem")
            local remembered = {}
            for _, item in ipairs(droppableHeld(player)) do
                table.insert(remembered, { item = item, primary = item == primary, twoHands = item == primary and item == secondary,
                                           landsBy = getTimestampMs() + LANDING_MS })
                ISInventoryPaneContextMenu.dropItem(item, playerNum(player))
            end
            dropped[player] = remembered
        end,
    },
    {
        -- The left stick's wheel (ISJoystickButtonRadialMenu.onToggleAutoWalk): keeps walking the way
        -- you were going (IsoPlayer.getInputMoveVector keeps the last direction while it's on).
        id = "auto_walk", text = "IGUI_LeftStickButtonRadial_ToggleAutoWalk", icon = "emotes/autowalk_on",
        available = function(player) return not isGamePaused() and try(player, "getVehicle") == nil end,
        on = function(player) return try(player, "isAutoWalk") == true end,
        run = function(player) player:setAutoWalk(not player:isAutoWalk()) end,
    },
    {
        -- The app shows the hotbar and the weapon in hand, and sends the slot to draw (drawing the
        -- one in hand puts it away), or which hand to put away (the game's Unequip) for a weapon
        -- that isn't on the hotbar.
        id = "weapons", name = "Weapons", icon = "Item_Axe",
        available = function(player)
            local held = hands(player)
            return hasHotbarItem(player) or held.primary ~= nil or held.secondary ~= nil
        end,
        run = function(player, args)
            if args and args.putAway ~= nil then
                if args.putAway ~= "primary" and args.putAway ~= "secondary" then
                    error("changed", 0)
                end
                local item = try(player, args.putAway == "secondary" and "getSecondaryHandItem" or "getPrimaryHandItem")
                if item == nil then
                    error("changed", 0)
                end
                if not freeHands(player) then
                    error("busy", 0)
                end
                ISInventoryPaneContextMenu.unequipItem(item, playerNum(player))
                return
            end
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
        -- The emote wheel's emotes: the app shows them and sends the one to play, which plays as the
        -- wheel plays it (ISEmoteRadialMenu:emote: a random variant of some, then playEmote).
        id = "emotes", name = "Emotes", icon = "emotes/wavehello",
        available = canEmote,
        run = function(player, args)
            local id = args and args.emote
            local found = false
            for _, group in pairs(emoteMenu() or {}) do
                found = found or (group.subMenu ~= nil and group.subMenu[id] ~= nil)
            end
            if not found then
                error("not an emote", 0)
            end
            local variants = ISEmoteRadialMenu.variants and ISEmoteRadialMenu.variants[id]
            if variants then
                id = variants[ZombRand(#variants) + 1]
            end
            player:playEmote(id)
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
            name = command.key and getText("UI_optionscreen_binding_" .. command.key)
                or command.text and getText(command.text) or command.name,
            icon = command.icon,
        }
        -- `describe` (Drop held) works out availability and the rest in one go.
        local ok, described = pcall(command.describe or function() return nil end, player)
        if ok and described then
            entry.available = described.available == true
            entry.icon = described.icon or entry.icon
            entry.pickUp = described.pickUp
        else
            entry.available = command.available == nil or check(command.available, player) == true
        end
        if command.on then
            entry.on = check(command.on, player) == true
        end
        table.insert(commands, entry)
    end
    local result = { clock = clock(player), commands = commands, hotbar = hotbarSlots(player), hands = hands(player),
                     emotes = emoteGroups() }
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
        elseif text:find("can't reach", 1, true) then
            return false, "Can't get to them from here"
        elseif text:find("changed", 1, true) or text:find("hotbar slot", 1, true) or text:find("not a time", 1, true)
            or text:find("not an emote", 1, true) then
            return false, "That changed in the game; try again"
        end
        return false, "That didn't work in this game version (" .. tostring(err) .. ")"
    end
    return true
end

return Deck
