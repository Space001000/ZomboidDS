--- "Here": the world menu for where the player stands, kept up to date while the app watches.
local B42Menu = require("ZomboidDS/Adapters/B42Menu")
local Util = require("ZomboidDS/Adapters/B42/Util")
local try = Util.try

local Here = {}

-- While the app watches (its Deck is open), the world menu for where the player stands is rebuilt
-- when they step onto another tile or turn, when their action finishes (the door is open now),
-- shortly after an option from the app ran, and every few seconds as a fallback. Never while the
-- game's own context menu is open on the top screen: the game has one per player, and building
-- ours would close it. Unchanged menus keep their id (B42Menu.openWorld), so the Emitter doesn't
-- re-send them.

local HERE_WATCH_MS = 10000   -- the app repeats watch_here while its Deck is open; expires otherwise
local HERE_FALLBACK_MS = 3000 -- rebuild at least this often while watched (things change around you)
local HERE_AFTER_SELECT_MS = 400

local here = { watchUntil = 0, last = nil, square = nil, dir = nil, busy = false, builtAt = 0, forceAt = nil }

local function gameMenuOpen(player)
    local menu = getPlayerContextMenu and getPlayerContextMenu(player:getPlayerNum())
    return menu ~= nil and try(menu, "isVisible") == true
end

function Here.snapshot(player)
    local now = getTimestampMs()
    if now >= here.watchUntil then
        here.last = nil
        return { watching = false }
    end
    local square, dir = player:getCurrentSquare(), player:getDir()
    local busy = ISTimedActionQueue ~= nil and ISTimedActionQueue.isPlayerDoingAction(player) == true
    local finished = here.busy and not busy
    here.busy = busy
    local due = here.last == nil or square ~= here.square or dir ~= here.dir or finished
        or now - here.builtAt >= HERE_FALLBACK_MS or (here.forceAt ~= nil and now >= here.forceAt)
        -- no menu (paused, nothing here): look again soon, e.g. right after unpausing
        or (here.last.unavailable ~= nil and now - here.builtAt >= 1000)
    if not due or gameMenuOpen(player) then
        return here.last or { watching = true }
    end
    here.square, here.dir, here.builtAt, here.forceAt = square, dir, now, nil
    local ok, reason, menu = B42Menu.openWorld(player)
    local buildMs = getTimestampMs() - now
    if ok then
        here.last = { watching = true, menuId = menu.menuId, options = menu.options }
    else
        here.last = { watching = true, unavailable = reason }
    end
    if buildMs > 20 then
        print("[ZomboidDS] building the world menu took " .. buildMs .. " ms")
    end
    return here.last
end

--- The app's Deck is open (`on`, repeated every few seconds) or closed.
function Here.watch(on)
    here.watchUntil = on and getTimestampMs() + HERE_WATCH_MS or 0
end

--- Something ran that may change what's here (a door opens): look again shortly.
function Here.lookAgainSoon()
    here.forceAt = getTimestampMs() + HERE_AFTER_SELECT_MS
end

return Here
