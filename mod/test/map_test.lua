-- The minimap (B42.snapshotMap): where the player is, which way they face, and whether the save
-- allows a map. Fakes stand in for the player, a car, the game's ISMiniMap/ISWorldMap rules and
-- the bridge's publishExplored.
-- Run with: python tools/test-lua.py
local MOD = MOD_ROOT
package.path = MOD .. "/common/media/lua/client/?.lua;" .. MOD .. "/42/media/lua/client/?.lua;" .. package.path

function getTimestampMs() return 0 end
function instanceof() return false end
local function check(cond, msg) if not cond then error("FAIL: " .. msg, 2) end print("ok   " .. msg) end

local rules = { mini = true, world = true }
ISMiniMap = { IsAllowed = function() return rules.mini end }
ISWorldMap = { IsAllowed = function() return rules.world end }
local published = 0
ZomboidDSBridge = { publishExplored = function() published = published + 1 end }

local function mover(x, y, z)
  return {
    x = x, y = y, z = z,
    getX = function(self) return self.x end,
    getY = function(self) return self.y end,
    getZ = function(self) return self.z end,
  }
end
local player = mover(10745.33, 9960.71, 0.4)
player.fx, player.fy = 0, 1
player.getForwardDirectionX = function(self) return self.fx end
player.getForwardDirectionY = function(self) return self.fy end
player.getVehicle = function(self) return self.vehicle end

local B42 = require("ZomboidDS/Adapters/B42")
local snap = B42.snapshotMap(player)
check(snap.x == 10745.3 and snap.y == 9960.7, "position in tiles, to a tenth")
check(snap.z == 0, "floor level as a whole number")
check(snap.heading == 90, "facing +y is 90 degrees (south, the map's y points down)")
check(snap.miniMap == true and snap.worldMap == true, "the save's map rules")
check(published == 1, "asks the Java side to send the seen areas")

player.fx, player.fy = 0.72, -0.69
check(B42.snapshotMap(player).heading == 315, "north-east, rounded to 5 degrees: " .. tostring(B42.snapshotMap(player).heading))
player.fx, player.fy = 0.766, -0.643 -- 320: the walk animation swaying
check(B42.snapshotMap(player).heading == 315, "a sway of a few degrees keeps the arrow still")
player.fx, player.fy = 1, 0
check(B42.snapshotMap(player).heading == 0, "a real turn moves it")
player.fx, player.fy = 0.985, -0.174 -- 350: across 0
check(B42.snapshotMap(player).heading == 0, "small sways across east (0/360) are small too")
player.fx, player.fy = 0.72, -0.69
B42.snapshotMap(player)

rules.mini = false
check(B42.snapshotMap(player).miniMap == false, "minimap off in the sandbox options")
rules.mini = function() error("boom") end
ISMiniMap.IsAllowed = function() error("renamed") end
check(B42.snapshotMap(player).miniMap == false, "a failing rule counts as not allowed")

-- Driving: the car's position, and the heading from how it moved.
local car = mover(100, 100, 0)
player.vehicle = car
snap = B42.snapshotMap(player)
check(snap.x == 100 and snap.y == 100, "the car's position while driving")
car.x = 100.2
check(B42.snapshotMap(player).heading == 315, "a tiny move keeps the last heading")
car.x, car.y = 90, 100
check(B42.snapshotMap(player).heading == 180, "driving west: 180 degrees")

check(B42.snapshotMap(mover(nil, nil, 0)) == nil, "no position: nothing to send")
ZomboidDSBridge = nil
player.vehicle = nil
check(B42.snapshotMap(player) ~= nil, "without the Java side (older bridge) the position still works")
print("ALL LUA CHECKS PASSED")
