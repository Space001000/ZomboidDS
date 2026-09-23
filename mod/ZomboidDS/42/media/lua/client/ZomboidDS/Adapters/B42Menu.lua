--- The game's own item context menu (right-click in the inventory), mirrored to the companion app.
---
--- Instead of listing item actions ourselves (read, eat, bandage, craft, ... and whatever other mods
--- add), we let the game build its menu exactly as for a right-click, hidden, and send its options
--- to the app. Choosing an option in the app runs the same call the game would run on a click.
---
--- Based on 42.20's ISInventoryPaneContextMenu.createMenu and ISContextMenu:
---   * createMenu(playerNum, isInPlayerInventory, items, x, y) returns the menu (nil while paused)
---   * each option: name, onSelect, target, param1..param10, notAvailable/isDisabled, toolTip,
---     subOption (resolved with menu:getSubMenu(subOption))
---   * a click is option.onSelect(option.target, option.param1, ..., option.param10)
--- The game reuses ONE menu object per player and wipes its option tables when the next menu
--- opens, so we copy what we need instead of keeping references to them.
local Signature = require("ZomboidDS/Core/Signature")

local B42Menu = {}

local MENU_TTL_MS = 60000
local MAX_DEPTH = 4

-- Only the latest menu of each kind is kept: the item menu (the app asks for a fresh one each time
-- an item is tapped) and the world menu ("Here"), so one never invalidates the other.
local menus = { item = nil, world = nil }
local nextMenuId = 0

--- The game's tooltips use rich-text tags (<RGB:1,0,0>, <LINE>, ...): keep only the text.
local function plainText(text)
    if type(text) ~= "string" then
        return nil
    end
    text = string.gsub(text, "<LINE>", "\n")
    text = string.gsub(text, "<BR>", "\n")
    text = string.gsub(text, "<[^>]*>", "")
    text = string.gsub(text, "^%s+", "")
    text = string.gsub(text, "%s+$", "")
    if text == "" then
        return nil
    end
    return text
end

