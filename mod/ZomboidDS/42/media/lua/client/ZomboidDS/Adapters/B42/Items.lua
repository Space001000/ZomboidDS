--- Items as the app shows them: name, icon, category, condition, freshness, cooking, fluids, what it may offer.
local Util = require("ZomboidDS/Adapters/B42/Util")
local try, round = Util.try, Util.round

local Items = {}

--- Texture name as the bridge's /icons endpoint knows it (e.g. "Item_Axe").
local function iconName(item)
    local name = Util.textureFileName(try(try(item, "getTex"), "getName"))
    if name then
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

--- The category as the game's inventory list shows it ("Cooking", not "CookingWeapon").
local function categoryName(item)
    local category = try(item, "getDisplayCategory") or try(item, "getCategory")
    if category == nil then
        return nil
    end
    local ok, text = pcall(getText, "IGUI_ItemCat_" .. category)
    if not ok or text == nil or text == "IGUI_ItemCat_" .. category then
        return category -- no translation (e.g. a mod's category)
    end
    return text
end

--- Food's age as the game names it (Food:getName in 42.20: "Fresh" while age < offAge, "Stale"
--- from offAge, "Rotten" from offAgeMax; 1e9 means it never goes off). Nil for other items.
local NEVER = 1000000000
local function freshness(item)
    if not instanceof(item, "Food") or try(item, "isFertilized") == true then
        return nil
    end
    local age, offAge, offAgeMax = try(item, "getAge"), try(item, "getOffAge"), try(item, "getOffAgeMax")
    if age == nil or offAge == nil or offAgeMax == nil then
        return nil
    end
    if offAgeMax < NEVER and age >= offAgeMax then return "rotten" end
    if offAgeMax < NEVER and age >= offAge then return "stale" end
    if offAge < NEVER and age < offAge then return "fresh" end
    return nil
end

--- The words the game puts in a food's name for its freshness ("Bread (Stale)"). Burnt food's
--- name says only "Burnt" (Food:getName), so then there's none.
local function freshnessText(item, freshness)
    if freshness == nil or try(item, "isBurnt") == true then return nil end
    if freshness == "rotten" then return try(item, "getOffString") end
    return getText(freshness == "fresh" and "Tooltip_food_Fresh" or "Tooltip_food_Stale")
end

local function tagged(item, name)
    local tag = ItemTag ~= nil and ItemTag[name] or nil
    return tag ~= nil and try(item, "hasTag", tag) == true
end

--- Cooked, uncooked or burnt, with the word the game's name uses (Food:getName in 42.20: Grilled
--- and Toasted are kinds of cooked; HIDE_COOKED / HIDE_UNCOOKED leave the word out). While it heats,
--- how far along, as the game's inventory draws it (ISInventoryPane:drawItemDetails): cooking up to
--- minutesToCook, then burning up to minutesToBurn.
local function cooking(item)
    if not instanceof(item, "Food") then return nil end
    local state, text
    if try(item, "isBurnt") == true then
        state, text = "burnt", try(item, "getBurntString")
    elseif try(item, "isCooked") == true then
        if not tagged(item, "HIDE_COOKED") then
            state = "cooked"
            if tagged(item, "GRILLED") then text = getText("Tooltip_food_Grilled")
            elseif tagged(item, "TOASTABLE") then text = getText("Tooltip_food_Toasted")
            else text = try(item, "getCookedString") end
        end
    elseif try(item, "isIsCookable") == true and not tagged(item, "HIDE_COOKED") and not tagged(item, "HIDE_UNCOOKED") then
        state, text = "uncooked", try(item, "getUnCookedString")
    end
    local progress, burning
    if try(item, "isIsCookable") == true and try(item, "isFrozen") ~= true and (try(item, "getHeat") or 0) > 1.6
        and state ~= "burnt" then
        local time, toCook, toBurn = try(item, "getCookingTime"), try(item, "getMinutesToCook"), try(item, "getMinutesToBurn")
        if time and toCook and toBurn and toCook > 0 then
            if time > toCook and toBurn > toCook then
                burning, progress = true, (time - toCook) / (toBurn - toCook)
            else
                progress = time / toCook
            end
            progress = round(math.min(math.max(progress, 0), 1), 2)
        end
    end
    if state == nil and progress == nil then return nil end
    return { state = state, text = text, progress = progress, burning = burning }
