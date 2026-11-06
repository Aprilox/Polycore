package com.polycore.client;

import com.polycore.client.ui.PolyScreen;
import com.polycore.client.ui.Theme;
import com.polycore.network.ClientBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;

/**
 * Optional always-on panel (Preferences > HUD overlay): chunks/s, tick time, CPU and task progress.
 */
public final class HudOverlay {
    private HudOverlay() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.get().hudEnabled || mc.options.hideGui || mc.player == null) return;
        if (mc.screen instanceof PolyScreen) return;
        CompoundTag s = ClientBridge.state();
        if (s == null || !s.getBoolean("canView") || !PolycoreClient.serverHasPolycore()) return;

        Font font = mc.font;
        // When Polycore's generation optimizations are off, the numbers are vanilla generation: say so.
        String line1 = String.format("%s gen: %.1f chunks/s", s.getBoolean("genPolycore") ? "Polycore" : "Vanilla", s.getDouble("cps1"));
        String lineCount = s.getLong("chunksGenerated") + " chunks generated";
        double cpu = s.getDouble("cpu");
        String line2 = String.format("%.1f ms - CPU %s", s.getDouble("mspt"), cpu < 0 ? "?" : String.format("%.0f%%", cpu * 100));
        boolean task = s.getBoolean("taskRunning");
        CompoundTag t = s.getCompound("task");
        String line3 = task ? String.format("%s %.0f%%", s.getBoolean("benchRunning") ? "Benchmark" : "Pre-gen", t.getDouble("fraction") * 100) : null;

        int w = Math.max(font.width("Polycore"), Math.max(font.width(line1), Math.max(font.width(lineCount), font.width(line2)))) + 12;
        if (line3 != null) w = Math.max(w, font.width(line3) + 12);
        int h = 46 + (line3 != null ? 16 : 0);
        int sw = g.guiWidth(), sh = g.guiHeight();
        int x = switch (ClientConfig.get().hudCorner) {
            case TOP_LEFT, BOTTOM_LEFT -> 4;
            default -> sw - w - 4;
        };
        int y = switch (ClientConfig.get().hudCorner) {
            case TOP_LEFT, TOP_RIGHT -> 4;
            default -> sh - h - 4;
        };
        g.fill(x, y, x + w, y + h, 0xB00D1420);
        g.fill(x, y, x + 2, y + h, Theme.ACCENT);
        g.drawString(font, "Polycore", x + 6, y + 4, Theme.ACCENT, false);
        g.drawString(font, line1, x + 6, y + 14, Theme.TEXT, false);
        g.drawString(font, lineCount, x + 6, y + 24, Theme.MUTED, false);
        g.drawString(font, line2, x + 6, y + 34, Theme.MUTED, false);
        if (line3 != null) {
            g.drawString(font, line3, x + 6, y + 46, Theme.AMBER, false);
            Theme.bar(g, x + 6, y + 56, w - 12, 3, t.getDouble("fraction"), Theme.AMBER);
        }
    }
}
