package dev.zomboidds.bridge.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake devices for the device panel (see PROTOCOL.md {@code device_state}): a TV with a VCR, a radio
 * on batteries, a walkie-talkie and a CD player. Opened from the console (t, r, k, c) as the game's
 * Device Options would; controls apply at once (the real game walks over first). Not thread-safe:
 * used on the mock's game thread.
 */
final class MockDevices {

    private static final class Device {
        final String name;
        final String icon;
        final String where;
        boolean on = true;
        boolean tv;
        double volume = 0.5;
        int channel;
        Integer min;
        Integer max;
        final List<Map<String, Object>> presets = new ArrayList<>();
        Double battery; // charge, or null without one
        boolean batteryPowered;
        boolean headphoneSlot;
        String headphones; // "Headphones" / "Earbuds" or null
        Integer transmit; // two-way range, or null
        boolean micMuted;
        String mediaType; // "cd" / "vhs" or null
        String media;
        boolean playing;

        Device(String name, String icon, String where) {
            this.name = name;
            this.icon = icon;
            this.where = where;
        }
    }

    private record Thing(long id, String name, String icon, String fits) {
    }

    private static final Map<Integer, String> STATIONS = Map.of(
        200, "Triple-N", 201, "WBLN News", 203, "Life and Living TV", 204, "TURBO", 205, "PawsTV",
        93200, "LBMW - Kentucky Radio", 98000, "NNR Radio", 89400, "Hitz FM", 101200, "KnoxTalk Radio",
        91200, "Civilian Radio");

    private final Map<String, Device> devices = new LinkedHashMap<>();
    /** What's lying around: on the player, then on the shelves. Insert takes it from here. */
    private final List<Thing> onYou = new ArrayList<>();
    private final List<Thing> shelves = new ArrayList<>();
    private Device open;

    MockDevices() {
        Device tv = new Device("ValuTech Television", "appliances_television_01_0_Icon", null);
        tv.tv = true;
        tv.channel = 200;
        for (int f : new int[] {200, 201, 203, 204, 205}) preset(tv, f);
        tv.mediaType = "vhs";
        tv.media = "VHS: Breaking Points";
        tv.playing = true;
        devices.put("t", tv);

        Device radio = new Device("ValuTech Radio", "Item_RadioBlack", "floor");
        radio.channel = 93200;
        radio.min = 88000;
        radio.max = 108000;
        radio.batteryPowered = true;
        radio.battery = 0.63;
        radio.headphoneSlot = true;
        for (int f : new int[] {93200, 98000, 89400}) preset(radio, f);
        devices.put("r", radio);

        Device walkie = new Device("Premium Tech. Walkie Talkie", "Item_WalkieTalkieCivilian2", "hand");
        walkie.channel = 91200;
        walkie.min = 25000;
        walkie.max = 300000;
        walkie.batteryPowered = true;
        walkie.battery = 0.41;
        walkie.headphoneSlot = true;
        walkie.headphones = "Earbuds";
        walkie.transmit = 2000;
        preset(walkie, 91200);
        devices.put("k", walkie);

        Device cd = new Device("CD Player", "Item_CDplayer", "hand");
        cd.channel = 98000;
        cd.min = 88000;
        cd.max = 108000;
        cd.batteryPowered = true;
        cd.battery = 0.88;
        cd.headphoneSlot = true;
        cd.headphones = "Earbuds";
        cd.mediaType = "cd";
        cd.media = "CD: Your Smile. Your Kiss.";
        devices.put("c", cd);

        onYou.add(new Thing(70001, "VHS: Pleistocene Land", "Item_Cassette3", "vhs"));
        onYou.add(new Thing(70002, "VHS: Eagle Down", "Item_Cassette3", "vhs"));
        onYou.add(new Thing(70003, "CD: Over", "Item_Disc", "cd"));
        onYou.add(new Thing(70004, "CD: You're Gone", "Item_Disc", "cd"));
        onYou.add(new Thing(70005, "Battery", "Item_Battery", "battery"));
        onYou.add(new Thing(70006, "Battery", "Item_Battery", "battery"));
        onYou.add(new Thing(70007, "Headphones", "Item_Headphones", "headphones"));
        shelves.add(new Thing(70101, "VHS: Home Invaders 2", "Item_Cassette3", "vhs"));
        shelves.add(new Thing(70102, "VHS: Man on the Run", "Item_Cassette3", "vhs"));
        shelves.add(new Thing(70103, "CD: Hidden", "Item_Disc", "cd"));
        shelves.add(new Thing(70104, "Battery", "Item_Battery", "battery"));
    }

    private static void preset(Device d, int frequency) {
        d.presets.add(new LinkedHashMap<>(Map.of("name", STATIONS.getOrDefault(frequency, frequency / 1000 + " MHz"), "frequency", frequency)));
    }

    /** Opens the device for console key [key]; false for an unknown key. */
    boolean open(String key) {
        open = devices.get(key);
        return open != null;
    }

    void close() {
        open = null;
    }

    private String fits(Device d, Thing t) {
        return switch (t.fits) {
            case "battery" -> d.batteryPowered ? "battery" : null;
            case "headphones" -> d.headphoneSlot ? "headphones" : null;
            default -> t.fits.equals(d.mediaType) ? "media" : null;
        };
    }

