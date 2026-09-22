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

function Bridge.clients()
    local native = ZomboidDSBridge
    if native then
        return native.clients()
    end
    return 0
end

return Bridge
