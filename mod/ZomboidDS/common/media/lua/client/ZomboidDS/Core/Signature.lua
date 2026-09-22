--- Deterministic string form of a plain table, used to detect "nothing changed" cheaply
--- without sending the data.
local Signature = {}

local function keyOrder(a, b)
    return tostring(a) < tostring(b)
end

local function write(value, out)
    if type(value) == "table" then
        local keys = {}
        for k in pairs(value) do
            keys[#keys + 1] = k
        end
        table.sort(keys, keyOrder)
        out[#out + 1] = "{"
        for i = 1, #keys do
            local k = keys[i]
            out[#out + 1] = tostring(k)
            out[#out + 1] = "="
            write(value[k], out)
            out[#out + 1] = ";"
        end
        out[#out + 1] = "}"
    else
        out[#out + 1] = tostring(value)
    end
end

function Signature.of(value)
    local out = {}
    write(value, out)
    return table.concat(out)
end

return Signature
