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

    /** Steps of a walk through a kitchen: the objects around you (in the game's order) and the one in front. */
    private static final String[][] WALK = {
        {"window", "sink", "|window"},
        {"window", "sink", "oven", "|oven"},
        {"microwave", "oven", "sink", "window", "|microwave"},
        {"microwave", "oven", "sink", "window", "|oven"},
        {"door", "window", "microwave", "|door"},
    };

    /** Which step of [WALK] the mock is at: the back door (with the door the tests use) until MockGame walks on. */
    int walkStep = WALK.length - 1;

    /**
     * The world menu for where the player stands ("Here"), shaped like the game's: objects with a
     * submenu of actions (keyed by where they are, one in front), a greyed-out action with the
     * game's reason, Disassemble as a list of objects, and a loose action at the end.
     */
    Map<String, Object> openWorld() {
        Map<String, Supplier<String>> actions = new LinkedHashMap<>();
        List<Object> options = new ArrayList<>();
        String[] step = WALK[walkStep % WALK.length];
        String front = step[step.length - 1].substring(1);
        int n = 0;
        for (int i = 0; i < step.length - 1; i++) {
            String id = String.valueOf(++n);
            List<Object> children = new ArrayList<>();
            Map<String, Object> card = switch (step[i]) {
                case "window" -> {
                    for (String a : new String[] {"Open Window", "Smash Window", "Close Curtains", "Remove Curtains", "Climb through"}) {
                        children.add(option(id + "." + (children.size() + 1), a, true, null, null));
                    }
                    yield withIcon(option(id, "Window", true, null, children), "fixtures_windows_01_17_Icon");
                }
                case "sink" -> {
                    children.add(option(id + ".1", "Drink", true, null, null));
                    children.add(option(id + ".2", "Fill", true, null, List.of(
                        option(id + ".2.1", "Water Bottle", true, null, null), option(id + ".2.2", "Cooking Pot", true, null, null))));
                    yield withIcon(option(id, "Chrome Sink", true, null, children), "fixtures_sinks_01_11_Icon");
                }
                case "oven" -> {
                    children.add(option(id + ".1", ovenOn ? "Turn off" : "Turn on", true, null, null));
                    actions.put(id + ".1", () -> {
                        ovenOn = !ovenOn;
                        return null;
                    });
                    children.add(option(id + ".2", "Settings", true, null, null));
                    yield withIcon(option(id, "Green Oven", true, null, children), "appliances_cooking_01_0_Icon");
                }
                case "microwave" -> {
                    children.add(option(id + ".1", "Turn on", false, "There's no power", null));
                    children.add(option(id + ".2", "Settings", true, null, null));
                    yield withIcon(option(id, "Chrome Microwave", true, null, children), "appliances_cooking_01_28_Icon");
                }
                default -> {
                    children.add(option(id + ".1", doorOpen ? "Close Door" : "Open Door", true, null, null));
                    actions.put(id + ".1", () -> {
                        doorOpen = !doorOpen;
                        return null;
                    });
                    children.add(option(id + ".2", "Lock Door", false, "You need the key", null));
                    yield withIcon(option(id, "Door", true, null, children), "fixtures_doors_01_28_Icon");
                }
            };
            for (Object child : children) {
                @SuppressWarnings("unchecked") Map<String, Object> c = (Map<String, Object>) child;
                if (Boolean.TRUE.equals(c.get("enabled")) && c.get("children") == null) {
                    actions.putIfAbsent((String) c.get("id"), () -> null);
                }
            }
            card.put("key", step[i]);
            if (step[i].equals(front)) {
                card.put("front", true);
            }
            options.add(card);
        }
        String id = String.valueOf(++n);
        List<Object> parts = new ArrayList<>();
        String[] scrap = {"Green Oven", "Air Conditioner", "Rough Wooden Corner Counter", "Chrome Toaster", "Coffee X-press"};
        for (String name : scrap) {
            boolean can = name.equals("Chrome Toaster");
            String partId = id + "." + (parts.size() + 1);
            parts.add(option(partId, name, can, can ? null : "Needs a screwdriver", null));
            if (can) {
                actions.put(partId, () -> null);
            }
        }
        Map<String, Object> disassemble = withIcon(option(id, "Disassemble", true, null, parts), "Item_Hammer");
        disassemble.put("key", "list:Disassemble");
        disassemble.put("tray", true);
        options.add(disassemble);
        String sit = String.valueOf(++n);
        Map<String, Object> sitOption = option(sit, "Sit on ground", true, null, null);
        sitOption.put("key", "name:Sit on ground");
        options.add(sitOption);
        actions.put(sit, () -> null);
        currentId = "m" + (++nextId);
        currentActions = actions;
        Map<String, Object> menu = new LinkedHashMap<>();
        menu.put("menuId", currentId);
        menu.put("options", options);
        return menu;
    }

    private boolean ovenOn;

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
