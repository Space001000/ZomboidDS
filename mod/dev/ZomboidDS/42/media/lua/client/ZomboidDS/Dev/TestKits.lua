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

kit("build", "Build", "Hammer, saw, 20 planks, 100 nails, hinges and doorknobs (a Wood Door); propane torch, "
    .. "welding mask and rods, 3 metal pipes but only 2 small metal sheets (something short); Carpentry 5, Welding 3.",
    function(player)
        local inventory = player:getInventory()
        add(inventory, "Base.Hammer")
        add(inventory, "Base.Saw")
        add(inventory, "Base.Plank", 20)
        add(inventory, "Base.Nails", 20) -- 5 a piece
        add(inventory, "Base.Hinge", 4)
        add(inventory, "Base.Doorknob", 2)
        add(inventory, "Base.BlowTorch")
        add(inventory, "Base.WeldingMask")
        add(inventory, "Base.WeldingRods")
        add(inventory, "Base.MetalPipe", 3)
        add(inventory, "Base.SmallSheetMetal", 2)
        setSkill(player, Perks.Woodwork, 5)
        setSkill(player, Perks.MetalWelding, 3)
    end)

kit("craft", "Craft", "Hunting knife, saw, hammer, scissors; 2 logs, 4 planks, 25 nails, 2 sheets, a tree branch, "
    .. "twine, duct tape, glue: something to make in most of Craft's categories.",
    function(player)
        local inventory = player:getInventory()
        add(inventory, "Base.HuntingKnife")
        add(inventory, "Base.Saw")
        add(inventory, "Base.Hammer")
        add(inventory, "Base.Scissors")
        add(inventory, "Base.Log", 2)
        add(inventory, "Base.Plank", 4)
        add(inventory, "Base.Nails", 5)
        add(inventory, "Base.Sheet", 2)
        add(inventory, "Base.TreeBranch2")
        add(inventory, "Base.Twine")
        add(inventory, "Base.DuctTape")
        add(inventory, "Base.Glue")
    end)

--- Food `age` days old: fresh, stale (between the two limits) or rotten (past the last one).
local function aged(item, state)
    local off, offMax = item:getOffAge(), item:getOffAgeMax()
    if state == "stale" then
        item:setAge((off + offMax) / 2)
    elseif state == "rotten" then
        item:setAge(offMax + 1)
    end
    return item
end

kit("food", "Food", "5 fresh apples (a stack), a stale and a rotten one; raw, frozen and burnt steak, cooked chicken; "
    .. "soup and a tin opener; a pot half full of water, a full water bottle, a pan; bread set Unwanted.",
    function(player)
        local inventory = player:getInventory()
        add(inventory, "Base.Apple", 5)
        aged(add(inventory, "Base.Apple"), "stale")
        aged(add(inventory, "Base.Apple"), "rotten")
        add(inventory, "Base.Steak")
        add(inventory, "Base.Steak"):setFreezingTime(100)
        local burnt = add(inventory, "Base.Steak")
        burnt:setCooked(true)
        burnt:setBurnt(true)
        add(inventory, "Base.Chicken"):setCooked(true)
        add(inventory, "Base.TinnedSoup")
        add(inventory, "Base.TinOpener")
        for fullType, fraction in pairs({ ["Base.Pot"] = 0.5, ["Base.WaterBottle"] = 1 }) do
            local fluids = add(inventory, fullType):getFluidContainer()
            if fluids then fluids:addFluid(Fluid.Water, fluids:getCapacity() * fraction) end
        end
        add(inventory, "Base.Pan")
        add(inventory, "Base.Bread"):setUnwanted(player, true)
    end)

kit("medic", "Medic", "A scratch on the left hand, a cut on the right forearm, glass in a deep wound on the left "
    .. "thigh; bandages, ripped sheets, disinfectant, tweezers, suture needle; First Aid 4.",
    function(player)
        local body = player:getBodyDamage()
        body:getBodyPart(BodyPartType.Hand_L):setScratched(true, true)
        body:getBodyPart(BodyPartType.ForeArm_R):setCut(true, true)
        body:getBodyPart(BodyPartType.UpperLeg_L):generateDeepShardWound()
        local inventory = player:getInventory()
        add(inventory, "Base.Bandage", 3)
        add(inventory, "Base.RippedSheets", 3)
        add(inventory, "Base.Disinfectant")
        add(inventory, "Base.Tweezers")
        add(inventory, "Base.SutureNeedle")
        setSkill(player, Perks.Doctor, 4)
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
