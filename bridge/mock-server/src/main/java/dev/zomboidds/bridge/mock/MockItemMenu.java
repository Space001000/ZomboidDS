package dev.zomboidds.bridge.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A stand-in for the game's item context menu (see PROTOCOL.md, "The game's item menu"): enough
 * variety to develop and test the app's menu UI: plain options, a submenu, a greyed-out option
 * with a reason, and the pills per kind of item (equip and attach for weapons, eat, wear, read,
 * apply, drop). Only the latest menu is valid, and only once, like in the B42 adapter.
 */
final class MockItemMenu {

    private String currentId;
    private Map<String, Supplier<String>> currentActions = Map.of();
    private int nextId;
    /** Inspect on a garment: the game would show it (MockGame sends the show event). */
    java.util.function.Consumer<Map<String, Object>> inspect = item -> { };

    /** Builds a menu for {@code item}; {@code remove} takes the item out of the inventory. */
    Map<String, Object> open(Map<String, Object> item, Runnable remove) {
        Map<String, Supplier<String>> actions = new LinkedHashMap<>();
        List<Object> options = new ArrayList<>();
        String category = String.valueOf(item.get("category"));
        int n = 0;

        if (item.get("equipped") != null) {
            String id = String.valueOf(++n);
            options.add(pill(option(id, "Unequip", true, null, null), "action"));
            actions.put(id, () -> null);
        }
        switch (category) {
            case "Food", "Water" -> {
                List<Object> portions = new ArrayList<>();
                int i = 0;
                for (String portion : List.of("All", "Half", "Quarter")) {
                    String id = (n + 1) + "." + (++i);
                    portions.add(option(id, portion, true, null, null));
                    actions.put(id, () -> {
                        remove.run();
                        return null;
                    });
                }
                options.add(pill(option(String.valueOf(++n), category.equals("Water") ? "Drink" : "Eat", true, null, portions), "action"));
            }
            case "Literature" -> {
                String id = String.valueOf(++n);
                options.add(pill(option(id, "Read", true, null, null), "action"));
                actions.put(id, () -> null);
            }
            case "FirstAid" -> {
                List<Object> parts = new ArrayList<>();
                int i = 0;
                for (String part : List.of("Head", "Left Hand", "Right Forearm")) {
                    String id = (n + 1) + "." + (++i);
                    parts.add(option(id, part, true, null, null));
                    actions.put(id, () -> {
                        remove.run();
                        return null;
                    });
                }
                options.add(pill(option(String.valueOf(++n), "Apply Bandage", true, null, parts), "action"));
            }
            case "Clothing" -> {
                String id = String.valueOf(++n);
                options.add(pill(option(id, "Wear", true, null, null), "action"));
                actions.put(id, () -> null);
                String inspectId = String.valueOf(++n);
                options.add(pill(option(inspectId, "Inspect", true, null, null), "action"));
                actions.put(inspectId, () -> {
                    inspect.accept(item);
                    return null;
                });
            }
            case "Weapon" -> {
                for (String equip : List.of("Equip Primary", "Equip Secondary", "Equip Two Hands")) {
                    String id = String.valueOf(++n);
                    options.add(pill(option(id, equip, true, null, null), "action"));
                    actions.put(id, () -> null);
                }
                List<Object> slots = new ArrayList<>();
                int i = 0;
                for (String slot : List.of("Belt Right", "Back")) {
                    String id = (n + 1) + "." + (++i);
                    slots.add(option(id, slot, true, null, null));
                    actions.put(id, () -> null);
                }
                options.add(pill(option(String.valueOf(++n), "Attach", true, null, slots), "action"));
            }
            default -> { }
        }
        String favorite = String.valueOf(++n);
        options.add(option(favorite, "Add to Favorites", true, null, null));
        actions.put(favorite, () -> null);
        options.add(option(String.valueOf(++n), "Rename", false, "Only in the real game", null));
        String drop = String.valueOf(++n);
        options.add(pill(option(drop, "Drop", true, null, null), "drop"));
        actions.put(drop, () -> {
            remove.run();
            return null;
        });

        currentId = "m" + (++nextId);
        currentActions = actions;
        Map<String, Object> menu = new LinkedHashMap<>();
        menu.put("menuId", currentId);
        menu.put("options", options);
        return menu;
    }

