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
    private boolean driving;
    private long nextItemId = 20000;
    private long tick;
    private boolean inventoryDirty = true;
    private boolean playerDirty = true;

    MockGame(BridgeContext bridge, String inventoryFixture) {
        this.bridge = bridge;
        this.inventory = fixtureData(inventoryFixture);
    }

    void start() {
        gameThread.execute(() -> bridge.state().publish("session", fixtureData("session.json")));
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
                  i  add an item             q  quit""");
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
                Map<String, Object> item = find(command);
                if (item == null) {
                    yield "item not found";
                }
                data[0] = menu.open(item, () -> {
                    items().remove(item);
                    inventoryDirty = true;
                });
                yield null;
            }
            case "menu_select" -> menu.select(command.args().get("menuId"), command.args().get("optionId"));
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
        items().remove(item);
        inventoryDirty = true;
        return null;
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
