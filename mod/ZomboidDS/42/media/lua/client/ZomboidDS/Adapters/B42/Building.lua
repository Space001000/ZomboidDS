--- Building: the recipes the game's build window lists, what one needs, and placing it.
---
--- 42.20's build window (ISBuildWindow -> ISBuildPanel) is Lua around the Java BuildLogic, which
--- shares its base with the crafting window's HandcraftLogic: the same recipe list, ingredients and
--- skills (Crafting.shared reads them). Its recipes are the game entities with a CraftRecipe
--- (getAllBuildableRecipes). The window's Build button doesn't build: it turns on the game's
--- placement cursor (ISBuildPanel:createBuildIsoEntity), which the player then moves, turns and
--- places with the mouse or controller on the top screen. Place does the same here, and like the
--- window we bring the cursor back after each placement so the next one can follow.
local Util = require("ZomboidDS/Adapters/B42/Util")
local Crafting = require("ZomboidDS/Adapters/B42/Crafting")
local try = Util.try
local shared = Crafting.shared

local Building = {}

local logics = {}     -- playerNum -> BuildLogic for listing and details
local recipesById = {} -- recipe id -> CraftRecipe, from the latest list
local placing = {}    -- playerNum -> { cursor, logic, recipe, id, square }

local function newLogic(player)
    local logic = BuildLogic.new(player, nil, nil)
    logic:setContainers(ISInventoryPaneContextMenu.getContainers(player))
    -- Also fills the logic's recipe -> entity lookup that getSelectedBuildObject needs.
    logic:setRecipes(logic:getAllBuildableRecipes())
    return logic
end

local function logicFor(player)
    local playerNum = player:getPlayerNum()
    local logic = logics[playerNum]
    if logic == nil then
        logic = newLogic(player)
        logics[playerNum] = logic
    elseif logic:setContainers(ISInventoryPaneContextMenu.getContainers(player)) then
        logic:setRecipes(logic:getAllBuildableRecipes())
    end
    return logic
end

--- Versions of one thing (Wood Chair Shoddy / Poor / Good) are entities named <thing>_Lvl1, _Lvl2,
--- ... in 42.20's scripts (Wood_Chair_Lvl1, WoodenDoorLvl2). Their names differ only in a suffix
--- in brackets, in every language that uses brackets: that's the version's label.
local function versionOf(recipe, name)
    local id = try(recipe, "getName") or ""
    local group, level = string.match(id, "^(.-)_?[Ll][Vv][Ll](%d+)$")
    if group == nil then
        return nil
    end
    local base, label = string.match(name or "", "^(.-)%s*%(([^()]+)%)%s*$")
    return { group = group, level = tonumber(level), label = label, base = base }
end

local function firstSkill(player, recipe)
    local skill = shared.describeSkills(player, recipe)[1]
    return skill and { name = skill.name, level = skill.level } or nil
end

