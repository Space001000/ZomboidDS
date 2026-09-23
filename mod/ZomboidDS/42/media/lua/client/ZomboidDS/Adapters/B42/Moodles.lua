--- Moodles (hungry, bleeding, ...) as the game's moodle column shows them.
local Util = require("ZomboidDS/Adapters/B42/Util")
local try, round = Util.try, Util.round

local Moodles = {}

-- The game's moodle column (zombie.ui.MoodlesUI in 42.20, Java): every moodle with a level above
-- 0, its icon on a round background tinted from grey towards the player's good/bad highlight
-- colour (options, so colour-blind settings carry over) by level / 4, and on hover its name and
-- description. Names, descriptions and levels come from player:getMoodles(); the icon table is
-- Java-only (zombie.ui.MoodleTextureSet), so it's copied here from 42.20's bytecode. Listed most
-- urgent first; the game's own order is its hash map's.

local MOODLE_DIR = "Moodles/128/"
local MOODLES = {
    { "BLEEDING", "Status_Bleeding" }, { "INJURED", "Status_InjuredMinor" }, { "PAIN", "Mood_Pained" },
    { "PANIC", "Mood_Panicked" }, { "SICK", "Mood_Nauseous" }, { "HAS_A_COLD", "Mood_Ill" },
    { "THIRST", "Status_Thirst" }, { "HUNGRY", "Status_Hunger" }, { "TIRED", "Mood_Sleepy" },
    { "ENDURANCE", "Status_DifficultyBreathing" }, { "HYPOTHERMIA", "Status_TemperatureLow" },
    { "HYPERTHERMIA", "Status_TemperatureHot" }, { "WINDCHILL", "Status_Windchill" }, { "WET", "Status_Wet" },
    { "HEAVY_LOAD", "Status_HeavyLoad" }, { "CANT_SPRINT", "Status_MovementRestricted" },
    { "STRESS", "Mood_Stressed" }, { "UNHAPPY", "Mood_Sad" }, { "BORED", "Mood_Bored" }, { "ANGRY", "Mood_Angry" },
    { "DRUNK", "Mood_Drunk" }, { "UNCOMFORTABLE", "Mood_Discomfort" }, { "NOXIOUS_SMELL", "Mood_NoxiousSmell" },
    { "FOOD_EATEN", "Status_Hunger" }, { "ZOMBIE", "Mood_Zombified" }, { "DEAD", "Mood_Dead" },
}
local MOODLE_TONES = { [1] = "good", [2] = "bad" }
-- MoodlesUI hides "food eaten" below Moodle.MoodleLevel.HighMoodleLevel (ordinal 3).
local MOODLE_MIN_LEVEL = { FOOD_EATEN = 3 }

--- The game's background colour for a moodle: lerp from Color.gray to the highlight colour.
local function moodleColour(tone, level)
    local highlight = nil
    if tone == "good" then highlight = try(getCore(), "getGoodHighlitedColor") end
    if tone == "bad" then highlight = try(getCore(), "getBadHighlitedColor") end
    local t = math.min(level, 4) / 4
    local function mix(getter)
        local to = highlight and try(highlight, getter) or 0.5
        return round(0.5 + (to - 0.5) * t, 3)
    end
    return { mix("getR"), mix("getG"), mix("getB") }
end

function Moodles.snapshot(player)
    local moodles = try(player, "getMoodles")
    local list = {}
    for _, entry in ipairs(MOODLES) do
        local okType, moodleType = pcall(function() return MoodleType[entry[1]] end)
        local level = okType and moodleType ~= nil and try(moodles, "getMoodleLevel", moodleType) or 0
        if level > 0 and level >= (MOODLE_MIN_LEVEL[entry[1]] or 1) then
            local tone = MOODLE_TONES[try(moodles, "getGoodBadNeutral", moodleType)] or "neutral"
            list[#list + 1] = {
                id = entry[1],
                name = try(moodles, "getMoodleDisplayString", moodleType) or entry[1],
                description = try(moodles, "getMoodleDescriptionString", moodleType),
                level = level,
                tone = tone,
                color = moodleColour(tone, level),
                icon = MOODLE_DIR .. entry[2],
            }
        end
    end
    return { moodles = list, background = MOODLE_DIR .. "_Moodles_BGsolid", border = MOODLE_DIR .. "_Moodles_BGoutline" }
end

return Moodles
