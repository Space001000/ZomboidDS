--- Wires the version-neutral core to the adapter for the running game build.
local Adapters = require("ZomboidDS/Core/Adapters")
local Bridge = require("ZomboidDS/Core/Bridge")
local Commands = require("ZomboidDS/Core/Commands")
local Config = require("ZomboidDS/Core/Config")
local Emitter = require("ZomboidDS/Core/Emitter")

local state = {
    adapter = nil,
    emitter = nil,
    hadClients = false,
}

local function onTick()
    local player = getSpecificPlayer(0)
    if player == nil then
        return
    end

    Commands.pump(Bridge, state.adapter, player, Config.maxCommandsPerTick)

    -- Skip all snapshot work while nobody is listening. When someone connects, resend everything
    -- so they don't get stale state the bridge retained from earlier.
    if Bridge.clients() == 0 then
        state.hadClients = false
        return
    end
    if not state.hadClients then
        state.hadClients = true
        state.emitter:invalidate()
    end
    state.emitter:update(player)
end

local function wireDirtyEvents(adapter, emitter)
    for channel, eventNames in pairs(adapter.dirtyEvents) do
        for _, eventName in ipairs(eventNames) do
            local event = Events[eventName]
            if event then
                event.Add(function() emitter:markDirty(channel) end)
            else
                print("[ZomboidDS] event " .. eventName .. " does not exist in this build; relying on polling")
            end
        end
    end
end

--- Shows [text] above the player a few seconds after the game starts, when they can see it.
local function notifySoon(adapter, text)
    print("[ZomboidDS] " .. text)
    if adapter == nil or adapter.notify == nil then
        return
    end
    local showAt = getTimestampMs() + 5000
    local function show()
        local player = getSpecificPlayer(0)
        if player == nil or getTimestampMs() < showAt then
            return
        end
        Events.OnTick.Remove(show)
        pcall(adapter.notify, player, text)
    end
    Events.OnTick.Add(show)
end

local function onGameStart()
    local _, _, versionText = Adapters.gameVersion()
    local adapter = Adapters.select()

    -- The mod's Lua runs, but its Java side doesn't: ZombieBuddy is missing or disabled.
    if not Bridge.isAvailable() then
        notifySoon(adapter, "ZomboidDS: the companion app can't connect because ZombieBuddy isn't active. "
            .. "Open the ZomboidDS app for setup.")
        return
    end

    if adapter == nil then
        print("[ZomboidDS] no adapter for game version " .. versionText)
        Bridge.emit("session", { inGame = false, gameVersion = versionText })
        return
    end
    print("[ZomboidDS] using adapter " .. adapter.id .. " for game version " .. versionText)

    local emitter = Emitter.new(Bridge)
    emitter:addChannel("player", adapter.snapshotPlayer, Config.intervalsMs.player)
    emitter:addChannel("inventory", adapter.snapshotInventory, Config.intervalsMs.inventory)
    emitter:addChannel("vehicle", adapter.snapshotVehicle, function(player)
        return adapter.isInVehicle(player) and Config.intervalsMs.vehicle or Config.intervalsMs.vehicleIdle
    end)
    wireDirtyEvents(adapter, emitter)

    state.adapter = adapter
    state.emitter = emitter

    Bridge.emit("session", {
        inGame = true,
        gameVersion = versionText,
        adapter = adapter.id,
        capabilities = adapter.capabilities,
    })

    if Config.hideNativeUI and adapter.hideNativeUI then
        adapter.hideNativeUI(0)
    end

    Events.OnTick.Add(onTick)
end

-- Lua (and this file) is reloaded when entering or leaving a game, so anything the bridge still
-- holds from before is stale.
Bridge.reset()

Events.OnGameStart.Add(onGameStart)
