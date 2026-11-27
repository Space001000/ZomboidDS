--- The game's character window (ISCharacterInfoWindow) for the Status tab: its Info tab
--- (`character`), its Skills tab (`skills`) and its Temperature tab (`temperature`). Values are read
--- the way those tabs read them (42.21 ISCharacterScreen, ISCharacterInfo, ISSkillProgressBar,
--- ISClothingInsPanel), so the app shows what the game would.
local Util = require("ZomboidDS/Adapters/B42/Util")
local try, call, round = Util.try, Util.call, Util.round

local Character = {}

-- Info -----------------------------------------------------------------------

local function textureName(texture)
    return Util.textureFileName(try(texture, "getName"))
end

--- Like ISCharacterScreen.loadProfession: the profession's name and its icon (none for Unemployed).
local function profession(player)
    local id = try(try(player, "getDescriptor"), "getCharacterProfession")
    if id == nil or CharacterProfessionDefinition == nil then
        return nil, nil
    end
    local definition = call(CharacterProfessionDefinition.getCharacterProfessionDefinition, id)
    return try(definition, "getUIName"), textureName(try(definition, "getTexture"))
end

--- Like ISCharacterScreen.setDisplayedTraits: the known traits that have an icon, with the game's
--- label and description (its tooltip). `good`: chosen for points (cost > 0), `bad`: gave points.
local function traits(player)
    local list = {}
    local known = try(try(player, "getCharacterTraits"), "getKnownTraits")
    if known == nil or CharacterTraitDefinition == nil then
        return list
    end
    for i = 0, known:size() - 1 do
        local trait = known:get(i)
        local definition = call(CharacterTraitDefinition.getCharacterTraitDefinition, trait)
        local texture = try(definition, "getTexture")
        if texture ~= nil then
            local cost = try(definition, "getCost") or 0
            local tone = nil
            if cost > 0 then tone = "good" elseif cost < 0 then tone = "bad" end
            list[#list + 1] = {
                id = tostring(trait),
                name = try(definition, "getLabel"),
                description = try(definition, "getDescription"),
                icon = textureName(texture),
                tone = tone,
            }
        end
    end
    return list
end

--- Like ISCharacterScreen.loadBeardAndHairStyle, plus whether its Change button is enabled (the
--- game disables it without hair or beard to cut). No beard for female characters.
local function styles(player)
    local visual = try(player, "getHumanVisual")
    local female = try(player, "isFemale") == true
    local hairStyles = call(getHairStylesInstance)
    local hairModel = try(visual, "getHairModel")
    local hair
    if female then
        hair = try(hairStyles, "FindFemaleStyle", hairModel)
    else
        hair = try(hairStyles, "FindMaleStyle", hairModel)
    end
    local hairName = try(hair, "getName")
    local result = {
        hair = (hairName and hairName ~= "") and getText("IGUI_Hair_" .. hairName) or getText("IGUI_Hair_Bald"),
        canChangeHair = (try(hair, "getLevel") or 0) > 0,
    }
    if not female then
        local beard = try(call(getBeardStylesInstance), "FindStyle", try(visual, "getBeardModel"))
        local beardName = try(beard, "getName")
        result.beard = (beardName and beardName ~= "") and getText("IGUI_Beard_" .. beardName) or getText("IGUI_Beard_None")
        result.canChangeBeard = (try(beard, "getLevel") or 0) > 0
    end
    return result
end

--- Like ISCharacterScreen.loadFavouriteWeapon: the weapon swung most ("Fav:<name>" in mod data).
local function favouriteWeapon(player)
    local data = try(player, "getModData")
    if type(data) ~= "table" then
        return nil
    end
    local best, swings = nil, 0
    for key, value in pairs(data) do
        local name = type(key) == "string" and string.match(key, "^Fav:(.+)") or nil
        if name and type(value) == "number" and value > swings then
            best, swings = name, value
        end
    end
    return best
end

--- "up", "upLot", "down" or nil, like the arrow the game draws next to the weight.
local function weightTrend(nutrition)
    if try(nutrition, "isIncWeightLot") == true then return "upLot" end
    if try(nutrition, "isIncWeight") == true then return "up" end
    if try(nutrition, "isDecWeight") == true then return "down" end
    return nil
end

function Character.snapshot(player)
    local descriptor = try(player, "getDescriptor")
    local professionName, professionIcon = profession(player)
    local nutrition = try(player, "getNutrition")
    local info = {
        name = (try(descriptor, "getForename") or "") .. " " .. (try(descriptor, "getSurname") or ""),
        profession = professionName,
        professionIcon = professionIcon,
        weight = round(try(nutrition, "getWeight"), 0),
        weightTrend = weightTrend(nutrition),
        traits = traits(player),
        favouriteWeapon = favouriteWeapon(player),
        kills = try(player, "getZombieKills"),
    }
    -- The game only shows how long you've survived while its clock shows the date.
    local clock = UIManager and call(UIManager.getClock)
    if try(clock, "isDateVisible") == true then
        info.survived = try(player, "getTimeSurvived")
    end
    for key, value in pairs(styles(player)) do
        info[key] = value
    end
    return info
