--- Newspapers, magazines and flyers: the game's print media window (the printed page, its text, and
--- the map buttons) on the bottom screen.
---
--- 42.21 opens it when reading one ends (ISReadABook:perform -> displayPrintMedia, a PZAPI.UI.PrintMedia
--- window). The app takes over there; the rest of reading (time, boredom, the read mark, the option
--- that reveals the places by itself) stays the game's. The page goes to the app as the game's own
--- layout (the Print_Media *_info rich text: pictures, plain rects and text at their places), which
--- the app draws; the text version is the game's (*_text). "Show on map" does what the window's map
--- button does: marks the place known and opens the game's map there (ISReadWorldMap).
local Util = require("ZomboidDS/Adapters/B42/Util")
local try = Util.try

local PrintMedia = {}

local open = {} -- playerNum -> { media (the item's printMedia mod data), icon }

--- The window's own zoom for a place of that size (PrintMedia.lua getMapZoom).
local function mapZoom(len)
    if len < 75 then return 17 end
    if len < 75 * 3 then return 16.5 end
    if len < 75 * 3 * 3 then return 16.0 end
    if len < 75 * 3 * 3 * 3 then return 15.5 end
    if len < 75 * 3 * 3 * 3 * 3 then return 14.5 end
    return 14
end

local function locations(mediaId)
    local details = PrintMediaDefinitions and PrintMediaDefinitions.MiscDetails and PrintMediaDefinitions.MiscDetails[mediaId]
    local list = {}
    for i = 1, 5 do
        local location = details and details["location" .. i]
        if location == nil then break end
        list[#list + 1] = location
    end
    return list
end

function PrintMedia.state(player)
    local entry = open[player:getPlayerNum()]
    if entry == nil then
        return false, "Nothing to read open"
    end
    local media = entry.media
    return true, nil, {
        title = getText(media.title),
        icon = entry.icon,
        -- The page as the game lays it out; "^" in text is a line break there.
        layout = getText(media.info),
        text = (string.gsub(getText(media.text), "\\n", "\n")),
        places = #locations(media.id),
    }
end

PrintMedia.commands = {
    print_state = function(player, _args) return PrintMedia.state(player) end,
    print_close = function(player, _args)
        open[player:getPlayerNum()] = nil
        return true
    end,
    -- The window's map button `index` (1-5): the place known on the map, the map opened there.
    print_map = function(player, args)
        local entry = open[player:getPlayerNum()]
        local location = entry and locations(entry.media.id)[tonumber(args.index) or 1]
        if location == nil then
            return false, "No place on this one"
        end
        local xx, yy, maxLen = 0, 0, 0
        for _, sq in ipairs(location) do
            WorldMapVisited.getInstance():setKnownInSquares(sq.x1, sq.y1, sq.x2, sq.y2)
            if isClient() then
                sendClientCommand(player, "map", "setKnownInSquares", { x1 = sq.x1, y1 = sq.y1, x2 = sq.x2, y2 = sq.y2 })
            end
            xx = xx + sq.x1 + sq.x2
            yy = yy + sq.y1 + sq.y2
            maxLen = math.max(sq.x2 - sq.x1, sq.y2 - sq.y1, maxLen)
        end
        player:setJoypadIgnoreAimUntilCentered(true)
        ISTimedActionQueue.clear(player)
        ISTimedActionQueue.add(ISReadWorldMap:new(player, xx / (#location * 2), yy / (#location * 2), mapZoom(maxLen)))
        return true
    end,
}

--- Reading's window opens the app's reader instead, while the app is there.
function PrintMedia.redirect(onShow)
    if ISReadABook == nil or ISReadABook.displayPrintMedia == nil or ISReadABook.zomboidDSRedirect then
        return
    end
    ISReadABook.zomboidDSRedirect = true
    local original = ISReadABook.displayPrintMedia
    ISReadABook.displayPrintMedia = function(self, ...)
        local media = self.item and self.item:hasModData() and self.item:getModData().printMedia or nil
        local playerNum = self.character:getPlayerNum()
        local previous = open[playerNum]
        open[playerNum] = media and { media = media, icon = Util.textureFileName(try(try(self.item, "getTex"), "getName")) } or previous
        local ok, handled = pcall(function() return media ~= nil and onShow(playerNum, { panel = "print" }) end)
        if ok and handled then
            -- What displayPrintMedia does besides its window: the option that reveals the places.
            if getCore():getOptionAutoRevealPrintMediaMapLocations() then
                self:revealPrintMediaLocationsOnMap(media.id)
            end
            return
        end
        open[playerNum] = previous
        return original(self, ...)
    end
end

return PrintMedia