--- Walks the menu (and its submenus) into plain tables for the app, storing each runnable option's
--- call data in `calls` under its id ("3", "3.2", ...).
local function snapshot(menu, calls, prefix, depth)
    local list = {}
    for index, option in ipairs(menu.options) do
        if option ~= nil and option.name ~= nil then
            local id = prefix .. index
            local entry = { id = id, name = tostring(option.name) }
            local toolTip = option.toolTip
            entry.tooltip = plainText(toolTip and toolTip.description)

            local subMenu = option.subOption ~= nil and depth < MAX_DEPTH and menu:getSubMenu(option.subOption) or nil
            if subMenu ~= nil then
                entry.children = snapshot(subMenu, calls, id .. ".", depth + 1)
                entry.enabled = #entry.children > 0
            else
                entry.enabled = option.onSelect ~= nil and not option.notAvailable and not option.isDisabled
                if entry.enabled then
                    calls[id] = {
                        fn = option.onSelect, target = option.target,
                        option.param1, option.param2, option.param3, option.param4, option.param5,
                        option.param6, option.param7, option.param8, option.param9, option.param10,
                    }
                end
            end
            list[#list + 1] = entry
        end
    end
    return list
end

local function isPaused()
    local ok, speed = pcall(function() return UIManager.getSpeedControls():getCurrentGameSpeed() end)
    return ok and speed == 0
end

--- Builds the menu for `item`. Returns true, nil, { menuId, options } or false, reason.
function B42Menu.open(player, item)
    if ISInventoryPaneContextMenu == nil or ISInventoryPaneContextMenu.createMenu == nil then
        return false, "The game's item menu is not available"
    end
    local container = item:getContainer()
    local inInventory = container ~= nil and container:isInCharacterInventory(player)

    local menu = ISInventoryPaneContextMenu.createMenu(player:getPlayerNum(), inInventory, { item }, 0, 0)
    if menu == nil then
        return false, isPaused() and "The game is paused" or "The game has no menu for this item"
    end

    local calls = {}
    local ok, options = pcall(snapshot, menu, calls, "", 1)
    menu:hideAndChildren() -- same tick as createMenu, so it never shows on the top screen
    if not ok then
        return false, "Could not read the game's menu: " .. tostring(options)
    end

    nextMenuId = nextMenuId + 1
    menus.item = { id = "m" .. nextMenuId, created = getTimestampMs(), itemId = item:getID(), calls = calls }
    return true, nil, { menuId = menus.item.id, options = options }
end

--- The game's world menu for where the player stands ("Here" on the app's deck): what the
--- controller's interact button opens (42.20 ISButtonPrompt:interact). The objects come from the
--- prompt's own getInteractOptionsButtonObjects (the player's tile and the three tiles they face,
--- not through walls), and the menu is built at the player's screen position like there.
--- Returns true, nil, { menuId, options } or false, reason.
function B42Menu.openWorld(player)
    local playerNum = player:getPlayerNum()
    local prompts = getButtonPrompts and getButtonPrompts(playerNum)
    if prompts == nil or ISContextManager == nil then
        return false, "The game's world menu is not available"
    end
    if player:getVehicle() then
        return false, "Not while in a vehicle"
    end
    if isPaused() then
        return false, "The game is paused"
    end
    local objects = prompts:getInteractOptionsButtonObjects(nil)
    if objects == nil or objects:isEmpty() then
        return false, "Nothing to do here"
    end
    local square = player:getCurrentSquare()
    local x = isoToScreenX(playerNum, square:getX(), square:getY(), square:getZ())
    local y = isoToScreenY(playerNum, square:getX(), square:getY(), square:getZ())

    local menu = ISContextManager.getInstance().createWorldMenu(playerNum, nil, objects.items, x, y)
    if menu == nil then
        return false, "Nothing to do here"
    end
    local calls = {}
    local ok, options = pcall(snapshot, menu, calls, "", 1)
    menu:hideAndChildren() -- same tick as createWorldMenu, so it never shows on the top screen
    if not ok then
        return false, "Could not read the game's menu: " .. tostring(options)
    end

    -- Same options as the last world menu (standing still): keep its id, so the app sees no change.
    local signature = Signature.of(options)
    local previous = menus.world
    local id
    if previous ~= nil and previous.signature == signature then
        id = previous.id
    else
        nextMenuId = nextMenuId + 1
        id = "m" .. nextMenuId
    end
    menus.world = { id = id, created = getTimestampMs(), calls = calls, signature = signature,
                    square = square, dir = player:getDir() }
    return true, nil, { menuId = id, options = options }
end

--- Runs option `args.optionId` of menu `args.menuId`, like clicking it in the game.
--- `findItem(id)` says whether the item is still within reach (default: in the player's inventory).
function B42Menu.select(player, args, findItem)
    local slot = nil
    for kind, menu in pairs(menus) do
        if menu.id == args.menuId then
            slot = kind
        end
    end
    local menu = slot and menus[slot]
    -- Item menus expire; a world menu stays valid while the player stays put (checked below).
    if menu == nil or (menu.itemId ~= nil and getTimestampMs() - menu.created > MENU_TTL_MS) then
        return false, "This menu is out of date; tap the item again"
    end
    local call = menu.calls[tostring(args.optionId)]
    if call == nil then
        return false, "That option isn't available"
    end
    if menu.itemId ~= nil then
        local stillThere = findItem and findItem(menu.itemId) or player:getInventory():getItemWithIDRecursiv(menu.itemId)
        if stillThere == nil then
            menus[slot] = nil
            return false, "The item is no longer there"
        end
    elseif menu.square ~= player:getCurrentSquare() or menu.dir ~= player:getDir() then
        -- A world menu is about what was around the player then; don't act on the wrong spot.
        menus[slot] = nil
        return false, "You've moved; the menu will update"
    end
    menus[slot] = nil -- one choice per menu, like in the game
    call.fn(call.target, call[1], call[2], call[3], call[4], call[5], call[6], call[7], call[8], call[9], call[10])
    return true
end

return B42Menu
