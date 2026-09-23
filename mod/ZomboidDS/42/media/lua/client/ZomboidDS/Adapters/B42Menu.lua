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
local Util = require("ZomboidDS/Adapters/B42/Util")

local B42Menu = {}

local MENU_TTL_MS = 60000
local MAX_DEPTH = 4

-- Only the latest menu of each kind is kept: the item menu (the app asks for a fresh one each time
-- an item is tapped) and the world menu ("Here"), so one never invalidates the other.
local menus = { item = nil, world = nil, health = nil }
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

--- The name of an option's icon (option.iconTexture, e.g. the object's sprite), for the bridge's
--- icon endpoint; nil for none or for plain-colour icons (Texture.getWhite).
local function iconName(texture)
    if texture == nil then
        return nil
    end
    local ok, name = pcall(function() return texture:getName() end)
    name = ok and Util.textureFileName(name) or nil
    if name == nil or name == "" or string.lower(name) == "white" then
        return nil
    end
    return name
end

--- The item menu's entries the app already has as its own buttons, by the game function behind
--- them (not their translated names): Grab / Grab all (the app's Take), the Move To submenu (Move
--- to...), Transfer all / Loot all (Put all / Take all). Grab one / Grab half stay: the app has none.
local function appDuplicates()
    local set = {}
    local function add(fn)
        if fn ~= nil then set[fn] = true end
    end
    if ISInventoryPaneContextMenu ~= nil then
        add(ISInventoryPaneContextMenu.onGrabItems)
        add(ISInventoryPaneContextMenu.onMoveItemsTo)
        add(ISInventoryPaneContextMenu.onPutItems)
    end
    if ISInventoryPage ~= nil then
        add(ISInventoryPage.transferAll)
        add(ISInventoryPage.lootAll)
    end
    return set
end

--- Walks the menu (and its submenus) into plain tables for the app, storing each runnable option's
--- call data in `calls` under its id ("3", "3.2", ...). Options whose function is in `skip` are left
--- out, and so is a submenu left with nothing else.
local function snapshot(menu, calls, prefix, depth, skip)
    local list = {}
    for index, option in ipairs(menu.options) do
        if option ~= nil and option.name ~= nil and not (skip and option.onSelect and skip[option.onSelect]) then
            local id = prefix .. index
            local entry = { id = id, name = tostring(option.name), icon = iconName(option.iconTexture) }
            local toolTip = option.toolTip
            entry.tooltip = plainText(toolTip and toolTip.description)

            local subMenu = option.subOption ~= nil and depth < MAX_DEPTH and menu:getSubMenu(option.subOption) or nil
            local dropped = false
            if subMenu ~= nil then
                entry.children = snapshot(subMenu, calls, id .. ".", depth + 1, skip)
                entry.enabled = #entry.children > 0
                dropped = skip ~= nil and #entry.children == 0 and #(subMenu.options or {}) > 0
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
            if not dropped then
                list[#list + 1] = entry
            end
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
    local ok, options = pcall(snapshot, menu, calls, "", 1, appDuplicates())
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
    menus.world = { id = id, created = getTimestampMs(), calls = calls, signature = signature, world = true,
                    square = square, dir = player:getDir() }
    return true, nil, { menuId = id, options = options }
end

--- The game's treatment menu for one of the player's body parts (bandage, disinfect, remove glass,
--- splint, ... with what they carry), as its health panel builds it on a click
--- (42.20 ISHealthPanel:doBodyPartContextMenu, on the player's own panel from getPlayerInfoPanel).
--- With a controller the game moves the controller focus to that menu: we put it back.
--- Returns true, nil, { menuId, options } or false, reason.
function B42Menu.openHealth(player, partId)
    local playerNum = player:getPlayerNum()
    local info = getPlayerInfoPanel and getPlayerInfoPanel(playerNum)
    local panel = info and info.healthView
    if panel == nil or panel.doBodyPartContextMenu == nil then
        return false, "The game's health panel is not available"
    end
    if isPaused() then
        return false, "The game is paused"
    end
    local bodyPart = nil
    local parts = player:getBodyDamage():getBodyParts()
    for i = 0, parts:size() - 1 do
        if tostring(parts:get(i):getType()) == partId then
            bodyPart = parts:get(i)
        end
    end
    if bodyPart == nil then
        return false, "No such body part"
    end

    local joypad = JoypadState and JoypadState.players[playerNum + 1]
    local focusBefore = joypad and joypad.focus
    panel:doBodyPartContextMenu(bodyPart, 0, 0)
    local menu = getPlayerContextMenu(playerNum)
    local calls = {}
    local ok, options = pcall(snapshot, menu, calls, "", 1)
    menu:hideAndChildren() -- same tick as the game built it, so it never shows on the top screen
    if joypad and joypad.focus ~= focusBefore then
        joypad.focus = focusBefore
        if updateJoypadFocus then updateJoypadFocus(joypad) end
    end
    if not ok then
        return false, "Could not read the game's menu: " .. tostring(options)
    end
    if #options == 0 then
        return false, "Nothing you can do for this with what you carry"
    end

    nextMenuId = nextMenuId + 1
    menus.health = { id = "m" .. nextMenuId, created = getTimestampMs(), calls = calls }
    return true, nil, { menuId = menus.health.id, options = options }
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
    -- Item and health menus expire; a world menu stays valid while the player stays put (checked below).
    if menu == nil or (not menu.world and getTimestampMs() - menu.created > MENU_TTL_MS) then
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
    elseif menu.world and (menu.square ~= player:getCurrentSquare() or menu.dir ~= player:getDir()) then
        -- A world menu is about what was around the player then; don't act on the wrong spot.
        menus[slot] = nil
        return false, "You've moved; the menu will update"
    end
    menus[slot] = nil -- one choice per menu, like in the game
    call.fn(call.target, call[1], call[2], call[3], call[4], call[5], call[6], call[7], call[8], call[9], call[10])
    return true
end

return B42Menu
