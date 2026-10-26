--- Thin wrapper around the `ZomboidDSBridge` global: our Java class, exposed to Lua by ZombieBuddy.
--- Without it (ZombieBuddy missing), everything here quietly does nothing, so the mod never breaks the game.
local Bridge = {}

local warned = false

local function api()
    local native = ZomboidDSBridge
    if native == nil and not warned then
        warned = true
        print("[ZomboidDS] Java bridge not loaded (is ZombieBuddy installed and enabled?). Companion app disabled.")
    end
    return native
end

function Bridge.isAvailable()
    return ZomboidDSBridge ~= nil
end

--- Publishes a message to the companion app. `data` must be a plain table (no Java objects).
function Bridge.emit(messageType, data)
    local native = api()
    if native then
        native.emit(messageType, data)
    end
end

--- Publishes unless `data` equals what was last sent for this type this way. Compared in Java after
--- converting, which is much faster than walking the table in Lua. Returns whether it was sent, or
--- nil when the bridge can't compare (then the caller compares itself).
function Bridge.emitIfChanged(messageType, data)
    local native = api()
    if native and native.emitIfChanged then
        return native.emitIfChanged(messageType, data)
    end
    return nil
end

--- Whether `data` equals what was last passed for `key` (remembered either way), compared in Java
--- like emitIfChanged; nil when the bridge can't compare (then the caller compares itself).
function Bridge.sameAsLast(key, data)
    local native = ZomboidDSBridge
    if native and native.sameAsLast then
        return native.sameAsLast(key, data)
    end
    return nil
end

--- The next emitIfChanged of every type is sent, changed or not.
function Bridge.forgetSent()
    local native = ZomboidDSBridge
    if native and native.forgetSent then
        native.forgetSent()
    end
end

--- Garbage collections so far, to tell the game's GC pauses from the mod's work; nil if unknown.
function Bridge.gcCount()
    local native = ZomboidDSBridge
    if native and native.gcCount then
        local count = native.gcCount()
        if count ~= nil and count >= 0 then
            return count
        end
    end
    return nil
end

--- Returns an array of { id, name, args } commands, or nil.
function Bridge.poll(max)
    local native = api()
    if native then
        return native.poll(max)
    end
    return nil
end

--- Forgets state from the previous session (called whenever the mod's Lua is loaded).
function Bridge.reset()
    local native = ZomboidDSBridge
    if native then
        native.reset()
    end
end

--- Milliseconds with fractions, for timing (the game's getTimestampMs only counts whole ones).
function Bridge.clockMs()
    local native = ZomboidDSBridge
    if native and native.clockMs then
        return native.clockMs()
    end
    return getTimestampMs()
end

function Bridge.clients()
    local native = ZomboidDSBridge
    if native then
        return native.clients()
    end
    return 0
end

return Bridge
