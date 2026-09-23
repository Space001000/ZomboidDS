--- Injuries (the game's health panel) for the Status tab.
local Util = require("ZomboidDS/Adapters/B42/Util")
local try = Util.try

local Health = {}

-- The game's health panel decides which body parts to list (ISHealthPanel.getDamagedParts) and what
-- to say about each (ISHealthBodyPartListBox.doDrawItem: "Scratched (Severe)", "Bandaged", ...,
-- depending on the player's First Aid level, in its own colours). We call both with stand-ins that
-- record the text instead of drawing it, so the lines, translations and rules are the game's own
-- (and other mods' changes to them), not a copy that drifts.

--- The game's colours: green = treated, red = a problem, orange = dirty bandage, infection, stiffness.
local function tone(r, g)
    if g > 0.8 and r < 0.5 then return "good" end
    if r > 0.95 then return "warn" end
    if r > 0.8 and g < 0.5 then return "bad" end
    return nil
end

local function describeBodyPart(player, panel, bodyPart)
    local lines = {}
    local recorder = {
        parent = panel, selected = -1, mouseoverselected = -1, width = 400,
        getWidth = function() return 400 end,
        drawText = function(_, text, _x, _y, r, g) lines[#lines + 1] = { text = text, r = r or 1, g = g or 1 } end,
        drawRect = function() end, drawRectBorder = function() end, drawProgressBar = function() end,
    }
    ISHealthBodyPartListBox.doDrawItem(recorder, 0, { item = { bodyPart = bodyPart }, height = 0, itemindex = 0 }, false)
    local entry = { id = tostring(bodyPart:getType()), name = lines[1] and lines[1].text or tostring(bodyPart:getType()), lines = {} }
    for i = 2, #lines do
        local text = string.gsub(lines[i].text, "^%s*%-%s*", "")
        entry.lines[#entry.lines + 1] = { text = text, tone = tone(lines[i].r, lines[i].g) }
    end
    return entry
end

function Health.snapshot(player)
    if ISHealthPanel == nil or ISHealthBodyPartListBox == nil then
        return { parts = {} }
    end
    local panel = {
        character = player, otherPlayer = nil, bodyPartAction = nil, actions = {},
        doctorLevel = try(player, "getPerkLevel", Perks and Perks.Doctor) or 0,
        getPatient = function() return player end, getDoctor = function() return player end,
    }
    local parts = {}
    local ok, damaged = pcall(ISHealthPanel.getDamagedParts, panel)
    for _, bodyPart in ipairs(ok and damaged or {}) do
        local described, entry = pcall(describeBodyPart, player, panel, bodyPart)
        if described then
            parts[#parts + 1] = entry
        end
    end
    -- Which body silhouette the app draws (the game has a male and a female one).
    return { parts = parts, female = try(player, "isFemale") == true }
end

return Health
