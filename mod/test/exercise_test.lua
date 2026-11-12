-- Exercise (B42/Exercise.lua): the game's exercise window, worked through a hidden instance of it.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end
function getText(key, a)
  local t = ({ IGUI_FitnessNeedItem = "(Requires %1)", IGUI_Fitness_ArmsStiffness = "arms strain" })[key] or key
  return a and (string.gsub(t, "%%1", tostring(a))) or t
end
function getItemDisplayName(t) return ({ ["Base.BarBell"] = "Barbell" })[t] or t end
ScriptManager = { instance = { getItem = function(_, t) return { getIcon = function() return t == "Base.BarBell" and "Barbell" or "Dumbbell" end } end } }
local handlers = {}
Events = { OnTick = {
  Add = function(f) handlers[#handlers + 1] = f end,
  Remove = function(f) for i, h in ipairs(handlers) do if h == f then table.remove(handlers, i) return end end end,
} }
local minutesNow = 0
function getGameTime() return { getCalender = function() return { getTimeInMillis = function() return minutesNow * 60000 end } end } end

FitnessExercises = { exercisesType = {
  pushups = { type = "pushups", name = "Push-ups", tooltip = "Improves strength.", stiffness = "arms,chest" },
  barbellcurl = { type = "barbellcurl", name = "Barbell Curls", item = "Base.BarBell", stiffness = "arms" },
} }

local queue = {}
ISTimedActionQueue = { getTimedActionQueue = function() return { queue = queue } end }
local blocked
-- The game's window: only what Exercise uses.
ISFitnessUI = {}
ISFitnessUI.__index = ISFitnessUI
function ISFitnessUI:new(_, _, _, _, player)
  return setmetatable({ player = player, regularityProgressBarWidth = 150, itemsToReequip = {} }, self)
end
function ISFitnessUI:initialise()
  self.ok = { internal = "OK" }; self.cancel = { internal = "CANCEL" }
  local text = "10"
  self.exeTime = { setText = function(_, t) text = t end, getInternalText = function() return text end }
end
function ISFitnessUI:updateButtons()
  self.ok.enable = blocked == nil
  self.ok.tooltip = blocked
end
function ISFitnessUI:selectedNewExercise() self.exeData = FitnessExercises.exercisesType[self.selectedExe] end
local reequipped = 0
function ISFitnessUI:update()
  if self.currentExercice and #queue == 0 then reequipped = reequipped + 1; self.currentExercice = nil end
end
function ISFitnessUI:onClick(button)
  if button.internal == "OK" then
    queue[1] = { Type = "ISFitnessAction", exeDataType = self.selectedExe, timeToExe = tonumber(self.exeTime:getInternalText()), startMS = minutesNow * 60000 }
    self.currentExercice = self.selectedExe
  elseif button.internal == "CANCEL" then
    queue[1] = nil
  end
end

local has = {}
local player = {
  getPlayerNum = function() return 0 end,
  getInventory = function() return { contains = function(_, t) return has[t] == true end } end,
  getFitness = function() return { getRegularity = function(_, id) return id == "pushups" and 50 or 0 end } end,
}

local Exercise = require("ZomboidDS/Adapters/B42/Exercise")
local ok, _, s = Exercise.commands.exercise_state(player, {})
check(ok and #s.exercises == 2 and s.exercises[1].name == "Barbell Curls", "the game's exercises, by name")
check(s.exercises[1].needs == "(Requires Barbell)" and s.exercises[1].icon == "Item_Barbell", "without a barbell: greyed with the game's words")
check(math.abs(s.exercises[2].regularity - 0.5) < 0.001 and s.exercises[2].strain[1] == "arms strain", "the regularity bar and the strain")
check(s.canStart and s.running == nil and s.minutes.max == 60, "nothing running, Start open, 10 to 60 minutes")
check(#handlers == 0, "no per-tick work before an exercise")

check(not Exercise.commands.exercise_start(player, { id = "barbellcurl", minutes = 20 }), "no barbell: can't start it")
check(not Exercise.commands.exercise_start(player, { id = "pushups", minutes = 25 }), "only steps of 10")
check(Exercise.commands.exercise_start(player, { id = "pushups", minutes = 20 }) and queue[1].timeToExe == 20, "Start presses the window's OK")
minutesNow = 7
ok, _, s = Exercise.commands.exercise_state(player, {})
check(s.running.id == "pushups" and s.running.minutes == 20 and s.running.done == 7, "running: 7 of 20 minutes")
check(#handlers == 1, "watching while it runs")
check(Exercise.commands.exercise_stop(player, {}) and queue[1] == nil, "Stop: the window's Cancel")
handlers[1]()
check(reequipped == 1 and #handlers == 0, "once it ends, the window puts things back; the watch stops")

blocked = "Carrying too much to exercise."
ok, _, s = Exercise.commands.exercise_state(player, {})
check(not s.canStart and s.why == blocked, "the game's reason when you can't")
local refused, why = Exercise.commands.exercise_start(player, { id = "pushups", minutes = 10 })
check(not refused and why == blocked, "and Start says it")
