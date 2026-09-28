package dev.zomboidds.bridge.mock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import dev.zomboidds.bridge.Log;
import dev.zomboidds.bridge.port.BridgeContext;
import dev.zomboidds.bridge.protocol.Command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A tiny fake game. Like the real one, all state lives on a single "game thread" that ticks at
 * 10 Hz, drains commands and publishes whatever changed. That way the threading model the app sees
 * matches the real bridge.
 */
final class MockGame {

    private static final Gson GSON = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();

    private final BridgeContext bridge;
    private final ScheduledExecutorService gameThread = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mock-game");
        t.setDaemon(true);
        return t;
    });

    // State below is only touched on gameThread.
    private final Map<String, Object> inventory;
    private final Map<String, Object> player = fixtureData("player.json");
    private final Map<String, Object> vehicle = fixtureData("vehicle_driving.json");
    private final MockItemMenu menu = new MockItemMenu();
    /** From containers.json; the "inventory" entry's items live in {@link #inventory}. */
    private final List<Map<String, Object>> containers = containersFixture();
    /** Containers that are left behind when you walk away ("w"): not on the player, not the floor. */
    private final List<Map<String, Object>> furniture = new ArrayList<>();
    private boolean driving;
    private long nextItemId = 20000;
    private long tick;
    private boolean inventoryDirty = true;
    private boolean playerDirty = true;
    private boolean containersDirty = true;
    /** The game's speed button, 0 pause ... 4 wait (see PROTOCOL.md "time"). */
    private int speed = 1;
    private boolean timeDirty = true;
    private boolean watchingHere;
    private boolean hereDirty;
    /** The Command deck (deck.json); modes flip when run. */
    private final Map<String, Object> deck = fixtureData("deck.json");
    private boolean deckDirty = true;

    MockGame(BridgeContext bridge, String inventoryFixture) {
        this.bridge = bridge;
        this.inventory = fixtureData(inventoryFixture);
    }

    /** The fake crafting window: the list, and each recipe's details by id (see PROTOCOL.md "Crafting"). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> crafting() {
        Map<String, Object> list = (Map<String, Object>) fixtureData("craft_list_result.json").get("data");
        Map<String, Object> byId = new LinkedHashMap<>();
        byId.put("list", list);
        for (Object entry : (List<Object>) list.get("recipes")) {
            Map<String, Object> recipe = (Map<String, Object>) entry;
            boolean can = Boolean.TRUE.equals(recipe.get("canCraft"));
            Map<String, Object> details = new LinkedHashMap<>(recipe);
            details.put("seconds", 4);
            details.put("max", can ? 4 : 0);
            details.put("inputs", List.of(Map.of("name", "T-shirt", "icon", "Item_TshirtGeneric", "need", 1, "have", can ? 4 : 0, "ok", can)));
            details.put("outputs", List.of(Map.of("name", recipe.get("name"), "icon", String.valueOf(recipe.get("icon")), "amount", 1)));
            details.put("skills", List.of());
            byId.put((String) recipe.get("id"), details);
        }
        Map<String, Object> axe = (Map<String, Object>) fixtureData("craft_recipe_result.json").get("data");
        byId.put((String) axe.get("id"), axe);
        return byId;
    }

    void start() {
        gameThread.execute(() -> {
            bridge.state().publish("session", fixtureData("session.json"));
            bridge.state().publish("health", fixtureData("health.json"));
            bridge.state().publish("moodles", fixtureData("moodles.json"));
        });
        gameThread.scheduleAtFixedRate(this::tick, 0, 100, TimeUnit.MILLISECONDS);
    }

    /** Blocks, reading keyboard commands that change the fake game. */
    void runConsole() throws IOException, InterruptedException {
        printHelp();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            String cmd = line.trim().toLowerCase();
            switch (cmd) {
                case "v" -> gameThread.execute(() -> { driving = !driving; Log.info("driving: " + driving); });
                case "d" -> gameThread.execute(() -> adjustHealth(-15));
                case "h" -> gameThread.execute(() -> adjustHealth(+15));
                case "i" -> gameThread.execute(this::addRandomItem);
                case "w" -> gameThread.execute(this::walk);
                case "q" -> { return; }
                case "" -> { }
                default -> printHelp();
            }
        }
        // No console (e.g. started from an IDE without stdin): keep serving until killed.
        Thread.currentThread().join();
    }

    private static void printHelp() {
        System.out.println("""
                Mock game running. Commands:
                  v  enter/leave vehicle     d  take damage     h  heal
                  i  add an item             w  walk away from / back to the furniture
                  q  quit""");
    }

    // --- game thread ---

    private void tick() {
        try {
            tick++;
            for (Command command : bridge.commands().drain(8)) {
                execute(command);
            }
            if (inventoryDirty) {
                // Publish copies: the sender thread serializes them while we keep mutating.
                bridge.state().publish("inventory", withActions(deepCopy(inventory)));
                inventoryDirty = false;
            }
            if (containersDirty) {
                bridge.state().publish("containers", Map.of("containers", containerSnapshot()));
                containersDirty = false;
            }
            if (hereDirty) {
                Map<String, Object> here = new LinkedHashMap<>();
                here.put("watching", watchingHere);
                if (watchingHere) {
                    here.putAll(menu.openWorld());
                }
                bridge.state().publish("here", here);
                hereDirty = false;
            }
            if (deckDirty) {
                bridge.state().publish("deck", deepCopy(deck));
                deckDirty = false;
            }
            if (timeDirty) {
                bridge.state().publish("time", Map.of("speed", speed, "canChange", true));
                timeDirty = false;
            }
            if (playerDirty) {
                bridge.state().publish("player", deepCopy(player));
                playerDirty = false;
            }
            publishVehicle();
        } catch (RuntimeException e) {
            Log.error("mock tick failed", e);
        }
    }

    private void publishVehicle() {
        if (driving) {
            double speed = 45 + 35 * Math.sin(tick / 25.0);
            vehicle.put("speedKmh", Math.round(speed * 10) / 10.0);
            vehicle.put("fuel", Math.max(0, 0.8 - tick / 20000.0));
            bridge.state().publish("vehicle", new LinkedHashMap<>(vehicle));
        } else if (tick % 10 == 0) {
            bridge.state().publish("vehicle", Map.of("inVehicle", false));
        }
    }

    private void execute(Command command) {
        Object[] data = new Object[1];
        String error = switch (command.name()) {
            case "equip" -> equip(command);
            case "wear" -> setEquipped(command, "worn");
            case "unequip" -> setEquipped(command, null);
            case "drop" -> drop(command);
            case "item_menu" -> {
                // Like the adapter: items you carry and items in containers within reach; several
                // picked together (`itemIds`) get the first one's menu, acting on all of them.
                List<?> ids = command.args().get("itemIds") instanceof List<?> list ? list : List.of(command.args().getOrDefault("itemId", ""));
                List<Map<String, Object>> picked = new ArrayList<>();
                for (Object id : ids) {
                    Map<String, Object> item = id instanceof Number n ? findAnywhere(n.longValue()) : null;
                    if (item == null) {
                        break;
                    }
                    picked.add(item);
                }
                if (picked.isEmpty() || picked.size() < ids.size()) {
                    yield "item not found";
                }
                data[0] = menu.open(picked.get(0), () -> {
                    items().removeAll(picked);
                    containers.forEach(c -> itemsOf(c).removeAll(picked));
                    inventoryDirty = true;
                    containersDirty = true;
                });
                yield null;
            }
            case "watch_here" -> {
                watchingHere = !Boolean.FALSE.equals(command.args().get("on"));
                hereDirty = true;
                yield null;
            }
            case "health_menu" -> {
                if (speed == 0) {
                    yield "The game is paused"; // like the game: no treatment menu while paused
                }
                data[0] = menu.openHealth();
                yield null;
            }
            case "world_menu" -> {
                data[0] = menu.openWorld();
                yield null;
            }
            case "menu_select" -> {
                String failed = menu.select(command.args().get("menuId"), command.args().get("optionId"));
                hereDirty = true; // like the adapter: what's here may have changed
                yield failed;
            }
            case "transfer" -> transfer(command);
            case "select_container" -> {
                Map<String, Object> target = reachableContainer(command.args().get("id"));
                if (target == null || Boolean.TRUE.equals(target.get("locked"))) {
                    yield "That container is out of reach";
                }
                containers.forEach(c -> c.remove("selected"));
                target.put("selected", true);
                containersDirty = true;
                yield null;
            }
            case "craft_list" -> {
                data[0] = crafting().get("list");
                yield null;
            }
            case "craft_recipe" -> {
                Object details = crafting().get(String.valueOf(command.args().get("recipe")));
                if (details == null) {
                    yield "That recipe isn't available";
                }
                data[0] = details;
                yield null;
            }
            case "craft" -> {
                Object details = crafting().get(String.valueOf(command.args().get("recipe")));
                if (!(details instanceof Map<?, ?> recipe) || !Boolean.TRUE.equals(recipe.get("canCraft"))) {
                    yield "You can't make that right now";
                }
                yield null;
            }
            case "set_speed" -> {
                Object value = command.args().get("speed");
                if (!(value instanceof Number n) || n.intValue() < 0 || n.intValue() > 4) {
                    yield "unknown speed " + value;
                }
                speed = n.intValue();
                timeDirty = true;
                yield null;
            }
            case "deck_run" -> runDeckCommand(String.valueOf(command.args().get("id")), command.args());
            case "transfer_all" -> transferAll(command);
            default -> "unknown command '" + command.name() + "'";
        };
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", command.id());
        result.put("ok", error == null);
        result.put("error", error);
        if (data[0] != null) {
            result.put("data", data[0]);
        }
        bridge.state().publish("command_result", result);
        Log.info("command " + command.name() + " " + command.args() + " -> " + (error == null ? "ok" : error));
    }

    /** Like the game's key bindings: modes switch on and off, the rest just "happens" (logged). */
    @SuppressWarnings("unchecked")
    private String runDeckCommand(String id, Map<String, Object> args) {
        if (id.equals("weapons")) {
            int slot = args.get("slot") instanceof Number n ? n.intValue() : -1;
            Map<String, Object> target = null;
            for (Object entry : (List<Object>) deck.get("hotbar")) {
                Map<String, Object> s = (Map<String, Object>) entry;
                if (s.get("slot") instanceof Number n && n.intValue() == slot) target = s;
            }
            if (target == null || target.get("item") == null) {
                return "That changed in the game; try again";
            }
            boolean wasInHand = Boolean.TRUE.equals(target.get("inHand"));
            for (Object entry : (List<Object>) deck.get("hotbar")) {
                ((Map<String, Object>) entry).put("inHand", false);
            }
            target.put("inHand", !wasInHand); // drawing the one in hand puts it away
            deckDirty = true;
            return null;
        }
        if (id.equals("alarm")) {
            Object hour = args.get("hour"), minute = args.get("minute");
            if (!(hour instanceof Number h) || !(minute instanceof Number mi) || h.intValue() > 23 || mi.intValue() > 59) {
                return "That changed in the game; try again";
            }
            Map<String, Object> alarm = (Map<String, Object>) deck.get("alarmClock");
            alarm.put("hour", h.intValue());
            alarm.put("minute", mi.intValue());
            alarm.put("on", !Boolean.FALSE.equals(args.get("on")));
            ((Map<String, Object>) deck.get("clock")).put("alarm", String.format("%02d:%02d", h.intValue(), mi.intValue()));
            deckDirty = true;
            return null;
        }
        for (Object entry : (List<Object>) deck.get("commands")) {
            Map<String, Object> deckCommand = (Map<String, Object>) entry;
            if (!id.equals(deckCommand.get("id"))) {
                continue;
            }
            if (!Boolean.TRUE.equals(deckCommand.get("available"))) {
                return "Can't do that right now";
            }
            if (deckCommand.get("on") instanceof Boolean on) {
                deckCommand.put("on", !on);
                deckDirty = true;
            }
            return null;
        }
        return "unknown deck command '" + id + "'";
    }

    private String equip(Command command) {
        String slot = String.valueOf(command.args().getOrDefault("slot", "primary"));
        if (!List.of("primary", "secondary", "both").contains(slot)) {
            return "bad slot '" + slot + "'";
        }
        // Whatever held the requested hand(s) is unequipped first.
        for (Map<String, Object> other : items()) {
            Object held = other.get("equipped");
            boolean inHands = held != null && !"worn".equals(held);
            if (inHands && ("both".equals(slot) || "both".equals(held) || slot.equals(held))) {
                other.remove("equipped");
            }
        }
        return setEquipped(command, slot);
    }

    private String setEquipped(Command command, String slot) {
        Map<String, Object> item = find(command);
        if (item == null) {
            return "item not found";
        }
        if (slot == null) {
            item.remove("equipped");
        } else {
            item.put("equipped", slot);
        }
        inventoryDirty = true;
        return null;
    }

    private String drop(Command command) {
        Map<String, Object> item = find(command);
        if (item == null) {
            return "item not found";
        }
        item.remove("equipped");
        moveItem(item, containerOfKind("floor"));
        return null;
    }

    // --- containers (like the B42 adapter: see PROTOCOL.md "containers" and "transfer") ---

    private String transfer(Command command) {
        Map<String, Object> destination = reachableContainer(command.args().get("to"));
        if (destination == null) {
            return "That container is out of reach";
        }
        if (Boolean.TRUE.equals(destination.get("locked"))) {
            return "That container is locked";
        }
        Object id = command.args().get("itemId");
        Map<String, Object> item = id instanceof Number n ? findAnywhere(n.longValue()) : null;
        if (item == null) {
            return "item not found";
        }
        if (itemsOf(destination).contains(item)) {
            return "It's already there";
        }
        item.remove("equipped");
        moveItem(item, destination);
        return null;
    }

    private String transferAll(Command command) {
        Map<String, Object> from = reachableContainer(command.args().get("from"));
        Map<String, Object> to = reachableContainer(command.args().get("to"));
        if (from == null || Boolean.TRUE.equals(from.get("locked"))) {
            return "That container is out of reach";
        }
        if (to == null) {
            return "That container is out of reach";
        }
        if (Boolean.TRUE.equals(to.get("locked"))) {
            return "That container is locked";
        }
        // Like the game's Transfer All: equipped items stay on you.
        List<Map<String, Object>> moving = itemsOf(from).stream().filter(item -> item.get("equipped") == null).toList();
        if (moving.isEmpty()) {
            return "Nothing to move";
        }
        moving.forEach(item -> moveItem(item, to));
        return null;
    }

    private void moveItem(Map<String, Object> item, Map<String, Object> destination) {
        items().remove(item);
        containers.forEach(c -> itemsOf(c).remove(item));
        itemsOf(destination).add(item);
        inventoryDirty = true;
        containersDirty = true;
    }

    private Map<String, Object> findAnywhere(long id) {
        for (Map<String, Object> container : containers) {
            if (Boolean.TRUE.equals(container.get("locked"))) {
                continue;
            }
            for (Map<String, Object> item : itemsOf(container)) {
                if (((Number) item.get("id")).longValue() == id) {
                    return item;
                }
            }
        }
        return null;
    }

    private Map<String, Object> reachableContainer(Object id) {
        return containers.stream().filter(c -> c.get("id").equals(id)).findFirst().orElse(null);
    }

    private Map<String, Object> containerOfKind(String kind) {
        return containers.stream().filter(c -> kind.equals(c.get("kind"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itemsOf(Map<String, Object> container) {
        if ("inventory".equals(container.get("kind"))) {
            return items();
        }
        return (List<Map<String, Object>>) container.computeIfAbsent("items", k -> new ArrayList<>());
    }

    /** Leaves the furniture behind, or comes back to it. */
    private void walk() {
        if (furniture.isEmpty()) {
            containers.stream().filter(c -> "nearby".equals(c.get("kind"))).forEach(furniture::add);
            containers.removeAll(furniture);
            Log.info("walked away from " + furniture.stream().map(c -> c.get("name")).toList());
        } else {
            containers.addAll(containers.size() - 1, furniture); // back in front of the floor
            furniture.clear();
            Log.info("back at the furniture");
        }
        containersDirty = true;
    }

    /** The containers message: weights from the items, no item list for the inventory or locked ones. */
    private List<Map<String, Object>> containerSnapshot() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> container : containers) {
            Map<String, Object> entry = deepCopy(container);
            if (Boolean.TRUE.equals(container.get("locked"))) {
                list.add(entry); // as in the fixture: can't be looked into, weight as the game reports it
                continue;
            }
            double weight = itemsOf(container).stream().mapToDouble(i -> ((Number) i.getOrDefault("weight", 0)).doubleValue()).sum();
            entry.put("weight", Math.round(weight * 100) / 100.0);
            if ("inventory".equals(container.get("kind"))) {
                entry.remove("items");
            } else {
                entry.put("items", itemsOf(container).stream().map(item -> {
                    Map<String, Object> copy = deepCopy(item);
                    copy.put("actions", List.of()); // like the adapter: quick actions only in the inventory
                    return copy;
                }).toList());
            }
            list.add(entry);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> containersFixture() {
        return new ArrayList<>((List<Map<String, Object>>) fixtureData("containers.json").get("containers"));
    }

    private void adjustHealth(double delta) {
        double health = ((Number) player.get("health")).doubleValue();
        player.put("health", Math.max(0, Math.min(100, health + delta)));
        player.put("bleeding", delta < 0);
        playerDirty = true;
    }

    private void addRandomItem() {
        String[][] pool = {
                {"Base.Hammer", "Hammer", "Weapon", "Item_Hammer"},
                {"Base.Pills", "Painkillers", "FirstAid", "Item_Pills"},
                {"Base.Crisps", "Chips", "Food", "Item_Crisps"},
        };
        String[] pick = pool[(int) (nextItemId % pool.length)];
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", nextItemId++);
        item.put("type", pick[0]);
        item.put("name", pick[1]);
        item.put("category", pick[2]);
        item.put("icon", pick[3]);
        item.put("weight", 0.5);
        items().add(item);
        inventoryDirty = true;
    }

    private Map<String, Object> find(Command command) {
        Object id = command.args().get("itemId");
        if (!(id instanceof Number n)) {
            return null;
        }
        return items().stream()
                .filter(item -> Objects.equals(((Number) item.get("id")).longValue(), n.longValue()))
                .findFirst().orElse(null);
    }

    /** Mirrors the B42 adapter's rules closely enough for app development (see PROTOCOL.md). */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> withActions(Map<String, Object> inventory) {
        for (Map<String, Object> item : (List<Map<String, Object>>) inventory.get("items")) {
            List<String> actions = new ArrayList<>();
            if (item.get("equipped") != null) {
                actions.add("unequip");
            } else if ("Clothing".equals(item.get("category"))) {
                actions.add("wear");
            } else {
                actions.add("equip.primary");
                actions.add("equip.secondary");
            }
            actions.add("drop");
            item.put("actions", actions);
        }
        return inventory;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items() {
        return (List<Map<String, Object>>) inventory.get("items");
    }

    /** Reads the {@code data} of a fixture envelope as mutable maps and lists. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> fixtureData(String name) {
        try (InputStream in = MockGame.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IllegalStateException("fixture " + name + " missing from classpath");
            }
            Map<String, Object> envelope = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Map.class);
            return deepCopy((Map<String, Object>) envelope.get("data"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((k, v) -> copy.put(k, v instanceof Map ? deepCopy((Map<String, Object>) v)
                : v instanceof List<?> list ? new ArrayList<>(list.stream()
                        .map(e -> e instanceof Map ? deepCopy((Map<String, Object>) e) : e).toList())
                : v));
        return copy;
    }
}
