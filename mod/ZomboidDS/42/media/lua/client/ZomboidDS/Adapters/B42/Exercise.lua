--- Exercise: the game's exercise window (ISFitnessUI, opened from its health panel) on the bottom
--- screen.
---
--- The app works through one hidden instance of the game's own window: it lists the exercises the way
--- the window does (FitnessExercises, greyed without the barbell or dumbbell), reads the regularity bar
--- and the reason Start is greyed from it (updateButtons), and presses its OK and Cancel. So the items
--- it equips, the bags it takes off and puts back after (update), and the action are the game's.
local Util = require("ZomboidDS/Adapters/B42/Util")
local try = Util.try

local Exercise = {}

local windows = {} -- playerNum -> the hidden ISFitnessUI
local watching = false

--- The hidden windows' update puts back what an exercise took off or out of the hands once it ends
--- (ISFitnessUI:update), as the game runs it for its window every frame. Only while one runs.
local function watch()
    local busy = false
    for _, window in pairs(windows) do
        if window.currentExercice then
            pcall(window.update, window)
            busy = busy or window.currentExercice ~= nil
        end
    end
    if not busy then
        Events.OnTick.Remove(watch)
        watching = false
    end
end

local function windowFor(player)
    local n = player:getPlayerNum()
    local window = windows[n]
    if window == nil or window.player ~= player then
        window = ISFitnessUI:new(0, 0, 600, 350, player)
        window:initialise()
        windows[n] = window
    end
    return window
end

local function currentAction(player)
    local queue = ISTimedActionQueue.getTimedActionQueue(player)
    local action = queue and queue.queue and queue.queue[1] or nil
    if action ~= nil and action.Type == "ISFitnessAction" then
        return action
    end
    return nil
end

local STRAIN = { legs = "IGUI_Fitness_LegsStiffness", arms = "IGUI_Fitness_ArmsStiffness", abs = "IGUI_Fitness_AbsStiffness" }

--- The exercises by name, what doesn't change (names, icons, strain, the "(Requires ...)" text) worked
--- out once per window.
local function fixedList(window)
    if window.zomboidDSList ~= nil then
        return window.zomboidDSList
    end
    local list = {}
    for id, data in pairs(FitnessExercises.exercisesType) do
        local entry = { id = id, name = data.name, tooltip = data.tooltip, item = data.item, strain = {} }
        if data.item then
            local icon = try(try(ScriptManager.instance, "getItem", data.item), "getIcon")
            entry.icon = icon and ("Item_" .. icon) or nil
            entry.needsText = getText("IGUI_FitnessNeedItem", getItemDisplayName(data.item))
        end
        for part in string.gmatch(data.stiffness or "", "[^,]+") do
            if STRAIN[part] then entry.strain[#entry.strain + 1] = getText(STRAIN[part]) end
        end
        list[#list + 1] = entry
    end
    table.sort(list, function(a, b) return a.name < b.name end)
    window.zomboidDSList = list
    return list
end

--- The exercises as the window lists them now: greyed without the item, with the regularity bar.
local function exercises(player, window)
    local list = {}
    for _, fixed in ipairs(fixedList(window)) do
        local entry = { id = fixed.id, name = fixed.name, tooltip = fixed.tooltip, icon = fixed.icon, strain = fixed.strain }
        if fixed.item and not player:getInventory():contains(fixed.item, true) then
            entry.needs = fixed.needsText
        end
        -- The window's bar: regularity * 1.5 of its 150 px (ISFitnessUI:render).
        local regularity = try(player:getFitness(), "getRegularity", fixed.id) or 0
        entry.regularity = math.max(0, math.min(1, regularity / window.regularityProgressBarWidth * 1.5))
        list[#list + 1] = entry
    end
    return list
end

function Exercise.state(player)
    local window = windowFor(player)
    local action = currentAction(player)
    -- updateButtons sets OK's enable and its tooltip (why not), as every frame in the game.
    window:updateButtons(action)
    local state = {
        exercises = exercises(player, window),
        canStart = window.ok.enable == true,
        why = (window.ok.enable ~= true) and window.ok.tooltip or nil,
        -- ISFitnessUI:onClickTime: 10 to 60 in steps of 10.
        minutes = { min = 10, max = 60, step = 10 },
    }
    if action ~= nil then
        local now = getGameTime():getCalender():getTimeInMillis()
        state.running = {
            id = action.exeDataType or action.exercise,
            minutes = action.timeToExe,
            done = math.max(0, math.min(action.timeToExe, math.floor((now - action.startMS) / 60000))),
        }
    end
    return true, nil, state
end

Exercise.commands = {
    exercise_state = function(player, _args) return Exercise.state(player) end,
    exercise_start = function(player, args)
        local id, minutes = tostring(args.id), tonumber(args.minutes)
        local data = FitnessExercises.exercisesType[id]
        if data == nil then return false, "Not an exercise" end
        if minutes == nil or minutes < 10 or minutes > 60 or minutes % 10 ~= 0 then return false, "10 to 60 minutes" end
        local window = windowFor(player)
        window:updateButtons(currentAction(player))
        if window.ok.enable ~= true then return false, window.ok.tooltip or "Can't exercise now" end
        if data.item and not player:getInventory():contains(data.item, true) then
            return false, getText("IGUI_FitnessNeedItem", getItemDisplayName(data.item))
        end
        window.selectedExe = id
        window:selectedNewExercise()
        window.exeTime:setText(tostring(minutes))
        window:onClick(window.ok)
        if not watching and Events and Events.OnTick then
            watching = true
            Events.OnTick.Add(watch)
        end
        return true
    end,
    exercise_stop = function(player, _args)
        local window = windowFor(player)
        window:onClick(window.cancel)
        return true
    end,
}

return Exercise
