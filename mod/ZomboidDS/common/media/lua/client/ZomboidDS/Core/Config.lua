--- Tunables for the version-neutral core.
local Config = {
    -- Hide the vanilla inventory/loot windows on the top screen. Leave off until the companion
    -- app can do everything you need from them.
    hideNativeUI = false,

    -- Commands executed per tick at most, so a burst from the app can't stall a frame.
    maxCommandsPerTick = 8,

    -- How often each channel is re-sampled even without a change event. Unchanged data isn't
    -- re-sent, so these mostly bound how stale polled values (health, speed) can get.
    intervalsMs = {
        player = 500,
        inventory = 1000,
        vehicle = 100,     -- while in a vehicle (speedometer)
        vehicleIdle = 1000,
    },
}

return Config
