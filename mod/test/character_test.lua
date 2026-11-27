-- The character window's data (B42/Character.lua): Info (`character`), Skills (`skills`) and the
-- Temperature tab (`temperature`), against fakes of the game's API.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
function instanceof() return false end
function getText(k) return "T:" .. k end
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end
local function list(t) return { size = function() return #t end, get = function(_, i) return t[i + 1] end } end
local function tex(name) return { getName = function() return name end } end

-- Info
CharacterProfessionDefinition = { getCharacterProfessionDefinition = function(id)
  if id == "carpenter" then return { getUIName = function() return "Carpenter" end, getTexture = function() return tex("profession_hammer2") end } end
end }
local traitDefs = {
  strong = { getLabel = function() return "Strong" end, getDescription = function() return "Extra knockback." end, getCost = function() return 10 end, getTexture = function() return tex("trait_strong") end },
  smoker = { getLabel = function() return "Smoker" end, getDescription = function() return "Needs smokes." end, getCost = function() return -3 end, getTexture = function() return tex("trait_smoker") end },
  hidden = { getLabel = function() return "Hidden" end, getCost = function() return 0 end, getTexture = function() return nil end },
}
CharacterTraitDefinition = { getCharacterTraitDefinition = function(t) return traitDefs[t] end }
local hairStyle = { getName = function() return "Short" end, getLevel = function() return 2 end }
local beardStyle = { getName = function() return "" end, getLevel = function() return 0 end }
function getHairStylesInstance() return { FindMaleStyle = function() return hairStyle end, FindFemaleStyle = function() return nil end } end
function getBeardStylesInstance() return { FindStyle = function() return beardStyle end } end
local dateVisible = true
UIManager = { getClock = function() return { isDateVisible = function() return dateVisible end } end }

-- Skills: Carpentry (Crafting) and Strength (Passive), as PerkFactory lists them.
Perks = { None = "None" }
local function perk(id, name, parent, passive, xpPerLevel)
  return { getType = function() return id end, getName = function() return name end, getParent = function() return parent end,
    isPassiv = function() return passive end, getXpForLevel = function(_, l) return xpPerLevel * l end }
end
local crafting, passive = perk("Crafting", "Crafting", "None", false, 0), perk("Passiv", "Passive", "None", true, 0)
local carpentry, strength = perk("Woodwork", "Carpentry", "Crafting", false, 100), perk("Strength", "Strength", "Passiv", true, 1000)
PerkFactory = { PerkList = list({ crafting, carpentry, passive, strength }),
  getPerk = function(id) return ({ Crafting = crafting, Passiv = passive })[id] end }
-- levels 1..L cost 100, 200, ... : the game's ISSkillProgressBar.getPreviousXpLvl sums them
ISSkillProgressBar = { getPreviousXpLvl = function(p, level) local s = 0 for l = 1, level do s = s + p:getXpForLevel(l) end return s end }
local levels, totalXp = { Woodwork = 2, Strength = 10 }, { Woodwork = 100 + 200 + 150, Strength = 99999 }
local xp = { getXP = function(_, t) return totalXp[t] end, getPerkBoost = function(_, t) return t == "Woodwork" and 3 or 0 end,
  getMultiplier = function(_, t) return t == "Woodwork" and 2.5 or 0 end }

-- Temperature: two body parts.
BodyPartType = { MAX = "MAX", ToIndex = function() return 2 end, FromIndex = function(i) return ({ [0] = "Hand_L", [1] = "Torso_Upper" })[i] end,
  getDisplayName = function(t) return ({ Hand_L = "Left Hand", Torso_Upper = "Upper Torso" })[t] end }
local function node(skin) return { getSkinCelcius = function() return skin end, getSkinCelciusUI = function() return (skin - 20) / 22 end,
  getBodyResponseUI = function() return 0.2 end, getInsulation = function() return 0.314 end, getInsulationUI = function() return 0.3 end,
  getWindresist = function() return 0.2 end, getWindresistUI = function() return 0.2 end, getBodyWetnessUI = function() return 0.86 end,
  getClothingWetnessUI = function() return 0.64 end } end
local thermos = { getCoreTemperature = function() return 35.64 end, getCoreTemperatureUI = function() return 0.4529 end,
  getHeatGenerationUI = function() return 0.1 end,
  getNodeForType = function(_, t) return ({ Hand_L = node(24.2), Torso_Upper = node(33.1) })[t] end }
local celsius = false
function getCore() return { isCelsius = function() return celsius end } end

local player = {
  getDescriptor = function() return { getForename = function() return "Jake" end, getSurname = function() return "Ward" end,
    getCharacterProfession = function() return "carpenter" end } end,
  getNutrition = function() return { getWeight = function() return 83.6 end, isIncWeight = function() return true end,
    isIncWeightLot = function() return false end, isDecWeight = function() return false end } end,
  getCharacterTraits = function() return { getKnownTraits = function() return list({ "strong", "smoker", "hidden" }) end } end,
  isFemale = function() return false end,
  getHumanVisual = function() return { getHairModel = function() return "Short" end, getBeardModel = function() return "" end } end,
  getModData = function() return { ["Fav:Sledgehammer"] = 40, ["Fav:Axe"] = 12, other = 99 } end,
  getZombieKills = function() return 1284 end, getTimeSurvived = function() return "2 months" end,
  getXp = function() return xp end, getPerkLevel = function(_, t) return levels[t] end,
  getBodyDamage = function() return { getThermoregulator = function() return thermos end } end,
}

local B42 = require("ZomboidDS/Adapters/B42")
local info = B42.snapshotCharacter(player)
check(info.name == "Jake Ward" and info.profession == "Carpenter" and info.professionIcon == "profession_hammer2",
  "name, profession and its icon, like the game's Info tab")
check(info.weight == 84 and info.weightTrend == "up", "weight rounded like the game, with its trend arrow")
check(#info.traits == 2 and info.traits[1].name == "Strong" and info.traits[1].icon == "trait_strong" and info.traits[1].tone == "good",
  "traits with an icon only, with the game's label and icon")
check(info.traits[2].tone == "bad" and info.traits[2].description == "Needs smokes.", "a trait that gave points is bad; its description")
check(info.hair == "T:IGUI_Hair_Short" and info.canChangeHair == true, "hair style, and Change is enabled with hair to cut")
check(info.beard == "T:IGUI_Beard_None" and info.canChangeBeard == false, "no beard: the game's 'None', Change disabled")
check(info.favouriteWeapon == "Sledgehammer" and info.kills == 1284 and info.survived == "2 months", "favourite weapon, kills, time survived")
dateVisible = false
check(B42.snapshotCharacter(player).survived == nil, "no time survived while the clock hides the date, like the game")
player.isFemale = function() return true end
check(B42.snapshotCharacter(player).beard == nil, "no beard row for female characters")
check(B42.snapshotCharacter(player).hair == "T:IGUI_Hair_Bald", "a female style that isn't found reads as bald, not a male style")
player.isFemale = function() return false end

local skills = B42.snapshotSkills(player).groups
check(#skills == 2 and skills[1].name == "Passive" and skills[2].name == "Crafting", "the passive group first, like the game")
local carp = skills[2].skills[1]
check(carp.name == "Carpentry" and carp.level == 2 and carp.xp == 150 and carp.xpNeeded == 300,
  "level and XP towards the next level, as the game's bar counts them")
check(carp.boost == 3 and carp.multiplier == 2.5, "profession boost and book multiplier")
local str = skills[1].skills[1]
check(str.level == 10 and str.xp == nil and str.multiplier == nil, "level 10: no XP to go, no multiplier when there is none")

local temp = B42.snapshotTemperature(player)
check(temp.core == 35.6 and temp.coreBar == 0.453 and temp.celsius == false, "core temperature, the game's bar value, the unit option")
check(#temp.parts == 2 and temp.parts[1].name == "Left Hand" and temp.parts[1].skin == 24.2 and temp.parts[1].insulation == 0.31,
  "per body part: skin temperature, insulation")
check(B42.snapshotTemperature({}) == nil, "no thermoregulator: nothing sent")
print("ALL LUA CHECKS PASSED")
