-- Test kits (mod/dev, development builds only): the Tailor kit against fakes of the inventory,
-- clothing and the action queue. Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;"
  .. MOD .. "/../dev/ZomboidDS/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
function instanceof() return false end
function getText(k) return k end
function isClient() return false end
local events = {}
function triggerEvent(name) events[#events + 1] = name end
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

BloodBodyPartType = setmetatable({}, { __index = function(_, k) return k end })
Perks = { Tailoring = "Tailoring" }

local function newContainer()
  local c = { items = {} }
  function c:AddItem(fullType)
    local item = { type = fullType, holes = {}, patches = {}, condition = 10 }
    function item:getVisual() local i = self return { setHole = function(_, part) i.holes[part] = true end } end
    function item:getCondition() return self.condition end
    function item:setCondition(v) self.condition = v end
    function item:getCondLossPerHole() return 1.25 end
    function item:addPatch(_, part, fabric) self.patches[part] = fabric end
    function item:getInventory() self.inner = self.inner or newContainer() return self.inner end
    table.insert(self.items, item)
    return item
  end
  function c:count(fullType) local n = 0 for _, i in ipairs(self.items) do if i.type == fullType then n = n + 1 end end return n end
  function c:find(fullType) for _, i in ipairs(self.items) do if i.type == fullType then return i end end end
  return c
end
local inventory = newContainer()
local levels = {}
local player = { getInventory = function() return inventory end, getPlayerNum = function() return 0 end,
  setPerkLevelDebug = function(_, perk, level) levels[perk] = level end,
  getXp = function() return { setXPToLevel = function(_, perk, level) levels[perk .. "xp"] = level end } end }
function instanceItem(fullType) return { type = fullType } end
local queued = {}
ISTimedActionQueue = { add = function(action) queued[#queued + 1] = action end }
ISWearClothing = { new = function(_, chr, item) return { wear = item, chr = chr } end }

local B42 = require("ZomboidDS/Adapters/B42")
require("ZomboidDS/Dev/TestKits")

local hasCap = false
for _, c in ipairs(B42.capabilities) do if c == "dev.kits" then hasCap = true end end
check(hasCap, "the dev mod announces its test kits")
local ok, _, data = B42.commands.dev_kits(player, {})
check(ok and data.kits[1].id == "tailor" and data.kits[1].name == "Tailor", "lists the kits")

check(B42.commands.dev_kit(player, { kit = "tailor" }), "gives the Tailor kit")
check(inventory:count("Base.Needle") == 1 and inventory:count("Base.Thread") == 1, "a needle and thread")
check(inventory:count("Base.RippedSheets") == 6 and inventory:count("Base.DenimStrips") == 3 and inventory:count("Base.LeatherStrips") == 0,
  "6 Rags and 3 Denim Strips, no Leather Strips")
local jacket = inventory:find("Base.Jacket_LeatherBlack")
check(jacket.holes.UpperArm_L and jacket.holes.ForeArm_R and jacket.condition == 6, "a jacket with two holes, condition lowered for each")
check(jacket.patches.Torso_Lower ~= nil, "and a patch")
check(queued[1] and queued[1].wear == jacket, "put on the way the game does it")
local bag = inventory:find("Base.Bag_ALICEpack")
local hoodie = bag and bag.inner and bag.inner:find("Base.HoodieDOWN_WhiteTINT")
check(hoodie and hoodie.holes.Torso_Upper and hoodie.holes.ForeArm_L, "a hoodie with holes in a backpack")
check(inventory:count("Base.Shoes_ArmyBoots") == 1, "boots the game can't repair")
check(levels.Tailoring == 4 and levels.Tailoringxp == 4, "Tailoring 4")
check(not B42.commands.dev_kit(player, { kit = "nope" }), "an unknown kit: refused")
print("ALL LUA CHECKS PASSED")
