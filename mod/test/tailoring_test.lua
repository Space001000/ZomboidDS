-- Tailoring (B42/Tailoring.lua, B42Menu.openGarment): the player's clothes, one garment as the
-- game's Inspect window shows it, its per-part menu, and Inspect opening the app's panel.
-- Fakes stand in for the game's clothing, inventory, ISGarmentUI and the action queue.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
function instanceof(obj, class) return type(obj) == "table" and obj.classes ~= nil and obj.classes[class] == true end
function getText(key, arg)
  local text = ({ IGUI_garment_CantRepair = "Can't be repaired.", ContextMenu_PatchHole = "Patch Hole",
    ContextMenu_AddPadding = "Add Padding", ContextMenu_RemovePatch = "Remove Patch" })[key]
  if key == "IGUI_TypeOfPatch" then return arg .. " patch" end
  return text or key
end
UIManager = { getSpeedControls = function() return { getCurrentGameSpeed = function() return 1 end } end }
Perks = { Tailoring = "Tailoring" }
ItemTag = { THREAD = "thread", SEWING_NEEDLE = "needle" }
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end
local function list(t) return { size = function() return #t end, get = function(_, i) return t[i + 1] end } end

-- Body parts: Java enums, tostring gives the id.
local partMeta = { __tostring = function(p) return p.id end }
local function part(id, name) return setmetatable({ id = id, getDisplayName = function() return name end }, partMeta) end
local TU, FAL, FAR, FootL, FootR = part("Torso_Upper", "Upper Torso"), part("ForeArm_L", "Left Forearm"),
  part("ForeArm_R", "Right Forearm"), part("Foot_L", "Left Foot"), part("Foot_R", "Right Foot")

local function garment(o)
  local g = { classes = { Clothing = true, InventoryItem = true } }
  function g:getID() return o.id end
  function g:getDisplayName() return o.name end
  function g:getName() return o.name end
  function g:getScriptItem() return { getIcon = function() return o.icon end } end
  function g:getCoveredParts() return list(o.parts) end
  function g:getHolesNumber() local n = 0 for _ in pairs(o.holes or {}) do n = n + 1 end return n end
  function g:getPatchesNumber() local n = 0 for _ in pairs(o.patches or {}) do n = n + 1 end return n end
  function g:getFabricType() return o.fabric end
  function g:getCondition() return o.cond end
  function g:getConditionMax() return 10 end
  function g:getBloodlevel() return 22 end
  function g:getDirtiness() return 31 end
  function g:getVisual() return { getHole = function(_, p) return (o.holes or {})[p.id] and 1 or 0 end } end
  function g:getBloodlevelForPart(p) return (o.blood or {})[p.id] or 0 end
  function g:getPatchType(p)
    local fabric = (o.patches or {})[p.id]
    return fabric and { getFabricTypeName = function() return fabric end } or nil
  end
  function g:getDefForPart(p, bite, bullet)
    if (o.holes or {})[p.id] then return 0 end
    if bullet then return 0 end
    return bite and 20 or 40
  end
  return g
end
local jacket = garment({ id = 1, name = "Leather Jacket", icon = "JacketBlack", fabric = "Leather", cond = 6,
  parts = { TU, FAL, FAR }, holes = { ForeArm_R = true }, patches = { ForeArm_L = "Leather Strips" }, blood = { ForeArm_R = 0.35 } })
local boots = garment({ id = 2, name = "Military Boots", icon = "BootsARmy", cond = 7, parts = { FootL, FootR } })
local hoodie = garment({ id = 3, name = "Hoodie", icon = "HoodieWhite", fabric = "Cotton", cond = 4, parts = { TU }, holes = { Torso_Upper = true } })
local wound = garment({ id = 4, name = "Wound", parts = { FAR } })
wound.isHidden = function() return true end
local rag = { classes = {}, getID = function() return 5 end }

local bagInventory = { getItems = function() return list({ hoodie }) end }
local bag = { classes = { InventoryContainer = true }, getID = function() return 6 end,
  getInventory = function() return bagInventory end }
local byId = { [1] = jacket, [2] = boots, [3] = hoodie, [5] = rag }
local counts = { RippedSheets = 12, DenimStrips = 3, LeatherStrips = 0 }
local hasNeedle = true
local inventory = {
  getItems = function() return list({ jacket, boots, wound, rag, bag }) end,
  getItemWithIDRecursiv = function(_, id) return byId[id] end,
  getItemFromType = function(_, kind) if kind == "Thread" then return {} end if kind == "Needle" and hasNeedle then return {} end end,
  getItemFromTag = function() return nil end,
  getFirstTagRecurse = function() return nil end,
  getItemCount = function(_, kind) return counts[kind] or 0 end,
}
for _, item in ipairs({ jacket, boots, hoodie }) do item.getContainer = function() return inventory end end
local player = { getPlayerNum = function() return 0 end, getInventory = function() return inventory end,
  getPerkLevel = function() return 4 end,
  getWornItems = function() return list({ { getItem = function() return jacket end }, { getItem = function() return boots end },
    { getItem = function() return wound end } }) end,
  isEquippedClothing = function(_, item) return item == jacket or item == boots end }
ScriptManager = { instance = { getItem = function(_, fullType)
  local names = { ["Base.RippedSheets"] = { "Rag", "Rag" }, ["Base.DenimStrips"] = { "Denim Strips", "DenimStrips" },
    ["Base.LeatherStrips"] = { "Leather Strips", "LeatherStrips" } }
  local n = names[fullType]
  return n and { getDisplayName = function() return n[1] end, getIcon = function() return n[2] end }
end } }

-- The player is patching the jacket's right forearm, half done.
local action = { Type = "ISRepairClothing", clothing = jacket, part = FAR, getJobDelta = function() return 0.5 end }
ISTimedActionQueue = { getTimedActionQueue = function() return { queue = { action } } end }

-- Fake context menu and the game's Inspect window.
local function newMenu()
  local menu = { options = {}, subMenus = {}, hidden = false }
  function menu:addOption(name, target, onSelect, ...)
    local option = { name = name, target = target, onSelect = onSelect }
    local params = { ... }
    for i = 1, 10 do option["param" .. i] = params[i] end
    table.insert(self.options, option)
    return option
  end
  function menu:addSubMenu(option, sub) table.insert(self.subMenus, sub) option.subOption = #self.subMenus end
  function menu:getSubMenu(index) return self.subMenus[index] end
  function menu:hideAndChildren() self.hidden = true end
  return menu
end
local repaired, lastMenu, inspected = nil, nil, nil
ISInventoryPaneContextMenu = {
  repairClothing = function(p, clothing, bodyPart, fabric) repaired = { clothing = clothing, part = bodyPart, fabric = fabric } end,
  onInspectClothingUI = function(_, clothing) inspected = clothing end,
}
ISGarmentUI = {}
function ISGarmentUI:new(_, _, chr, clothing) return setmetatable({ chr = chr, clothing = clothing }, { __index = ISGarmentUI }) end
function ISGarmentUI:initialise() self.parts = {} end
function ISGarmentUI:doContextMenu(bodyPart)
  local menu = newMenu()
  if self.clothing:getFabricType() then
    local patch = menu:addOption("Patch Hole")
    local sub = newMenu()
    local rag = sub:addOption("Rag", self.chr, ISInventoryPaneContextMenu.repairClothing, self.clothing, bodyPart, "rag", "thread", "needle")
    rag.itemForTexture = { getTex = function() return { getName = function() return "media/textures/Item_Rag.png" end } end }
    menu:addSubMenu(patch, sub)
  end
  lastMenu = menu
  return menu
end

local B42 = require("ZomboidDS/Adapters/B42")

-- tailor_list
local ok, _, data = B42.commands.tailor_list(player, {})
check(ok and #data.garments == 3, "lists the clothes in the inventory and bags, hidden wounds left out")
local g1, g2, g3 = data.garments[1], data.garments[2], data.garments[3]
check(g1.name == "Leather Jacket" and g1.worn and g2.name == "Military Boots" and g2.worn, "worn clothes first")
check(g3.name == "Hoodie" and not g3.worn, "then carried ones, in bags too")
check(g1.holes == 1 and g1.patches == 1 and g3.holes == 1, "holes and patches counted")
check(g1.repairable and g2.repairable == false, "clothes without a fabric can't be repaired")
check(g1.icon == "Item_JacketBlack" and g1.condition == 0.6, "icon and condition as the inventory shows them")
check(data.kit.needle and data.kit.thread, "needle and thread found, like the game's menu finds them")
local f = data.kit.fabrics
check(#f == 3 and f[1].name == "Rag" and f[1].count == 12 and f[2].count == 3 and f[3].count == 0, "the three fabrics with their counts")
check(f[1].icon == "Item_Rag" and f[3].type == "LeatherStrips", "fabric icons and types")
check(data.tailoring == 4, "the Tailoring level")
hasNeedle = false
check(select(3, B42.commands.tailor_list(player, {})).kit.needle == false, "no needle: the kit says so")
hasNeedle = true

-- tailor_garment
local okG, _, j = B42.commands.tailor_garment(player, { itemId = 1 })
check(okG and j.name == "Leather Jacket" and j.worn and #j.parts == 3, "a garment with the parts it covers")
local tu, fal, far = j.parts[1], j.parts[2], j.parts[3]
check(tu.id == "Torso_Upper" and tu.name == "Upper Torso" and tu.bite == 20 and tu.scratch == 40 and tu.bullet == 0, "each part's defence")
check(far.hole and far.bite == 0 and far.blood == 0.35, "a hole: no defence there, and its blood")
check(fal.patch == "Leather Strips patch" and fal.hole == nil, "a patch, in the game's words")
check(far.sewing and far.sewing.name == "Patch Hole" and far.sewing.progress == 0.5 and tu.sewing == nil,
  "the patch being sewn shows on its part, with the game's label and progress")
check(j.condition == 0.6 and j.blood == 0.22 and j.dirt == 0.31 and j.cantRepair == nil, "condition, blood and dirt")
check(select(3, B42.commands.tailor_garment(player, { itemId = 2 })).cantRepair == "Can't be repaired.", "boots: the game's Can't be repaired.")
check(not B42.commands.tailor_garment(player, { itemId = 5 }), "not clothing: refused")

-- tailor_menu
local okM, _, menu = B42.commands.tailor_menu(player, { itemId = 1, part = "ForeArm_R" })
check(okM and menu.options[1].name == "Patch Hole" and menu.options[1].children[1].name == "Rag", "the Inspect window's menu for that part")
check(lastMenu.hidden, "built hidden, never shown on the top screen")
check(menu.options[1].children[1].icon == "Item_Rag", "the fabric's icon, from the item the game shows next to it")
check(B42.commands.menu_select(player, { menuId = menu.menuId, optionId = "1.1" }), "choosing an option")
check(repaired and repaired.clothing == jacket and repaired.part == FAR and repaired.fabric == "rag", "runs the game's repair with the window's arguments")
local okB, reason = B42.commands.tailor_menu(player, { itemId = 2, part = "Foot_L" })
check(not okB and reason == "Can't be repaired.", "boots: no menu, the game's reason")
check(not B42.commands.tailor_menu(player, { itemId = 1, part = "Head" }), "a part it doesn't cover: refused")

-- Inspect opens the app's panel while the app is there
local shown = nil
local appThere = true
B42.redirectGameWindows(function(playerNum, show) if not appThere then return false end shown = show return true end)
ISInventoryPaneContextMenu.onInspectClothingUI(player, jacket)
check(shown and shown.panel == "garment" and shown.item == 1 and inspected == nil, "Inspect: the app shows the garment, the game's window stays shut")
appThere = false
ISInventoryPaneContextMenu.onInspectClothingUI(player, boots)
check(inspected == boots, "without the app: the game's own window")
print("ALL LUA CHECKS PASSED")
