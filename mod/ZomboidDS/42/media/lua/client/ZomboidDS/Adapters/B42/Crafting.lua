--- Crafting: the recipes the game's crafting window lists, what one needs, and crafting it.
---
--- 42.20's crafting window (ISHandcraftWindow -> ISHandCraftPanel) is Lua around the Java
--- HandcraftLogic, which knows the recipes, what the player has in reach and what can be made. We
--- use our own HandcraftLogic the same way the panel does, so the rules (known recipes, skills,
--- ingredients from bags and containers around, a surface to work on) are the game's. Crafting runs
--- the window's own start (ISWidgetHandCraftControl:startHandcraft) with a stand-in for its craft
--- control: it fetches the ingredients, walks to the surface, queues the actions, puts items back.
local Util = require("ZomboidDS/Adapters/B42/Util")
local try, round = Util.try, Util.round

local Crafting = {}

-- The recipes the window lists without a workbench (ISHandcraftWindow's default query).
local RECIPE_QUERY = "InHandCraft;AnySurfaceCraft"

local logics = {}    -- playerNum -> HandcraftLogic for listing and details
local recipesById = {} -- recipe id -> CraftRecipe, from the latest list

local function textureName(texture)
    return Util.textureFileName(try(texture, "getName"))
end

--- A surface to craft on next to the player (a table, a counter), as the window looks for one.
local function craftSurface(player)
    return ISEntityUI and ISEntityUI.FindCraftSurface and ISEntityUI.FindCraftSurface(player, 1) or nil
end

--- A HandcraftLogic that knows the player's containers and the recipes, like the window's panel
--- after refreshRecipeList.
local function newLogic(player)
    local logic = HandcraftLogic.new(player, nil, craftSurface(player))
    -- As the panel does (ISHandCraftPanel:new): the craft action takes the ingredients the logic
    -- picked (ISHandcraftAction.FromLogicMultiple); without this it fails.
    logic:setManualSelectInputs(true)
    logic:setContainers(ISInventoryPaneContextMenu.getContainers(player))
    logic:setRecipes(CraftRecipeManager.queryRecipes(RECIPE_QUERY))
    return logic
end

local function logicFor(player)
    local playerNum = player:getPlayerNum()
    local logic = logics[playerNum]
    if logic == nil then
        logic = newLogic(player)
        logics[playerNum] = logic
    else
        logic:setIsoObject(craftSurface(player))
        if logic:setContainers(ISInventoryPaneContextMenu.getContainers(player)) then
            logic:setRecipes(CraftRecipeManager.queryRecipes(RECIPE_QUERY))
        end
    end
    return logic
end

local function recipeId(recipe)
    return try(recipe, "getScriptObjectFullType") or try(recipe, "getName")
end

--- Whether the window would list it: some recipes decide that themselves (OnAddToMenu).
local function listed(player, recipe)
    local onAddToMenu = try(recipe, "getOnAddToMenu")
    if onAddToMenu == nil then
        return true
    end
    local ok, shown = pcall(callLuaBool, onAddToMenu, { player = player, recipe = recipe, shouldShowAll = false })
    return not ok or shown
end

--- Like the window's list (ISRecipeScrollingListBox:isCraftable).
local function canCraft(logic, player, recipe)
    if try(player, "isBuildCheat") == true then
        return true
    end
    local info = logic:getCachedRecipeInfo(recipe)
    return info == nil or (info:isValid() and info:isCanPerform())
end

local function categoryName(category)
    local key = "IGUI_CraftingCategories_" .. string.upper(string.sub(category, 1, 1)) .. string.sub(category, 2)
    local ok, text = pcall(getText, key)
    if not ok or text == nil or text == key then
        return category
    end
    return text
end

--- The recipes the game's crafting window lists for the player right now, with whether each can
--- be made, and the categories.
function Crafting.list(player)
    if HandcraftLogic == nil or CraftRecipeManager == nil then
        return false, "The game's crafting isn't available"
    end
    local logic = logicFor(player)
    logic:filterRecipeList("", "")
    local all = logic:getRecipeList():getAllRecipes()
    local recipes, seenCategories, categories = {}, {}, {}
    recipesById = {}
    for i = 0, all:size() - 1 do
        local recipe = all:get(i)
        if listed(player, recipe) then
            local id = recipeId(recipe)
            local category = try(recipe, "getCategory")
            recipesById[id] = recipe
            recipes[#recipes + 1] = {
                id = id,
                name = try(recipe, "getTranslationName") or id,
                icon = textureName(try(recipe, "getIconTexture")),
                category = category,
                canCraft = canCraft(logic, player, recipe),
            }
            if category ~= nil and not seenCategories[category] then
                seenCategories[category] = true
                categories[#categories + 1] = { id = category, name = categoryName(category) }
            end
        end
    end
    return true, nil, { recipes = recipes, categories = categories }
end

local function findRecipe(id)
    local recipe = recipesById[id]
    if recipe == nil and ScriptManager and ScriptManager.instance then
        recipe = try(ScriptManager.instance, "getCraftRecipe", id)
    end
    return recipe
end

local function itemName(item)
    return try(item, "getDisplayName") or try(item, "getName")
end

local function itemIcon(item)
    local name = textureName(try(item, "getNormalTexture"))
    if name == nil then
        local icon = try(item, "getIcon")
        name = icon and ("Item_" .. icon) or nil
    end
    return name
end

--- One ingredient as the window's input widget shows it (ISWidgetInput): the item (the first one
--- the player has, else the first that would do), how many are needed and whether it's satisfied.
local function describeInput(logic, input)
    local entry = {
        need = try(input, "getIntAmount"),
        ok = logic:isInputSatisfied(input) == true,
        keep = (try(input, "isKeep") == true or try(input, "isTool") == true) or nil,
    }
    local resource = try(input, "getResourceType")
    if resource == ResourceType.Item then
        local have = logic:getSatisfiedInputItems(input)
        local choices = (have ~= nil and have:size() > 0) and have or try(input, "getPossibleInputItems")
        local first = choices ~= nil and choices:size() > 0 and choices:get(0) or nil
        entry.name = itemName(first)
        entry.icon = itemIcon(first)
        entry.others = choices ~= nil and choices:size() > 1 and (choices:size() - 1) or nil
        local items = logic:getSatisfiedInputInventoryItems(input)
        entry.have = items ~= nil and items:size() or 0
    elseif resource == ResourceType.Fluid then
        local fluids = try(input, "getPossibleInputFluids")
        local first = fluids ~= nil and fluids:size() > 0 and fluids:get(0) or nil
        entry.name = try(first, "getDisplayName") or try(first, "getFluidTypeString")
        entry.need = round(try(input, "getAmount"), 2)
        entry.have = round(logic:getInputUses(input), 2)
        entry.unit = "L"
    else
        entry.name = tostring(resource)
    end
    return entry
end

--- Every input of a recipe, with the player's items counted.
local function describeInputs(logic, recipe)
    local inputs, list = {}, try(recipe, "getInputs")
    for i = 0, (list and list:size() or 0) - 1 do
        local ok, entry = pcall(describeInput, logic, list:get(i))
        if ok then inputs[#inputs + 1] = entry end
    end
    return inputs
end

local function describeOutput(output)
    local items = try(output, "getPossibleResultItems")
    local first = items ~= nil and items:size() > 0 and items:get(0) or nil
    if first ~= nil then
        return { name = itemName(first), icon = itemIcon(first), amount = try(output, "getIntAmount") }
    end
    local fluid = try(output, "getFluid")
    return { name = try(fluid, "getDisplayName") or try(fluid, "getFluidTypeString"), amount = round(try(output, "getAmount"), 2), unit = "L" }
end

--- The skills a recipe needs: { name, level, have }.
local function describeSkills(player, recipe)
    local skills = {}
    for i = 0, (try(recipe, "getRequiredSkillCount") or 0) - 1 do
        local skill = recipe:getRequiredSkill(i)
        skills[#skills + 1] = {
            name = try(try(skill, "getPerk"), "getName"),
            level = try(skill, "getLevel"),
            have = try(player, "getPerkLevel", try(skill, "getPerk")),
        }
    end
    return skills
end

--- What a recipe needs and makes, whether the player can make it now and how many times.
function Crafting.recipe(player, id)
    local recipe = findRecipe(id)
    if recipe == nil then
        return false, "That recipe isn't available"
    end
    local logic = logicFor(player)
    logic:setRecipe(recipe)
    logic:autoPopulateInputs()
    local data = {
        id = id,
        name = try(recipe, "getTranslationName") or id,
        icon = textureName(try(recipe, "getIconTexture")),
        category = try(recipe, "getCategory"),
        seconds = try(recipe, "getTime", player),
        canCraft = canCraft(logic, player, recipe),
        max = try(logic, "getPossibleCraftCount", true) or 0,
        inputs = describeInputs(logic, recipe),
        outputs = {},
        skills = {},
    }
    local outputs = try(recipe, "getOutputs")
    for i = 0, (outputs and outputs:size() or 0) - 1 do
        local ok, entry = pcall(describeOutput, outputs:get(i))
        if ok then data.outputs[#data.outputs + 1] = entry end
    end
    data.skills = describeSkills(player, recipe)
    return true, nil, data
end

--- Crafts `id` `count` times, the way the window's Craft button does.
function Crafting.craft(player, id, count)
    local recipe = findRecipe(id)
    if recipe == nil then
        return false, "That recipe isn't available"
    end
    if ISWidgetHandCraftControl == nil or ISWidgetHandCraftControl.startHandcraft == nil then
        return false, "The game's crafting isn't available"
    end
    -- Its own logic: the queued actions keep using it until they're done.
    local logic = newLogic(player)
    logic:setRecipe(recipe)
    logic:autoPopulateInputs()
    count = math.max(1, math.floor(tonumber(count) or 1))
    local possible = try(logic, "getPossibleCraftCount", true) or 0
    if possible < 1 then
        return false, "You can't make that right now"
    end
    count = math.min(count, possible)
    local control = setmetatable({
        logic = logic, player = player, allowBatchCraft = true, craftTimes = count,
        entryBox = { getInternalText = function() return tostring(count) end, setText = function() end },
        triggerEvent = function() end, setCraftQuantity = function() end,
    }, { __index = ISWidgetHandCraftControl })
    control:startHandcraft(false)
    return true
end

-- For Building.lua: the build window's logic shares its base with the crafting window's
-- (BaseCraftingLogic), so lists, ingredients and skills read the same way.
Crafting.shared = {
    textureName = textureName,
    recipeId = recipeId,
    listed = listed,
    canCraft = canCraft,
    categoryName = categoryName,
    describeInputs = describeInputs,
    describeSkills = describeSkills,
}

Crafting.commands = {
    craft_list = function(player, _args)
        return Crafting.list(player)
    end,
    craft_recipe = function(player, args)
        return Crafting.recipe(player, tostring(args.recipe))
    end,
    craft = function(player, args)
        return Crafting.craft(player, tostring(args.recipe), args.count)
    end,
}

return Crafting
