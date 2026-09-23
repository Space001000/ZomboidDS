-- Crafting (B42/Crafting.lua): the recipes the game's crafting window lists, what one needs, and
-- crafting through the window's own start. Fakes stand in for HandcraftLogic and its friends.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
function instanceof() return false end
local TEXT = { IGUI_CraftingCategories_Tailoring = "Tailoring", IGUI_CraftingCategories_Assembly = "Assembly" }
function getText(k) return TEXT[k] or k end
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end
local function list(t) return { size = function() return #t end, get = function(_, i) return t[i + 1] end, add = function(_, v) t[#t + 1] = v end } end
local function texture(name) return { getName = function() return "media/textures/" .. name .. ".png" end } end

ResourceType = { Item = "Item", Fluid = "Fluid" }
local function item(name, icon) return { getDisplayName = function() return name end, getNormalTexture = function() return texture(icon) end } end
local rag, shirt, branch, stone, axe = item("Ripped Sheets", "Item_Rag"), item("T-shirt", "Item_TshirtGeneric"),
  item("Tree Branch", "Item_Branch"), item("Sharpened Stone", "Item_RockSharpened"), item("Stone Axe", "Item_AxeStone")
local function input(possible, amount, opts)
  opts = opts or {}
  return { getResourceType = function() return ResourceType.Item end, getIntAmount = function() return amount end,
    isKeep = function() return opts.keep == true end, isTool = function() return false end,
    getPossibleInputItems = function() return list(possible) end, have = opts.have or 0 }
end
local function recipe(id, name, category, inputs, outputs, opts)
  opts = opts or {}
  return { id = id, getScriptObjectFullType = function() return "Base." .. id end, getTranslationName = function() return name end,
    getCategory = function() return category end, getIconTexture = function() return texture(opts.icon) end,
    getOnAddToMenu = function() return opts.onAddToMenu end, getTime = function() return opts.seconds or 10 end,
    getInputs = function() return list(inputs) end,
    getOutputs = function() return list({ { getPossibleResultItems = function() return list(outputs) end, getIntAmount = function() return 1 end } }) end,
    getRequiredSkillCount = function() return opts.skill and 1 or 0 end,
    getRequiredSkill = function() return { getPerk = function() return { getName = function() return "Maintenance" end } end, getLevel = function() return 1 end } end,
    canMake = opts.canMake }
end
local ripInput = input({ shirt }, 1, { have = 4 })
local rip = recipe("RipClothing", "Rip Clothing", "Tailoring", { ripInput }, { rag }, { icon = "Item_Rag", canMake = true })
local axeInputs = { input({ branch }, 1, { have = 1 }), input({ stone }, 1, { have = 0 }) }
local stoneAxe = recipe("MakeCrudeStoneAxe", "Crude Stone Axe", "Assembly", axeInputs, { axe }, { icon = "Item_AxeStone", skill = true, canMake = false })
local secret = recipe("Secret", "Secret", "Assembly", {}, {}, { onAddToMenu = "Recipe.hideMe" })

local query
CraftRecipeManager = { queryRecipes = function(q) query = q return list({ rip, stoneAxe, secret }) end }
function callLuaBool(fn, params) return fn ~= "Recipe.hideMe" end
ISInventoryPaneContextMenu = { getContainers = function() return list({ "inventory", "shelf" }) end }
ISEntityUI = { FindCraftSurface = function() return "table" end }

local logicsMade = 0
HandcraftLogic = { new = function(player, bench, surface)
  logicsMade = logicsMade + 1
  local logic = { surface = surface, recipes = {} }
  function logic:setManualSelectInputs(on) self.manual = on end
  function logic:setContainers(c) local changed = self.containers == nil self.containers = c return changed end
  function logic:setRecipes(r) self.recipes = r end
  function logic:setIsoObject(o) self.surface = o end
  function logic:filterRecipeList() end
  function logic:getRecipeList() local r = self.recipes return { getAllRecipes = function() return r end } end
  function logic:getCachedRecipeInfo(r) return { isValid = function() return true end, isCanPerform = function() return r.canMake end } end
  function logic:setRecipe(r) self.recipe = r end
  function logic:autoPopulateInputs() end
  function logic:isInputSatisfied(i) return i.have >= i:getIntAmount() end
  function logic:getSatisfiedInputItems(i) return list(i.have > 0 and { i:getPossibleInputItems():get(0) } or {}) end
  function logic:getSatisfiedInputInventoryItems(i) local t = {} for n = 1, i.have do t[n] = "x" end return list(t) end
  function logic:getPossibleCraftCount() return self.recipe and self.recipe.canMake and 4 or 0 end
  return logic
end }
local started
ISWidgetHandCraftControl = { startHandcraft = function(self, force) started = { times = self.craftTimes, force = force, logic = self.logic, text = self.entryBox:getInternalText() } end }
local player = { getPlayerNum = function() return 0 end, getPerkLevel = function() return 0 end }

local B42 = require("ZomboidDS/Adapters/B42")
local ok, err, data = B42.commands.craft_list(player, {})
check(ok and query == "InHandCraft;AnySurfaceCraft", "lists the crafting window's recipes (its default query)")
check(#data.recipes == 2 and data.recipes[1].id == "Base.RipClothing", "recipes a recipe hides itself from (OnAddToMenu) are left out")
check(data.recipes[1].canCraft == true and data.recipes[2].canCraft == false, "whether each can be made now, like the window's list")
check(data.recipes[1].icon == "Item_Rag" and data.recipes[1].name == "Rip Clothing", "the game's name and icon")
check(#data.categories == 2 and data.categories[2].name == "Assembly", "categories with the game's names")

local ok2, _, axeData = B42.commands.craft_recipe(player, { recipe = "Base.MakeCrudeStoneAxe" })
check(ok2 and axeData.seconds == 10 and axeData.max == 0, "details: time in seconds, how many can be made")
check(axeData.inputs[1].name == "Tree Branch" and axeData.inputs[1].ok and axeData.inputs[1].have == 1, "an ingredient you have")
check(axeData.inputs[2].name == "Sharpened Stone" and not axeData.inputs[2].ok and axeData.inputs[2].have == 0,
  "one you don't: named by what would do")
check(axeData.outputs[1].name == "Stone Axe" and axeData.skills[1].name == "Maintenance" and axeData.skills[1].have == 0,
  "what it makes and the skill it needs")
check(select(2, B42.commands.craft_recipe(player, { recipe = "Base.Nope" })) ~= nil, "an unknown recipe: refused")

local okCraft = B42.commands.craft(player, { recipe = "Base.RipClothing", count = 9 })
check(okCraft and started and started.times == 4 and started.text == "4" and started.force == false,
  "craft: the window's own start, as many as possible at most")
check(started.logic.surface == "table" and started.logic.recipe == rip, "with a logic of its own, on the surface next to you")
check(started.logic.manual == true, "picking the ingredients like the window's panel (the craft action needs them)")
local noCraft, reason = B42.commands.craft(player, { recipe = "Base.MakeCrudeStoneAxe", count = 1 })
check(noCraft == false and reason:find("can't make"), "missing ingredients: refused with a reason")
print("ALL LUA CHECKS PASSED")
