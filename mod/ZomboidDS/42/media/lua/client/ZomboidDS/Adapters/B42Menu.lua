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
local menus = { item = nil, world = nil, health = nil, garment = nil }
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

--- The icon of the item the game shows next to an option (option.itemForTexture: the fabric in the
--- tailoring menu, an ingredient in recipe menus), as the bridge's icon endpoint knows it.
local function itemIconName(item)
    if item == nil then
        return nil
    end
    local name = Util.textureFileName(Util.try(Util.try(item, "getTex"), "getName"))
    if name ~= nil and name ~= "" then
        return name
    end
    local scriptIcon = Util.try(Util.try(item, "getScriptItem"), "getIcon")
    return scriptIcon and ("Item_" .. scriptIcon) or nil
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

--- The item menu's entries the app shows as pills above the rest, by the game function behind them
--- (never by name, so translations and other mods' labels don't matter): the item's main uses. A
--- submenu is a pill when everything in it is (Eat > All / Half / Quarter, Apply Bandage > a body
--- part, Attach > a slot). Equipping and attaching are pills for weapons only: the game offers them
--- for nearly anything you can hold (and only for one weapon, not a selection). Drop is "drop" (the
--- app draws it quieter); the rest "action".
local function pillFunctions(items)
    local set = {}
    local function add(fn, kind)
        if fn ~= nil then set[fn] = kind or "action" end
    end
    local P = ISInventoryPaneContextMenu
    if P ~= nil then
        if #items == 1 and instanceof(items[1], "HandWeapon") then
            add(P.OnPrimaryWeapon)
            add(P.OnSecondWeapon)
            add(P.OnTwoHandsEquip)
            if ISHotbar ~= nil then add(ISHotbar.attachItem) end
        end
        for _, name in ipairs({
            "onEatItems", "onDrinkFluid", "onDrinkForThirst", -- eat (and smoke), drink
            "onWearItems", "onClothingItemExtra", "onUnEquip", -- wear (clothes, bags), take off
            "onLiteratureItems", "onPillsItems", "onApplyBandage", -- read, take pills, apply
            "onInspectClothing", -- a garment's holes and patches (the app's garment panel)
            "onRackGun", "onInsertMagazine", "onEjectMagazine", -- firearms
            "onLoadBulletsIntoFirearm", "onUnloadBulletsFromFirearm",
            "onLoadBulletsInMagazine", "onUnloadBulletsFromMagazine",
            "onActivateItem", "onSetAlarm", "onStopAlarm", "onCheckMap", -- turn on/off, alarm, map
        }) do
            add(P[name])
        end
        add(P.onDropItems, "drop")
    end
    if ISRadioAndTvMenu ~= nil then add(ISRadioAndTvMenu.openRadioPanel) end -- device options
    return set
end

--- The pill kind of an option: its own function's, or for a submenu the one all its options share.
local function pillOf(option, menu, pills, depth)
    if option.onSelect ~= nil then
        return pills[option.onSelect]
    end
    local subMenu = option.subOption ~= nil and depth < MAX_DEPTH and menu:getSubMenu(option.subOption) or nil
    if subMenu == nil then
        return nil
    end
    local kind
    for _, child in ipairs(subMenu.options or {}) do
        if child ~= nil and child.name ~= nil then
            local childKind = pillOf(child, subMenu, pills, depth + 1)
            if childKind == nil or (kind ~= nil and childKind ~= kind) then
                return nil
            end
            kind = childKind
        end
    end
    return kind
end

