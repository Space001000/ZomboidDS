--- Runs commands from the companion app on the game thread, and answers each one with a
--- `command_result`.
local Commands = {}

local function execute(adapter, player, command)
    local handler = adapter.commands[command.name]
    if handler == nil then
        return false, "unsupported command '" .. tostring(command.name) .. "'"
    end
    local ok, result, reason = pcall(handler, player, command.args or {})
    if not ok then
        return false, tostring(result)
    end
    return result ~= false, reason
end

function Commands.pump(bridge, adapter, player, max)
    local batch = bridge.poll(max)
    if batch == nil then
        return
    end
    for i = 1, #batch do
        local command = batch[i]
        local ok, reason = execute(adapter, player, command)
        bridge.emit("command_result", { id = command.id, ok = ok, error = reason })
    end
end

return Commands
