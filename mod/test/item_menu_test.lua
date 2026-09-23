-- The mirrored item menu (B42Menu.lua) against a fake of the game's context menu.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

local now = 0
function getTimestampMs() return now end
function instanceof() return false end
local paused = false
UIManager = { getSpeedControls = function() return { getCurrentGameSpeed = function() return paused and 0 or 1 end } end }

local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

-- A book in the player's inventory.
local book = { getID = function() return 42 end }
local inventory = {
  isInCharacterInventory = function() return true end,
  getItemWithIDRecursiv = function(_, id) if id == 42 and book.present ~= false then return book end end,
}
book.getContainer = function() return inventory end
local player = { getPlayerNum = function() return 0 end, getInventory = function() return inventory end }

-- Fake ISContextMenu: options in an array, submenus resolved through getSubMenu.
local function newMenu()
  local menu = { options = {}, subMenus = {}, hidden = false }
  function menu:addOption(name, target, onSelect, ...)
    local option = { name = name, target = target, onSelect = onSelect }
    local params = { ... }
    for i = 1, 10 do option["param" .. i] = params[i] end
    table.insert(self.options, option)
    return option
  end
  function menu:addSubMenu(option, sub)
    table.insert(self.subMenus, sub)
    option.subOption = #self.subMenus
  end
  function menu:getSubMenu(index) return self.subMenus[index] end
  function menu:hideAndChildren() self.hidden = true end
  return menu
end

local calls = {}
local lastMenu
ISInventoryPaneContextMenu = {
  createMenu = function(playerNum, inInventory, items, x, y)
    if paused then return nil end
    local menu = newMenu()
    menu:addOption("Read", "readTarget", function(target, a, b) table.insert(calls, "read " .. target .. " " .. tostring(a) .. " " .. tostring(b)) end, items[1], "fast")
    local eat = menu:addOption("Eat")
    local sub = newMenu()
    sub:addOption("All", nil, function() table.insert(calls, "eat all") end)
    sub:addOption("Half", nil, function() table.insert(calls, "eat half") end)
    menu:addSubMenu(eat, sub)
    local disabled = menu:addOption("Rip into sheets", nil, function() table.insert(calls, "rip") end)
    disabled.notAvailable = true
    disabled.toolTip = { description = "<RGB:1,0,0> Requires <LINE> a knife " }
    lastMenu = menu
    return menu
  end,
}

local B42 = require("ZomboidDS/Adapters/B42")

-- open
local ok, err, data = B42.commands.item_menu(player, { itemId = 42 })
check(ok and err == nil and data.menuId ~= nil, "item_menu returns a menu")
check(lastMenu.hidden, "the game's menu is hidden again right away")
local o = data.options
check(#o == 3 and o[1].name == "Read" and o[1].enabled and o[1].id == "1", "top-level options copied")
check(o[2].name == "Eat" and #o[2].children == 2 and o[2].children[2].id == "2.2", "submenus become children with path ids")
check(o[3].enabled == false and o[3].tooltip == "Requires \n a knife", "greyed out option keeps its plain-text reason: " .. tostring(o[3].tooltip))

-- select runs the game's own call with its target and params
check(B42.commands.menu_select(player, { menuId = data.menuId, optionId = "1" }) == true, "menu_select succeeds")
check(calls[1] == "read readTarget " .. tostring(book) .. " fast", "the option ran with its target and params: " .. tostring(calls[1]))

-- one choice per menu
local ok2, reason2 = B42.commands.menu_select(player, { menuId = data.menuId, optionId = "2.1" })
check(ok2 == false and reason2:find("out of date"), "a menu can be used once")

-- submenu option, and the game wiping its option tables doesn't matter (we copied)
local _, _, data2 = B42.commands.item_menu(player, { itemId = 42 })
for _, option in ipairs(lastMenu.options) do for k in pairs(option) do option[k] = nil end end
check(B42.commands.menu_select(player, { menuId = data2.menuId, optionId = "2.1" }) == true and calls[2] == "eat all",
  "submenu option runs, even after the game reused its menu tables")

-- disabled options can't be run
local _, _, data3 = B42.commands.item_menu(player, { itemId = 42 })
local ok3 = B42.commands.menu_select(player, { menuId = data3.menuId, optionId = "3" })
check(ok3 == false and #calls == 2, "greyed out options can't be selected")

-- expiry and vanished items
local _, _, data4 = B42.commands.item_menu(player, { itemId = 42 })
now = now + 61000
check(B42.commands.menu_select(player, { menuId = data4.menuId, optionId = "1" }) == false, "menus expire after a minute")
local _, _, data5 = B42.commands.item_menu(player, { itemId = 42 })
book.present = false
local ok5, reason5 = B42.commands.menu_select(player, { menuId = data5.menuId, optionId = "1" })
check(ok5 == false and reason5:find("no longer"), "an item that's gone can't be used")
book.present = true

-- paused game
paused = true
local ok6, reason6 = B42.commands.item_menu(player, { itemId = 42 })
check(ok6 == false and reason6 == "The game is paused", "no menu while paused")
print("ALL LUA CHECKS PASSED")
