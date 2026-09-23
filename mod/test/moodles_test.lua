-- Moodles (B42.snapshotMoodles): what the game's moodle column shows, with its names, colours and
-- icons. Fakes stand in for player:getMoodles() and the options' highlight colours.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
function instanceof() return false end
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

-- Like 42.20's MoodleType: static fields. WINDCHILL is missing, as if a later build renamed it.
MoodleType = { HUNGRY = "HUNGRY", BLEEDING = "BLEEDING", FOOD_EATEN = "FOOD_EATEN", BORED = "BORED", DRUNK = "DRUNK" }
local levels = { HUNGRY = 2, BLEEDING = 4, FOOD_EATEN = 2, BORED = 0, DRUNK = 1 }
local goodBad = { HUNGRY = 2, BLEEDING = 2, FOOD_EATEN = 1, DRUNK = 0 }
local names = { HUNGRY = "Hungry", BLEEDING = "Severe Bleeding", FOOD_EATEN = "Well Fed", DRUNK = "Tipsy" }
local moodles = {
  getMoodleLevel = function(_, t) return levels[t] end,
  getGoodBadNeutral = function(_, t) return goodBad[t] end,
  getMoodleDisplayString = function(_, t) return names[t] end,
  getMoodleDescriptionString = function(_, t) return names[t] .. "." end,
}
local player = { getMoodles = function() return moodles end }
local function colour(r, g, b) return { getR = function() return r end, getG = function() return g end, getB = function() return b end } end
function getCore() return {
  getGoodHighlitedColor = function() return colour(0.1, 0.9, 0.1) end,
  getBadHighlitedColor = function() return colour(0.9, 0.1, 0.1) end,
} end

local B42 = require("ZomboidDS/Adapters/B42")
local snap = B42.snapshotMoodles(player)
local ids = {}
for i, m in ipairs(snap.moodles) do ids[i] = m.id end
check(table.concat(ids, ",") == "BLEEDING,HUNGRY,DRUNK", "moodles above level 0, most urgent first: " .. table.concat(ids, ","))
local bleeding, hungry, drunk = snap.moodles[1], snap.moodles[2], snap.moodles[3]
check(bleeding.name == "Severe Bleeding" and bleeding.description == "Severe Bleeding." and bleeding.level == 4,
  "the game's name and description for the level")
check(bleeding.icon == "Moodles/128/Status_Bleeding" and hungry.icon == "Moodles/128/Status_Hunger",
  "the game's icon for each moodle, by path (the sizes share file names)")
check(bleeding.tone == "bad" and drunk.tone == "neutral", "good/bad/neutral from the game")
check(bleeding.color[1] == 0.9 and bleeding.color[2] == 0.1, "level 4: the full bad highlight colour from the options")
check(hungry.color[1] == 0.7 and hungry.color[2] == 0.3, "level 2: halfway from grey, like the game's lerp")
check(drunk.color[1] == 0.5 and drunk.color[3] == 0.5, "neutral stays grey")
levels.FOOD_EATEN = 3
check(B42.snapshotMoodles(player).moodles[4].id == "FOOD_EATEN", "'food eaten' only from level 3, like the game's column")
check(snap.background == "Moodles/128/_Moodles_BGsolid" and snap.border == "Moodles/128/_Moodles_BGoutline",
  "the game's background and border images")
check(#B42.snapshotMoodles({}).moodles == 0, "no moodles object: an empty list, not an error")
print("ALL LUA CHECKS PASSED")
