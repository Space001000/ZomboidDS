package dev.zomboidds.bridge.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake game windows (see PROTOCOL.md {@code question_state}, {@code info_state}, {@code exercise_state}):
 * the sleep and delete questions, a generator's, a plant's and a campfire's info, and the exercises.
 * An exercise counts one game minute per real second.
 */
final class MockGameWindows {
    private final Map<Long, Map<String, Object>> questions = new LinkedHashMap<>();
    private long nextQuestion = 1;
    private Map<String, Object> info;
    private String exercise;
    private int exerciseMinutes;
    private long exerciseStartMs;

    /** A new question ({@code s} sleep, {@code x} delete); its id, for the {@code show}. */
    long ask(String key) {
        long id = nextQuestion++;
        String text = "s".equals(key) ? "Are you sure you want to sleep?" : "Delete all these items?";
        questions.put(id, Map.of("id", id, "text", text, "buttons",
                List.of(Map.of("id", "yes", "label", "Yes"), Map.of("id", "no", "label", "No"))));
        return id;
    }

    Map<String, Object> question(Object id) {
        Map<String, Object> q = id instanceof Number n ? questions.get(n.longValue()) : null;
        return q != null ? q : Map.of("gone", true);
    }

    String answer(Map<String, Object> args) {
        Object id = args.get("id");
        if (!(id instanceof Number n) || questions.remove(n.longValue()) == null) return "That question was already answered";
        return null;
    }

    private static Map<String, Object> row(String label, String value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        if (value != null) row.put("value", value);
        return row;
    }

    /** Info on the generator ({@code g}), a plant ({@code p}) or a campfire ({@code f}); false for another key. */
    boolean openInfo(String key) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> card = new LinkedHashMap<>();
        switch (key) {
            case "g" -> {
                card.put("kind", "generator");
                card.put("title", "Gas-powered Generator");
                card.put("icon", "appliances_misc_01_0_Icon");
                Map<String, Object> fuel = row("Fuel", "63%");
                fuel.put("meter", 0.63);
                Map<String, Object> condition = row("Condition", "88%");
                condition.put("meter", 0.88);
                rows.add(fuel);
                rows.add(condition);
                rows.add(row("Power Consumption", null));
                for (String[] thing : new String[][] {{"Lights x6", "0.0012 L/h"}, {"Green Fridge x1", "0.08 L/h"},
                        {"Green Oven x1", "0.09 L/h"}, {"Gas-powered Generator", "0.02 L/h"}}) {
                    Map<String, Object> r = row(thing[0], thing[1]);
                    r.put("indent", true);
                    rows.add(r);
                }
                rows.add(row("Total", "0.1912 L/h"));
                rows.add(Map.of("text", "Will fill the building with toxic fumes when operated here.", "tone", "bad"));
            }
            case "p" -> {
                card.put("kind", "plant");
                card.put("title", "Tomato Vine");
                card.put("icon", "Item_Tomato");
                card.put("status", "Plant Information");
                rows.add(row("Next growth stage", "2 days"));
                rows.add(row("Compost", "No"));
                rows.add(row("Disease", null));
                Map<String, Object> aphids = row("Aphids", "Moderate");
                aphids.put("icon", "Item_Insect_Aphid");
                aphids.put("tone", "bad");
                aphids.put("indent", true);
                rows.add(aphids);
                Map<String, Object> water = row("Water level", "Fine");
                water.put("meter", 0.58);
                water.put("water", true);
                rows.add(water);
            }
            case "f" -> {
                card.put("kind", "campfire");
                card.put("title", "Campfire");
                card.put("icon", "camping_01_6_Icon");
                rows.add(row("Fuel", "1 hour 40 minutes (burning)"));
            }
            default -> {
                return false;
            }
        }
        card.put("rows", rows);
        info = card;
        return true;
    }

    Map<String, Object> info() {
        return info != null ? info : Map.of("gone", true);
    }

    void closeInfo() {
        info = null;
    }

    private static Map<String, Object> exerciseType(String id, String name, String tooltip, String icon, String needs, double regularity, String strain) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("name", name);
        e.put("tooltip", tooltip);
        if (icon != null) e.put("icon", icon);
        if (needs != null) e.put("needs", needs);
        e.put("regularity", regularity);
        e.put("strain", List.of(strain));
        return e;
    }

    Map<String, Object> exercise() {
        String arms = "Muscle strain will be felt on arms, reducing combat capabilities.";
        String legs = "Muscle strain will be felt on legs, reducing movement speed.";
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("exercises", List.of(
                exerciseType("barbellcurl", "Barbell Curls", "Improves strength when performed regularly.", "Item_Barbell", "(Requires Barbell)", 0, arms),
                exerciseType("bicepscurl", "Bicep Curls", "Improves strength when performed regularly.", "Item_Dumbbell", null, 0.05, arms),
                exerciseType("burpees", "Burpees", "Improves strength and fitness when performed regularly. Will drain endurance a lot.", null, null, 0.1, legs),
                exerciseType("dumbbellpress", "Dumbbell Presses", "Improves strength when performed regularly.", "Item_Dumbbell", null, 0.15, arms),
                exerciseType("pushups", "Push-ups", "Improves strength when performed regularly.", null, null, 0.6, arms),
                exerciseType("situp", "Sit-ups", "Improves fitness when performed regularly.", null, null, 0.2,
                        "General exercise fatigue will be felt, increasing clumsiness."),
                exerciseType("squats", "Squats", "Improves fitness when performed regularly.", null, null, 0.35, legs)));
        state.put("canStart", true);
        state.put("minutes", Map.of("min", 10, "max", 60, "step", 10));
        if (exercise != null) {
            int done = (int) ((System.currentTimeMillis() - exerciseStartMs) / 1000);
            if (done >= exerciseMinutes) {
                exercise = null;
            } else {
                state.put("running", Map.of("id", exercise, "minutes", exerciseMinutes, "done", done));
            }
        }
        return state;
    }

    String startExercise(Map<String, Object> args) {
        if ("barbellcurl".equals(args.get("id"))) return "(Requires Barbell)";
        if (!(args.get("minutes") instanceof Number m)) return "10 to 60 minutes";
        exercise = String.valueOf(args.get("id"));
        exerciseMinutes = m.intValue();
        exerciseStartMs = System.currentTimeMillis();
        return null;
    }

    void stopExercise() {
        exercise = null;
    }
}
