--- Build 42 adapter: the only Lua file that calls the game API.
--- Implements the contract documented in ZomboidDS/Core/Adapters.lua.
---
--- Engine calls go through `try`, so one renamed method in a B42 update degrades one field to nil
--- instead of breaking the whole snapshot. Every call below was checked against 42.20's Lua
--- sources and projectzomboid.jar (2026-09-22).
local Adapters = require("ZomboidDS/Core/Adapters")
local B42Menu = require("ZomboidDS/Adapters/B42Menu")

local B42 = {
    id = "b42",
    capabilities = { "player", "inventory", "vehicle", "cmd.equip", "cmd.wear", "cmd.unequip", "cmd.drop", "item_menu",
                     "containers", "transfer", "time", "world_menu", "here", "select_container", "health" },
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

--- `inInventory`: whether the item is in the player's main inventory. Quick actions only apply
--- there for now; items in bags and nearby containers get theirs with transfers (Phase 6).
local function describeItem(player, item, inInventory)
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
        actions = inInventory and actionsFor(item, equipped) or {},
    }
end

--- The visible items of a container, like the game's inventory window shows them: hidden items are
--- skipped (e.g. B42 models wounds as invisible worn "Wound_*" clothing).
local function itemsOf(player, container, inInventory)
    local items = container:getItems()
    local list = {}
    for i = 0, items:size() - 1 do
        local item = items:get(i)
        if try(item, "isHidden") ~= true then
            list[#list + 1] = describeItem(player, item, inInventory)
        end
    end
    return list
end

--- Main inventory only; equipped bags and nearby containers come with container management (PLAN.md, Phase 6).
function B42.snapshotInventory(player)
    local inventory = player:getInventory()
    return {
        weight = {
            current = round(try(inventory, "getCapacityWeight"), 2),
            max = round(try(player, "getMaxWeight"), 2),
        },
        items = itemsOf(player, inventory, true),
    }
end

-- Containers -------------------------------------------------------------------
-- We read the game's own container lists, i.e. the tabs of its inventory window (your inventory,
-- bags, key rings) and loot window (everything within reach, the floor). So the rules are exactly
-- the game's: reachability through walls, safehouses, locks, corpses, vehicles, bags on the floor,
-- loot generated on first look, and containers other mods add. 42.20's ISInventoryPage keeps them in
-- `page.backpacks` (buttons with `.inventory`, `.name`, `.capacity`, an image, and `onclick == nil`
-- plus a lock image for locked containers) and refreshes them when the player moves or turns.

local containerIds = {} -- ItemContainer -> short id, stable while the container exists
local nextContainerId = 0
local reachable = {}    -- id -> ItemContainer, from the latest snapshot (commands resolve ids here)
local lockedIds = {}    -- id -> true for locked containers in the latest snapshot

local function containerId(container)
    local id = containerIds[container]
    if id == nil then
        nextContainerId = nextContainerId + 1
        id = "c" .. nextContainerId
        containerIds[container] = id
    end
    return id
end

local function textureName(texture)
    local name = try(texture, "getName")
    if name == nil then
        return nil
    end
    name = string.gsub(name, "^.*[/\\]", "")
    name = string.gsub(name, "%.png$", "")
    return name
end

local function describeContainer(player, button, kind)
    local container = button.inventory
    local locked = button.onclick == nil
    local entry = {
        id = containerId(container),
        kind = kind,
        name = button.name or try(container, "getType"),
        icon = textureName(button.textureOverride or button.image),
        weight = round(try(container, "getCapacityWeight"), 2),
        capacity = round(button.capacity, 2),
        locked = locked or nil,
    }
    -- The main inventory's items are in the `inventory` message; locked containers can't be looked into.
    if kind ~= "inventory" and not locked then
        entry.items = itemsOf(player, container, false)
    end
    return entry
end

