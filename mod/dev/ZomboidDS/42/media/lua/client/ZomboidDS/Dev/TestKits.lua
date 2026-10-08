--- DEVELOPMENT ONLY. Test kits: one command puts a feature's test setup on the player (items, worn
--- clothes, skill levels), so testing on the device doesn't start with a hunt for materials.
---
--- This file lives in mod/dev/ and only goes into the dev builds' mod zip (version "<x>-dev", see
--- bridge/adapter-b42/build.gradle.kts). The release zip is built from mod/ZomboidDS alone and its
--- build fails if a Dev/ file ever gets in. The app's Test kits section exists only in the debug and
--- dev builds as well.
---
--- Commands: dev_kits (the list) and dev_kit { kit } (give it). Single player.
local B42 = require("ZomboidDS/Adapters/B42")

local Kits = {}
local order = {}

local function kit(id, name, description, give)
    Kits[id] = { name = name, description = description, give = give }
    order[#order + 1] = id
end

local function add(container, fullType, count)
    local last
    for _ = 1, count or 1 do
        last = container:AddItem(fullType)
    end
    return last
end

--- Holes in `parts` (BloodBodyPartType names), each costing condition like a real one.
local function holes(clothing, parts)
    for _, name in ipairs(parts) do
        clothing:getVisual():setHole(BloodBodyPartType[name])
        clothing:setCondition(math.max(1, math.floor(clothing:getCondition() - clothing:getCondLossPerHole())), false)
    end
end

local function wear(player, item)
    ISTimedActionQueue.add(ISWearClothing:new(player, item))
end

local function setSkill(player, perk, level)
    player:setPerkLevelDebug(perk, level)
    player:getXp():setXPToLevel(perk, level)
end

kit("tailor", "Tailor", "Needle, thread, 6 Rags, 3 Denim Strips (no Leather Strips); a worn Leather Jacket with "
    .. "two holes and a leather patch; a Hoodie with holes in a backpack; Military Boots; Tailoring 4.",
    function(player)
        local inventory = player:getInventory()
        add(inventory, "Base.Needle")
        add(inventory, "Base.Thread")
        add(inventory, "Base.RippedSheets", 6)
        add(inventory, "Base.DenimStrips", 3)
        local jacket = add(inventory, "Base.Jacket_LeatherBlack")
        holes(jacket, { "UpperArm_L", "ForeArm_R" })
        jacket:addPatch(player, BloodBodyPartType.Torso_Lower, instanceItem("Base.LeatherStrips"))
        wear(player, jacket)
        local bag = add(inventory, "Base.Bag_ALICEpack")
        local hoodie = add(bag:getInventory(), "Base.HoodieDOWN_WhiteTINT")
        holes(hoodie, { "Torso_Upper", "UpperArm_R", "ForeArm_L" })
        add(inventory, "Base.Shoes_ArmyBoots")
        setSkill(player, Perks.Tailoring, 4)
        triggerEvent("OnClothingUpdated", player)
    end)

B42.commands.dev_kits = function(_player, _args)
    local list = {}
    for _, id in ipairs(order) do
        list[#list + 1] = { id = id, name = Kits[id].name, description = Kits[id].description }
    end
    return true, nil, { kits = list }
end

B42.commands.dev_kit = function(player, args)
    local chosen = Kits[tostring(args.kit)]
    if chosen == nil then
        return false, "No such kit"
    end
    if isClient() then
        return false, "Test kits are for single player"
    end
    local ok, err = pcall(chosen.give, player)
    if not ok then
        return false, "The kit failed: " .. tostring(err)
    end
    return true
end

table.insert(B42.capabilities, "dev.kits")
