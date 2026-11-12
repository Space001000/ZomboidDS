-- InfoWindows (B42/InfoWindows.lua): a generator's, campfire's, barbecue's and plant's info as the
-- game's windows say it, opened by their actions and closed where the windows close.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end
function getText(key, a)
  local t = ({ IGUI_Generator_TypeGas = "Gas-powered Generator", Farming_Compost = "Compost", Farming_Compost_False = "No",
    Farming_Compost_True = "Yes", Farming_Next_growing_phase = "Next growth stage", Farming_Water_levels = "Water level",
    Farming_Disease = "Disease", Farming_Aphid = "Aphids", Farming_Plant_Information = "Plant Information" })[key] or key
  return a and (string.gsub(t, "%%1", tostring(a))) or t
end
ItemTag = { MAGNIFIER = "magnifier" }
local Info = require("ZomboidDS/Adapters/B42/InfoWindows")

-- Rich text to rows
local rows = Info.rows("Fuel: 63% <LINE> Condition: 88% <LINE>  <LINE> Power Consumption: <LINE>  <INDENT:10>    Lights x6 (0.0012 L/h) <LINE> Gas-powered Generator (0.02 L/h) <LINE> Total: 0.1912 L/h <LINE>  <LINE> <RED> Will fill the building with toxic fumes when operated here.")
check(rows[1].label == "Fuel" and rows[1].value == "63%" and rows[1].meter == 0.63, "Fuel: a value with a meter")
check(rows[3].label == "Power Consumption" and rows[3].value == nil, "a heading")
check(rows[4].label == "Lights x6" and rows[4].value == "0.0012 L/h" and rows[4].indent, "a powered thing and its use")
check(rows[6].label == "Total" and rows[6].value == "0.1912 L/h", "the total")
check(rows[7].text == "Will fill the building with toxic fumes when operated here." and rows[7].tone == "bad", "red text is bad news")
rows = Info.rows("Fuel: 1 hour 40 minutes (burning) <LINE> <RGB:1,0,0> Needs a propane tank")
check(rows[1].label == "Fuel" and rows[1].value == "1 hour 40 minutes (burning)" and rows[2].tone == "bad", "a barbecue's lines")

-- The game's windows: only setObject / getRichText as 42.21 has them
local generator = { idx = 3 }
function generator:getObjectIndex() return self.idx end
function generator:getTextureName() return "appliances_misc_01_0" end
ISGeneratorInfoWindow = {
  setObject = function(self, object) self.object = object; self.panel:setName(getText("IGUI_Generator_TypeGas")); self.panel:setTexture(object:getTextureName()) end,
  getRichText = function() return "Fuel: 63% <LINE> Condition: 88%" end,
}
ISBaseTimedAction = { perform = function(self) self.finished = true end }
local windowOpened = false
ISGeneratorInfoAction = { perform = function() windowOpened = true end }
ISPlantInfoAction = { perform = function() windowOpened = true end }
local shown
Info.redirect(function(_, show) shown = show return true end)

