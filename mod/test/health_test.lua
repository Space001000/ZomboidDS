-- Health (B42.snapshotHealth): the game's own health panel code decides which parts and lines to
-- show; we record its drawText calls. Fakes stand in for that code. Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
function instanceof() return false end
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

Perks = { Doctor = "Doctor" }
local firstAid = 1
local player = { getPerkLevel = function(_, perk) return perk == "Doctor" and firstAid or 0 end }

local function part(kind, scratched, bandaged, broken)
  return { getType = function() return kind end, scratched = scratched, bandaged = bandaged, broken = broken }
end
local hand = part("Hand_L", true, false)
local shin = part("LowerLeg_R", false, true)
local weird = part("Head", false, false, true)

-- Like the game: which parts are listed is decided by the panel (patient = the player).
ISHealthPanel = { getDamagedParts = function(panel)
  assert(panel:getPatient() == player)
  return { hand, shin, weird }
end }
-- Like the game's doDrawItem: name in white, then "- " lines in its colours, severity by First Aid level.
ISHealthBodyPartListBox = { doDrawItem = function(self, y, item)
  local bp = item.item.bodyPart
  if bp.broken then error("a mod broke this part's drawing") end
  self:drawRect(0, y, self:getWidth(), 10, 0.2, 1, 1, 1)
  self:drawText(bp:getType() == "Hand_L" and "Left Hand" or "Right Shin", 0, y, 1, 1, 1, 1)
  if bp.scratched then
    local text = self.parent.doctorLevel > 2 and " (Severe)" or ""
    self:drawText("- Scratched" .. text, 15, y, 0.89, 0.28, 0.28, 1)
  end
  if bp.bandaged then
    self:drawText("- Bandaged", 15, y, 0.28, 0.89, 0.28, 1)
    self:drawText("- Dirty bandage", 15, y, 1, 0.28, 0, 1)
  end
  return y
end }

local B42 = require("ZomboidDS/Adapters/B42")
local health = B42.snapshotHealth(player)
check(#health.parts == 2, "the parts the game lists; one whose drawing fails is skipped, not fatal")
check(health.parts[1].id == "Hand_L" and health.parts[1].name == "Left Hand", "part id and the game's name")
check(health.parts[1].lines[1].text == "Scratched" and health.parts[1].lines[1].tone == "bad",
  "the game's line, without its '- ', and red means a problem")
check(health.parts[2].lines[1].tone == "good" and health.parts[2].lines[2].tone == "warn",
  "green = treated, orange = needs attention")
firstAid = 5
check(B42.snapshotHealth(player).parts[1].lines[1].text == "Scratched (Severe)",
  "the player's First Aid level decides what they can tell, as in the game")
-- Treatment menu for a body part: the game's health panel builds it into the player's context menu.
UIManager = { getSpeedControls = function() return { getCurrentGameSpeed = function() return 1 end } end }
local function list(t) return { size = function() return #t end, get = function(_, i) return t[i + 1] end } end
player.getPlayerNum = function() return 0 end
player.getBodyDamage = function() return { getBodyParts = function() return list({ hand, shin }) end } end
local treated = {}
local contextMenu = { options = {}, hidden = false }
function contextMenu:hideAndChildren() self.hidden = true end
function getPlayerContextMenu() return contextMenu end
JoypadState = { players = { { focus = "game" } } }
function updateJoypadFocus() end
local healthView = {}
function healthView:doBodyPartContextMenu(bodyPart)
  contextMenu.hidden = false
  contextMenu.options = {}
  if bodyPart == hand then
    table.insert(contextMenu.options, { name = "Apply Bandage", target = bodyPart,
      onSelect = function(target) table.insert(treated, target:getType()) end })
  end
  JoypadState.players[1].focus = contextMenu -- the game hands the controller to its menu
end
function getPlayerInfoPanel() return { healthView = healthView } end

local ok, _, menu = B42.commands.health_menu(player, { part = "Hand_L" })
check(ok and menu.options[1].name == "Apply Bandage", "the game's treatment menu for that body part")
check(contextMenu.hidden, "hidden again right away, never shown on the top screen")
check(JoypadState.players[1].focus == "game", "the controller focus the game moved to its menu is put back")
check(B42.commands.menu_select(player, { menuId = menu.menuId, optionId = "1" }) and treated[1] == "Hand_L",
  "choosing a treatment runs it like in the game")
local ok2, reason2 = B42.commands.health_menu(player, { part = "LowerLeg_R" })
check(ok2 == false and reason2:find("carry"), "nothing to do with what you carry: says so")
check(B42.commands.health_menu(player, { part = "Tail" }) == false, "unknown body part: refused")

ISHealthPanel = nil
check(#B42.snapshotHealth(player).parts == 0, "no health panel code: an empty list, not an error")
print("ALL LUA CHECKS PASSED")
