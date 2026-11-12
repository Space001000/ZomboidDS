--- Ovens and microwaves: the game's settings window (temperature, timer, Turn on / Turn off), as a
--- panel on the bottom screen.
---
--- 42.21 opens the window from one place: ISOvenUITimedAction:perform, queued after a walk to the
--- stove by "Settings" in the world menu, in the menu of an item inside it, and in the loot window
--- (ISWorldObjectContextMenu.onStoveSetting / onMicrowaveSetting). The app takes over there, after
--- the walk, as the game would open its window. The window (ISOvenUI, ISMicrowaveUI) sets the knobs
--- straight on the stove (ChangeKnob) and turns it on or off with the server's
--- setOvenParamsAndToggle; the panel does the same. Its knob marks come from the window itself
--- (addKnobValues), so the panel stops where the game's knobs stop.
local Util = require("ZomboidDS/Adapters/B42/Util")
local Containers = require("ZomboidDS/Adapters/B42/Containers")
local try = Util.try

local Ovens = {}

local REACH = 3 -- ISOvenUI:update closes the window once the player is further away

local open = {} -- playerNum -> { stove, microwave, dials }: the stove the app shows

local function current(player)
    return open[player:getPlayerNum()]
end

--- The window's own knob marks: { angle, value } in the order it adds them (ISKnob:addValue).
local function knobMarks(window)
    local function knob()
        local values = {}
        return { values = values, addValue = function(_, angle, value) values[#values + 1] = { angle = angle, value = value } end }
    end
    local stub = { tempKnob = knob(), timerKnob = knob() }
    if window == nil or window.addKnobValues == nil or not pcall(window.addKnobValues, stub) then
        return nil
    end
    return stub.tempKnob.values, stub.timerKnob.values
end

--- Whether the window would still be open (ISOvenUI:update): within REACH of a stove still in the world.
local function stillThere(player, entry)
    local stove = entry.stove
    if try(stove, "getSquare") == nil or (try(stove, "getObjectIndex") or -1) < 0 then
        return false
    end
    return (try(player, "DistTo", stove:getX(), stove:getY()) or math.huge) <= REACH
end

--- The name the game's menus give it ("Chrome Oven"), as ISWorldObjectContextMenu's
--- getMoveableDisplayName finds it.
local function nameOf(stove, microwave)
    local props = try(try(stove, "getSprite"), "getProperties")
    if props ~= nil and try(props, "has", "CustomName") == true then
        local name = props:get("CustomName")
        if try(props, "has", "GroupName") == true then
            name = props:get("GroupName") .. " " .. name
        end
        -- A static method: Util.call, not try (see Util.try).
        local shown = Translator and Util.call(Translator.getMoveableDisplayName, name)
        if shown ~= nil then return shown end
    end
    return getText(microwave and "IGUI_ContainerTitle_microwave" or "IGUI_ContainerTitle_stove")
end

--- What the oven's temperature art says at each mark: Fahrenheit 0, 250 ... 500 for the game's 0, 50
--- ... 300 (KnobBGFarhenOvenTemp), Celsius the value itself (KnobBGCelciusOvenTemp). Numbers only: the
--- app adds "°F" / "°C" from `celsius` (this file's text reaches the game as ASCII only).
--- The microwave's art has pictures, no numbers: its mark's place, lowest power first.
local function temperatureLabel(value, microwave, celsius, marks)
    if microwave then
        local below = 0
        for _, mark in ipairs(marks) do
            if mark.value < value then below = below + 1 end
        end
        return (below + 1) .. " of " .. #marks
    end
    if celsius then return tostring(value) end
    return tostring(value == 0 and 0 or value + 200)
end

--- What doesn't change while the same stove is open: the knobs' marks with their labels, its name and
--- icon. Worked out on the first read and again only when Fahrenheit/Celsius changes (the labels).
local function dials(entry)
    local microwave = entry.microwave
    local celsius = not microwave and try(getCore(), "isCelsius") == true
    local d = entry.dials
    if d == nil or d.celsius ~= celsius then
        local tempMarks, timerMarks = knobMarks(microwave and ISMicrowaveUI or ISOvenUI)
        if tempMarks == nil then
            return nil
        end
        for _, mark in ipairs(tempMarks) do
            mark.label = temperatureLabel(mark.value, microwave, celsius, tempMarks)
        end
        local sprite = try(try(entry.stove, "getSprite"), "getName")
        d = { celsius = celsius, temp = tempMarks, timer = timerMarks, name = nameOf(entry.stove, microwave),
              icon = sprite and (sprite .. "_Icon") or nil }
        entry.dials = d
    end
    return d
end

--- The stove as the window shows it.
function Ovens.state(player)
    local entry = current(player)
    if entry == nil then
        return false, "No oven open"
    end
    if not stillThere(player, entry) then
        open[player:getPlayerNum()] = nil
        return true, nil, { gone = true }
    end
    local stove, microwave = entry.stove, entry.microwave
    local d = dials(entry)
    if d == nil then
        return false, "This game version's oven window isn't known"
    end
    local celsius = d.celsius
    local container = try(stove, "getContainer")
    local temperature = math.floor((try(stove, "getMaxTemperature") or 0) + 0.5)
    local seconds = math.max(0, try(stove, "getTimer") or 0)
    local running = try(stove, "isRunningFor") or 0
    local state = {
        name = d.name,
        icon = d.icon,
        microwave = microwave or nil,
        on = try(stove, "Activated") == true,
        -- ISOvenUI:updateButtons: Turn on needs electricity there.
        powered = try(container, "isPowered") == true,
        broken = try(stove, "isBroken") == true or nil,
        container = container and Containers.idOf(container) or nil,
        temperature = temperature,
        temperatureText = temperatureLabel(temperature, microwave, celsius, d.temp),
        timer = math.floor(seconds / 60),
        dial = "KnobDial",
        temperatureDial = { art = microwave and "KnobBGMicrowaveTemp" or (celsius and "KnobBGCelciusOvenTemp" or "KnobBGFarhenOvenTemp"),
                            marks = d.temp },
        timerDial = { art = microwave and "KnobBGMicrowaveTimer" or "KnobBGOvenTimer", marks = d.timer },
    }
    -- An oven's Fahrenheit/Celsius switch; a microwave has none. (Not `a and celsius or nil`: false
    -- would come out nil, and the switch would vanish for Fahrenheit.)
    if not microwave then
        state.celsius = celsius
    end
    -- While it runs, the timer knob shows what's left (ISOvenUI:updateButtons).
    if running > 0 and seconds > 0 then
        state.left = math.max(0, math.ceil((seconds - running) / 60))
    end
    return true, nil, state
end

-- Controls ------------------------------------------------------------------------------

local function isMark(marks, value)
    for _, mark in ipairs(marks) do
        if mark.value == value then return true end
    end
    return false
end

--- The mark a knob shows for `value` (ISKnob:setKnobPosition): that mark, or between two, the higher
--- one from halfway up; below all, the first. `marks` in the window's order.
local function snapped(marks, value)
    for _, mark in ipairs(marks) do
        if mark.value == value then return mark.value end
    end
    for i = #marks, 2, -1 do
        local prev, cur = marks[i - 1].value, marks[i].value
        if value >= prev + (cur - prev) / 2 then return cur end
    end
    return marks[1] and marks[1].value or value
end

local controls = {}

--- A knob turned (ISOvenUI:ChangeKnob): straight on the stove, with the knob's sound.
function controls.set(player, entry, args)
    local stove, microwave = entry.stove, entry.microwave
    local d = dials(entry)
    local temperature, timer = tonumber(args.temperature), tonumber(args.timer)
    if d == nil or (temperature ~= nil and not isMark(d.temp, temperature))
        or (timer ~= nil and not isMark(d.timer, timer)) then
        return false, "Not a setting of this knob"
    end
    if temperature ~= nil then
        stove:setMaxTemperature(temperature)
        getSoundManager():playUISound("ToggleTemp")
    end
    if timer ~= nil then
        stove:setTimer(timer * 60)
        getSoundManager():playUISound("KnobSwitch")
    end
    stove:sync()
    if not microwave then
        try(stove, "syncSpriteGridObjects", false, true)
    end
    return true
end

--- Turn on / Turn off (the window's OK button): the server sets the knobs' values and toggles.
function controls.toggle(player, entry)
    local stove, microwave = entry.stove, entry.microwave
    if not try(try(stove, "getContainer"), "isPowered") then
        return false, "No power"
    end
    local timer = math.max(0, try(stove, "getTimer") or 0)
    -- Running, the knob shows the time left, snapped to its marks, and the button sends the knob's
    -- value (ISOvenUI:updateButtons, onClick).
    local running = try(stove, "isRunningFor") or 0
    if running > 0 and timer > 0 then
        local d = dials(entry)
        local left = math.max(0, math.ceil((timer - running) / 60))
        timer = (d and snapped(d.timer, left) or left) * 60
    end
    sendClientCommand(player, "stove", "setOvenParamsAndToggle", {
        x = stove:getX(), y = stove:getY(), z = stove:getZ(), timer = timer, maxTemperature = stove:getMaxTemperature(),
    })
    if not microwave then
        getSoundManager():playUISound("ToggleStove")
    end
    return true
end

--- Fahrenheit or Celsius: the window's tick box, which is the game's own option (saved).
function controls.units(_player, entry, args)
    if entry.microwave then return false, "A microwave has no degrees" end
    getCore():setCelsius(args.celsius == true)
    getCore():saveOptions()
    return true
end

Ovens.commands = {
    oven_state = function(player, _args)
        return Ovens.state(player)
    end,
    oven_close = function(player, _args)
        open[player:getPlayerNum()] = nil
        return true
    end,
    oven_control = function(player, args)
        local entry = current(player)
        if entry == nil or not stillThere(player, entry) then
            open[player:getPlayerNum()] = nil
            return false, "No oven open"
        end
        local control = controls[tostring(args.control)]
        if control == nil then
            return false, "Unknown control"
        end
        return control(player, entry, args)
    end,
}

--- The game's oven settings window opens the app's oven panel instead, while the app is there.
function Ovens.redirect(onShow)
    if ISOvenUITimedAction == nil or ISOvenUITimedAction.perform == nil or ISOvenUITimedAction.zomboidDSRedirect then
        return
    end
    ISOvenUITimedAction.zomboidDSRedirect = true
    local original = ISOvenUITimedAction.perform
    ISOvenUITimedAction.perform = function(self, ...)
        local playerNum = self.character:getPlayerNum()
        local previous = open[playerNum]
        local stove = self.mcwave or self.stove
        open[playerNum] = stove and { stove = stove, microwave = self.mcwave ~= nil } or previous
        local ok, handled = pcall(function()
            return stove ~= nil and onShow(playerNum, { panel = "oven" })
        end)
        if ok and handled then
            -- The action itself still finishes, as the game's perform does before opening its window.
            return ISBaseTimedAction.perform(self)
        end
        open[playerNum] = previous
        return original(self, ...)
    end
end

return Ovens