local player = { getPlayerNum = function() return 0 end, DistTo = function() return 2 end }
local action = { character = player, object = generator }
ISGeneratorInfoAction.perform(action)
check(shown and shown.panel == "info" and action.finished and not windowOpened, "Generator Info: the app's card, the action finishes")
local ok, _, s = Info.commands.info_state(player, {})
check(ok and s.kind == "generator" and s.title == "Gas-powered Generator" and s.icon == "appliances_misc_01_0_Icon", "the window's name and picture")
check(#s.rows == 2 and s.rows[2].meter == 0.88, "and its lines")
generator.idx = -1
ok, _, s = Info.commands.info_state(player, {})
check(s.gone, "gone with the generator")

-- A plant, with Farming 6: next stage, compost, pests, water
local plant = { typeOfSeed = "Tomato", compost = false, fertilizer = 0, aphidLvl = 12, mildewLvl = 0, fliesLvl = 0, slugsLvl = 0, waterLvl = 58 }
function plant:updateFromIsoObject() end
function plant:getIsoObject() return {} end
function plant:getObject() return {} end
function plant:getSquare() return { getX = function() return 1 end, getY = function() return 1 end } end
function plant:isAlive() return true end
local farmingLevel = 6
CFarmingSystem = { instance = { getXp = function() return farmingLevel end } }
ISFarmingMenu = { cheat = false }
ISFarmingInfo = {
  getNextGrowingPhase = function() return "2 days" end,
  hasDisease = function(p) return p.aphidLvl > 0 end,
  getDiseaseString = function() return "Moderate" end,
  getWaterLvl = function() return "Fine" end,
}
farming_vegetableconf = { getObjectName = function() return "Tomato Vine" end, props = { Tomato = { icon = "Item_Tomato" } } }
ISPlantInfoAction.perform({ character = player, plant = plant })
ok, _, s = Info.commands.info_state(player, {})
check(s.kind == "plant" and s.title == "Tomato Vine" and s.icon == "Item_Tomato", "the plant's name and icon")
local labels = {}
for _, r in ipairs(s.rows) do labels[#labels + 1] = r.label end
check(table.concat(labels, ",") == "Next growth stage,Compost,Disease,Aphids,Water level", "Farming 6: the rows the game shows, in its order")
check(s.rows[4].icon == "Item_Insect_Aphid" and s.rows[4].tone == "bad" and s.rows[5].meter == 0.58, "pests with their icon, the water bar")
farmingLevel = 2
ok, _, s = Info.commands.info_state(player, {})
labels = {}
for _, r in ipairs(s.rows) do labels[#labels + 1] = r.label end
check(table.concat(labels, ",") == "Compost,Water level" and s.rows[2].meter == nil, "Farming 2: no next stage, no pests, no water bar")
player.DistTo = function() return 7 end
ok, _, s = Info.commands.info_state(player, {})
check(s.gone, "more than 6 tiles from the plant: closed, as the game's")

-- An animal track: the window's own fields, made but never shown
local data = {
  getTrackType = function() return "poop" end, getDir = function() return { toString = function() return "N" end } end,
  getFreshnessString = function(_, level) return level >= 3 and "Fresh" or "Recent" end,
}
ISAnimalTracksUI = { new = function(_, _, _, _, _, track, chr)
  return { track = track.data, trackingLevel = chr.tracking, texture = { getName = function() return "media/textures/Item_AnimalPoop.png" end },
    getAnimalType = function() return "Rabbit" end }
end }
ISInspectAnimalTrackAction = { perform = function() windowOpened = true end }
Info.redirect(function(_, show) shown = show return true end)
player.DistTo = function() return 1 end
player.tracking = 3
local fresh = { data = data }
ISInspectAnimalTrackAction.perform({ character = player, track = fresh })
ok, _, s = Info.commands.info_state(player, {})
labels = {}
for _, r in ipairs(s.rows) do labels[#labels + 1] = r.label .. "=" .. r.value end
check(s.kind == "track" and s.title == "IGUI_AnimalTracks_poop" and s.icon == "Item_AnimalPoop", "a track: its type as the title, its picture")
check(table.concat(labels, ",") == "IGUI_AnimalTracks_Type=IGUI_AnimalTracks_poop,IGUI_AnimalTracks_Direction=N,IGUI_AnimalTracks_Animal=Rabbit,IGUI_AnimalTracks_Freshness=Fresh",
  "type, direction, species and freshness, by the Tracking level")

-- A tape's back cover opens straight away
local mediaWindow = false
ISMediaInfo = { openPanel = function() mediaWindow = true end }
Info.redirect(function(_, show) shown = show return true end)
ISMediaInfo.openPanel(0, "Starring: Rex Neat. Released 1991.")
ok, _, s = Info.commands.info_state(player, {})
check(not mediaWindow and s.kind == "media" and s.title == "IGUI_media_readLabel" and #s.rows == 1
  and s.rows[1].text == "Starring: Rex Neat. Released 1991.", "Check Back Cover: its text as one piece of prose, no window")
check(Info.commands.info_close(player, {}) and not Info.commands.info_state(player, {}), "closing it forgets it")
