-- Item details (B42.snapshotInventory): the category the game's inventory shows, and food freshness
-- the way the game names it. Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
local TRANSLATIONS = { IGUI_ItemCat_CookingWeapon = "Cooking", IGUI_ItemCat_Food = "Food" }
function getText(k) return TRANSLATIONS[k] or k end -- like the game: the key itself when there's none
function instanceof(obj, cls) return obj._class == cls end
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

local function list(t) return { size = function() return #t end, get = function(_, i) return t[i + 1] end } end
local function item(id, name, category, cls, food)
  local it = { _class = cls, getID = function() return id end, getFullType = function() return "Base." .. name end,
    getDisplayName = function() return name end, getDisplayCategory = function() return category end }
  if food then
    it.getAge = function() return food.age end
    it.getOffAge = function() return food.off end
    it.getOffAgeMax = function() return food.max end
    it.isFertilized = function() return food.fertilized == true end
  end
  return it
end
local NEVER = 1000000000
local items = {
  item(1, "Pan", "CookingWeapon", "HandWeapon"),
  item(2, "Apple", "Food", "Food", { age = 1, off = 3, max = 5 }),
  item(3, "Bread", "Food", "Food", { age = 3, off = 3, max = 5 }),
  item(4, "Banana", "Food", "Food", { age = 9, off = 3, max = 5 }),
  item(5, "Beans", "Food", "Food", { age = 99, off = NEVER, max = NEVER }),
  item(6, "Egg", "Food", "Food", { age = 1, off = 3, max = 5, fertilized = true }),
  item(7, "Gizmo", "ModdedThing", "InventoryItem"),
}
local player = { getInventory = function() return { getItems = function() return list(items) end } end }

local B42 = require("ZomboidDS/Adapters/B42")
local byName = {}
for _, it in ipairs(B42.snapshotInventory(player).items) do byName[it.name] = it end
check(byName.Pan.category == "Cooking", "the category as the game's inventory shows it, translated")
check(byName.Gizmo.category == "ModdedThing", "no translation (a mod's category): the category itself")
check(byName.Apple.freshness == "fresh" and byName.Bread.freshness == "stale" and byName.Banana.freshness == "rotten",
  "fresh before offAge, stale from it, rotten from offAgeMax, like the game's food names")
check(byName.Beans.freshness == nil, "food that never goes off has no freshness")
check(byName.Egg.freshness == nil and byName.Pan.freshness == nil, "fertilized eggs and non-food: none either")
print("ALL LUA CHECKS PASSED")
