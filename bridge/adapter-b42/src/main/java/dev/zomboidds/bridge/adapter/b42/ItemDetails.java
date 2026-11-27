package dev.zomboidds.bridge.adapter.b42;

import zombie.characters.IsoGameCharacter;
import zombie.core.Core;
import zombie.core.textures.ColorInfo;
import zombie.inventory.InventoryItem;
import zombie.ui.ObjectTooltip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An item's details as the game's hover tooltip lists them: the game builds its tooltip without
 * drawing it (measure only, as its own inventory does to size the tooltip), and its rows are read
 * here. So every item gets the game's own lines, in its order: condition, sharpness and damage
 * bars, food values, wetness, ammo, and lines other mods add to the item's tooltip in Java.
 * Game thread only.
 */
final class ItemDetails {

    private static ObjectTooltip tooltip;

    private ItemDetails() {
    }

    /**
     * The tooltip's rows: {@code label}, and optionally {@code value}, {@code bar} (0-1) and
     * {@code tone} (0 = the game's "bad" colour, 1 = its "good" one, for coloured bars and text).
     */
    static List<Map<String, Object>> rows(InventoryItem item, IsoGameCharacter character) {
        if (tooltip == null) {
            tooltip = new ObjectTooltip();
        }
        ObjectTooltip tt = tooltip;
        tt.setCharacter(character);
        tt.setWeightOfStack(0);
        tt.setMeasureOnly(true);
        ObjectTooltip.Layout layout = tt.beginLayout();
        layout.setMinLabelWidth(80);
        layout.setMinValueWidth(80);
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            item.DoTooltipEmbedded(tt, layout, 0);
            for (ObjectTooltip.Layout l = layout; l != null; l = l.next) {
                for (ObjectTooltip.LayoutItem row : l.items) {
                    Map<String, Object> out = row(row);
                    if (out != null) {
                        rows.add(out);
                    }
                }
            }
        } finally {
            tt.endLayout(layout);
            tt.setMeasureOnly(false);
            tt.setCharacter(null);
        }
        return rows;
    }

    private static Map<String, Object> row(ObjectTooltip.LayoutItem row) {
        String label = clean(row.label);
        String value = row.hasValue ? clean(row.value) : null;
        boolean bar = row.progressFraction != -1.0f;
        if (label.isEmpty() && (value == null || value.isEmpty()) && !bar) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", label);
        if (value != null && !value.isEmpty()) {
            out.put("value", value);
        }
        Double tone;
        if (bar) {
            out.put("bar", round(Math.max(0, Math.min(1, row.progressFraction))));
            tone = tone(row.r1, row.g1, row.b1);
        } else if (value != null && !value.isEmpty()) {
            tone = tone(row.r1, row.g1, row.b1);
            if (tone == null) {
                tone = tone(row.r0, row.g0, row.b0);
            }
        } else {
            tone = tone(row.r0, row.g0, row.b0);
        }
        if (tone != null) {
            out.put("tone", tone);
        }
        return out;
    }

    /** "Encumbrance:" → "Encumbrance"; runs of spaces become one. */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        String s = text.replaceAll("\\s+", " ").trim();
        while (s.endsWith(":")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }

    /**
     * Where a colour lies between the game's bad (0) and good (1) highlight colours, which its
     * bars blend between; plain red text counts as bad. Null for the usual white or cream.
     */
    static Double tone(float r, float g, float b) {
        ColorInfo bad = Core.getInstance().getBadHighlitedColor();
        ColorInfo good = Core.getInstance().getGoodHighlitedColor();
        return tone(r, g, b, bad.getR(), bad.getG(), bad.getB(), good.getR(), good.getG(), good.getB());
    }

    static Double tone(float r, float g, float b, float br, float bg, float bb, float gr, float gg, float gb) {
        float dr = gr - br, dg = gg - bg, db = gb - bb;
        float len = dr * dr + dg * dg + db * db;
        if (len > 0.0001f) {
            float t = ((r - br) * dr + (g - bg) * dg + (b - bb) * db) / len;
            float er = br + t * dr - r, eg = bg + t * dg - g, eb = bb + t * db - b;
            if (t > -0.05f && t < 1.05f && er * er + eg * eg + eb * eb < 0.01f) {
                return round(Math.max(0, Math.min(1, t)));
            }
        }
        if (r > 0.9f && g < 0.6f && b < 0.6f) {
            return 0.0; // the game's plain red warnings ("Can't sprint", tainted water)
        }
        return null;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