--- The containers a player can use right now, as the game's inventory and loot windows list them.
function B42.snapshotContainers(player)
    local playerNum = player:getPlayerNum()
    local pages = {}
    if getPlayerInventory then pages[#pages + 1] = { page = getPlayerInventory(playerNum), onCharacter = true } end
    if getPlayerLoot then pages[#pages + 1] = { page = getPlayerLoot(playerNum), onCharacter = false } end

    local list = {}
    local seen = {}
    local locked = {}
    for _, source in ipairs(pages) do
        local buttons = source.page and source.page.backpacks or {}
        -- The loot window's selected container: the one the game outlines in the world.
        local selected = not source.onCharacter and source.page and source.page.inventoryPane
            and source.page.inventoryPane.inventory or nil
        for _, button in ipairs(buttons) do
            local container = button.inventory
            if container ~= nil then
                local kind
                if source.onCharacter then
                    kind = container == player:getInventory() and "inventory" or "bag"
                else
                    kind = try(container, "getType") == "floor" and "floor" or "nearby"
                end
                local entry = describeContainer(player, button, kind)
                entry.selected = (selected ~= nil and container == selected) or nil
                seen[entry.id] = container
                locked[entry.id] = entry.locked
                list[#list + 1] = entry
            end
        end
    end
    reachable = seen
    lockedIds = locked
    return { containers = list }
end

--- A container from the latest snapshot, or nil if it's no longer within reach.
function B42.reachableContainer(id)
    return reachable[id]
end

-- Moving items -------------------------------------------------------------------
-- Moves go through the game's own ISInventoryPane:transferItemsByWeight (what its Take All /
-- Transfer All buttons use): timed transfer actions with animation, the game's capacity checks and
-- interruptions; a move to the floor is a normal drop; corpse storage has its own action.

--- Item `id` in the player's inventory (including bags) or in an unlocked container within reach.
local function findItemAnywhere(player, id)
    local item = try(player:getInventory(), "getItemWithIDRecursiv", id)
    if item ~= nil then
        return item
    end
    for containerId, container in pairs(reachable) do
        if not lockedIds[containerId] then -- the game doesn't let you into locked containers either
            item = try(container, "getItemWithIDRecursiv", id)
            if item ~= nil then
                return item
            end
        end
    end
    return nil
end

--- Like the game's buttons: walk to the containers that aren't on the player, then transfer.
local function transferItems(player, items, destination)
    local playerNum = player:getPlayerNum()
    local loot = getPlayerLoot and getPlayerLoot(playerNum)
    local pane = loot and loot.inventoryPane
    if pane == nil or pane.transferItemsByWeight == nil then
        return false, "The game's inventory window isn't available"
    end
    local toVisit = { destination }
    for _, item in ipairs(items) do
        toVisit[#toVisit + 1] = item:getContainer()
    end
    local visited = {}
    for _, container in ipairs(toVisit) do
        if container ~= nil and not visited[container] and not container:isInCharacterInventory(player) then
            visited[container] = true
            if not luautils.walkToContainer(container, playerNum) then
                return false, "Can't reach that container"
            end
        end
    end
    pane:transferItemsByWeight(items, destination)
    return true
end

local function destinationFor(id)
    local destination = reachable[id]
    if destination == nil then
        return nil, "That container is out of reach"
    end
    if lockedIds[id] then
        return nil, "That container is locked"
    end
    return destination
end

local function isKeyRing(item)
    return try(item, "isItemType", ItemType.KEY_RING) == true or try(item, "hasTag", ItemTag.KEY_RING) == true
end

--- Which items "move all" takes, with the filters of the game's own buttons:
--- from your inventory like Transfer All (not equipped, key rings, hotbar or favourites), from
--- elsewhere like Take All (not items you marked unwanted, not heavy items like corpses/generators).
local function itemsToMoveAll(player, from, to)
    local playerNum = player:getPlayerNum()
    local hotbar = getPlayerHotbar and getPlayerHotbar(playerNum)
    local fromPlayer = from == player:getInventory()
    local toFloor = try(to, "getType") == "floor"
    local items = {}
    local list = from:getItems()
    for i = 0, list:size() - 1 do
        local item = list:get(i)
        local ok = try(item, "isHidden") ~= true
        if fromPlayer then
            ok = ok and not try(item, "isEquipped") and not isKeyRing(item) and try(item, "isFavorite") ~= true
                and not (hotbar and try(hotbar, "isInHotbar", item))
        else
            ok = ok and try(item, "isUnwanted", player) ~= true and not (isForceDropHeavyItem and isForceDropHeavyItem(item))
        end
        if toFloor and instanceof(item, "Moveable") and try(item, "getSpriteGrid") == nil
            and try(item, "CanBeDroppedOnFloor") == false then
            ok = false
        end
        if ok then
            items[#items + 1] = item
        end
    end
    return items
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

-- Health ------------------------------------------------------------------------------
-- The game's health panel decides which body parts to list (ISHealthPanel.getDamagedParts) and what
-- to say about each (ISHealthBodyPartListBox.doDrawItem: "Scratched (Severe)", "Bandaged", ...,
-- depending on the player's First Aid level, in its own colours). We call both with stand-ins that
-- record the text instead of drawing it, so the lines, translations and rules are the game's own
-- (and other mods' changes to them), not a copy that drifts.

--- The game's colours: green = treated, red = a problem, orange = dirty bandage, infection, stiffness.
local function tone(r, g)
    if g > 0.8 and r < 0.5 then return "good" end
    if r > 0.95 then return "warn" end
    if r > 0.8 and g < 0.5 then return "bad" end
    return nil
end

local function describeBodyPart(player, panel, bodyPart)
    local lines = {}
    local recorder = {
        parent = panel, selected = -1, mouseoverselected = -1, width = 400,
        getWidth = function() return 400 end,
        drawText = function(_, text, _x, _y, r, g) lines[#lines + 1] = { text = text, r = r or 1, g = g or 1 } end,
        drawRect = function() end, drawRectBorder = function() end, drawProgressBar = function() end,
    }
    ISHealthBodyPartListBox.doDrawItem(recorder, 0, { item = { bodyPart = bodyPart }, height = 0, itemindex = 0 }, false)
    local entry = { id = tostring(bodyPart:getType()), name = lines[1] and lines[1].text or tostring(bodyPart:getType()), lines = {} }
    for i = 2, #lines do
        local text = string.gsub(lines[i].text, "^%s*%-%s*", "")
        entry.lines[#entry.lines + 1] = { text = text, tone = tone(lines[i].r, lines[i].g) }
    end
    return entry
end

function B42.snapshotHealth(player)
    if ISHealthPanel == nil or ISHealthBodyPartListBox == nil then
        return { parts = {} }
    end
    local panel = {
        character = player, otherPlayer = nil, bodyPartAction = nil, actions = {},
        doctorLevel = try(player, "getPerkLevel", Perks and Perks.Doctor) or 0,
        getPatient = function() return player end, getDoctor = function() return player end,
    }
    local parts = {}
    local ok, damaged = pcall(ISHealthPanel.getDamagedParts, panel)
    for _, bodyPart in ipairs(ok and damaged or {}) do
        local described, entry = pcall(describeBodyPart, player, panel, bodyPart)
        if described then
            parts[#parts + 1] = entry
        end
    end
    return { parts = parts }
end

-- Game speed ------------------------------------------------------------------------
-- The game's own speed buttons (top right; zombie.ui.SpeedControls in 42.20): speeds 0 pause,
-- 1 play, 2 fast forward (x5), 3 faster (x20), 4 wait (x40). We press its buttons by name with
-- ButtonClicked, like the controller's back-button wheel (ISBackButtonWheel), so its icons and
-- state stay in sync. Not in multiplayer, same as the game.

local SPEED_BUTTONS = { [0] = "Pause", [1] = "Play", [2] = "Fast Forward x 1", [3] = "Fast Forward x 2", [4] = "Wait" }

local function speedControls()
    return UIManager and try(UIManager, "getSpeedControls") or nil
end

--- The game's pause menu (Esc: settings, quit, ...) is open. The game ignores its speed buttons
--- behind it; so do we, or the game would run on under a menu that no longer takes the controller.
--- Same check as the game's controller code (42.20 JoyPadSetup.lua).
local function pauseMenuOpen()
    local screen = MainScreen and MainScreen.instance
    return screen ~= nil and screen.inGame == true and try(screen, "isReallyVisible") == true
end

function B42.snapshotTime(_player)
    return {
        speed = try(speedControls(), "getCurrentGameSpeed"),
        canChange = speedControls() ~= nil and not (isClient and isClient()),
        gameMenuOpen = pauseMenuOpen() or nil,
    }
end

local function setSpeed(speed)
    local controls = speedControls()
    if controls == nil then
        return false, "The game's speed controls aren't available"
    end
    if isClient and isClient() then
        return false, "Game speed can't be changed in multiplayer"
    end
    if pauseMenuOpen() then
        return false, "Close the game's menu first"
    end
    local button = SPEED_BUTTONS[speed]
    if button == nil then
        return false, "unknown speed " .. tostring(speed)
    end
    local current = try(controls, "getCurrentGameSpeed")
    if current == speed then
        return true
    end
    -- "Pause" while paused toggles back to play in the game, so only press it when running.
    controls:ButtonClicked(button)
    return true
end

-- "Here" follows the player ------------------------------------------------------
-- While the app watches (its Deck is open), the world menu for where the player stands is rebuilt
-- when they step onto another tile or turn, when their action finishes (the door is open now),
-- shortly after an option from the app ran, and every few seconds as a fallback. Never while the
-- game's own context menu is open on the top screen: the game has one per player, and building
-- ours would close it. Unchanged menus keep their id (B42Menu.openWorld), so the Emitter doesn't
-- re-send them.

local HERE_WATCH_MS = 10000   -- the app repeats watch_here while its Deck is open; expires otherwise
local HERE_FALLBACK_MS = 3000 -- rebuild at least this often while watched (things change around you)
local HERE_AFTER_SELECT_MS = 400

local here = { watchUntil = 0, last = nil, square = nil, dir = nil, busy = false, builtAt = 0, forceAt = nil }

local function gameMenuOpen(player)
    local menu = getPlayerContextMenu and getPlayerContextMenu(player:getPlayerNum())
    return menu ~= nil and try(menu, "isVisible") == true
end

function B42.snapshotHere(player)
    local now = getTimestampMs()
    if now >= here.watchUntil then
        here.last = nil
        return { watching = false }
    end
    local square, dir = player:getCurrentSquare(), player:getDir()
    local busy = ISTimedActionQueue ~= nil and ISTimedActionQueue.isPlayerDoingAction(player) == true
    local finished = here.busy and not busy
    here.busy = busy
    local due = here.last == nil or square ~= here.square or dir ~= here.dir or finished
        or now - here.builtAt >= HERE_FALLBACK_MS or (here.forceAt ~= nil and now >= here.forceAt)
        -- no menu (paused, nothing here): look again soon, e.g. right after unpausing
        or (here.last.unavailable ~= nil and now - here.builtAt >= 1000)
    if not due or gameMenuOpen(player) then
        return here.last or { watching = true }
    end
    here.square, here.dir, here.builtAt, here.forceAt = square, dir, now, nil
    local ok, reason, menu = B42Menu.openWorld(player)
    local buildMs = getTimestampMs() - now
    if ok then
        here.last = { watching = true, menuId = menu.menuId, options = menu.options }
    else
        here.last = { watching = true, unavailable = reason }
    end
    if buildMs > 20 then
        print("[ZomboidDS] building the world menu took " .. buildMs .. " ms")
    end
    return here.last
end

B42.commands = {
    -- The app's Deck is open (on = true, repeated every few seconds) or closed.
    watch_here = function(_player, args)
        if args.on == false then
            here.watchUntil = 0
        else
            here.watchUntil = getTimestampMs() + HERE_WATCH_MS
        end
        return true
    end,

    -- Select a container around the player in the game's (hidden) loot window, as clicking its tab
    -- would: the game then outlines it in the world (ISInventoryPage:updateContainerHighlight keeps
    -- running while the window is hidden) and plays its open/close sounds. Same call the game's
    -- transfer action uses (42.20 ISInventoryPage:selectButtonForContainer).
    select_container = function(player, args)
        local container = reachable[args.id]
        local loot = getPlayerLoot and getPlayerLoot(player:getPlayerNum())
        if container == nil or loot == nil or lockedIds[args.id] then
            return false, "That container is out of reach"
        end
        if try(container, "isInCharacterInventory", player) == true then
            return false, "Only containers around you are highlighted"
        end
        loot:selectButtonForContainer(container)
        return true
    end,

    -- The game's speed buttons (see "Game speed").
    set_speed = function(_player, args)
        return setSpeed(tonumber(args.speed))
    end,

    -- Move one item to a container (see "Moving items").
    transfer = function(player, args)
        local destination, reason = destinationFor(args.to)
        if destination == nil then
            return false, reason
        end
        local item = findItemAnywhere(player, tonumber(args.itemId))
        if item == nil then
            return false, "item not found"
        end
        if item:getContainer() == destination then
            return false, "It's already there"
        end
        if not destination:isItemAllowed(item) then
            return false, "That can't go in there"
        end
        return transferItems(player, { item }, destination)
    end,

    -- Move everything from one container to another, like the game's Take All / Transfer All.
    transfer_all = function(player, args)
        local from = reachable[args.from]
        if from == nil or lockedIds[args.from] then
            return false, "That container is out of reach"
        end
        local destination, reason = destinationFor(args.to)
        if destination == nil then
            return false, reason
        end
        local items = itemsToMoveAll(player, from, destination)
        if #items == 0 then
            return false, "Nothing to move"
        end
        return transferItems(player, items, destination)
    end,

    -- The game's own right-click menu for an item (see B42Menu.lua): for items you carry, and for
    -- items in containers within reach, which get the loot window's menu (Grab, and options like
    -- Read or Eat that take the item first).
    item_menu = function(player, args)
        local item = findItemAnywhere(player, tonumber(args.itemId))
        if item == nil then
            return false, "item not found"
        end
        return B42Menu.open(player, item)
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
        local ok, reason = B42Menu.select(player, args, function(id) return findItemAnywhere(player, id) end)
        -- Whatever ran may change what's here (a door opens): look again shortly.
        here.forceAt = getTimestampMs() + HERE_AFTER_SELECT_MS
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

--- The container the game's loot window has selected: what it would show on Y.
local function selectedLootContainer(playerNum)
    local page = getPlayerLoot and getPlayerLoot(playerNum)
    local pane = page and page.inventoryPane
    return pane and pane.inventory or nil
end

--- Calls `onShow(playerNum, { panel = "inventory", container = id })` when the player asks the game
--- for its inventory or loot window. If it returns true, the game's window stays closed.
function B42.redirectGameWindows(onShow)
    if ISButtonPrompt == nil or ISButtonPrompt.zomboidDSRedirect then
        return
    end
    ISButtonPrompt.zomboidDSRedirect = true
    local function wrap(original, containerFor)
        return function(self, ...)
            local ok, handled = pcall(function()
                local container = containerFor(self.player)
                return onShow(self.player, { panel = "inventory", container = container and containerId(container) or nil })
            end)
            if ok and handled then
                return
            end
            return original(self, ...)
        end
    end
    ISButtonPrompt.cmdShowLoot = wrap(ISButtonPrompt.cmdShowLoot, selectedLootContainer)
    ISButtonPrompt.cmdShowInventory = wrap(ISButtonPrompt.cmdShowInventory, function(playerNum)
        local player = getSpecificPlayer(playerNum)
        return player and player:getInventory() or nil
    end)
end

Adapters.register(B42)

return B42
