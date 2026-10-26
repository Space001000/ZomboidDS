--- Decides when to snapshot each channel and sends only what changed.
---
--- A channel is re-snapshotted when a game event marks it dirty, or when its interval has passed.
--- The snapshot is sent only if it differs from the last one sent.
local Signature = require("ZomboidDS/Core/Signature")

local Emitter = {}
Emitter.__index = Emitter

--- @param bridge  table with emit(type, data); optionally emitIfChanged(type, data) -> sent (nil:
---                can't compare) and forgetSent(), which compare in Java instead of with Signature
--- @param clock   function returning milliseconds (defaults to the game's getTimestampMs)
--- @param perf    optional Perf (Core/Perf.lua) that times each channel
function Emitter.new(bridge, clock, perf)
    return setmetatable({ bridge = bridge, clock = clock or getTimestampMs, perf = perf, channels = {} }, Emitter)
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
    if self.bridge.forgetSent then
        self.bridge.forgetSent()
    end
end

function Emitter:update(player)
    local now = self.clock()
    local perf = self.perf
    for _, channel in ipairs(self.channels) do
        if channel.dirty or now >= channel.due then
            local wasDirty = channel.dirty
            local interval = channel.interval
            if type(interval) == "function" then
                interval = interval(player)
            end
            channel.dirty = false
            channel.due = now + interval

            local started = perf and perf:now()
            local ok, data = pcall(channel.snapshot, player)
            local snapshotMs, signatureMs, emitMs, size
            if perf then snapshotMs = perf:now() - started end
            if not ok then
                print("[ZomboidDS] snapshot '" .. channel.type .. "' failed: " .. tostring(data))
            elseif data ~= nil then
                local checked = perf and perf:now()
                -- The bridge compares in Java (converting, comparing and sending in one call);
                -- without that, the Signature here.
                local sent = self.bridge.emitIfChanged and self.bridge.emitIfChanged(channel.type, data)
                if sent ~= nil then
                    if perf then
                        signatureMs = perf:now() - checked
                        emitMs = sent and 0 or nil
                    end
                else
                    local signature = Signature.of(data)
                    if perf then signatureMs, size = perf:now() - checked, #signature end
                    if signature ~= channel.lastSignature then
                        local emitting = perf and perf:now()
                        channel.lastSignature = signature
                        self.bridge.emit(channel.type, data)
                        if perf then emitMs = perf:now() - emitting end
                    end
                end
            end
            if perf then perf:channel(channel.type, wasDirty, snapshotMs, signatureMs, emitMs, size) end
        end
    end
end

return Emitter