    private List<Map<String, Object>> rows(Device d, List<Thing> things) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Thing t : things) {
            String fit = fits(d, t);
            if (fit == null) continue;
            String key = fit.equals("media") ? String.valueOf(t.id) : t.name;
            Map<String, Object> row = byKey.get(key);
            if (row == null) {
                row = new LinkedHashMap<>(Map.of("id", t.id, "name", t.name, "icon", t.icon, "fits", fit));
                byKey.put(key, row);
                rows.add(row);
            } else {
                row.put("count", ((Number) row.getOrDefault("count", 1)).intValue() + 1);
            }
        }
        return rows;
    }

    /** The {@code device_state} data, or null with nothing open. */
    Map<String, Object> state() {
        Device d = open;
        if (d == null) return null;
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", d.name);
        s.put("icon", d.icon);
        if (d.where != null) s.put("where", d.where);
        s.put("on", d.on);
        if (d.tv) s.put("tv", true);
        s.put("volume", d.volume);
        s.put("channel", d.channel);
        if (STATIONS.containsKey(d.channel)) s.put("channelName", STATIONS.get(d.channel));
        s.put("presets", d.presets);
        if (d.min != null) {
            s.put("range", Map.of("min", d.min, "max", d.max));
            s.put("maxPresets", 10);
        }
        if (d.batteryPowered) {
            s.put("battery", Map.of("level", d.battery == null ? 0 : d.battery, "has", d.battery != null));
        } else {
            s.put("grid", Map.of("power", true, "text", "Has power."));
        }
        if (d.headphoneSlot) {
            Map<String, Object> phones = new LinkedHashMap<>(Map.of("slot", true));
            if (d.headphones != null) {
                phones.put("name", d.headphones);
                phones.put("icon", "Item_" + d.headphones);
            }
            s.put("headphones", phones);
        }
        if (d.transmit != null) s.put("twoWay", Map.of("range", d.transmit, "micMuted", d.micMuted));
        if (d.mediaType != null) {
            Map<String, Object> media = new LinkedHashMap<>(Map.of("type", d.mediaType, "playing", d.playing,
                "empty", d.mediaType.equals("vhs") ? "- NO TAPE -" : "- NO DISC -"));
            if (d.media != null) media.put("name", d.media);
            s.put("media", media);
        }
        List<Map<String, Object>> drawer = new ArrayList<>();
        List<Map<String, Object>> mine = rows(d, onYou);
        if (!mine.isEmpty()) drawer.add(Map.of("onYou", true, "items", mine));
        List<Map<String, Object>> near = rows(d, shelves);
        if (!near.isEmpty()) drawer.add(Map.of("name", "Shelves", "items", near));
        s.put("drawer", drawer);
        return s;
    }

    /** The thing with [id], left where it is, or null. */
    private Thing find(long id) {
        for (List<Thing> list : List.of(onYou, shelves)) {
            for (Thing t : list) {
                if (t.id == id) return t;
            }
        }
        return null;
    }

    private long nextId = 71000;

    /** A {@code device_control}; null when done, or the reason it can't be. */
    String control(Map<String, Object> args) {
        Device d = open;
        if (d == null) return "No device open";
        Object value = args.get("value");
        switch (String.valueOf(args.get("control"))) {
            case "power" -> d.on = !d.on;
            case "volume" -> d.volume = Math.max(0, Math.min(1, ((Number) value).doubleValue()));
            case "mute" -> d.volume = 0;
            case "unmute" -> d.volume = value instanceof Number n && n.doubleValue() > 0 ? n.doubleValue() : 0.1;
            case "channel" -> {
                d.playing = false;
                d.channel = ((Number) value).intValue();
            }
            case "play" -> {
                if (d.media == null || !d.on) return "Nothing to play";
                d.playing = !d.playing;
            }
            case "eject" -> {
                if (d.media == null) return "Nothing in it";
                onYou.add(new Thing(nextId++, d.media, d.mediaType.equals("vhs") ? "Item_Cassette3" : "Item_Disc", d.mediaType));
                d.media = null;
                d.playing = false;
            }
            case "battery_out" -> {
                if (d.battery == null) return "No battery in it";
                onYou.add(new Thing(nextId++, "Battery", "Item_Battery", "battery"));
                d.battery = null;
                d.on = false;
            }
            case "headphones_out" -> {
                if (d.headphones == null) return "No headphones in it";
                onYou.add(new Thing(nextId++, d.headphones, "Item_" + d.headphones, "headphones"));
                d.headphones = null;
            }
            case "insert" -> {
                Thing t = args.get("itemId") instanceof Number n ? find(n.longValue()) : null;
                String fit = t == null ? null : fits(d, t);
                if (fit == null) return "That doesn't fit";
                onYou.remove(t);
                shelves.remove(t);
                switch (fit) {
                    case "media" -> {
                        if (d.media != null) control(Map.of("control", "eject"));
                        d.media = t.name;
                    }
                    case "battery" -> {
                        if (d.battery != null) control(Map.of("control", "battery_out"));
                        d.battery = 1.0;
                    }
                    default -> {
                        if (d.headphones != null) control(Map.of("control", "headphones_out"));
                        d.headphones = t.name;
                    }
                }
            }
            case "mic" -> d.micMuted = Boolean.TRUE.equals(value);
            case "add_preset" -> {
                int f = ((Number) value).intValue();
                if (d.min == null || f < d.min || f > d.max) return "Out of range";
                if (d.presets.stream().noneMatch(p -> ((Number) p.get("frequency")).intValue() == f)) preset(d, f);
            }
            default -> {
                return "Unknown control";
            }
        }
        return null;
    }
}
