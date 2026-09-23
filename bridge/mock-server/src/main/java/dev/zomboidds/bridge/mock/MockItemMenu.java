package dev.zomboidds.bridge.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A stand-in for the game's item context menu (see PROTOCOL.md, "The game's item menu"): enough
 * variety to develop and test the app's menu UI: plain options, a submenu, a greyed-out option
 * with a reason. Only the latest menu is valid, and only once, like in the B42 adapter.
 */
final class MockItemMenu {

    private String currentId;
    private Map<String, Supplier<String>> currentActions = Map.of();
    private int nextId;

    /** Builds a menu for {@code item}; {@code remove} takes the item out of the inventory. */
    Map<String, Object> open(Map<String, Object> item, Runnable remove) {
        Map<String, Supplier<String>> actions = new LinkedHashMap<>();
        List<Object> options = new ArrayList<>();
        String category = String.valueOf(item.get("category"));
        int n = 0;

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
                options.add(option(String.valueOf(++n), category.equals("Water") ? "Drink" : "Eat", true, null, portions));
            }
            case "Literature" -> {
                String id = String.valueOf(++n);
                options.add(option(id, "Read", true, null, null));
                actions.put(id, () -> null);
            }
            case "FirstAid" -> {
                String id = String.valueOf(++n);
                options.add(option(id, "Apply", true, null, null));
                actions.put(id, () -> {
                    remove.run();
                    return null;
                });
            }
            default -> { }
        }
        options.add(option(String.valueOf(++n), "Rename", false, "Only in the real game", null));
        String drop = String.valueOf(++n);
        options.add(option(drop, "Drop", true, null, null));
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

    /** Runs an option; returns an error message, or null on success. */
    String select(Object menuId, Object optionId) {
        if (currentId == null || !currentId.equals(String.valueOf(menuId))) {
            return "This menu is out of date; tap the item again";
        }
        Supplier<String> action = currentActions.get(String.valueOf(optionId));
        currentId = null;
        return action == null ? "That option isn't available" : action.get();
    }

    private static Map<String, Object> withIcon(Map<String, Object> option, String icon) {
        option.put("icon", icon);
        return option;
    }

    private static Map<String, Object> option(String id, String name, boolean enabled, String tooltip, List<Object> children) {
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
