-- Questions (B42/Questions.lua): the game's yes/no dialogs go to the app, the answer presses the
-- dialog's own button, and a dialog goes back to the game when the app leaves.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

-- The game's clock, a second further each read (the watch checks every half second).
local clock = 0
function getTimestampMs() clock = clock + 1000 return clock end
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end
function getText(key) return ({ UI_Yes = "Yes", UI_No = "No", UI_Ok = "Ok" })[key] or key end
local inGame = true
function getPlayer() return inGame and {} or nil end
MainScreen = { instance = { getIsVisible = function() return false end } }

local handlers = {}
Events = { OnTick = {
  Add = function(f) handlers[#handlers + 1] = f end,
  Remove = function(f) for i, h in ipairs(handlers) do if h == f then table.remove(handlers, i) return end end end,
} }
local function tick() for _, h in ipairs({ unpack(handlers) }) do h() end end

-- The game's dialog, as 42.21 builds it (only what's used).
local shownInGame, destroyed = {}, 0
ISUIElement = { addToUIManager = function(d) table.insert(shownInGame, d) end }
ISModalDialog = setmetatable({}, { __index = ISUIElement })
ISModalDialog.__index = ISModalDialog
function ISModalDialog:new(_, _, _, _, text, yesno, target, onclick, player, p1, p2)
  local o = setmetatable({ text = text, yesno = yesno, target = target, onclick = onclick, player = player, param1 = p1, param2 = p2 }, self)
  return o
end
function ISModalDialog:initialise()
  if self.yesno then self.yes = { internal = "YES" }; self.no = { internal = "NO" } else self.ok = { internal = "OK" } end
end
function ISModalDialog:destroy() destroyed = destroyed + 1 end
function ISModalDialog:onClick(button)
  self:destroy()
  if self.onclick then self.onclick(self.target, button, self.param1, self.param2) end
end
ISSubDialog = setmetatable({}, { __index = ISModalDialog })
ISSubDialog.__index = ISSubDialog

local Questions = require("ZomboidDS/Adapters/B42/Questions")
local shown, appThere = nil, true
Questions.redirect(function(_, show) if not appThere then return false end shown = show return true end, function() return appThere end)

-- Sleep asks first
local slept
local dialog = ISModalDialog:new(0, 0, 250, 150, "Are you sure you want to sleep?", true, nil, function(_, button) slept = button.internal end, 0, "bed")
dialog:initialise()
dialog:addToUIManager()
check(shown and shown.panel == "question" and #shownInGame == 0, "the question goes to the app, not the screen")
local ok, _, s = Questions.commands.question_state(nil, { id = shown.id })
check(ok and s.text == "Are you sure you want to sleep?" and s.buttons[1].label == "Yes" and s.buttons[2].id == "no", "its text and the game's Yes / No")
check(#handlers == 1, "one watch while it waits")
check(Questions.commands.question_answer(nil, { id = shown.id, button = "yes" }) and slept == "YES" and destroyed == 1,
  "Yes presses the dialog's own button: the game sleeps, the dialog is done")
ok, _, s = Questions.commands.question_state(nil, { id = shown.id })
check(ok and s.gone, "then it's gone")
check(not Questions.commands.question_answer(nil, { id = shown.id, button = "yes" }), "a second answer does nothing")
tick()
check(#handlers == 0, "no watch once nothing waits")

-- Answered with the controller (the game's own path): the app sees it gone
local d2 = ISModalDialog:new(0, 0, 250, 150, "Delete all these items?", true, nil, nil, 0)
d2:initialise(); d2:addToUIManager()
local id2 = shown.id
d2:onClick(d2.no)
ok, _, s = Questions.commands.question_state(nil, { id = id2 })
check(s.gone, "answered with the controller: gone for the app too")

-- An OK-only notice
local d3 = ISModalDialog:new(0, 0, 250, 150, "Line one\\nline two", false, nil, nil, 0)
d3:initialise(); d3:addToUIManager()
ok, _, s = Questions.commands.question_state(nil, { id = shown.id })
check(#s.buttons == 1 and s.buttons[1].id == "ok" and s.text == "Line one\nline two", "an OK-only one, its line break kept")

-- The app leaves: the game shows it after all
appThere = false
tick()
check(#shownInGame == 1 and shownInGame[1] == d3 and #handlers == 0, "the app went away: the game shows the question")
appThere = true

-- Not ours: subclasses and the main menu
local sub = ISSubDialog:new(0, 0, 1, 1, "x", true); sub:initialise(); sub:addToUIManager()
check(#shownInGame == 2, "a subclass of the dialog stays in the game")
inGame = false
local menu = ISModalDialog:new(0, 0, 1, 1, "Quit?", true); menu:initialise(); menu:addToUIManager()
check(#shownInGame == 3, "outside a game (the main menu) it stays in the game")
