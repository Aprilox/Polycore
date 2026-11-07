package com.polycore.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Colors and drawing helpers shared by the Polycore screens.
 */
public final class Theme {
    public static final int BACKDROP = 0xC0050A12;
    public static final int PANEL = 0xF20D1420;
    public static final int SIDEBAR = 0xF2090F19;
    public static final int CARD = 0xFF141F30;
    public static final int CARD_BORDER = 0xFF22324A;
    public static final int DIVIDER = 0xFF1E2B40;

    public static final int TEXT = 0xFFE2E8F0;
    public static final int MUTED = 0xFF94A3B8;
    public static final int DIM = 0xFF64748B;

    public static final int ACCENT = 0xFF22D3EE;
    public static final int ACCENT_DARK = 0xFF0E7490;
    public static final int AMBER = 0xFFF59E0B;
    public static final int GREEN = 0xFF34D399;
    public static final int RED = 0xFFF87171;
    public static final int VIOLET = 0xFFA78BFA;

    private Theme() {}

    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, CARD);
        g.renderOutline(x, y, w, h, CARD_BORDER);
    }

    /** Small rounded-looking label with a colored background. Returns its width. */
    public static int pill(GuiGraphics g, Font font, String text, int x, int y, int color) {
        int w = font.width(text) + 8;
        int bg = (color & 0x00FFFFFF) | 0x33000000;
        g.fill(x, y, x + w, y + 11, bg);
        g.fill(x, y, x + 1, y + 11, color);
        g.drawString(font, text, x + 4, y + 2, color, false);
        return w;
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, double fraction, int color) {
        fraction = Math.max(0, Math.min(1, fraction));
        g.fill(x, y, x + w, y + h, 0xFF0A111C);
        g.fill(x, y, x + (int) Math.round(w * fraction), y + h, color);
    }

    /** Line chart of the values (oldest first), scaled to max(values, minMax). */
    public static void sparkline(GuiGraphics g, int x, int y, int w, int h, int[] values, int minMax, int color) {
        g.fill(x, y, x + w, y + h, 0x40000000);
        if (values == null || values.length < 2) return;
        int max = minMax;
        for (int v : values) max = Math.max(max, v);
        int n = values.length;
        int fill = (color & 0x00FFFFFF) | 0x40000000;
        for (int i = 0; i < w; i++) {
            int idx = (int) ((long) i * (n - 1) / Math.max(1, w - 1));
            int v = values[idx];
            int bh = (int) Math.round((double) v / max * (h - 1));
            if (bh <= 0) continue;
            g.fill(x + i, y + h - bh, x + i + 1, y + h, fill);
            g.fill(x + i, y + h - bh, x + i + 1, y + h - bh + 1, color);
        }
    }

    /** Text drawn at a larger scale (e.g. 2.0 for big numbers). */
    public static void bigText(GuiGraphics g, Font font, String text, int x, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();
    }

    public static String gb(long mb) {
        return String.format("%.1f GB", mb / 1024.0);
    }

    public static String duration(double seconds) {
        long t = Math.round(seconds);
        if (t < 60) return t + "s";
        if (t < 3600) return (t / 60) + "m " + (t % 60) + "s";
        return (t / 3600) + "h " + ((t % 3600) / 60) + "m";
    }
}
