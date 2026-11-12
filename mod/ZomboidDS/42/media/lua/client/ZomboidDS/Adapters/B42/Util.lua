--- Helpers shared by the Build 42 adapter's modules.
local Util = {}

--- obj:method(...), or nil if obj is nil, the method doesn't exist, or it throws. Engine calls go
--- through this, so one renamed method in a B42 update degrades one field to nil instead of breaking
--- the whole snapshot.
---
--- For instance methods only: it passes `obj` as the first argument. A static Java method called this
--- way (try(Translator, "getMoveableDisplayName", name)) gets a wrong argument and fails inside Kahlua
--- on every call, leaking Kahlua's pooled call objects; done often, that broke the game's own Lua (its
--- world menu threw on every X press). Use Util.call for statics.
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

--- fn(...), or nil if fn is nil or throws: for static Java methods (Translator.getMoveableDisplayName)
--- and plain functions, which take no `self`.
function Util.call(fn, ...)
    if fn == nil then
        return nil
    end
    local ok, result = pcall(fn, ...)
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
