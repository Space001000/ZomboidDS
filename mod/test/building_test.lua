-- Building (B42/Building.lua): the recipes the game's build window lists, their versions, and
-- placing through the game's build cursor. Fakes stand in for BuildLogic and the cursor.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
function instanceof() return false end
local TEXT = { IGUI_CraftingCategories_Furniture = "Furniture" }
function getText(k) return TEXT[k] or k end
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end
local function list(t) return { size = function() return #t end, get = function(_, i) return t[i + 1] end } end
local function texture(name) return { getName = function() return "media/textures/" .. name .. ".png" end } end

ResourceType = { Item = "Item", Fluid = "Fluid" }
local function item(name, icon, fullName)
  return { getDisplayName = function() return name end, getNormalTexture = function() return texture(icon) end,
    getFullName = function() return fullName end }
end
local plank, nails, hammer = item("Plank", "Item_Plank"), item("Nails", "Item_Nails"), item("Hammer", "Item_Hammer", "Base.Hammer")
local function input(possible, amount, have, keep)
  return { getResourceType = function() return ResourceType.Item end, getIntAmount = function() return amount end,
    isKeep = function() return keep == true end, isTool = function() return false end,
    getPossibleInputItems = function() return list(possible) end, have = have }
end
local hammerInput = input({ hammer }, 1, 1, true)
local function recipe(name, display, inputs, opts)
  opts = opts or {}
  return { getName = function() return name end, getScriptObjectFullType = function() return "Base." .. name end,
    getTranslationName = function() return display end, getCategory = function() return "Furniture" end,
    getIconTexture = function() return texture(opts.icon or "Build_Chair") end, getOnAddToMenu = function() return nil end,
    getTime = function() return 50 end, getInputs = function() return list(inputs) end,
    getRequiredSkillCount = function() return 1 end,
    getRequiredSkill = function() return { getPerk = function() return { getName = function() return "Carpentry" end } end,
      getLevel = function() return opts.level or 1 end } end,
    getToolBoth = function() return nil end, getToolRight = function() return nil end, getToolLeft = function() return hammerInput end,
    canMake = opts.canMake }
end
local chair1 = recipe("Wood_Chair_Lvl1", "Wood Chair (Shoddy)", { hammerInput, input({ plank }, 2, 38), input({ nails }, 2, 212) }, { canMake = true })
local chair3 = recipe("Wood_Chair_Lvl3", "Wood Chair (Good)", { hammerInput, input({ plank }, 2, 38) }, { level = 6, canMake = false })
local crate2 = recipe("Wood_Crate_Lvl2", "Wood Crate", { input({ plank }, 4, 38) }, { icon = "Build_CrateWood", canMake = true })
local bench = recipe("Log_Bench", "Log Bench", { input({ plank }, 9, 1) }, { icon = "Build_LogBench", canMake = false })

ISInventoryPaneContextMenu = { getContainers = function() return list({ "inventory", "shelf" }) end }
local listeners = {}
BuildLogic = { new = function()
  local logic = { recipes = {} }
  function logic:setContainers(c) local changed = self.containers == nil self.containers = c return changed end
  function logic:getAllBuildableRecipes() return list({ chair1, chair3, crate2, bench }) end
  function logic:setRecipes(r) self.recipes = r end
  function logic:filterRecipeList() end
  function logic:getRecipeList() local r = self.recipes return { getAllRecipes = function() return r end } end
  function logic:getCachedRecipeInfo(r) return { isValid = function() return true end, isCanPerform = function() return r.canMake end } end
  function logic:setRecipe(r) self.recipe = r end
  function logic:autoPopulateInputs() self.populated = (self.populated or 0) + 1 end
  function logic:refresh() end
  function logic:isInputSatisfied(i) return i.have >= i:getIntAmount() end
  function logic:getSatisfiedInputItems(i) return list(i.have > 0 and { i:getPossibleInputItems():get(0) } or {}) end
  function logic:getSatisfiedInputInventoryItems(i) local t = {} for n = 1, i.have do t[n] = "x" end return list(t) end
  function logic:canPerformCurrentRecipe() return self.recipe.canMake == true end
  function logic:isCraftActionInProgress() return false end
  function logic:getSelectedBuildObject() return self.recipe and { objectFor = self.recipe } or nil end
  function logic:getWallCoveringParams() return nil end
  function logic:addEventListener(event, fn, target) listeners[event] = function() fn(target) end end
  return logic
end }
ISBuildIsoEntity = { predicateMaterial = function() return true end,
  new = function(_, player, info, nSprite, containers, logic)
    return { player = player, info = info, logic = logic, dragNilAfterPlace = true, blockAfterPlace = false, blockBuild = false }
  end }
local drag = {}
function getCell() return { setDrag = function(_, cursor, n) drag[n] = cursor end, getDrag = function(_, n) return drag[n] end } end
local ticks = {}
Events = { OnTick = { Add = function(f) ticks[f] = true end, Remove = function(f) ticks[f] = nil end } }
local inventory = { getAllTypeEvalRecurse = function(_, fullName) return list(fullName == "Base.Hammer" and { { getFullType = function() return "Base.Hammer" end } } or {}) end }
local square = {}
local player = { getPlayerNum = function() return 0 end, getPerkLevel = function() return 5 end,
  getInventory = function() return inventory end, getCurrentSquare = function() return square end }

local B42 = require("ZomboidDS/Adapters/B42")
local ok, _, data = B42.commands.build_list(player, {})
check(ok and #data.recipes == 4, "lists the build window's recipes")
local r1, r3, r4 = data.recipes[1], data.recipes[2], data.recipes[4]
check(r1.canBuild == true and r3.canBuild == false, "whether each can be built now, like the window's list")
check(r1.group == "Wood_Chair" and r3.group == "Wood_Chair" and r1.level == 1 and r3.level == 3, "versions grouped by the entity's Lvl suffix")
check(r1.version == "Shoddy" and r1.groupName == "Wood Chair" and r3.version == "Good", "the version's label from the name's brackets")
check(data.recipes[3].group == "Wood_Crate" and data.recipes[3].version == nil, "a version without brackets: grouped, no label")
check(r4.group == nil, "something with one version: no group")
check(r1.skill.name == "Carpentry" and r3.skill.level == 6, "the skill each version needs")
check(#data.categories == 1 and data.categories[1].name == "Furniture", "categories with the game's names")

local ok2, _, chair = B42.commands.build_recipe(player, { recipe = "Base.Wood_Chair_Lvl1" })
check(ok2 and chair.canBuild and #chair.inputs == 3 and chair.inputs[1].keep and chair.inputs[2].have == 38, "details: inputs counted, tools kept")
check(select(2, B42.commands.build_recipe(player, { recipe = "Base.Nope" })) ~= nil, "an unknown recipe: refused")

check(next(B42.snapshotBuilding(player)) == nil, "not placing: an empty building message")
local okPlace = B42.commands.build_place(player, { recipe = "Base.Wood_Chair_Lvl1" })
local cursor = drag[0]
check(okPlace and cursor ~= nil and cursor.info.objectFor == chair1, "place: the game's cursor for that object is up")
check(cursor.dragNilAfterPlace == false and cursor.blockAfterPlace == true and cursor.blockBuild == false,
  "it stays up after placing, like the build window's")
check(cursor.secondItem == "Base.Hammer", "with the tool in hand the window would pick")
local placing = B42.snapshotBuilding(player).placing
check(placing.name == "Wood Chair (Shoddy)" and placing.missing == nil, "the building message says what's being placed")

-- After a placement the game blocks the cursor until the panel re-checks; we re-check too.
cursor.blockBuild = true
listeners.onStopCraft()
check(cursor.blockBuild == false and drag[0] == cursor, "after a placement: re-counted and unblocked when there's enough")

check(B42.commands.build_place(player, { recipe = "Base.Log_Bench" }) and drag[0].blockBuild == true, "not enough: the cursor comes up blocked")
local short = B42.snapshotBuilding(player).placing
check(short.missing and short.missing[1] == "Plank", "and the message names what's short")

check(B42.commands.build_stop(player) and drag[0] == nil and next(B42.snapshotBuilding(player)) == nil, "stop puts the cursor away")
B42.commands.build_place(player, { recipe = "Base.Wood_Chair_Lvl1" })
drag[0] = nil -- B on the controller
check(next(B42.snapshotBuilding(player)) == nil, "the player put it away (B): not placing anymore")
for f in pairs(ticks) do f() end
check(next(ticks) == nil, "and the per-tick watch stops")
print("ALL LUA CHECKS PASSED")
