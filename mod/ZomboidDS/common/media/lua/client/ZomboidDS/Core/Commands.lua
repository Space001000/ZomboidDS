--- Runs commands from the companion app on the game thread, and answers each one with a
--- `command_result`.
local Commands = {}

local function execute(adapter, player, command)
    local handler = adapter.commands[command.name]
    if handler == nil then
        return false, "unsupported command '" .. tostring(command.name) .. "'"
    end
    local ok, result, reason, data = pcall(handler, player, command.args or {})
    if not ok then
        return false, tostring(result)
    end
    return result ~= false, reason, data
end

--- "itemId=12 to=c3", for the game log.
local function describeArgs(args)
    local parts = {}
    for key, value in pairs(args or {}) do
        if type(value) ~= "table" then
            parts[#parts + 1] = tostring(key) .. "=" .. tostring(value)
        end
    end
    table.sort(parts)
    return table.concat(parts, " ")
end

function Commands.pump(bridge, adapter, player, max)
    local batch = bridge.poll(max)
    if batch == nil then
        return
    end
    for i = 1, #batch do
        local command = batch[i]
        local ok, reason, data = execute(adapter, player, command)
        -- One line per command, so a report like "it didn't move" can be checked in the game log.
        print("[ZomboidDS] command " .. tostring(command.name) .. " " .. describeArgs(command.args)
            .. " -> " .. (ok and "ok" or ("failed: " .. tostring(reason))))
        bridge.emit("command_result", { id = command.id, ok = ok, error = reason, data = data })
    end
end

return Commands