end

--- How full a bottle, pot or bucket is (B42 fluid containers), in litres, and what's in it: the
--- main fluid's name, or a mixture; the colour the game's tooltip draws its bar in.
local function fluid(item)
    local container = try(item, "getFluidContainer")
    local capacity = try(container, "getCapacity")
    if not capacity or capacity <= 0 then return nil end
    local amount = try(container, "getAmount") or 0
    local result = { amount = round(amount, 3), capacity = round(capacity, 3) }
    if amount > 0 then
        if try(container, "isMixture") == true then
            result.mixture = true
        else
            result.name = try(try(container, "getPrimaryFluid"), "getTranslatedName")
        end
        local color = try(container, "getColor")
        if color then
            result.color = { round(try(color, "getRedFloat"), 3), round(try(color, "getGreenFloat"), 3), round(try(color, "getBlueFloat"), 3) }
        end
    end
    return result
end

--- Read, watched or heard already: the game's inventory puts a tick on it (ISInventoryPane render:
--- isLiteratureRead, hasBeenSeen, hasBeenHeard, hasReadMap).
local function done(player, item)
    local pane = ISInventoryPane
    if pane ~= nil and pane.isLiteratureRead ~= nil then
        local ok, read = pcall(pane.isLiteratureRead, pane, player, item)
        if ok and read == true then return true end
    end
    return try(item, "hasBeenSeen", player) == true or try(item, "hasBeenHeard", player) == true
        or try(player, "hasReadMap", item) == true
end

--- An item as the app shows it. `inInventory`: whether it's in the player's main inventory, the
--- only place the quick actions (equip, wear, drop) apply; elsewhere the app moves it first.
function Items.describe(player, item, inInventory)
    -- Food ages, freezes and thaws only when something asks (Food:updateAge catches up on the hours
    -- since it last did); in single player that's the game's inventory window, which does this for
    -- every item it draws (42.20 ISInventoryPane:renderdetails). With that window closed and only
    -- the app open, food stayed frozen and fresh, then rotted at once without the mod.
    try(item, "updateAge")
    if instanceof(item, "Clothing") then try(item, "updateWetness") end
    local equipped = equippedSlot(player, item)
    local plainName = try(item, "getDisplayName")
    -- The name as the game's inventory list shows it: "Steak (Fresh, Cooked)", "Water Bottle (Water)".
    local name = try(item, "getName", player) or plainName
    local fresh = freshness(item)
    return {
        id = item:getID(),
        type = try(item, "getFullType"),
        name = name,
        shortName = name ~= plainName and plainName or nil,
        category = categoryName(item),
        freshness = fresh,
        freshnessText = freshnessText(item, fresh),
        cooking = cooking(item),
        fluid = fluid(item),
        read = done(player, item) or nil,
        -- "Set Unwanted" in the game's menu (More): its inventory greys these out.
        unwanted = try(item, "isUnwanted", player) == true or nil,
        icon = iconName(item),
        weight = round(try(item, "getActualWeight"), 2),
        condition = condition(item),
        equipped = equipped,
        actions = inInventory and actionsFor(item, equipped) or {},
    }
end

--- The visible items of a container, like the game's inventory window shows them: hidden items are
--- skipped (e.g. B42 models wounds as invisible worn "Wound_*" clothing).
function Items.of(player, container, inInventory)
    local items = container:getItems()
    local list = {}
    for i = 0, items:size() - 1 do
        local item = items:get(i)
        if try(item, "isHidden") ~= true then
            list[#list + 1] = Items.describe(player, item, inInventory)
        end
    end
    return list
end

--- The main inventory (`inventory` message); bags and containers around are in Containers.
function Items.snapshotInventory(player)
    local inventory = player:getInventory()
    return {
        weight = {
            current = round(try(inventory, "getCapacityWeight"), 2),
            max = round(try(player, "getMaxWeight"), 2),
        },
        items = Items.of(player, inventory, true),
    }
end

--- Item `id` in the player's inventory, including bags.
function Items.inInventory(player, id)
    return try(player:getInventory(), "getItemWithIDRecursiv", id)
end

function Items.isKeyRing(item)
    return try(item, "isItemType", ItemType.KEY_RING) == true or try(item, "hasTag", ItemTag.KEY_RING) == true
end

return Items
