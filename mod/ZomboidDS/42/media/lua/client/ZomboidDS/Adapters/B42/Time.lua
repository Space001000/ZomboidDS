--- Game speed: the game's own speed buttons.
local Util = require("ZomboidDS/Adapters/B42/Util")
local try = Util.try

local Time = {}

-- The game's own speed buttons (top right; zombie.ui.SpeedControls in 42.20): speeds 0 pause,
-- 1 play, 2 fast forward (x5), 3 faster (x20), 4 wait (x40). We press its buttons by name with
-- ButtonClicked, like the controller's back-button wheel (ISBackButtonWheel), so its icons and
-- state stay in sync. Not in multiplayer, same as the game.

local SPEED_BUTTONS = { [0] = "Pause", [1] = "Play", [2] = "Fast Forward x 1", [3] = "Fast Forward x 2", [4] = "Wait" }

local function speedControls()
    return UIManager and try(UIManager, "getSpeedControls") or nil
end

--- The game's pause menu (Esc: settings, quit, ...) is open. The game ignores its speed buttons
--- behind it; so do we, or the game would run on under a menu that no longer takes the controller.
--- Same check as the game's controller code (42.20 JoyPadSetup.lua).
local function pauseMenuOpen()
    local screen = MainScreen and MainScreen.instance
    return screen ~= nil and screen.inGame == true and try(screen, "isReallyVisible") == true
end

function Time.snapshot(_player)
    return {
        speed = try(speedControls(), "getCurrentGameSpeed"),
        canChange = speedControls() ~= nil and not (isClient and isClient()),
        gameMenuOpen = pauseMenuOpen() or nil,
    }
end

function Time.setSpeed(speed)
    local controls = speedControls()
    if controls == nil then
        return false, "The game's speed controls aren't available"
    end
    if isClient and isClient() then
        return false, "Game speed can't be changed in multiplayer"
    end
    if pauseMenuOpen() then
        return false, "Close the game's menu first"
    end
    local button = SPEED_BUTTONS[speed]
    if button == nil then
        return false, "unknown speed " .. tostring(speed)
    end
    local current = try(controls, "getCurrentGameSpeed")
    if current == speed then
        return true
    end
    -- "Pause" while paused toggles back to play in the game, so only press it when running.
    controls:ButtonClicked(button)
    return true
end

return Time