    private boolean doorOpen;

    /**
     * The world menu for where the player stands ("Here"), shaped like the game's: objects with a
     * submenu of actions, a greyed-out action with the game's reason, and a loose action at the end.
     */
    Map<String, Object> openWorld() {
        Map<String, Supplier<String>> actions = new LinkedHashMap<>();
        List<Object> options = new ArrayList<>();
        List<Object> window = new ArrayList<>();
        for (String[] w : new String[][] {{"1.1", "Open Window"}, {"1.2", "Smash Window"}, {"1.3", "Open Curtains"}, {"1.4", "Remove Curtains"}}) {
            window.add(option(w[0], w[1], true, null, null));
            actions.put(w[0], () -> null);
        }
        options.add(withIcon(option("1", "Window", true, null, window), "fixtures_windows_01_17_Icon"));
        List<Object> door = new ArrayList<>();
        door.add(option("2.1", doorOpen ? "Close Door" : "Open Door", true, null, null));
        actions.put("2.1", () -> {
            doorOpen = !doorOpen;
            return null;
        });
        door.add(option("2.2", "Lock Door", false, "You need the key", null));
        options.add(withIcon(option("2", "Door", true, null, door), "fixtures_sinks_01_11_Icon"));
        List<Object> light = new ArrayList<>();
        light.add(option("3.1", "Turn on", false, "There's no power", null));
        light.add(option("3.2", "Remove Light Bulb", true, null, null));
        actions.put("3.2", () -> null);
        options.add(withIcon(option("3", "Fluorescent Wall Light", true, null, light), "appliances_cooking_01_30_Icon"));
        options.add(option("4", "Sit on ground", true, null, null));
        actions.put("4", () -> null);
        currentId = "m" + (++nextId);
        currentActions = actions;
        Map<String, Object> menu = new LinkedHashMap<>();
        menu.put("menuId", currentId);
        menu.put("options", options);
        return menu;
    }

    /** The treatment menu for a body part: one option, one greyed out with the game's reason. */
    Map<String, Object> openHealth() {
        Map<String, Supplier<String>> actions = new LinkedHashMap<>();
        List<Object> options = new ArrayList<>();
        options.add(option("1", "Apply Bandage", true, null, null));
        actions.put("1", () -> null);
        options.add(option("2", "Disinfect", false, "Requires a disinfectant", null));
        currentId = "m" + (++nextId);
        currentActions = actions;
        Map<String, Object> menu = new LinkedHashMap<>();
        menu.put("menuId", currentId);
        menu.put("options", options);
        return menu;
    }

    /** A menu built elsewhere (the tailoring menu), under the same rules: only the latest, once. */
    Map<String, Object> register(List<Object> options, Map<String, Supplier<String>> actions) {
        currentId = "m" + (++nextId);
        currentActions = actions;
        Map<String, Object> menu = new LinkedHashMap<>();
        menu.put("menuId", currentId);
        menu.put("options", options);
        return menu;
    }

    /** Runs an option; returns an error message, or null on success. */
    String select(Object menuId, Object optionId) {
        if (currentId == null || !currentId.equals(String.valueOf(menuId))) {
            return "This menu is out of date; tap the item again";
        }
        Supplier<String> action = currentActions.get(String.valueOf(optionId));
        currentId = null;
        return action == null ? "That option isn't available" : action.get();
    }

    private static Map<String, Object> pill(Map<String, Object> option, String kind) {
        option.put("pill", kind);
        return option;
    }

    private static Map<String, Object> withIcon(Map<String, Object> option, String icon) {
        option.put("icon", icon);
        return option;
    }

    static Map<String, Object> option(String id, String name, boolean enabled, String tooltip, List<Object> children) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("id", id);
        option.put("name", name);
        option.put("enabled", enabled);
        if (tooltip != null) {
            option.put("tooltip", tooltip);
        }
        if (children != null) {
            option.put("children", children);
        }
        return option;
    }
}
