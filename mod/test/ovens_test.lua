-- Ovens (B42/Ovens.lua): the game's oven and microwave settings window as the app's panel: the
-- knobs' marks read from the window itself, the state, the controls, and Settings opening the panel.
-- Fakes stand in for the stove, the window classes and the timed action.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
function instanceof() return false end
function getText(key) return ({ IGUI_ContainerTitle_stove = "Oven", IGUI_ContainerTitle_microwave = "Microwave" })[key] or key end
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

local sounds, sent = {}, {}
function getSoundManager() return { playUISound = function(_, name) table.insert(sounds, name) end } end
function sendClientCommand(_, module, command, args) table.insert(sent, { module = module, command = command, args = args }) end
local celsius, saved = false, false
function getCore() return {
  isCelsius = function() return celsius end,
  setCelsius = function(_, c) celsius = c end,
  saveOptions = function() saved = true end,
} end
Translator = { getMoveableDisplayName = function(name) return ({ ["Chrome Oven"] = "Chrome Oven" })[name] end }

-- The game's windows: only their knob marks (42.21's addKnobValues).
ISOvenUI = { addKnobValues = function(self)
  for i, v in ipairs({ 0, 50, 100, 150, 200, 250, 300 }) do self.tempKnob:addValue((i - 1) * 45, v) end
  for i, v in ipairs({ 0, 1, 2, 3, 4, 5, 10, 15, 20, 25, 30, 40, 50, 60, 90, 120 }) do self.timerKnob:addValue((i - 1) * 18, v) end
end }
ISMicrowaveUI = { addKnobValues = function(self)
  self.tempKnob:addValue(0, 90); self.tempKnob:addValue(45, 110); self.tempKnob:addValue(90, 130)
  self.tempKnob:addValue(270, 50); self.tempKnob:addValue(315, 70)
  for i, v in ipairs({ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 25, 30, 45, 60 }) do self.timerKnob:addValue((i - 1) * 18, v) end
end }

local function stove(o)
  local s = { maxTemp = o.temp or 0, timer = o.timer or -1, running = o.running or 0, on = o.on or false, synced = 0, gridSynced = 0 }
  local props = { CustomName = o.custom, GroupName = o.group }
  function s:getSprite() return { getName = function() return o.sprite end,
    getProperties = function() return { has = function(_, k) return props[k] ~= nil end, get = function(_, k) return props[k] end } end } end
  function s:getSquare() return o.gone and nil or {} end
  function s:getObjectIndex() return o.gone and -1 or 3 end
  function s:getX() return 10 end
  function s:getY() return 20 end
  function s:getZ() return 0 end
  function s:getContainer() return { isPowered = function() return o.powered ~= false end } end
  function s:getMaxTemperature() return self.maxTemp end
  function s:setMaxTemperature(t) self.maxTemp = t end
  function s:getTimer() return self.timer end
  function s:setTimer(t) self.timer = t end
  function s:isRunningFor() return self.running end
  function s:Activated() return self.on end
  function s:isBroken() return false end
  function s:sync() self.synced = self.synced + 1 end
  function s:syncSpriteGridObjects() self.gridSynced = self.gridSynced + 1 end
  return s
end

local distance = 1
local player = { getPlayerNum = function() return 0 end, DistTo = function() return distance end }

local Ovens = require("ZomboidDS/Adapters/B42/Ovens")

-- Settings opens the app's panel while the app is there --------------------------------------
local finished, gameWindow = 0, 0
ISBaseTimedAction = { perform = function() finished = finished + 1 end }
ISOvenUITimedAction = { perform = function() finished = finished + 1; gameWindow = gameWindow + 1 end }
local shown, appThere = nil, true
Ovens.redirect(function(_, show) if not appThere then return false end shown = show return true end)

check(not Ovens.commands.oven_state(player, {}), "nothing open: nothing to show")
local oven = stove({ sprite = "appliances_cooking_01_20", custom = "Oven", group = "Chrome", temp = 200, timer = 1800, running = 720, on = true })
ISOvenUITimedAction.perform({ character = player, stove = oven })
check(shown and shown.panel == "oven" and gameWindow == 0 and finished == 1,
  "Settings: the app's panel, the game's window stays shut, the action still finishes")

