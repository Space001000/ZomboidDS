--- Timings of the mod's work in the game's tick, summed up in the game log every few seconds: per
--- channel (snapshot, change check, emit), per app command, and per tick (how long the mod held up
--- a frame). Only on when Config.perfReportMs is set (the dev builds, see mod/dev); off, nothing
--- here runs.
local Perf = {}
Perf.__index = Perf

--- @param clock    function returning milliseconds, fractions welcome (the bridge's clockMs)
--- @param reportMs how often to write the summary
--- @param log      function(line), defaults to print
--- @param gcCount  optional function returning the JVM's garbage collections so far (nil: unknown),
---                 to mark ticks the game's GC paused, which aren't the mod's own cost
function Perf.new(clock, reportMs, log, gcCount)
    local self = setmetatable({ clock = clock, reportMs = reportMs, log = log or print, gcCount = gcCount }, Perf)
    self:reset(clock())
    return self
end

function Perf:reset(now)
    self.reportAt = now + self.reportMs
    self.channels, self.channelOrder = {}, {}
    self.commands, self.commandOrder = {}, {}
    self.ticks, self.busyTicks, self.busyTotal = 0, 0, 0
    self.over8, self.over16, self.over8Gc, self.over16Gc = 0, 0, 0, 0
    self.worst, self.worstParts, self.worstGc = 0, nil, false
    self.worstClean, self.worstCleanParts = 0, nil
    self.tickStart, self.tickParts, self.tickGc = nil, nil, nil
end

function Perf:now()
    return self.clock()
end

local function phase()
    return { total = 0, max = 0, n = 0 }
end

local function addTo(p, ms)
    p.total = p.total + ms
    p.n = p.n + 1
    if ms > p.max then p.max = ms end
end

function Perf:beginTick()
    self.tickGc = self.gcCount and self.gcCount() or nil
    self.tickStart = self.clock()
    self.tickParts = {}
end

--- One channel ran: `ms` per phase (`emitMs` nil when nothing changed), `size` the change check's length.
function Perf:channel(name, dirty, snapshotMs, signatureMs, emitMs, size)
    local c = self.channels[name]
    if c == nil then
        c = { runs = 0, dirty = 0, sent = 0, size = 0, snapshot = phase(), signature = phase(), emit = phase() }
        self.channels[name] = c
        self.channelOrder[#self.channelOrder + 1] = name
    end
    c.runs = c.runs + 1
    if dirty then c.dirty = c.dirty + 1 end
    c.size = c.size + (size or 0)
    addTo(c.snapshot, snapshotMs)
    if signatureMs then addTo(c.signature, signatureMs) end
    if emitMs then
        c.sent = c.sent + 1
        addTo(c.emit, emitMs)
    end
    if self.tickParts then
        self.tickParts[#self.tickParts + 1] = string.format("%s %.1f", name, snapshotMs + (signatureMs or 0) + (emitMs or 0))
    end
end

function Perf:command(name, ms)
    local c = self.commands[name]
    if c == nil then
        c = phase()
        self.commands[name] = c
        self.commandOrder[#self.commandOrder + 1] = name
    end
    addTo(c, ms)
    if self.tickParts then
        self.tickParts[#self.tickParts + 1] = string.format("cmd %s %.1f", name, ms)
    end
end

function Perf:endTick()
    if self.tickStart == nil then
        return
    end
    local now = self.clock()
    local ms = now - self.tickStart
    local gc = false
    if self.tickGc ~= nil then
        local count = self.gcCount()
        gc = count ~= nil and count ~= self.tickGc
    end
    self.ticks = self.ticks + 1
    if #self.tickParts > 0 then
        self.busyTicks = self.busyTicks + 1
        self.busyTotal = self.busyTotal + ms
        if ms > 8 then
            self.over8 = self.over8 + 1
            if gc then self.over8Gc = self.over8Gc + 1 end
        end
        if ms > 16 then
            self.over16 = self.over16 + 1
            if gc then self.over16Gc = self.over16Gc + 1 end
        end
        if ms > self.worst then
            self.worst, self.worstParts, self.worstGc = ms, self.tickParts, gc
        end
        if not gc and ms > self.worstClean then
            self.worstClean, self.worstCleanParts = ms, self.tickParts
        end
    end
    self.tickStart, self.tickParts, self.tickGc = nil, nil, nil
    if now >= self.reportAt then
        self:report()
        self:reset(now)
    end
end

local function avgMax(p)
    if p.n == 0 then return "-" end
    return string.format("%.1f/%.1f", p.total / p.n, p.max)
end

function Perf:report()
    if self.busyTicks == 0 then
        return
    end
    local seconds = self.reportMs / 1000
    if self.gcCount then
        -- A tick the game's garbage collector paused is longer than the mod's work in it.
        self.log(string.format("[ZomboidDS] perf %ds: %d ticks, %d with work, avg %.1f ms, %d over 8 ms (%d with GC), "
            .. "%d over 16 ms (%d with GC); worst %.1f ms%s (%s); worst without GC %.1f ms (%s)",
            seconds, self.ticks, self.busyTicks, self.busyTotal / self.busyTicks, self.over8, self.over8Gc,
            self.over16, self.over16Gc, self.worst, self.worstGc and " with GC" or "", table.concat(self.worstParts or {}, ", "),
            self.worstClean, table.concat(self.worstCleanParts or {}, ", ")))
    else
        self.log(string.format("[ZomboidDS] perf %ds: %d ticks, %d with work, avg %.1f ms, %d over 8 ms, %d over 16 ms; worst %.1f ms (%s)",
            seconds, self.ticks, self.busyTicks, self.busyTotal / self.busyTicks, self.over8, self.over16,
            self.worst, table.concat(self.worstParts or {}, ", ")))
    end
    for _, name in ipairs(self.channelOrder) do
        local c = self.channels[name]
        -- The size is the Lua Signature's length: unknown when the bridge compares in Java, where
        -- "check" is converting, comparing and sending in one.
        local size = c.size > 0 and string.format(", ~%d chars", math.floor(c.size / c.runs)) or ""
        self.log(string.format("[ZomboidDS] perf   %s: %d runs (%d dirty), %d sent%s | snapshot %s | check %s | emit %s ms avg/max",
            name, c.runs, c.dirty, c.sent, size, avgMax(c.snapshot), avgMax(c.signature), avgMax(c.emit)))
    end
    for _, name in ipairs(self.commandOrder) do
        self.log(string.format("[ZomboidDS] perf   command %s: %d | %s ms avg/max", name, self.commands[name].n, avgMax(self.commands[name])))
    end
end

return Perf
