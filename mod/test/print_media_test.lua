-- PrintMedia (B42/PrintMedia.lua): reading a newspaper or flyer ends in the app's reader, with the
-- game's page layout and text, and its map button.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end
function getText(key)
  return ({ Print_Media_SunstarMotel_title = "Sunstar Motel",
    Print_Media_SunstarMotel_info = "<type:parent, width:800, height:1131><type:texture, x:0, y:0, texture:media/textures/printMedia/FlyerPics/Sunstar.png, width:800, height:1131>",
    Print_Text_SunstarMotel_info = "Stay with us at the Sunstar Motel!\\n\\n118 Dixie Highway, Muldraugh, KY" })[key] or key
end
local autoReveal = false
function getCore() return { getOptionAutoRevealPrintMediaMapLocations = function() return autoReveal end } end
function isClient() return false end
local known, queued = {}, {}
WorldMapVisited = { getInstance = function() return { setKnownInSquares = function(_, x1, y1, x2, y2) known[#known + 1] = { x1, y1, x2, y2 } end } end }
ISTimedActionQueue = { clear = function() queued = {} end, add = function(a) queued[#queued + 1] = a end }
ISReadWorldMap = { new = function(_, _, x, y, zoom) return { x = x, y = y, zoom = zoom } end }
PrintMediaDefinitions = { MiscDetails = { sunstarmotel = { location1 = { { x1 = 10600, y1 = 9790, x2 = 10659, y2 = 9849 } } } } }

local gameWindow, revealed = 0, nil
ISReadABook = { displayPrintMedia = function() gameWindow = gameWindow + 1 end }
local PrintMedia = require("ZomboidDS/Adapters/B42/PrintMedia")
local shown, appThere = nil, true
PrintMedia.redirect(function(_, show) if not appThere then return false end shown = show return true end)

local player = { getPlayerNum = function() return 0 end, setJoypadIgnoreAimUntilCentered = function() end }
local flyer = {
  hasModData = function() return true end,
  getModData = function() return { printMedia = { id = "sunstarmotel", title = "Print_Media_SunstarMotel_title",
    info = "Print_Media_SunstarMotel_info", text = "Print_Text_SunstarMotel_info" } } end,
  getTex = function() return { getName = function() return "media/textures/Item_Flier.png" end } end,
}
local action = { character = player, item = flyer, revealPrintMediaLocationsOnMap = function(_, id) revealed = id end }

check(not PrintMedia.commands.print_state(player, {}), "nothing read yet: nothing open")
ISReadABook.displayPrintMedia(action)
check(shown and shown.panel == "print" and gameWindow == 0, "reading ends in the app's reader, not the game's window")
check(revealed == nil, "the places stay hidden unless the game's option reveals them")
local ok, _, s = PrintMedia.commands.print_state(player, {})
check(ok and s.title == "Sunstar Motel" and s.icon == "Item_Flier", "its title and the item's icon")
check(s.layout:find("FlyerPics/Sunstar.png", 1, true) ~= nil, "the page as the game lays it out")
check(s.text == "Stay with us at the Sunstar Motel!\n\n118 Dixie Highway, Muldraugh, KY", "the text version, line breaks kept")
check(s.places == 1, "one place to show on the map")

check(PrintMedia.commands.print_map(player, { index = 1 }), "Show on map")
check(#known == 1 and known[1][1] == 10600 and known[1][4] == 9849, "the place becomes known, as the window's button")
check(queued[1] and queued[1].x == (10600 + 10659) / 2 and queued[1].zoom == 17, "and the game's map opens there, at the window's zoom")
check(not PrintMedia.commands.print_map(player, { index = 2 }), "no second place")

autoReveal = true
ISReadABook.displayPrintMedia(action)
check(revealed == "sunstarmotel", "with the game's option on, reading reveals the places, as the game's window does")
check(PrintMedia.commands.print_close(player, {}) and not PrintMedia.commands.print_state(player, {}), "closing forgets it")

appThere = false
ISReadABook.displayPrintMedia(action)
check(gameWindow == 1, "no app: the game's own window")
