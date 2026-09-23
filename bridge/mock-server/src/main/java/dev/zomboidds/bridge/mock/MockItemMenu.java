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

    /** The world menu for where the player stands ("Here"): a chair, a door, a light switch. */
    Map<String, Object> openWorld() {
        Map<String, Supplier<String>> actions = new LinkedHashMap<>();
        List<Object> options = new ArrayList<>();
        options.add(option("1", "Sit on chair", true, null, null));
        actions.put("1", () -> null);
        options.add(option("2", doorOpen ? "Close door" : "Open door", true, null, null));
        actions.put("2", () -> {
            doorOpen = !doorOpen;
            return null;
        });
        options.add(option("3", "Turn on light", false, "There's no power", null));
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
