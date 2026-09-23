--- Helpers shared by the Build 42 adapter's modules.
local Util = {}

--- obj:method(...), or nil if obj is nil, the method doesn't exist, or it throws. Engine calls go
--- through this, so one renamed method in a B42 update degrades one field to nil instead of breaking
--- the whole snapshot.
function Util.try(obj, method, ...)
    if obj == nil then
        return nil
    end
    local ok, result = pcall(obj[method], obj, ...)
    if ok then
        return result
    end
    return nil
end

function Util.round(x, digits)
    if type(x) ~= "number" then
        return nil
    end
    local m = 10 ^ (digits or 2)
    return math.floor(x * m + 0.5) / m
end

--- A texture path ("media/textures/Item_Axe.png") as the bridge's icon endpoint knows it ("Item_Axe").
function Util.textureFileName(path)
    if type(path) ~= "string" then
        return nil
    end
    path = string.gsub(path, "^.*[/\\]", "")
    path = string.gsub(path, "%.png$", "")
    return path
end

return Util
