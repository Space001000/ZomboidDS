--- Registry of game-version adapters.
---
--- The core (this folder) never calls the game API directly. Everything version-specific goes
--- through an adapter, and each game build ships its own in its version folder
--- (e.g. `42/media/lua/client/ZomboidDS/Adapters/B42.lua`).
---
--- Adapter contract:
---   id                        string, reported to the app (e.g. "b42")
---   capabilities              array of strings, see protocol/PROTOCOL.md
---   matches(major, minor)     -> boolean: can this adapter drive the running game?
---   snapshotPlayer(player)    -> table   `player` message data
---   snapshotInventory(player) -> table   `inventory` message data
---   snapshotVehicle(player)   -> table   `vehicle` message data
---   snapshotContainers(player) -> table  `containers` message data (optional)
---   snapshotTime(player)      -> table   `time` message data (optional)
---   snapshotHere(player)      -> table   `here` message data (optional)
---   isInVehicle(player)       -> boolean
---   commands                  table: name -> function(player, args) returning
---                             true [, nil, data] on success or false, "reason" on failure
---   dirtyEvents               table: channel -> array of Events names that mean "re-snapshot now"
---   redirectGameWindows(onShow) optional: when the player opens the game's inventory/loot windows,
---                             calls onShow(playerNum, `show` message data); true keeps them closed
---   notify(player, text)      optional: short on-screen message for the player
---
--- Snapshots must return plain tables (strings, numbers, booleans, nested tables) and never
--- Java objects.
local Adapters = { list = {} }

local REQUIRED = {
    "id", "capabilities", "matches",
    "snapshotPlayer", "snapshotInventory", "snapshotVehicle", "isInVehicle",
    "commands", "dirtyEvents",
}

function Adapters.register(adapter)
    for _, field in ipairs(REQUIRED) do
        if adapter[field] == nil then
            error("[ZomboidDS] adapter '" .. tostring(adapter.id) .. "' is missing '" .. field .. "'")
        end
    end
    table.insert(Adapters.list, adapter)
end

--- Returns major, minor, display string. Major/minor are nil if the version can't be read.
function Adapters.gameVersion()
    local ok, version = pcall(function() return getCore():getGameVersion() end)
    if ok and version then
        local okMajor, major = pcall(function() return version:getMajor() end)
        local okMinor, minor = pcall(function() return version:getMinor() end)
        return okMajor and major or nil, okMinor and minor or nil, tostring(version)
    end
    return nil, nil, "unknown"
end

function Adapters.select()
    local major, minor = Adapters.gameVersion()
    for _, adapter in ipairs(Adapters.list) do
        if adapter.matches(major, minor) then
            return adapter
        end
    end
    return nil
end

return Adapters
