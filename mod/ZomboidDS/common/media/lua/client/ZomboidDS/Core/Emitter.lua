--- Decides when to snapshot each channel and sends only what changed.
---
--- A channel is re-snapshotted when a game event marks it dirty, or when its interval has passed.
--- The snapshot is sent only if it differs from the last one sent.
local Signature = require("ZomboidDS/Core/Signature")

local Emitter = {}
Emitter.__index = Emitter

--- @param bridge  table with emit(type, data)
--- @param clock   function returning milliseconds (defaults to the game's getTimestampMs)
function Emitter.new(bridge, clock)
    return setmetatable({ bridge = bridge, clock = clock or getTimestampMs, channels = {} }, Emitter)
end

--- @param interval number (ms) or function(player) -> ms
function Emitter:addChannel(messageType, snapshot, interval)
    table.insert(self.channels, {
        type = messageType,
        snapshot = snapshot,
        interval = interval,
        dirty = true,
        due = 0,
        lastSignature = nil,
    })
end

function Emitter:markDirty(messageType)
    for _, channel in ipairs(self.channels) do
        if channel.type == messageType then
            channel.dirty = true
        end
    end
end

--- Forces every channel to snapshot and send again, e.g. when a client connects.
function Emitter:invalidate()
    for _, channel in ipairs(self.channels) do
        channel.dirty = true
        channel.lastSignature = nil
    end
end

function Emitter:update(player)
    local now = self.clock()
    for _, channel in ipairs(self.channels) do
        if channel.dirty or now >= channel.due then
            local interval = channel.interval
            if type(interval) == "function" then
                interval = interval(player)
            end
            channel.dirty = false
            channel.due = now + interval

            local ok, data = pcall(channel.snapshot, player)
            if not ok then
                print("[ZomboidDS] snapshot '" .. channel.type .. "' failed: " .. tostring(data))
            elseif data ~= nil then
                local signature = Signature.of(data)
                if signature ~= channel.lastSignature then
                    channel.lastSignature = signature
                    self.bridge.emit(channel.type, data)
                end
            end
        end
    end
end

return Emitter
