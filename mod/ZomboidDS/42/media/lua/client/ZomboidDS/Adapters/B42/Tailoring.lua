--- Tailoring: the player's clothes with their holes and patches, one garment as the game's Inspect
--- window shows it, and the sewing kit that decides what can be patched.
---
--- 42.20's Inspect window (ISGarmentUI, opened from a garment's menu) lists the body parts the
--- garment covers (Clothing:getCoveredParts, BloodBodyPartType, Back included) with Bite / Scratch /
--- Bullet defence (getDefForPart: 0 over a hole), holes, blood and patches, and offers a menu per
--- part (ISGarmentUI:doContextMenu: Patch Hole / Add Padding with each fabric, Remove Patch). That
--- menu goes to the app through B42Menu.openGarment; this module reads the rest. The kit is found
--- the way doContextMenu finds it: by item type and tag, anywhere in the player's inventory and bags.
local Util = require("ZomboidDS/Adapters/B42/Util")
local Items = require("ZomboidDS/Adapters/B42/Items")
local Containers = require("ZomboidDS/Adapters/B42/Containers")
local B42Menu = require("ZomboidDS/Adapters/B42Menu")
local try, round = Util.try, Util.round

local Tailoring = {}

-- The fabrics doContextMenu patches with, in its order.
local FABRICS = { "RippedSheets", "DenimStrips", "LeatherStrips" }

local function inspectable(item)
    if not instanceof(item, "Clothing") or try(item, "isHidden") == true then
        return false -- B42 wounds are hidden clothing
    end
    local parts = try(item, "getCoveredParts")
    return parts ~= nil and parts:size() > 0
end

local function scriptItem(fullType)
    local manager = ScriptManager and ScriptManager.instance
    return manager and try(manager, "getItem", fullType) or nil
end

local function holes(item)
    return try(item, "getHolesNumber") or 0
end