end

-- Skills ---------------------------------------------------------------------

--- XP gathered towards the next level, as ISSkillProgressBar.getPerkXp counts it.
local function previousLevelsXp(perk, level)
    if ISSkillProgressBar and ISSkillProgressBar.getPreviousXpLvl then
        return call(ISSkillProgressBar.getPreviousXpLvl, perk, level) or 0
    end
    return 0
end

local function skill(player, xp, perk)
    local type = try(perk, "getType")
    local level = try(player, "getPerkLevel", type) or 0
    local entry = {
        id = tostring(type),
        name = try(perk, "getName"),
        level = level,
        -- The game's XP boost from profession and traits (0-3): it colours the name.
        boost = try(xp, "getPerkBoost", type) or 0,
    }
    local multiplier = try(xp, "getMultiplier", type) or 0
    if multiplier > 0 then
        entry.multiplier = round(multiplier, 2) -- from skill books: the game's moving arrows
    end
    if level < 10 then
        local needed = try(perk, "getXpForLevel", level + 1)
        local have = (try(xp, "getXP", type) or 0) - previousLevelsXp(perk, level)
        if needed and needed > 0 then
            have = math.max(0, math.min(have, needed))
            entry.xp, entry.xpNeeded = round(have, 0), round(needed, 0)
        end
    end
    return entry
end

--- Like ISCharacterInfo: every skill under its parent, the passive group first, then by name.
function Character.snapshotSkills(player)
    if PerkFactory == nil or PerkFactory.PerkList == nil then
        return { groups = {} }
    end
    local xp = try(player, "getXp")
    local byParent, parents = {}, {}
    local perks = PerkFactory.PerkList
    for i = 0, perks:size() - 1 do
        local perk = perks:get(i)
        local parent = try(perk, "getParent")
        if parent ~= nil and parent ~= Perks.None then
            local key = tostring(parent)
            if byParent[key] == nil then
                byParent[key] = {}
                parents[#parents + 1] = parent
            end
            table.insert(byParent[key], skill(player, xp, perk))
        end
    end
    local groups = {}
    for _, parent in ipairs(parents) do
        local perk = call(PerkFactory.getPerk, parent)
        groups[#groups + 1] = {
            id = tostring(parent),
            name = try(perk, "getName") or tostring(parent),
            passive = try(perk, "isPassiv") == true,
            skills = byParent[tostring(parent)],
        }
    end
    table.sort(groups, function(a, b)
        if a.passive ~= b.passive then
            return a.passive
        end
        return a.name < b.name
    end)
    for _, group in ipairs(groups) do
        group.passive = nil
    end
    return { groups = groups }
end

-- Temperature ----------------------------------------------------------------

--- The game's Temperature tab: core temperature and body heat (its two bars, 0-1 as it draws them),
--- and per body part the values it shows when you pick one. Celsius; `celsius` is the player's option.
function Character.snapshotTemperature(player)
    local thermos = try(try(player, "getBodyDamage"), "getThermoregulator")
    if thermos == nil or BodyPartType == nil then
        return nil
    end
    local parts = {}
    local count = call(BodyPartType.ToIndex, BodyPartType.MAX) or 0
    for i = 0, count - 1 do
        local type = call(BodyPartType.FromIndex, i)
        local node = try(thermos, "getNodeForType", type)
        if node ~= nil then
            parts[#parts + 1] = {
                id = tostring(type),
                name = call(BodyPartType.getDisplayName, type) or tostring(type),
                skin = round(try(node, "getSkinCelcius"), 1),
                skinBar = round(try(node, "getSkinCelciusUI"), 3),
                response = round(try(node, "getBodyResponseUI"), 3),
                insulation = round(try(node, "getInsulation"), 2),
                insulationBar = round(try(node, "getInsulationUI"), 3),
                wind = round(try(node, "getWindresist"), 2),
                windBar = round(try(node, "getWindresistUI"), 3),
                bodyWet = round(try(node, "getBodyWetnessUI"), 3),
                clothesWet = round(try(node, "getClothingWetnessUI"), 3),
            }
        end
    end
    local core = try(getCore(), "isCelsius")
    return {
        core = round(try(thermos, "getCoreTemperature"), 1),
        coreBar = round(try(thermos, "getCoreTemperatureUI"), 3),
        heatBar = round(try(thermos, "getHeatGenerationUI"), 3),
        celsius = core ~= false,
        parts = parts,
    }
end

return Character