--- Walks the menu (and its submenus) into plain tables for the app, storing each runnable option's
--- call data in `calls` under its id ("3", "3.2", ...). Options whose function is in `skip` are left
--- out, and so is a submenu left with nothing else. Top-level options in `pills` get `pill` = kind.
--- `all`, if given, gets the call data of every option, greyed ones too.
local function snapshot(menu, calls, prefix, depth, skip, pills, all)
    local list = {}
    for index, option in ipairs(menu.options) do
        if option ~= nil and option.name ~= nil and not (skip and option.onSelect and skip[option.onSelect]) then
            local id = prefix .. index
            local entry = { id = id, name = tostring(option.name), icon = iconName(option.iconTexture) or itemIconName(option.itemForTexture) }
            if pills ~= nil then
                entry.pill = pillOf(option, menu, pills, depth)
            end
            local toolTip = option.toolTip
            entry.tooltip = plainText(toolTip and toolTip.description)

            local subMenu = option.subOption ~= nil and depth < MAX_DEPTH and menu:getSubMenu(option.subOption) or nil
            local dropped = false
            if subMenu ~= nil then
                entry.children = snapshot(subMenu, calls, id .. ".", depth + 1, skip, nil, all)
                entry.enabled = #entry.children > 0
                dropped = skip ~= nil and #entry.children == 0 and #(subMenu.options or {}) > 0
            else
                entry.enabled = option.onSelect ~= nil and not option.notAvailable and not option.isDisabled
                local call = {
                    fn = option.onSelect, target = option.target,
                    option.param1, option.param2, option.param3, option.param4, option.param5,
                    option.param6, option.param7, option.param8, option.param9, option.param10,
                }
                if entry.enabled then
                    calls[id] = call
                end
                if all ~= nil then
                    all[id] = call
                end
            end
            if not dropped then
                list[#list + 1] = entry
            end
        end
    end
    return list
end

--- In a vehicle there's no world menu: the game's controller opens the vehicle radial menu instead
--- (42.20 ISVehicleMenu.showRadialMenu: switch seat, engine, headlights, heater, horn, windows,
--- doors, mechanics, sleep, exit). We let it build that menu into a recorder instead of the real
--- radial menu, with its open sound and the controller focus switched off for that moment (Here
--- rebuilds it every few seconds), and keep the slices: text, icon, and the function with its args.
local function recordVehicleMenu(player)
    local slices = {}
    local recorder = { sounds = {} }
    function recorder:addSlice(text, texture, fn, ...)
        slices[#slices + 1] = { text = text, texture = texture, fn = fn, args = { ... } }
        return {}
    end
    function recorder:isReallyVisible() return false end
    function recorder:getWidth() return 0 end
    function recorder:getHeight() return 0 end
    local function nothing() end
    for _, name in ipairs({ "clear", "undisplay", "setX", "setY", "addToUIManager", "setHideWhenButtonReleased" }) do
        recorder[name] = nothing
    end

    local slot = player:getPlayerNum() + 1
    local joypads = JoypadState and JoypadState.players
    local joypad = joypads and joypads[slot]
    local saved = { radial = getPlayerRadialMenu, sounds = getSoundManager, focus = setJoypadFocus }
    getPlayerRadialMenu = function() return recorder end
    getSoundManager = function() return { playUISound = nothing } end
    setJoypadFocus = nothing
    if joypads then joypads[slot] = nil end -- no focus grab, no "ignore aim until centred"
    local ok, err = pcall(ISVehicleMenu.showRadialMenu, player)
    getPlayerRadialMenu, getSoundManager, setJoypadFocus = saved.radial, saved.sounds, saved.focus
    if joypads then joypads[slot] = joypad end
    return ok, err, slices
end

local function isPaused()
    local ok, speed = pcall(function() return UIManager.getSpeedControls():getCurrentGameSpeed() end)
    return ok and speed == 0
end

--- Builds the menu for `items`: one item, or several picked together (the game's menu for a
--- selection in its own inventory: Drop, Eat, ... for all of them). The first one leads.
--- Returns true, nil, { menuId, options } or false, reason.
function B42Menu.open(player, items)
    if ISInventoryPaneContextMenu == nil or ISInventoryPaneContextMenu.createMenu == nil then
        return false, "The game's item menu is not available"
    end
    local item = items[1]
    local container = item:getContainer()
    local inInventory = container ~= nil and container:isInCharacterInventory(player)

    local menu = ISInventoryPaneContextMenu.createMenu(player:getPlayerNum(), inInventory, items, 0, 0)
    if menu == nil then
        return false, isPaused() and "The game is paused" or "The game has no menu for this item"
    end

    local calls = {}
    local ok, options = pcall(snapshot, menu, calls, "", 1, appDuplicates(), pillFunctions(items))
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
--- A world object, or the `object` of a table that carries one (Disassemble passes
--- { object = ..., square = ... }); nil for anything else. Characters, zombies and vehicles are
--- objects too, but never what an action is about: Wash at a sink passes the player first.
local function asWorldObject(value)
    if type(value) == "table" and not instanceof(value, "IsoObject") then
        value = value.object
    end
    if value ~= nil and instanceof(value, "IsoObject") and not instanceof(value, "IsoMovingObject") then
        return value
    end
    return nil
end

--- The world object a call acts on: its target, or the first parameter that is one.
local function callObject(call)
    local object = asWorldObject(call.target)
    for i = 1, 10 do
        object = object or asWorldObject(call[i])
    end
    return object
end

--- The start of the keys of objects on `square`: "x,y,z#".
local function squarePrefix(square)
    return square:getX() .. "," .. square:getY() .. "," .. square:getZ() .. "#"
end

--- Where an object is: its square and its place among the square's objects. Survives rebuilds,
--- walking away and coming back, and the object changing (a door opening swaps its sprite).
local function objectKey(object)
    local square = Util.try(object, "getSquare")
    local index = Util.try(object, "getObjectIndex")
    if square == nil or index == nil then
        return nil
    end
    return squarePrefix(square) .. index
end

--- A door, window, window frame or curtain: what the game's own door/window check decides on.
local function isOpening(object)
    for _, class in ipairs({ "IsoDoor", "IsoWindow", "IsoWindowFrame", "IsoCurtain" }) do
        if instanceof(object, class) then
            return true
        end
    end
    return instanceof(object, "IsoThumpable")
        and (Util.try(object, "isDoor") == true or Util.try(object, "isWindow") == true)
end

--- The calls under an option (itself, or every option below it, greyed ones too), in menu order.
local function callsUnder(entry, calls, out)
    if calls[entry.id] ~= nil then
        out[#out + 1] = calls[entry.id]
    end
    for _, child in ipairs(entry.children or {}) do
        callsUnder(child, calls, out)
    end
    return out
end

--- What the game's interact button would act on (its prompt, bottom right): the prompt's own
--- object (a stove, a light), or for a door or window, which the prompt names without passing it,
--- the one the game picks the same way (ISButtonPrompt.getBestAButtonAction). nil without a prompt.
local function promptObject(player, prompts)
    if prompts.aPrompt == nil then
        return nil
    end
    local params = prompts.aParams or {}
    for i = 1, 4 do
        local object = asWorldObject(params[i])
        if object ~= nil then
            return object
        end
    end
    return Util.try(player, "getContextDoorOrWindowOrWindowFrame", player:getDir())
end

--- Whether an option is one action over a list of objects (Disassemble > each object): the
--- entries under it run the same function, each on its own object. Disassemble is one even with
--- a single object. `list`: the calls under it; `objects`: how many objects they act on.
local function isObjectList(entry, list, objects)
    if #(entry.children or {}) == 0 or list[1] == nil then
        return false
    end
    for _, child in ipairs(entry.children) do
        if #(child.children or {}) > 0 then
            return false
        end
    end
    for _, call in ipairs(list) do
        if call.fn ~= list[1].fn then
            return false
        end
    end
    local disassemble = ISDisassembleMenu and ISDisassembleMenu.disassemble
    return (objects >= 2 and objects == #list) or (disassemble ~= nil and list[1].fn == disassemble)
end

--- Gives the world menu's top-level options what the app needs to keep them in place:
---   key   the object an option belongs to (its first call's object), so a card keeps its place
---         however the game orders its menu; options without an object get their name
---   tray  a list of objects under one action (see isObjectList); the app shows these apart
---   front the option for what the interact button would act on; when it acts on nothing here
---         (the game has no one-button action for a sink), the first option whose object is on
---         the square the player faces, leaving out doors and windows (the game's own check
---         above already decided against them: a window on the far wall of a sink's square)
--- `calls`: the call data of every option, greyed ones too.
local function markWorld(player, prompts, options, calls)
    local front = promptObject(player, prompts)
    local frontKey = front and objectKey(front)
    local used, firstObjects = {}, {}
    for _, entry in ipairs(options) do
        local list = callsUnder(entry, calls, {})
        local keys, firstKey, objects = {}, nil, 0
        for _, call in ipairs(list) do
            local object = callObject(call)
            local key = object and objectKey(object)
            if key ~= nil and not keys[key] then
                keys[key] = true
                objects = objects + 1
                if firstKey == nil then
                    firstKey, firstObjects[entry] = key, object
                end
            end
        end
        entry.tray = isObjectList(entry, list, objects) or nil
        local key = entry.tray and ("list:" .. entry.name) or firstKey or ("name:" .. entry.name)
        used[key] = (used[key] or 0) + 1
        entry.key = used[key] == 1 and key or (key .. "|" .. used[key])
        entry.front = (frontKey ~= nil and not entry.tray and keys[frontKey]) or nil
    end
    for _, entry in ipairs(options) do
        if entry.front then
            return
        end
    end
    local square = Util.try(player:getCurrentSquare(), "getAdjacentSquare", player:getDir())
    local prefix = square and squarePrefix(square)
    for _, entry in ipairs(options) do
        local object = firstObjects[entry]
        if prefix ~= nil and object ~= nil and not entry.tray and not isOpening(object)
            and string.sub(entry.key, 1, #prefix) == prefix then
            entry.front = true
            return
        end
    end
end

--- Returns true, nil, { menuId, options } or false, reason.
function B42Menu.openWorld(player)
    local playerNum = player:getPlayerNum()
    local prompts = getButtonPrompts and getButtonPrompts(playerNum)
    if prompts == nil or ISContextManager == nil then
        return false, "The game's world menu is not available"
    end
    if isPaused() then
        return false, "The game is paused"
    end
    local vehicle = player:getVehicle()
    if vehicle then
        return B42Menu.openVehicle(player, vehicle)
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
    local all = {}
    local ok, options = pcall(snapshot, menu, calls, "", 1, nil, nil, all)
    menu:hideAndChildren() -- same tick as createWorldMenu, so it never shows on the top screen
    if not ok then
        return false, "Could not read the game's menu: " .. tostring(options)
    end
    local marked, err = pcall(markWorld, player, prompts, options, all)
    if not marked then
        print("[ZomboidDS] Here: could not mark the world menu: " .. tostring(err))
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

--- "Here" in a vehicle: the vehicle's radial menu (see recordVehicleMenu), as one card named after
--- the vehicle with its slices as actions. A slice without a function is the game's reason why not
--- (e.g. "Not tired enough"): greyed, with that text. Returns true, nil, { menuId, options } or false, reason.
function B42Menu.openVehicle(player, vehicle)
    if ISVehicleMenu == nil or ISVehicleMenu.showRadialMenu == nil then
        return false, "The game's vehicle menu is not available"
    end
    local ok, err, slices = recordVehicleMenu(player)
    if not ok then
        return false, "Could not read the vehicle menu: " .. tostring(err)
    end
    if #slices == 0 then
        return false, "Nothing to do here"
    end
    local calls, children = {}, {}
    for index, slice in ipairs(slices) do
        local id = "1." .. index
        local text = plainText(tostring(slice.text)) or "?"
        local entry = { id = id, name = text, icon = iconName(slice.texture), enabled = slice.fn ~= nil }
        if slice.fn ~= nil then
            local a = slice.args
            calls[id] = { fn = slice.fn, target = a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11] }
        else
            entry.tooltip = text
        end
        children[#children + 1] = entry
    end
    local script = Util.try(vehicle, "getScript")
    local name = Util.try(script, "getName")
    name = name and getText("IGUI_VehicleName" .. name) or "Vehicle"
    local options = { { id = "1", name = name, enabled = true, children = children } }

    local signature = Signature.of(options)
    local previous = menus.world
    local id
    if previous ~= nil and previous.signature == signature then
        id = previous.id
    else
        nextMenuId = nextMenuId + 1
        id = "m" .. nextMenuId
    end
    menus.world = { id = id, created = getTimestampMs(), calls = calls, signature = signature, vehicle = vehicle }
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

--- The game's tailoring menu for one body part of a garment (Patch Hole / Add Padding with each
--- fabric, Patch all Holes, Remove Patch, or the greyed "Tailoring" with its reason), as the
--- Inspect window builds it on a click (42.20 ISGarmentUI:doContextMenu). The window is created but
--- never shown: doContextMenu needs its list of the garment's parts for the "all" options.
--- Returns true, nil, { menuId, options } or false, reason.
function B42Menu.openGarment(player, clothing, partId)
    if ISGarmentUI == nil or ISGarmentUI.doContextMenu == nil then
        return false, "The game's garment window is not available"
    end
    if isPaused() then
        return false, "The game is paused"
    end
    local part = nil
    local covered = clothing:getCoveredParts()
    for i = 0, covered:size() - 1 do
        if tostring(covered:get(i)) == partId then
            part = covered:get(i)
            break
        end
    end
    if part == nil then
        return false, "No such body part"
    end

    local playerNum = player:getPlayerNum()
    local joypad = JoypadState and JoypadState.players[playerNum + 1]
    local focusBefore = joypad and joypad.focus
    local window = ISGarmentUI:new(0, 0, player, clothing)
    window:initialise()
    local menu = window:doContextMenu(part, 0, 0)
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
        return false, getText("IGUI_garment_CantRepair")
    end

    nextMenuId = nextMenuId + 1
    menus.garment = { id = "m" .. nextMenuId, created = getTimestampMs(), itemId = clothing:getID(), calls = calls }
    return true, nil, { menuId = menus.garment.id, options = options }
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
    -- Item and health menus expire; a world or vehicle menu stays valid while the player stays put
    -- (or in the vehicle; checked below).
    if menu == nil or (not menu.world and not menu.vehicle and getTimestampMs() - menu.created > MENU_TTL_MS) then
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
    elseif menu.vehicle ~= nil then
        -- The car moves, so no position check: only that you're still in it.
        if player:getVehicle() ~= menu.vehicle then
            menus[slot] = nil
            return false, "You're no longer in that vehicle"
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
