-- Item details (B42.snapshotInventory): the category the game's inventory shows, and food freshness
-- the way the game names it. Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
local TRANSLATIONS = { IGUI_ItemCat_CookingWeapon = "Cooking", IGUI_ItemCat_Food = "Food",
  Tooltip_food_Fresh = "Fresh", Tooltip_food_Stale = "Stale", Tooltip_food_Grilled = "Grilled" }
ItemTag = { HIDE_COOKED = "HideCooked", HIDE_UNCOOKED = "HideUncooked", GRILLED = "Grilled", TOASTABLE = "Toastable" }
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

-- Cooking and fluids (mod 0.24): the game's own name and words, the cooking bar, how full.
local function food(id, name, f)
  local it = item(id, name, "Food", "Food", { age = 1, off = 3, max = 5 })
  local tags = f.tags or {}
  it.getName = function(_, p) return f.fullName or name end
  it.isBurnt = function() return f.burnt == true end
  it.isCooked = function() return f.cooked == true end
  it.isIsCookable = function() return f.cookable == true end
  it.isFrozen = function() return false end
  it.hasTag = function(_, tag) return tags[tag] == true end
  it.getHeat = function() return f.heat or 1 end
  it.getCookingTime = function() return f.time or 0 end
  it.getMinutesToCook = function() return 60 end
  it.getMinutesToBurn = function() return 120 end
  it.getCookedString = function() return "Cooked" end
  it.getUnCookedString = function() return "Uncooked" end
  it.getBurntString = function() return "Burnt" end
  it.getOffString = function() return "Rotten" end
  return it
end
local function bottle(id, name, amount, mixture)
  local it = item(id, name, "Water", "InventoryItem")
  it.getFluidContainer = function() return {
    getCapacity = function() return 0.6 end, getAmount = function() return amount end,
    isMixture = function() return mixture == true end,
    getPrimaryFluid = function() return { getTranslatedName = function() return "Water" end } end,
    getColor = function() return { getRedFloat = function() return 0.2 end, getGreenFloat = function() return 0.4 end,
      getBlueFloat = function() return 0.8 end } end } end
  return it
end
items = {
  food(11, "Steak", { cookable = true, cooked = true, fullName = "Steak (Fresh, Cooked)" }),
  food(12, "Chicken", { cookable = true, heat = 2, time = 30 }),
  food(13, "Bacon", { cookable = true, cooked = true, burnt = true }),
  food(14, "Fish", { cookable = true, cooked = true, heat = 2, time = 90, tags = { Grilled = true } }),
  food(15, "Bread", { cookable = true, tags = { HideUncooked = true } }),
  bottle(16, "Water Bottle", 0.3), bottle(17, "Pop Bottle", 0.6, true), bottle(18, "Empty Bottle", 0),
}
byName = {}
for _, it in ipairs(B42.snapshotInventory(player).items) do byName[it.shortName or it.name] = it end
check(byName.Steak.name == "Steak (Fresh, Cooked)" and byName.Steak.shortName == "Steak",
  "the game's full name, and the plain one for tiles")
check(byName.Chicken.shortName == nil, "no plain name when it's the same")
check(byName.Steak.cooking.state == "cooked" and byName.Steak.cooking.text == "Cooked" and byName.Steak.cooking.progress == nil,
  "cooked, cold: the word, no bar")
check(byName.Chicken.cooking.state == "uncooked" and byName.Chicken.cooking.progress == 0.5 and not byName.Chicken.cooking.burning,
  "heating: cooking, half way")
check(byName.Fish.cooking.text == "Grilled" and byName.Fish.cooking.burning == true and byName.Fish.cooking.progress == 0.5,
  "grilled, past minutesToCook: burning, half way to burnt")
check(byName.Bacon.cooking.state == "burnt" and byName.Bacon.freshnessText == nil, "burnt: no freshness word (the name has none)")
check(byName.Steak.freshnessText == "Fresh", "the freshness word as in the name")
check(byName.Bread.cooking == nil, "HIDE_UNCOOKED: nothing")
local w = byName["Water Bottle"].fluid
check(w.amount == 0.3 and w.capacity == 0.6 and w.name == "Water" and w.color[3] == 0.8, "a bottle: amount, capacity, fluid, colour")
check(byName["Pop Bottle"].fluid.mixture == true and byName["Pop Bottle"].fluid.name == nil, "a mixture")
check(byName["Empty Bottle"].fluid.amount == 0 and byName["Empty Bottle"].fluid.name == nil, "empty: only the capacity")
check(byName.Steak.fluid == nil, "no fluid container: no fluid")

-- Read / watched and unwanted (mod 0.24): the game's tick and grey.
ISInventoryPane = { isLiteratureRead = function(_, _, it) return it._read == true end }
local book = item(21, "Book", "Literature", "Literature"); book._read = true
local tape = item(22, "VHS", "Entertainment", "InventoryItem"); tape.hasBeenSeen = function() return true end
local magazine = item(23, "Magazine", "Literature", "Literature")
local junk = item(24, "Spiffo", "Junk", "InventoryItem"); junk.isUnwanted = function() return true end
items = { book, tape, magazine, junk }
byName = {}
for _, it in ipairs(B42.snapshotInventory(player).items) do byName[it.name] = it end
check(byName.Book.read == true and byName.VHS.read == true, "a read book and a watched tape are done")
check(byName.Magazine.read == nil and byName.Magazine.unwanted == nil, "an unread magazine: neither")
check(byName.Spiffo.unwanted == true, "set unwanted in the game")
print("ALL LUA CHECKS PASSED")