local ok, _, s = Ovens.commands.oven_state(player, {})
check(ok and s.name == "Chrome Oven" and s.icon == "appliances_cooking_01_20_Icon", "its name in the game's menus, its sprite's icon")
check(s.on and s.powered and s.microwave == nil, "an oven, on and powered")
check(s.temperature == 200 and s.temperatureText == "400", "200 is 400 on the game's Fahrenheit dial")
check(s.celsius == false, "Fahrenheit: the switch is there, set to F (false, not left out)")
check(s.timer == 30 and s.left == 18, "set to 30 min, 18 min left while it runs")
check(#s.temperatureDial.marks == 7 and s.temperatureDial.marks[2].angle == 45 and s.temperatureDial.marks[2].label == "250",
  "the temperature marks come from the game's window, with the dial's numbers")
check(#s.timerDial.marks == 16 and s.timerDial.art == "KnobBGOvenTimer" and s.temperatureDial.art == "KnobBGFarhenOvenTemp",
  "the timer's 16 marks, and the game's dial art")

-- Controls --------------------------------------------------------------------------------
check(Ovens.commands.oven_control(player, { control = "set", temperature = 250 }) and oven.maxTemp == 250
  and oven.synced == 1 and oven.gridSynced == 1 and sounds[#sounds] == "ToggleTemp",
  "turning the temperature knob: on the stove, synced, with the knob's sound")
check(Ovens.commands.oven_control(player, { control = "set", timer = 5 }) and oven.timer == 300 and sounds[#sounds] == "KnobSwitch",
  "the timer knob: minutes to seconds, its own sound")
check(not Ovens.commands.oven_control(player, { control = "set", temperature = 225 }) and oven.maxTemp == 250,
  "a value between the knob's marks is refused")
oven.timer, oven.running = 1800, 720
check(Ovens.commands.oven_control(player, { control = "toggle" }) and sent[1].command == "setOvenParamsAndToggle"
  and sent[1].args.timer == 20 * 60 and sent[1].args.maxTemperature == 250 and sounds[#sounds] == "ToggleStove",
  "Turn off: the server's toggle with the knobs (18 min left shows as the 20 mark, as the knob), the stove's sound")
check(Ovens.commands.oven_control(player, { control = "units", celsius = true }) and celsius and saved, "Celsius: the game's own option, saved")
ok, _, s = Ovens.commands.oven_state(player, {})
check(s.celsius and s.temperatureText == "250" and s.temperatureDial.art == "KnobBGCelciusOvenTemp", "then the Celsius dial")
celsius = false

-- No power
local cold = stove({ sprite = "appliances_cooking_01_0", powered = false })
ISOvenUITimedAction.perform({ character = player, stove = cold })
ok, _, s = Ovens.commands.oven_state(player, {})
check(not s.powered and not s.on and s.timer == 0 and s.left == nil and s.temperatureText == "0", "no power, never set: off, 0")
check(not Ovens.commands.oven_control(player, { control = "toggle" }) and #sent == 1, "no power: Turn on does nothing, as the greyed button")
check(s.name == "Oven", "no moveable name: the game's word for it")

-- A microwave
local mw = stove({ sprite = "appliances_cooking_01_24", temp = 110, timer = 420, running = 240, on = true })
ISOvenUITimedAction.perform({ character = player, mcwave = mw })
ok, _, s = Ovens.commands.oven_state(player, {})
check(s.microwave and s.celsius == nil and s.temperatureText == "4 of 5" and s.temperatureDial.art == "KnobBGMicrowaveTemp",
  "a microwave: power marks without numbers, 110 is the 4th of 5")
check(#s.timerDial.marks == 17 and s.left == 3, "its 17 timer marks, 3 min left")
check(not Ovens.commands.oven_control(player, { control = "units", celsius = true }) and not celsius, "no degrees to switch")
sounds = {}
check(Ovens.commands.oven_control(player, { control = "set", temperature = 50 }) and mw.maxTemp == 50 and mw.gridSynced == 0,
  "a microwave's power: no sprite grid to sync")
check(Ovens.commands.oven_control(player, { control = "toggle" }) and #sounds == 1, "a microwave's button has no sound of its own")

-- Walking away closes it, as the game's window
distance = 3.5
ok, _, s = Ovens.commands.oven_state(player, {})
check(ok and s.gone, "more than 3 tiles away: gone")
check(not Ovens.commands.oven_state(player, {}), "and forgotten")
distance = 1

-- Without the app the game's window opens
appThere = false
ISOvenUITimedAction.perform({ character = player, stove = oven })
check(gameWindow == 1, "no app: the game's own window")
appThere = true
ISOvenUITimedAction.perform({ character = player, stove = oven })
check(Ovens.commands.oven_close(player, {}) and not Ovens.commands.oven_state(player, {}), "closing the panel forgets the oven")
