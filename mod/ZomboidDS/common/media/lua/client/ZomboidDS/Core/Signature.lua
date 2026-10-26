--- Deterministic string form of a plain table, used to detect "nothing changed" cheaply
--- without sending the data.
---
--- Runs on every snapshot, so it avoids Lua callbacks in the hot path: arrays are written in order
--- without sorting, and keys that are all strings are sorted by table.sort's own comparison (in
--- Kahlua that's Java, not a Lua function per comparison). Tables that never change can be marked
--- with Signature.constant, so they're written once and reused.
local Signature = {}

local constants = {} -- table -> its string, or false until first written

local function keyOrder(a, b)
    return tostring(a) < tostring(b)
end

local function write(value, out)
    if type(value) ~= "table" then
        out[#out + 1] = tostring(value)
        return
    end
    local constant = constants[value]
    if constant == false then
        -- Marked, not written yet: write it once on its own.
        local own = {}
        constants[value] = nil
        write(value, own)
        constant = table.concat(own)
        constants[value] = constant
    end
    if constant then
        out[#out + 1] = constant
        return
    end
    out[#out + 1] = "{"
    -- The array part (items, options, ...) in order: "v;".
    local n = #value
    for i = 1, n do
        write(value[i], out)
        out[#out + 1] = ";"
    end
    -- The other keys sorted: "k=v;".
    local keys, allStrings = nil, true
    for k in pairs(value) do
        if not (type(k) == "number" and k >= 1 and k <= n and k % 1 == 0) then
            keys = keys or {}
            keys[#keys + 1] = k
            allStrings = allStrings and type(k) == "string"
        end
    end
    if keys then
        if allStrings then
            table.sort(keys)
        else
            table.sort(keys, keyOrder)
        end
        for i = 1, #keys do
            local k = keys[i]
            out[#out + 1] = tostring(k)
            out[#out + 1] = "="
            write(value[k], out)
            out[#out + 1] = ";"
        end
    end
    out[#out + 1] = "}"
end

function Signature.of(value)
    local out = {}
    write(value, out)
    return table.concat(out)
end

--- Marks [value] as never changing (nor anything in it): its string is made once and reused.
--- Returns [value].
function Signature.constant(value)
    if type(value) == "table" and constants[value] == nil then
        constants[value] = false
    end
    return value
end

return Signature
