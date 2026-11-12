--- The game's yes/no questions ("Are you sure you want to sleep?", "Delete all these items?") as a
--- card on the bottom screen.
---
--- 42.21 builds every one of them the same way: ISModalDialog:new, initialise, addToUIManager. While
--- a game runs and the app is there, the dialog is made as usual but not shown: the app gets its
--- question, and the answer presses the dialog's own button (ISModalDialog:onClick), so what follows
--- (the sleep, the delete, unpausing in destroy) is the game's. The controller keeps working on the
--- dialog the game gave it to, unseen; the app sees the question gone. If the app goes away first,
--- the dialog is shown in the game after all.
local Questions = {}

local pending = {} -- id -> { dialog, show }: questions the app has, not answered yet
local nextId = 0
local appThere = function() return true end
local watching = false

--- While questions wait: if the app goes away, the game shows them after all. Only while some wait.
local CHECK_MS = 500
local nextCheck = 0

local function watch()
    local now = getTimestampMs()
    if now < nextCheck then
        return
    end
    nextCheck = now + CHECK_MS
    local there = appThere()
    local waiting = false
    for id, entry in pairs(pending) do
        if there then
            waiting = true
        else
            pending[id] = nil
            pcall(entry.show, entry.dialog)
        end
    end
    if not waiting then
        Events.OnTick.Remove(watch)
        watching = false
    end
end

--- In a game, not on the main menu (whose dialogs, like quitting, stay in the game).
local function inGame()
    local screen = MainScreen and MainScreen.instance
    return getPlayer() ~= nil and not (screen and screen.getIsVisible and screen:getIsVisible())
end

--- The question as the app shows it, or nil once answered (here or with the controller).
function Questions.state(id)
    local entry = pending[id]
    if entry == nil then
        return nil
    end
    local dialog = entry.dialog
    local text = tostring(dialog.text or "")
    text = string.gsub(text, "\\n", "\n") -- the game writes line breaks as \n in its texts
    local buttons
    if dialog.yesno then
        buttons = { { id = "yes", label = getText("UI_Yes") }, { id = "no", label = getText("UI_No") } }
    else
        buttons = { { id = "ok", label = getText("UI_Ok") } }
    end
    return { id = id, text = text, buttons = buttons }
end

Questions.commands = {
    question_state = function(_player, args)
        local state = Questions.state(tonumber(args.id))
        return true, nil, state or { gone = true }
    end,
    question_answer = function(_player, args)
        local entry = pending[tonumber(args.id)]
        if entry == nil then
            return false, "That question was already answered"
        end
        local dialog = entry.dialog
        local button = ({ yes = dialog.yes, no = dialog.no, ok = dialog.ok })[tostring(args.button)]
        if button == nil then
            return false, "Not one of its buttons"
        end
        dialog:onClick(button)
        return true
    end,
}

--- `onShow` as for the other windows; `appThere()` says whether the app is still connected.
function Questions.redirect(onShow, isAppThere)
    appThere = isAppThere or appThere
    if ISModalDialog == nil or ISModalDialog.new == nil or ISModalDialog.zomboidDSRedirect then
        return
    end
    ISModalDialog.zomboidDSRedirect = true
    local original = ISModalDialog.new
    ISModalDialog.new = function(self, ...)
        local dialog = original(self, ...)
        -- Only the game's plain dialog: subclasses draw more than a question.
        if self ~= ISModalDialog or not inGame() then
            return dialog
        end
        dialog.addToUIManager = function(d)
            nextId = nextId + 1
            local id = nextId
            local show = ISModalDialog.addToUIManager -- the class's (ISUIElement's), not this override
            local playerNum = type(d.player) == "number" and d.player or 0
            pending[id] = { dialog = d, show = show }
            local ok, handled = pcall(onShow, playerNum, { panel = "question", id = id })
            if ok and handled then
                if not watching and Events and Events.OnTick then
                    watching = true
                    Events.OnTick.Add(watch)
                end
                return
            end
            pending[id] = nil
            return show(d)
        end
        -- However it's answered (the app, the controller, the game closing it), it's no longer asked.
        dialog.destroy = function(d)
            for id, entry in pairs(pending) do
                if entry.dialog == d then pending[id] = nil end
            end
            return ISModalDialog.destroy(d)
        end
        return dialog
    end
end

return Questions
