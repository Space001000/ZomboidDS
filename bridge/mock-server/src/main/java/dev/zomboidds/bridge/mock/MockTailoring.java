package dev.zomboidds.bridge.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A stand-in for the game's Inspect window (see PROTOCOL.md "Tailoring"): the clothes of
 * tailor_garments.json, a sewing kit, and the per-part menu. Patching takes a few seconds, so the
 * app's progress bar can be seen; then the hole is gone (or the padding added) and a fabric used,
 * with the defence worked out like 42.20 at Tailoring 4.
 */
final class MockTailoring {

    private record Fabric(String type, String name, String icon, String patch, int scratch, int bite) { }

    private static final List<Fabric> FABRICS = List.of(
            new Fabric("RippedSheets", "Rag", "Item_Rag", "Ripped Sheets", 2, 0),
            new Fabric("DenimStrips", "Denim Strips", "Item_DenimStrips", "Denim Strips", 4, 2),
            new Fabric("LeatherStrips", "Leather Strips", "Item_LeatherStrips", "Leather Strips", 8, 4));
    private static final int TAILORING = 4;
    private static final long SEW_MS = 6_000;

    /** What's being sewn: a patch with [fabric], or the patch coming off (fabric null). */
    private record Job(long garment, String part, Fabric fabric, String label, long start) { }

    private final Map<Long, Map<String, Object>> garments = new LinkedHashMap<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>(Map.of("RippedSheets", 12, "DenimStrips", 3, "LeatherStrips", 0));
    private Job job;

    @SuppressWarnings("unchecked")
    MockTailoring(Map<String, Object> fixture) {
        for (Object entry : (List<Object>) fixture.get("garments")) {
            Map<String, Object> garment = (Map<String, Object>) entry;
            garments.put(((Number) garment.get("id")).longValue(), garment);
        }
    }

    /** The `tailor_list` data. */
    Map<String, Object> list() {
        finishSewing();
        List<Object> list = new ArrayList<>();
        for (Map<String, Object> garment : garments.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            for (String key : List.of("id", "name", "icon", "condition", "worn")) {
                if (garment.containsKey(key)) entry.put(key, garment.get(key));
            }
            if (!Boolean.TRUE.equals(garment.get("worn"))) entry.put("bag", "Military Backpack");
            entry.put("holes", parts(garment).stream().filter(p -> Boolean.TRUE.equals(p.get("hole"))).count());
            entry.put("patches", parts(garment).stream().filter(p -> p.get("patch") != null).count());
            entry.put("repairable", garment.get("cantRepair") == null);
            list.add(entry);
        }
        List<Object> fabrics = new ArrayList<>();
        for (Fabric fabric : FABRICS) {
            fabrics.add(Map.of("type", fabric.type(), "name", fabric.name(), "icon", fabric.icon(), "count", counts.get(fabric.type())));
        }
        return Map.of("garments", list, "kit", Map.of("needle", true, "thread", true, "fabrics", fabrics), "tailoring", TAILORING);
    }

    /** The `tailor_garment` data, or null when there's no such garment. */
    Map<String, Object> garment(long id) {
        finishSewing();
        Map<String, Object> garment = garments.get(id);
        if (garment == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(garment);
        List<Object> parts = new ArrayList<>();
        for (Map<String, Object> part : parts(garment)) {
            Map<String, Object> shown = new LinkedHashMap<>(part);
            if (job != null && job.garment() == id && job.part().equals(part.get("id"))) {
                double progress = Math.min(1.0, (System.currentTimeMillis() - job.start()) / (double) SEW_MS);
                shown.put("sewing", Map.of("name", job.label(), "progress", Math.round(progress * 100) / 100.0));
            }
            parts.add(shown);
        }
        copy.put("parts", parts);
        return copy;
    }

    /** The `tailor_menu` data, registered with [menu]; or an error. */
    Object menu(long id, String partId, MockItemMenu menu) {
        Map<String, Object> garment = garments.get(id);
        if (garment == null) {
            return "item not found";
        }
        if (garment.get("cantRepair") != null) {
            return garment.get("cantRepair");
        }
        Map<String, Object> part = parts(garment).stream().filter(p -> partId.equals(p.get("id"))).findFirst().orElse(null);
        if (part == null) {
            return "No such body part";
        }
        List<Object> options = new ArrayList<>();
        Map<String, Supplier<String>> actions = new LinkedHashMap<>();
        if (part.get("patch") != null) {
            options.add(MockItemMenu.option("1", "Remove Patch", true, "14 % chance to get Patch back", null));
            actions.put("1", () -> start(id, partId, null, "Remove Patch"));
        } else {
            boolean hole = Boolean.TRUE.equals(part.get("hole"));
            String label = hole ? "Patch Hole" : "Add Padding";
            List<Object> choices = new ArrayList<>();
            int i = 0;
            for (Fabric fabric : FABRICS) {
                if (counts.get(fabric.type()) > 0) {
                    String choiceId = "1." + (++i);
                    choices.add(MockItemMenu.option(choiceId, fabric.name(), true,
                            "Tailoring :" + TAILORING + "\nScratch Defense +" + fabric.scratch() + "\nBite Defense +" + fabric.bite(), null));
                    actions.put(choiceId, () -> start(id, partId, fabric, label));
                }
            }
            if (choices.isEmpty()) {
                options.add(MockItemMenu.option("1", "Tailoring", false, "Fabric, needle and thread required for clothing repair.", null));
            } else {
                options.add(MockItemMenu.option("1", label, true, null, choices));
            }
        }
        return menu.register(options, actions);
    }

    private String start(long garment, String part, Fabric fabric, String label) {
        finishSewing();
        if (job != null) {
            return "Already sewing"; // the game would queue it; one at a time is enough here
        }
        job = new Job(garment, part, fabric, label, System.currentTimeMillis());
        return null;
    }

    /** Applies the patch (or its removal) once the sewing time is up. */
    private void finishSewing() {
        if (job == null || System.currentTimeMillis() - job.start() < SEW_MS) {
            return;
        }
        Map<String, Object> garment = garments.get(job.garment());
        Map<String, Object> part = parts(garment).stream().filter(p -> job.part().equals(p.get("id"))).findFirst().orElseThrow();
        Fabric fabric = job.fabric();
        if (fabric != null) {
            boolean hole = Boolean.TRUE.equals(part.remove("hole"));
            int bite = hole ? fabric.bite() : ((Number) part.get("bite")).intValue() + fabric.bite();
            int scratch = hole ? fabric.scratch() : ((Number) part.get("scratch")).intValue() + fabric.scratch();
            part.put("bite", bite);
            part.put("scratch", scratch);
            part.put("patch", fabric.patch() + " patch");
            counts.merge(fabric.type(), -1, Integer::sum);
        } else {
            part.remove("patch"); // the fixture's patches all cover holes: the hole is back
            part.put("hole", true);
            part.put("bite", 0);
            part.put("scratch", 0);
        }
        job = null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parts(Map<String, Object> garment) {
        return (List<Map<String, Object>>) (List<?>) garment.get("parts");
    }
}
