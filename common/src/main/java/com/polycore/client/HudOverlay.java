package com.polycore.client;

import com.polycore.client.ui.PolyScreen;
import com.polycore.client.ui.Theme;
import com.polycore.network.ClientBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;

/**
 * Optional always-on panel (Preferences > HUD overlay): TPS, generation, tick time, CPU and task progress.
 * Drawn about 25% smaller than the GUI when the GUI scale allows it, always in whole screen pixels so the text
 * stays sharp (GUI scale 1 and 2: normal size).
 */
public final class HudOverlay {

    private HudOverlay() {}

    /** Counts calls so the client self-test can tell the HUD layer is registered. */
    public static volatile int renderCalls = 0;

    public static void extractRenderState(GuiGraphicsExtractor g) {
        renderCalls++;
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.get().hudEnabled || mc.gui.hud.isHidden() || mc.player == null) return;
        if (mc.gui.screen() instanceof PolyScreen) return;
        CompoundTag s = ClientBridge.hudState();
        if (s == null || !s.getBooleanOr("canView", false) || !PolycoreClient.serverHasPolycore()) return;

        Font font = mc.font;
        double gui = mc.getWindow().getGuiScale();
        float scale = (float) (Math.max(1, Math.round(gui * 0.75)) / gui);
        double tpsValue = s.getDoubleOr("tps", 0);
        String tps = String.format("%.1f TPS", tpsValue);
        int tpsColor = tpsValue >= 19.5 ? Theme.GREEN : tpsValue >= 15 ? Theme.AMBER : Theme.RED;
        // When Polycore's generation optimizations are off, the numbers are vanilla generation: say so.
        String gen = String.format("%s gen %.1f/s - %d total", s.getBooleanOr("genPolycore", false) ? "Polycore" : "Vanilla",
                s.getDoubleOr("cps1", 0), s.getLongOr("chunksGenerated", 0L));
        double cpu = s.getDoubleOr("cpu", 0);
        String tick = String.format("%.1f ms - CPU %s", s.getDoubleOr("mspt", 0), cpu < 0 ? "?" : String.format("%.0f%%", cpu * 100));
        boolean task = s.getBooleanOr("taskRunning", false);
        CompoundTag t = s.getCompoundOrEmpty("task");
        String progress = task ? String.format("%s %.0f%%", s.getBooleanOr("benchRunning", false) ? "Benchmark" : "Pre-gen", t.getDoubleOr("fraction", 0) * 100) : null;

        int w = Math.max(font.width("Polycore") + 10 + font.width(tps), Math.max(font.width(gen), font.width(tick))) + 10;
        if (progress != null) w = Math.max(w, font.width(progress) + 10);
        int h = 34 + (progress != null ? 15 : 0);

        // Work in scaled coordinates so the panel still hugs the chosen corner.
        int sw = (int) (g.guiWidth() / scale), sh = (int) (g.guiHeight() / scale);
        int x = switch (ClientConfig.get().hudCorner) {
            case TOP_LEFT, BOTTOM_LEFT -> 4;
            default -> sw - w - 4;
        };
        int y = switch (ClientConfig.get().hudCorner) {
            case TOP_LEFT, TOP_RIGHT -> 4;
            default -> sh - h - 4;
        };
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        g.fill(x, y, x + w, y + h, 0xB00D1420);
        g.fill(x, y, x + 2, y + h, Theme.ACCENT);
        g.text(font, "Polycore", x + 5, y + 3, Theme.ACCENT, false);
        g.text(font, tps, x + w - 5 - font.width(tps), y + 3, tpsColor, false);
        g.text(font, gen, x + 5, y + 13, Theme.TEXT, false);
        g.text(font, tick, x + 5, y + 23, Theme.MUTED, false);
        if (progress != null) {
            g.text(font, progress, x + 5, y + 34, Theme.AMBER, false);
            Theme.bar(g, x + 5, y + 44, w - 10, 2, t.getDoubleOr("fraction", 0), Theme.AMBER);
        }
        g.pose().popMatrix();
    }
}
