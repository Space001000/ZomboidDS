package dev.zomboidds.bridge.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake ovens for the oven panel (see PROTOCOL.md {@code oven_state}): a Chrome Oven next to the
 * Oven container ({@code c6}, with a steak in it) and a microwave, the empty Microwave container ({@code c7}). Their knobs stop on 42.21's marks;
 * a running timer counts down one game minute per real second.
 */
final class MockOvens {
    private static final int[] OVEN_TEMPS = {0, 50, 100, 150, 200, 250, 300};
    private static final int[] OVEN_TIMER = {0, 1, 2, 3, 4, 5, 10, 15, 20, 25, 30, 40, 50, 60, 90, 120};
    private static final int[][] MICROWAVE_TEMPS = {{0, 90}, {45, 110}, {90, 130}, {270, 50}, {315, 70}};
    private static final int[] MICROWAVE_TIMER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 25, 30, 45, 60};

    private static final class Oven {
        final boolean microwave;
        final String name;
        final String icon;
        final String container;
        int temperature;
        int timer;
        boolean on;
        long startedMs;

        Oven(boolean microwave, String name, String icon, String container, int temperature, int timer) {
            this.microwave = microwave;
            this.name = name;
            this.icon = icon;
            this.container = container;
            this.temperature = temperature;
            this.timer = timer;
        }
    }

    private final Map<String, Oven> ovens = Map.of(
            "o", new Oven(false, "Chrome Oven", "appliances_cooking_01_20_Icon", "c6", 200, 30),
            "m", new Oven(true, "White Microwave", "appliances_cooking_01_24_Icon", "c7", 110, 7));
    private Oven open;
    private boolean celsius;

    /** "Settings" on the oven ({@code o}) or the microwave ({@code m}); false for another key. */
    boolean open(String key) {
        Oven oven = ovens.get(key);
        if (oven == null) return false;
        open = oven;
        return true;
    }

    void close() {
        open = null;
    }

    private static List<Map<String, Object>> marks(int[] values) {
        List<Map<String, Object>> marks = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            marks.add(new LinkedHashMap<>(Map.of("angle", i * (values.length > 7 ? 18 : 45), "value", values[i])));
        }
        return marks;
    }

    private String temperatureText(Oven oven, int value) {
        if (oven.microwave) {
            int below = 0;
            for (int[] mark : MICROWAVE_TEMPS) if (mark[1] < value) below++;
            return (below + 1) + " of " + MICROWAVE_TEMPS.length;
        }
        return celsius ? String.valueOf(value) : String.valueOf(value == 0 ? 0 : value + 200);
    }

    private int left(Oven oven) {
        long minutes = (System.currentTimeMillis() - oven.startedMs) / 1000;
        return (int) Math.max(0, oven.timer - minutes);
    }

    /** The {@code oven_state} data, or null with nothing open. */
    Map<String, Object> state() {
        Oven oven = open;
        if (oven == null) return null;
        if (oven.on && oven.timer > 0 && left(oven) == 0) {
            if (oven.microwave) oven.on = false; // a microwave stops; an oven only rings
            oven.timer = 0;
        }
        List<Map<String, Object>> tempMarks = new ArrayList<>();
        if (oven.microwave) {
            for (int[] mark : MICROWAVE_TEMPS) tempMarks.add(new LinkedHashMap<>(Map.of("angle", mark[0], "value", mark[1])));
        } else {
            tempMarks = marks(OVEN_TEMPS);
        }
        tempMarks.forEach(m -> m.put("label", temperatureText(oven, ((Number) m.get("value")).intValue())));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", oven.name);
        state.put("icon", oven.icon);
        if (oven.microwave) state.put("microwave", true);
        state.put("on", oven.on);
        state.put("powered", true);
        if (oven.container != null) state.put("container", oven.container);
        state.put("temperature", oven.temperature);
        state.put("temperatureText", temperatureText(oven, oven.temperature));
        if (!oven.microwave) state.put("celsius", celsius);
        state.put("timer", oven.timer);
        if (oven.on && oven.timer > 0) state.put("left", left(oven));
        state.put("dial", "KnobDial");
        state.put("temperatureDial", Map.of("art", oven.microwave ? "KnobBGMicrowaveTemp" : celsius ? "KnobBGCelciusOvenTemp" : "KnobBGFarhenOvenTemp",
                "marks", tempMarks));
        state.put("timerDial", Map.of("art", oven.microwave ? "KnobBGMicrowaveTimer" : "KnobBGOvenTimer",
                "marks", marks(oven.microwave ? MICROWAVE_TIMER : OVEN_TIMER)));
        return state;
    }

    private static boolean isMark(int[] values, int value) {
        for (int v : values) if (v == value) return true;
        return false;
    }

    /** An {@code oven_control}; null when done, or the reason it can't be. */
    String control(Map<String, Object> args) {
        Oven oven = open;
        if (oven == null) return "No oven open";
        switch (String.valueOf(args.get("control"))) {
            case "set" -> {
                if (args.get("temperature") instanceof Number t) {
                    int[] temps = oven.microwave ? Arrays.stream(MICROWAVE_TEMPS).mapToInt(m -> m[1]).toArray() : OVEN_TEMPS;
                    if (!isMark(temps, t.intValue())) return "Not a setting of this knob";
                    oven.temperature = t.intValue();
                }
                if (args.get("timer") instanceof Number t) {
                    if (!isMark(oven.microwave ? MICROWAVE_TIMER : OVEN_TIMER, t.intValue())) return "Not a setting of this knob";
                    oven.timer = t.intValue();
                    oven.startedMs = System.currentTimeMillis();
                }
                return null;
            }
            case "toggle" -> {
                if (oven.on && oven.timer > 0) oven.timer = left(oven);
                oven.on = !oven.on;
                oven.startedMs = System.currentTimeMillis();
                return null;
            }
            case "units" -> {
                if (oven.microwave) return "A microwave has no degrees";
                celsius = Boolean.TRUE.equals(args.get("celsius"));
                return null;
            }
            default -> {
                return "Unknown control";
            }
        }
    }
}
