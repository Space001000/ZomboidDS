--- Containers: the player's inventory and bags and everything within reach, and moving items
--- between them.
local Util = require("ZomboidDS/Adapters/B42/Util")
local try, round = Util.try, Util.round
local Items = require("ZomboidDS/Adapters/B42/Items")

local Containers = {}

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

function Containers.idOf(container)
    local id = containerIds[container]
    if id == nil then
        nextContainerId = nextContainerId + 1
        id = "c" .. nextContainerId
        containerIds[container] = id
    end
    return id
end

local function describeContainer(player, button, kind)
    local container = button.inventory
    local locked = button.onclick == nil
    local entry = {
        id = Containers.idOf(container),
        kind = kind,
        name = button.name or try(container, "getType"),
        icon = Util.textureFileName(try(button.textureOverride or button.image, "getName")),
        weight = round(try(container, "getCapacityWeight"), 2),
        capacity = round(button.capacity, 2),
        locked = locked or nil,
    }
    -- The main inventory's items are in the `inventory` message; locked containers can't be looked into.
    if kind ~= "inventory" and not locked then
        entry.items = Items.of(player, container, false)
    end
    return entry
end

--- The containers a player can use right now, as the game's inventory and loot windows list them.
function Containers.snapshot(player)
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

--- A container from the latest snapshot, or nil if it's no longer within reach (or locked when
--- `unlockedOnly`).
function Containers.reachable(id, unlockedOnly)
    if unlockedOnly and lockedIds[id] then
        return nil
    end
    return reachable[id]
end

-- Moving items -------------------------------------------------------------------
-- Moves go through the game's own ISInventoryPane:transferItemsByWeight (what its Take All /
-- Transfer All buttons use): timed transfer actions with animation, the game's capacity checks and
-- interruptions; a move to the floor is a normal drop; corpse storage has its own action.

--- Item `id` in the player's inventory (including bags) or in an unlocked container within reach.
function Containers.findItem(player, id)
    local item = Items.inInventory(player, id)
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
            ok = ok and not try(item, "isEquipped") and not Items.isKeyRing(item) and try(item, "isFavorite") ~= true
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

--- The container the game's loot window has selected: what it would show on Y, and what it outlines.
function Containers.selectedLoot(playerNum)
    local page = getPlayerLoot and getPlayerLoot(playerNum)
    local pane = page and page.inventoryPane
    return pane and pane.inventory or nil
end

Containers.commands = {
    -- Select a container around the player in the game's (hidden) loot window, as clicking its tab
    -- would: the game then outlines it in the world (ISInventoryPage:updateContainerHighlight keeps
    -- running while the window is hidden) and plays its open/close sounds. Same call the game's
    -- transfer action uses (42.20 ISInventoryPage:selectButtonForContainer).
    select_container = function(player, args)
        local container = Containers.reachable(args.id, true)
        local loot = getPlayerLoot and getPlayerLoot(player:getPlayerNum())
        if container == nil or loot == nil then
            return false, "That container is out of reach"
        end
        if try(container, "isInCharacterInventory", player) == true then
            return false, "Only containers around you are highlighted"
        end
        loot:selectButtonForContainer(container)
        return true
    end,

    -- Move one item to a container.
    transfer = function(player, args)
        local destination, reason = destinationFor(args.to)
        if destination == nil then
            return false, reason
        end
        local item = Containers.findItem(player, tonumber(args.itemId))
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
        local from = Containers.reachable(args.from, true)
        if from == nil then
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
}

return Containers