--- The garments in the player's inventory and bags: worn ones first, in the game's worn order.
local function garments(player)
    local list, seen = {}, {}
    local worn = try(player, "getWornItems")
    for i = 0, (worn and worn:size() or 0) - 1 do
        local item = try(worn:get(i), "getItem")
        if item ~= nil and inspectable(item) and not seen[item:getID()] then
            seen[item:getID()] = true
            list[#list + 1] = { item = item, worn = true }
        end
    end
    local function walk(container)
        local items = container:getItems()
        for i = 0, items:size() - 1 do
            local item = items:get(i)
            if inspectable(item) and not seen[item:getID()] then
                seen[item:getID()] = true
                list[#list + 1] = { item = item, worn = false }
            end
            local inner = instanceof(item, "InventoryContainer") and try(item, "getInventory") or nil
            if inner ~= nil then
                walk(inner)
            end
        end
    end
    walk(player:getInventory())
    return list
end

local function sewingKit(player)
    local inventory = player:getInventory()
    local thread = try(inventory, "getItemFromType", "Thread", true, true)
        or (ItemTag and try(inventory, "getItemFromTag", ItemTag.THREAD, true, true))
    local needle = try(inventory, "getItemFromType", "Needle", true, true)
        or (ItemTag and try(inventory, "getFirstTagRecurse", ItemTag.SEWING_NEEDLE))
    local fabrics = {}
    for _, kind in ipairs(FABRICS) do
        local script = scriptItem("Base." .. kind)
        local icon = try(script, "getIcon")
        fabrics[#fabrics + 1] = {
            type = kind,
            name = try(script, "getDisplayName") or kind,
            icon = icon and ("Item_" .. icon) or nil,
            count = try(inventory, "getItemCount", kind, true) or 0,
        }
    end
    return { needle = needle ~= nil, thread = thread ~= nil, fabrics = fabrics }
end

--- The clothes the player has, each as the inventory describes it plus its holes and patches, and
--- the sewing kit.
function Tailoring.list(player)
    local result = {}
    for _, entry in ipairs(garments(player)) do
        local item = entry.item
        local described = Items.describe(player, item, true)
        result[#result + 1] = {
            id = described.id,
            name = described.name,
            icon = described.icon,
            condition = described.condition,
            worn = entry.worn or nil,
            holes = holes(item),
            patches = try(item, "getPatchesNumber") or 0,
            repairable = try(item, "getFabricType") ~= nil,
        }
    end
    return true, nil, {
        garments = result,
        kit = sewingKit(player),
        tailoring = try(player, "getPerkLevel", Perks.Tailoring),
    }
end

--- The sewing the player is doing on `item` right now (the first action in their queue), as the
--- Inspect window shows it on the part's row: the game's own label and how far along it is.
local function sewing(player, item)
    local queue = ISTimedActionQueue and ISTimedActionQueue.getTimedActionQueue(player)
    local action = queue and queue.queue and queue.queue[1]
    if action == nil or action.clothing == nil or action.part == nil or action.clothing:getID() ~= item:getID() then
        return nil
    end
    local name
    if action.Type == "ISRepairClothing" then
        local hole = try(try(item, "getVisual"), "getHole", action.part) or 0
        name = getText(hole > 0 and "ContextMenu_PatchHole" or "ContextMenu_AddPadding")
    elseif action.Type == "ISRemovePatch" then
        name = getText("ContextMenu_RemovePatch")
    else
        return nil
    end
    return { part = tostring(action.part), name = name, progress = round(try(action, "getJobDelta") or 0, 2) }
end

--- One garment as the Inspect window shows it.
function Tailoring.garment(player, item)
    if item == nil or not inspectable(item) then
        return false, "That's not something you can inspect"
    end
    local described = Items.describe(player, item, true)
    local visual = try(item, "getVisual")
    local doing = sewing(player, item)
    local parts = {}
    local covered = item:getCoveredParts()
    for i = 0, covered:size() - 1 do
        local part = covered:get(i)
        local id = tostring(part)
        local patch = try(item, "getPatchType", part)
        local blood = try(item, "getBloodlevelForPart", part) or 0
        parts[#parts + 1] = {
            id = id,
            name = try(part, "getDisplayName") or id,
            bite = round(try(item, "getDefForPart", part, true, false), 0),
            scratch = round(try(item, "getDefForPart", part, false, false), 0),
            bullet = round(try(item, "getDefForPart", part, false, true), 0),
            hole = (try(visual, "getHole", part) or 0) > 0 or nil,
            blood = blood > 0 and round(blood, 2) or nil,
            patch = patch and getText("IGUI_TypeOfPatch", try(patch, "getFabricTypeName")) or nil,
            sewing = doing and doing.part == id and { name = doing.name, progress = doing.progress } or nil,
        }
    end
    local max = try(item, "getConditionMax") or 0
    return true, nil, {
        id = described.id,
        name = described.name,
        icon = described.icon,
        worn = described.equipped == "worn" or nil,
        condition = max > 0 and round((try(item, "getCondition") or 0) / max, 2) or nil,
        blood = round((try(item, "getBloodlevel") or 0) / 100, 2),
        dirt = round((try(item, "getDirtiness") or 0) / 100, 2),
        -- The game's "Can't be repaired." for a garment without a fabric (boots, helmets).
        cantRepair = try(item, "getFabricType") == nil and getText("IGUI_garment_CantRepair") or nil,
        tailoring = try(player, "getPerkLevel", Perks.Tailoring),
        parts = parts,
    }
end

Tailoring.commands = {
    tailor_list = function(player, _args)
        return Tailoring.list(player)
    end,
    tailor_garment = function(player, args)
        return Tailoring.garment(player, Containers.findItem(player, tonumber(args.itemId)))
    end,
    -- The game's menu for one part of the garment (see B42Menu.openGarment); chosen with menu_select.
    tailor_menu = function(player, args)
        local item = Containers.findItem(player, tonumber(args.itemId))
        if item == nil or not inspectable(item) then
            return false, "item not found"
        end
        return B42Menu.openGarment(player, item, tostring(args.part))
    end,
}

return Tailoring
