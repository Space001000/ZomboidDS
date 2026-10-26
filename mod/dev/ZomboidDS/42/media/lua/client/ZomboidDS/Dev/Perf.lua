--- DEVELOPMENT ONLY. Turns on the mod's timings (Core/Perf.lua): every 10 s the game log gets a
--- summary of how long each channel, command and tick took (logcat tag zomdroid-main, lines with
--- "[ZomboidDS] perf"). Only in the dev builds' mod zip, like TestKits.lua.
local Config = require("ZomboidDS/Core/Config")

Config.perfReportMs = 10000