--- The recipes the build window lists for the player right now, with whether each can be built,
--- its version group, and the categories.
function Building.list(player)
    if BuildLogic == nil then
        return false, "The game's building isn't available"
    end
    local logic = logicFor(player)
    logic:filterRecipeList("", "")
    local all = logic:getRecipeList():getAllRecipes()
    local recipes, seenCategories, categories = {}, {}, {}
    recipesById = {}
    for i = 0, all:size() - 1 do
        local recipe = all:get(i)
        if shared.listed(player, recipe) then
            local id = shared.recipeId(recipe)
            local name = try(recipe, "getTranslationName") or id
            local category = try(recipe, "getCategory")
            local version = versionOf(recipe, name)
            recipesById[id] = recipe
            recipes[#recipes + 1] = {
                id = id,
                name = name,
                icon = shared.textureName(try(recipe, "getIconTexture")),
                category = category,
                canBuild = shared.canCraft(logic, player, recipe),
                group = version and version.group or nil,
                level = version and version.level or nil,
                version = version and version.label or nil,
                groupName = version and version.base or nil,
                skill = firstSkill(player, recipe),
            }
            if category ~= nil and not seenCategories[category] then
                seenCategories[category] = true
                categories[#categories + 1] = { id = category, name = shared.categoryName(category) }
            end
        end
    end
    return true, nil, { recipes = recipes, categories = categories }
end

local function findRecipe(player, id)
    if recipesById[id] == nil then
        Building.list(player)
    end
    return recipesById[id]
end

--- Every input with the player's items counted, as the window's ingredient widgets show them.
local function describeInputs(logic, recipe)
    local inputs, list = {}, try(recipe, "getInputs")
    for i = 0, (list and list:size() or 0) - 1 do
        local ok, entry = pcall(shared.describeInput, logic, list:get(i))
        if ok then inputs[#inputs + 1] = entry end
    end
    return inputs
end

--- What a recipe needs and whether the player can build it now.
function Building.recipe(player, id)
    local recipe = findRecipe(player, id)
    if recipe == nil then
        return false, "That recipe isn't available"
    end
    local logic = logicFor(player)
    logic:setRecipe(recipe)
    logic:autoPopulateInputs()
    return true, nil, {
        id = id,
        name = try(recipe, "getTranslationName") or id,
        icon = shared.textureName(try(recipe, "getIconTexture")),
        category = try(recipe, "getCategory"),
        seconds = try(recipe, "getTime", player),
        canBuild = shared.canCraft(logic, player, recipe),
        inputs = describeInputs(logic, recipe),
        skills = shared.describeSkills(player, recipe),
    }
end

-- Placing ----------------------------------------------------------------------

--- The item to hold for a tool input, as ISBuildPanel's getTool picks it.
local function tool(input, inventory)
    if input == nil then
        return nil
    end
    local types = input:getPossibleInputItems()
    for i = 0, types:size() - 1 do
        local found = inventory:getAllTypeEvalRecurse(types:get(i):getFullName(), ISBuildIsoEntity.predicateMaterial)
        if found:size() > 0 then
            return found:get(0):getFullType()
        end
    end
    return nil
end

local function canPlace(state)
    local logic = state.logic
    if try(state.player, "isBuildCheat") == true then
        return true
    end
    return logic:canPerformCurrentRecipe() and not logic:isCraftActionInProgress()
end

--- Whether the player is still placing what we armed (the cursor is ours and still up).
local function current(player)
    local state = placing[player:getPlayerNum()]
    if state ~= nil and getCell():getDrag(player:getPlayerNum()) == state.cursor then
        return state
    end
    placing[player:getPlayerNum()] = nil
    return nil
end

--- The window's panel does this when its containers change and after each placement
--- (ISBuildPanel:updateContainers, onStopCraft): count what's in reach again and block the cursor
--- when it isn't enough.
local function refresh(state)
    local logic = state.logic
    logic:setContainers(ISInventoryPaneContextMenu.getContainers(state.player))
    logic:autoPopulateInputs()
    logic:refresh()
    if state.cursor.blockBuild ~= nil then
        state.cursor.blockBuild = not canPlace(state)
    end
end

local function onStopCraft(state)
    if current(state.player) == state then
        refresh(state)
    end
end

--- While placing: the panel re-counts when the player moves to another square.
local function watch()
    local any = false
    for _, state in pairs(placing) do
        if current(state.player) == state then
            any = true
            local square = state.player:getCurrentSquare()
            if square ~= nil and square ~= state.square then
                state.square = square
                refresh(state)
            end
        end
    end
    if not any then
        Events.OnTick.Remove(watch)
    end
end

--- Turns on the game's placement cursor for `id`, the way the build window's Build button does.
function Building.place(player, id)
    local recipe = findRecipe(player, id)
    if recipe == nil then
        return false, "That recipe isn't available"
    end
    if ISBuildIsoEntity == nil then
        return false, "The game's building isn't available"
    end
    -- Its own logic: the cursor and its build actions keep using it.
    local logic = newLogic(player)
    logic:setRecipe(recipe)
    logic:autoPopulateInputs()
    local state = { player = player, logic = logic, recipe = recipe, id = id, square = player:getCurrentSquare() }
    local playerNum = player:getPlayerNum()
    local info = logic:getSelectedBuildObject()
    local covering = logic:getWallCoveringParams()
    if info ~= nil then
        local cursor = ISBuildIsoEntity:new(player, info, 1, ISInventoryPaneContextMenu.getContainers(player), logic)
        cursor.dragNilAfterPlace = false
        cursor.blockAfterPlace = true
        local inventory = player:getInventory()
        cursor.equipBothHandItem = tool(recipe:getToolBoth(), inventory)
        cursor.firstItem = tool(recipe:getToolRight(), inventory)
        cursor.secondItem = tool(recipe:getToolLeft(), inventory)
        state.cursor = cursor
        cursor.blockBuild = not canPlace(state)
    elseif covering ~= nil then
        -- Wallpaper, paint and plaster have their own cursors (ISBuildPanel:createBuildIsoEntity).
        if covering.actionType == WallCoveringType.WALLPAPER then
            state.cursor = ISPaperCursor:new(player, covering.wallpaperType, WallPaper["wall"][covering.wallpaperType])
        else
            state.cursor = ISPaintCursor:new(player, covering.actionType:toString(), covering)
        end
    else
        return false, "That can't be placed"
    end
    logic:addEventListener("onStopCraft", onStopCraft, state)
    placing[playerNum] = state
    getCell():setDrag(state.cursor, playerNum)
    Events.OnTick.Remove(watch)
    Events.OnTick.Add(watch)
    return true
end

--- Puts the cursor away, as B on the controller does.
function Building.stop(player)
    if current(player) ~= nil then
        getCell():setDrag(nil, player:getPlayerNum())
    end
    placing[player:getPlayerNum()] = nil
    return true
end

--- What the player is placing, for the app's strip: { placing = { id, name, icon, missing[] } },
--- or {} when nothing. `missing` names what's short when the cursor is blocked.
function Building.snapshot(player)
    local state = current(player)
    if state == nil then
        return {}
    end
    local recipe = state.recipe
    local entry = {
        id = state.id,
        name = try(recipe, "getTranslationName") or state.id,
        icon = shared.textureName(try(recipe, "getIconTexture")),
    }
    if state.cursor.blockBuild == true and not state.logic:isCraftActionInProgress() then
        local missing = {}
        for _, input in ipairs(describeInputs(state.logic, recipe)) do
            if not input.ok then missing[#missing + 1] = input.name end
        end
        for _, skill in ipairs(shared.describeSkills(player, recipe)) do
            if (skill.have or 0) < (skill.level or 0) then missing[#missing + 1] = skill.name end
        end
        entry.missing = missing
    end
    return { placing = entry }
end

Building.commands = {
    build_list = function(player, _args)
        return Building.list(player)
    end,
    build_recipe = function(player, args)
        return Building.recipe(player, tostring(args.recipe))
    end,
    build_place = function(player, args)
        return Building.place(player, tostring(args.recipe))
    end,
    build_stop = function(player, _args)
        return Building.stop(player)
    end,
}

return Building
