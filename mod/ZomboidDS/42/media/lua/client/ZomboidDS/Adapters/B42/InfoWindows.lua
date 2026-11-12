--- The game's info windows (a generator's, a campfire's, a barbecue's, a plant's, an animal track's,
--- a CD's or tape's back cover) as a card on the bottom screen.
---
--- 42.21 opens each from its timed action's perform, after the walk there (ISGeneratorInfoAction,
--- ISCampingInfoAction, ISBBQInfoAction, ISPlantInfoAction); the app takes over there. The card says
--- what the window says: the generator's, campfire's and barbecue's text comes from the windows' own
--- setObject / getRichText (run on a stand-in, so no window opens); a plant's rows follow
--- ISFarmingInfo:render with its own helpers, so the Farming level decides what shows. Each closes
--- when the game's window would.
local Util = require("ZomboidDS/Adapters/B42/Util")
local try = Util.try

local InfoWindows = {}

local open = {} -- playerNum -> { kind, object }

--- A texture or sprite name as the icon endpoint knows it.
local function iconOf(texture)
    local name = Util.textureFileName(texture)
    if name == nil or name == "" then return nil end
    if string.find(name, "^Item_") then return name end
    return name .. "_Icon"
end

--- The game's rich text as rows: "Fuel: 63%" is a label and a value (a meter when it's a
--- percentage), "Lights x6 (0.0012 L/h)" a name and an amount, red text is bad news.
function InfoWindows.rows(text)
    local rows = {}
    text = string.gsub(text or "", "<INDENT:%d+>", "")
    for line in string.gmatch(text .. "<LINE>", "(.-)<LINE>") do
        local bad = string.find(line, "<RED>") ~= nil or string.find(line, "<RGB:1,0,0>") ~= nil
        line = string.gsub(line, "<[^>]*>", "")
        line = string.gsub(string.gsub(line, "^%s+", ""), "%s+$", "")
        if line ~= "" then
            local row = { tone = bad and "bad" or nil }
            local label, value = string.match(line, "^([^:]+):%s*(.*)$")
            if label ~= nil then
                row.label, row.value = label, value ~= "" and value or nil
                local percent = value and tonumber(string.match(value, "^(%d+)%%$"))
                if percent ~= nil then row.meter = math.max(0, math.min(1, percent / 100)) end
            else
                local name, amount = string.match(line, "^(.-)%s*%(([^()]*)%)$")
                if name ~= nil and name ~= "" then
                    row.label, row.value, row.indent = name, amount, true
                else
                    row.text = line
                end
            end
            rows[#rows + 1] = row
        end
    end
    return rows
end

--- A stand-in for the window: setObject fills in its panel's name, texture and description.
local function standIn(player)
    local panel = {}
    function panel.setName(p, name) p.name = name end
    function panel.setTexture(p, texture) p.texture = texture end
    return { panel = panel, character = player, playerNum = player:getPlayerNum() }
end

local kinds = {}

kinds.generator = {
    -- ISGeneratorInfoWindow:update closes it once the generator is gone (no distance limit).
    there = function(_player, object) return (try(object, "getObjectIndex") or -1) >= 0 end,
    read = function(player, object)
        local window = standIn(player)
        ISGeneratorInfoWindow.setObject(window, object)
        return { title = window.panel.name, icon = iconOf(window.panel.texture),
                 rows = InfoWindows.rows(ISGeneratorInfoWindow.getRichText(object, true)) }
    end,
}
kinds.campfire = {
    -- ISCampingInfoWindow:update: gone with its object, closed more than 2 tiles away.
    there = function(player, campfire)
        if try(campfire, "getIsoObject") == nil then return false end
        local fire = CCampfireSystem.instance:getLuaObjectOnSquare(campfire:getSquare())
        if fire == nil then return false end
        local dx, dy = player:getX() - fire.x, player:getY() - fire.y
        return dx <= 2 and dx >= -2 and dy <= 2 and dy >= -2
    end,
    read = function(player, campfire)
        local window = standIn(player)
        ISCampingInfoWindow.setObject(window, campfire)
        return { title = window.panel.name, icon = iconOf(window.panel.texture), rows = InfoWindows.rows(window.panel.description) }
    end,
}

kinds.bbq = {
    there = function(_player, bbq) return (try(bbq, "getObjectIndex") or -1) >= 0 end,
    read = function(player, bbq)
        local window = standIn(player)
        ISBBQInfoWindow.setObject(window, bbq)
        return { title = window.panel.name, icon = iconOf(window.panel.texture), rows = InfoWindows.rows(window.panel.description) }
    end,
}

local PESTS = {
    { level = "aphidLvl", text = "Farming_Aphid", icon = "Item_Insect_Aphid" },
    { level = "mildewLvl", text = "Farming_Mildew", icon = "Item_Mildew" },
    { level = "fliesLvl", text = "Farming_Pest_Flies", icon = "Item_Insect_Fly" },
    { level = "slugsLvl", text = "Farming_Slugs", icon = "Item_Snail" },
}

kinds.plant = {
    -- ISFarmingInfo:update: hidden when the plant is gone or more than 6 tiles away.
    there = function(player, plant)
        plant:updateFromIsoObject()
        local square = try(plant, "getSquare")
        return try(plant, "getIsoObject") ~= nil and square ~= nil
            and (try(player, "DistTo", square:getX(), square:getY()) or math.huge) <= 6
    end,
    read = function(player, plant)
        local farming = CFarmingSystem.instance:getXp(player)
        local cheat = ISFarmingMenu.cheat == true
        local info = { plant = plant, character = player }
        local rows = {}
        local function row(label, value, extra)
            local r = extra or {}
            r.label, r.value = label, value
            rows[#rows + 1] = r
        end
        local alive = plant:isAlive()
        if cheat then row(getText("Farming_Current_growing_phase"), ISFarmingInfo.getCurrentGrowingPhase(info, farming)) end
        if (farming >= 6 or cheat) and alive then row(getText("Farming_Next_growing_phase"), ISFarmingInfo.getNextGrowingPhase(info)) end
        if cheat then
            local fertilizer = getText("Farming_Compost_False")
            if plant.fertilizer == 1 then fertilizer = getText("Farming_Compost_True")
            elseif plant.fertilizer > 1 then fertilizer = getText("Farming_Fertilizer_TooMuch") end
            row(getText("Farming_Fertilized"), fertilizer)
        end
        row(getText("Farming_Compost"), getText(plant.compost and "Farming_Compost_True" or "Farming_Compost_False"))
        if cheat then row(getText("Farming_Health"), ISFarmingInfo.getHealth(info, farming)) end
        -- Pests show with Farming 3, or 1 with a magnifier in hand.
        local magnifier = 0
        for _, hand in ipairs({ try(player, "getPrimaryHandItem"), try(player, "getSecondaryHandItem") }) do
            if try(hand, "hasTag", ItemTag.MAGNIFIER) == true then magnifier = 2 end
        end
        if (farming + magnifier >= 3 or cheat) and ISFarmingInfo.hasDisease(plant) then
            row(getText("Farming_Disease"), nil)
            for _, pest in ipairs(PESTS) do
                if plant[pest.level] > 0 then
                    row(getText(pest.text), ISFarmingInfo.getDiseaseString(plant[pest.level], farming),
                        { tone = "bad", icon = pest.icon, indent = true })
                end
            end
        end
        if alive then
            -- The water bar with Farming 4.
            row(getText("Farming_Water_levels"), ISFarmingInfo.getWaterLvl(plant, farming),
                { meter = farming >= 4 and math.max(0, math.min(1, plant.waterLvl / 100)) or nil, water = true })
        end
        local title
        if plant:getObject() then
            title = farming_vegetableconf.getObjectName(plant)
        else
            title = "Dead " .. getText("Farming_" .. plant.typeOfSeed) -- the game's own words
        end
        local props = farming_vegetableconf.props[plant.typeOfSeed]
        return { title = title, icon = props and iconOf(props.icon) or nil, status = getText("Farming_Plant_Information"), rows = rows }
    end,
}

--- An animal track (ISAnimalTracksUI): its window is made but never shown; its own fields and
--- getAnimalType give what it says, with the Tracking level it was looked at with. It has no close
--- of its own: it stays until closed.
local function label(key)
    return (string.gsub(getText(key), ":%s*$", ""))
end

kinds.track = {
    there = function() return true end,
    static = true,
    read = function(player, track)
        local window = ISAnimalTracksUI:new(0, 0, 300, 200, track, player)
        local data = window.track
        local rows = {}
        rows[#rows + 1] = { label = label("IGUI_AnimalTracks_Type"), value = getText("IGUI_AnimalTracks_" .. data:getTrackType()) }
        if data:getDir() then
            rows[#rows + 1] = { label = label("IGUI_AnimalTracks_Direction"), value = data:getDir():toString() }
        end
        rows[#rows + 1] = { label = label("IGUI_AnimalTracks_Animal"), value = window:getAnimalType() }
        rows[#rows + 1] = { label = label("IGUI_AnimalTracks_Freshness"), value = data:getFreshnessString(window.trackingLevel) }
        return { title = getText("IGUI_AnimalTracks_" .. data:getTrackType()), icon = iconOf(try(window.texture, "getName")), rows = rows }
    end,
}

--- A CD's or tape's back cover ("Check Back Cover", ISMediaInfo): its text, which is prose ("Released
--- February 1993. Potassium Records."), so it stays one text row, never split into a label and value.
kinds.media = {
    there = function() return true end,
    static = true,
    read = function(_player, text)
        text = string.gsub(string.gsub(tostring(text), "<[^>]*>", " "), "^%s+", "")
        return { title = getText("IGUI_media_readLabel"), rows = { { text = (string.gsub(text, "%s+$", "")) } } }
    end,
}

function InfoWindows.state(player)
    local entry = open[player:getPlayerNum()]
    if entry == nil then
        return false, "No info open"
    end
    local kind = kinds[entry.kind]
    local ok, there = pcall(kind.there, player, entry.object)
    if not ok or not there then
        open[player:getPlayerNum()] = nil
        return true, nil, { gone = true }
    end
    -- A track's or back cover's card never changes: read once (a track's read builds a window object).
    if entry.state ~= nil then
        return true, nil, entry.state
    end
    local read, state = pcall(kind.read, player, entry.object)
    if not read then
        return false, "This game version's info window isn't known"
    end
    state.kind = entry.kind
    if kind.static then
        entry.state = state
    end
    return true, nil, state
end

InfoWindows.commands = {
    info_state = function(player, _args) return InfoWindows.state(player) end,
    info_close = function(player, _args)
        open[player:getPlayerNum()] = nil
        return true
    end,
}

--- Each info action opens the app's card instead of its window, while the app is there.
function InfoWindows.redirect(onShow)
    local actions = {
        { class = ISGeneratorInfoAction, kind = "generator", object = function(a) return a.object end },
        { class = ISCampingInfoAction, kind = "campfire", object = function(a) return a.campfire end },
        { class = ISBBQInfoAction, kind = "bbq", object = function(a) return a.bbq end },
        { class = ISPlantInfoAction, kind = "plant", object = function(a) return a.plant end },
        { class = ISInspectAnimalTrackAction, kind = "track", object = function(a) return a.track end },
    }
    for _, action in ipairs(actions) do
        local class = action.class
        if class ~= nil and class.perform ~= nil and not class.zomboidDSRedirect then
            class.zomboidDSRedirect = true
            local original = class.perform
            class.perform = function(self, ...)
                local playerNum = self.character:getPlayerNum()
                local previous = open[playerNum]
                local object = action.object(self)
                open[playerNum] = object and { kind = action.kind, object = object } or previous
                local ok, handled = pcall(function()
                    return object ~= nil and onShow(playerNum, { panel = "info" })
                end)
                if ok and handled then
                    return ISBaseTimedAction.perform(self)
                end
                open[playerNum] = previous
                return original(self, ...)
            end
        end
    end
    -- "Check Back Cover" opens its window straight away (ISMediaInfo.openPanel), no action.
    if ISMediaInfo ~= nil and ISMediaInfo.openPanel ~= nil and not ISMediaInfo.zomboidDSRedirect then
        ISMediaInfo.zomboidDSRedirect = true
        local original = ISMediaInfo.openPanel
        ISMediaInfo.openPanel = function(playerNum, text, ...)
            local previous = open[playerNum]
            open[playerNum] = text and { kind = "media", object = text } or previous
            local ok, handled = pcall(function() return text ~= nil and onShow(playerNum, { panel = "info" }) end)
            if ok and handled then
                return nil
            end
            open[playerNum] = previous
            return original(playerNum, text, ...)
        end
    end
end

return InfoWindows
