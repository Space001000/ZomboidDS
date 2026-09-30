--- The minimap: where the player is, which way they face, whether the save allows a map, and
--- (through the Java side) which parts of the world they've seen.
local Util = require("ZomboidDS/Adapters/B42/Util")
local try, round = Util.try, Util.round

local Map = {}

-- The player's choice in the game's Options > Mods page (PZAPI.ModOptions, B42): show the map on
-- every save, not only on saves that allow the game's minimap. Created when the game loads the
-- mod's Lua, so it's in the options before a game starts; the game reads its saved value when it
-- builds its options screen, and we read it again when a game starts.
local ALWAYS_SHOW_ID = "mapOnEverySave"
local options
if PZAPI and PZAPI.ModOptions then
    local ok, created = pcall(function() return PZAPI.ModOptions:create("ZomboidDS", "ZomboidDS") end)
    if ok and created then
        options = created
        pcall(function()
            options:addTickBox(ALWAYS_SHOW_ID, "Map on every save", false,
                "Show the bottom-screen map even on saves that don't allow the minimap. "
                    .. "It still only shows what you've explored.")
        end)
    end
end

if Events and Events.OnGameStart then
    Events.OnGameStart.Add(function()
        if options then
            pcall(function() PZAPI.ModOptions:load() end)
        end
    end)
end

local function alwaysShow()
    local option = options and options.getOption and options:getOption(ALWAYS_SHOW_ID)
    return option ~= nil and option.value == true
end

-- While driving, the heading comes from how the car moved: the car's own angles are Euler
-- angles in a Vector3f, awkward from Lua. Moves shorter than this don't change it.
local MIN_MOVE = 0.5

-- Turns smaller than this (degrees) keep the last heading.
local MIN_TURN = 10

local function angleBetween(a, b)
    local d = math.abs(a - b) % 360
    return d > 180 and 360 - d or d
end

local last = { x = nil, y = nil, heading = nil, driving = false }

--- Degrees clockwise from east (+x), the way the map's y axis points down: 0 east, 90 south.
--- Rounded to 5 degrees, so the snapshot only changes when the arrow would visibly turn.
local function degrees(dx, dy)
    if dx == nil or dy == nil or (dx == 0 and dy == 0) then
        return nil
    end
    local deg = math.deg(math.atan2(dy, dx))
    deg = math.floor(deg / 5 + 0.5) * 5
    return deg % 360
end

local function allowed(check)
    local ok, result = pcall(check)
    return ok and result == true
end

--- The game's rules (ISMiniMap.IsAllowed / ISWorldMap.IsAllowed): sandbox Map options, never in the tutorial.
local function rules()
    return {
        miniMap = ISMiniMap ~= nil and allowed(ISMiniMap.IsAllowed),
        worldMap = ISWorldMap ~= nil and allowed(ISWorldMap.IsAllowed),
    }
end

function Map.snapshot(player)
    local allow = rules()
    local vehicle = try(player, "getVehicle")
    -- The game's minimap centres on the car while driving (ISMiniMapInner:prerenderHack).
    local source = vehicle or player
    local x, y = try(source, "getX"), try(source, "getY")
    if x == nil or y == nil then
        return nil
    end
    local heading
    if vehicle then
        -- Getting in: measure from here, and keep the way the player faced until the car moves.
        if not last.driving or last.x == nil then
            last.x, last.y, last.driving = x, y, true
        elseif ((x - last.x) ^ 2 + (y - last.y) ^ 2) >= MIN_MOVE ^ 2 then
            last.heading = degrees(x - last.x, y - last.y) or last.heading
            last.x, last.y = x, y
        end
        heading = last.heading
    else
        last.x, last.y, last.driving = x, y, false
        -- The facing sways a little with the walk animation: only turn the arrow for a real turn.
        local facing = degrees(try(player, "getForwardDirectionX"), try(player, "getForwardDirectionY"))
        if facing ~= nil and (last.heading == nil or angleBetween(facing, last.heading) > MIN_TURN) then
            last.heading = facing
        end
        heading = last.heading
    end
    -- The Java side sends the seen areas itself (a large bit field; only when it changed).
    if ZomboidDSBridge and ZomboidDSBridge.publishExplored then
        pcall(ZomboidDSBridge.publishExplored)
    end
    return {
        miniMap = allow.miniMap,
        worldMap = allow.worldMap,
        alwaysShow = alwaysShow(),
        x = round(x, 1),
        y = round(y, 1),
        z = math.floor(try(source, "getZ") or 0),
        heading = heading,
    }
end

return Map
