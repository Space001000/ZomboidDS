--- Tunables for the version-neutral core.
local Config = {
    -- While the app is connected, the controller's Loot/Inventory button (Y) shows the container on
    -- the bottom screen instead of opening the game's windows on the top screen. Without the app
    -- it always opens the game's windows.
    redirectGameWindows = true,

    -- Commands executed per tick at most, so a burst from the app can't stall a frame.
    maxCommandsPerTick = 8,

    -- How often each channel is re-sampled even without a change event. Unchanged data isn't
    -- re-sent, so these mostly bound how stale polled values (health, speed) can get.
    intervalsMs = {
        player = 500,
        inventory = 1000,
        containers = 1000, -- also re-sent right away when the game refreshes its container lists
        time = 250,        -- the game speed; only sent when it changes
        health = 500,      -- injuries; only sent when they change
        moodles = 500,     -- only sent when they change
        here = 200,        -- how often "Here" checks for a move/turn while the app watches (cheap when not)
        vehicle = 100,     -- while in a vehicle (speedometer)
        vehicleIdle = 1000,
    },
}

return Config
