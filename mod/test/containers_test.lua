-- Container snapshots (B42.snapshotContainers) against fakes of the game's inventory and loot
-- windows. Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
function instanceof() return false end

local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

local function list(t) return { size = function() return #t end, get = function(_, i) return t[i + 1] end } end
local function item(id, name)
  local it = { getID = function() return id end, getFullType = function() return "Base." .. name end,
    getDisplayName = function() return name end, getDisplayCategory = function() return "Misc" end,
    getTex = function() return { getName = function() return "Item_" .. name end } end,
    getActualWeight = function() return 0.5 end, favorite = false, equipped = false, unwanted = false }
  function it:isFavorite() return self.favorite end
  function it:isEquipped() return self.equipped end
  function it:isUnwanted() return self.unwanted end
  function it:getContainer() return self.container end
  return it
end
local function container(kind, items, weight, onPlayer)
  local c = { items = items }
  for _, it in ipairs(items) do it.container = c end
  function c:getType() return kind end
  function c:getItems() return list(self.items) end
  function c:getCapacityWeight() return weight end
  function c:isInCharacterInventory() return onPlayer == true end
  function c:isItemAllowed() return kind ~= "fridge_full" end
  function c:getItemWithIDRecursiv(id) for _, it in ipairs(self.items) do if it:getID() == id then return it end end end
  return c
end
local function texture(path) return { getName = function() return path end } end
local function button(c, name, capacity, image) return { inventory = c, name = name, capacity = capacity, image = image, onclick = function() end } end

-- The player: main inventory with a backpack equipped.
local mainInventory = container("none", { item(1, "Axe"), item(4, "Sock"), item(5, "Lucky Coin") }, 3, true)
local backpack = container("Bag_Schoolbag", { item(2, "Apple"), item(3, "Apple") }, 1, true)
local player = { getPlayerNum = function() return 0 end, getInventory = function() return mainInventory end,
  getPrimaryHandItem = function() end, getSecondaryHandItem = function() end, isEquippedClothing = function() return false end }

-- Around the player: a shelf, a locked crate, and the floor.
local shelf = container("shelves", { item(10, "Book"), item(11, "Pen") }, 1.1)
local crate = container("crate", { item(12, "Secret") }, 5)
local floor = container("floor", { item(13, "Rock") }, 2)
local lockedButton = button(crate, "Crate", 50, texture("media/ui/Container_Crate.png"))
lockedButton.onclick = nil
lockedButton.textureOverride = texture("media/ui/lock.png")

local inventoryPage = { backpacks = {
  button(mainInventory, "Inventory", 12, texture("media/ui/Icon_InventoryBasic.png")),
  button(backpack, "School Bag", 13, texture("Item_Schoolbag")),
} }
local lootPage = { backpacks = {
  button(shelf, "Shelves", 50, texture("media/ui/Container_Shelf.png")),
  lockedButton,
  button(floor, "Floor", 50, texture("media/ui/Container_Floor.png")),
} }
function getPlayerInventory() return inventoryPage end
function getPlayerLoot() return lootPage end

local B42 = require("ZomboidDS/Adapters/B42")
local data = B42.snapshotContainers(player)
local c = data.containers
local function byName(name) for _, x in ipairs(c) do if x.name == name then return x end end end

check(#c == 5, "all containers from both windows: " .. #c)
check(byName("Inventory").kind == "inventory" and byName("Inventory").items == nil,
  "the main inventory is listed as a destination, without repeating its items")
check(byName("School Bag").kind == "bag" and #byName("School Bag").items == 2, "equipped bags with their items")
check(byName("Shelves").kind == "nearby" and #byName("Shelves").items == 2 and byName("Shelves").items[1].name == "Book",
  "nearby containers with their items")
check(byName("Floor").kind == "floor", "the floor")
check(byName("Crate").locked == true and byName("Crate").items == nil, "locked containers: shown, but not their contents")
check(byName("Shelves").icon == "Container_Shelf" and byName("Crate").icon == "lock", "icons as texture names")
check(byName("Shelves").capacity == 50 and byName("Shelves").weight == 1.1, "weight and capacity")
check(#byName("Shelves").items[1].actions == 0, "no quick actions for items outside the inventory yet")

-- ids are stable across snapshots and resolve back to containers
local shelfId = byName("Shelves").id
local again = B42.snapshotContainers(player)
local sameId = false
for _, x in ipairs(again.containers) do if x.name == "Shelves" then sameId = x.id == shelfId end end
check(sameId, "container ids stay the same across snapshots")
check(B42.reachableContainer(shelfId) == shelf, "ids resolve to the container while it's within reach")

-- walking away: the shelf is no longer in the loot window, so its id no longer resolves
lootPage.backpacks = { lootPage.backpacks[3] }
B42.snapshotContainers(player)
check(B42.reachableContainer(shelfId) == nil, "out of reach: the id no longer resolves")
-- Moving items -------------------------------------------------------------------
local transfers, walks = {}, {}
lootPage.inventoryPane = { transferItemsByWeight = function(_, items, destination)
  for _, it in ipairs(items) do table.insert(transfers, it:getDisplayName() .. " -> " .. destination:getType()) end
end }
luautils = { walkToContainer = function(c) table.insert(walks, c:getType()) return true end }
ItemType, ItemTag = { KEY_RING = "KEY_RING" }, { KEY_RING = "KEY_RING" }
function getPlayerHotbar() return { isInHotbar = function() return false end } end

lootPage.backpacks = { button(shelf, "Shelves", 50, texture("media/ui/Container_Shelf.png")), lockedButton,
  button(floor, "Floor", 50, texture("media/ui/Container_Floor.png")) }
local snapshot = B42.snapshotContainers(player)
local ids = {}
for _, x in ipairs(snapshot.containers) do ids[x.name] = x.id end

local ok, reason = B42.commands.transfer(player, { itemId = 10, to = ids["Inventory"] })
check(ok == true and transfers[1] == "Book -> none", "take an item from a shelf into the inventory")
check(walks[1] == "shelves" and #walks == 1, "walks to the shelf first (not to the inventory), like the game")

ok = B42.commands.transfer(player, { itemId = 1, to = ids["Floor"] })
check(ok == true and transfers[2] == "Axe -> floor", "put an item from the inventory on the floor")

ok, reason = B42.commands.transfer(player, { itemId = 10, to = ids["Crate"] })
check(ok == false and reason:find("locked"), "can't move into a locked container")
ok, reason = B42.commands.transfer(player, { itemId = 11, to = ids["Shelves"] })
check(ok == false and reason:find("already"), "moving an item to where it already is is refused")
ok, reason = B42.commands.transfer(player, { itemId = 10, to = "c999" })
check(ok == false and reason:find("out of reach"), "unknown or far away containers are refused")
ok, reason = B42.commands.transfer(player, { itemId = 12, to = ids["Inventory"] })
check(ok == false and reason == "item not found", "items in locked containers can't be reached")

-- move all: Take All rules from a container, Transfer All rules from the inventory
transfers = {}
shelf.items[2].unwanted = true
ok = B42.commands.transfer_all(player, { from = ids["Shelves"], to = ids["Inventory"] })
check(ok == true and #transfers == 1 and transfers[1] == "Book -> none", "take all skips items marked unwanted")

transfers = {}
mainInventory.items[1].equipped = true
mainInventory.items[3].favorite = true
ok = B42.commands.transfer_all(player, { from = ids["Inventory"], to = ids["Shelves"] })
check(ok == true and #transfers == 1 and transfers[1] == "Sock -> shelves",
  "transfer all from the inventory skips equipped items and favourites: " .. table.concat(transfers, ", "))

ok, reason = B42.commands.transfer_all(player, { from = ids["Crate"], to = ids["Inventory"] })
check(ok == false, "can't take all from a locked container")
-- The game's item menu for items around you: built like the loot window's (not "in inventory").
local menuCalls, menuArgs = {}, nil
ISInventoryPaneContextMenu = { createMenu = function(playerNum, inInventory, items)
  menuArgs = { inInventory = inInventory, item = items[1] }
  local option = { name = "Grab", target = items[1], onSelect = function(target) table.insert(menuCalls, "grab " .. target:getDisplayName()) end }
  return { options = { option }, hideAndChildren = function() end }
end }
function shelf:isInCharacterInventory() return false end
snapshot = B42.snapshotContainers(player)
local ok2, _, menu = B42.commands.item_menu(player, { itemId = 10 })
check(ok2 == true and menuArgs.inInventory == false and menuArgs.item:getDisplayName() == "Book",
  "item menu for an item on a shelf: the game's loot menu")
check(B42.commands.menu_select(player, { menuId = menu.menuId, optionId = "1" }) == true and menuCalls[1] == "grab Book",
  "its options run, while the item is still within reach")
ok2 = B42.commands.item_menu(player, { itemId = 12 })
check(ok2 == false, "no menu for items in locked containers")

local _, _, menu2 = B42.commands.item_menu(player, { itemId = 10 })
lootPage.backpacks = {}
B42.snapshotContainers(player)
local ok3, reason3 = B42.commands.menu_select(player, { menuId = menu2.menuId, optionId = "1" })
check(ok3 == false and reason3:find("no longer"), "walked away: the option no longer runs")
-- The loot window's selection: reported, and set from the app (the game outlines it in the world).
lootPage.backpacks = { button(shelf, "Shelves", 50, texture("media/ui/Container_Shelf.png")),
  button(floor, "Floor", 50, texture("media/ui/Container_Floor.png")) }
lootPage.inventoryPane.inventory = floor
local selectedCalls = {}
function lootPage:selectButtonForContainer(c) table.insert(selectedCalls, c) ; self.inventoryPane.inventory = c end
function floor:isInCharacterInventory() return false end
local snap = B42.snapshotContainers(player)
local byName2 = {}
for _, x in ipairs(snap.containers) do byName2[x.name] = x end
check(byName2["Floor"].selected == true and byName2["Shelves"].selected == nil, "containers: the game's selected loot container is marked")
check(B42.commands.select_container(player, { id = byName2["Shelves"].id }) == true and selectedCalls[1] == shelf,
  "select_container selects it in the game's loot window (which outlines it)")
check(B42.commands.select_container(player, { id = byName2["Inventory"].id }) == false and #selectedCalls == 1,
  "your own bags aren't loot: not selected there")
check(B42.commands.select_container(player, { id = "c999" }) == false, "out of reach: refused")

-- The game's windows refresh their container lists only when drawn; hidden, the snapshot does it.
local refreshes = 0
ISInventoryPage = { renderDirty = true, dirtyUI = function() refreshes = refreshes + 1 end }
B42.snapshotContainers(player)
check(refreshes == 1 and ISInventoryPage.renderDirty == false,
  "a container changed (renderDirty) while the windows are hidden: the snapshot refreshes their lists first")
B42.snapshotContainers(player)
check(refreshes == 1, "nothing changed since: no refresh")
local pageDirty = true
player.isInvPageDirty = function() return pageDirty end
player.setInvPageDirty = function(_, v) pageDirty = v end
B42.snapshotContainers(player)
check(refreshes == 2 and pageDirty == false, "the player's own inventory changed (invPageDirty): refreshed too")
ISInventoryPage.renderDirty = true
lootPage.getIsVisible = function() return true end
B42.snapshotContainers(player)
check(refreshes == 2, "a visible window refreshes itself when drawn: left to the game")
lootPage.getIsVisible = nil
ISInventoryPage = nil
print("ALL LUA CHECKS PASSED")
