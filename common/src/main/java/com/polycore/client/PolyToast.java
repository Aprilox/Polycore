package com.polycore.client;

import com.polycore.client.ui.Theme;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import com.polycore.client.ui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;

/**
 * Polycore notification shown in the top-right corner instead of a chat message.
 */
public final class PolyToast implements Toast {
    private static final long DURATION_MS = 5000;

    private final String title;
    private final String message;
    private final int color;

    public PolyToast(String level, String title, String message) {
        this.title = title;
        this.message = message;
        this.color = switch (level) {
            case "SUCCESS" -> Theme.GREEN;
            case "WARN" -> Theme.AMBER;
            case "ERROR" -> Theme.RED;
            default -> Theme.ACCENT;
        };
    }

    @Override
    public int width() {
        return 220;
    }

    @Override
    public int height() {
        return 32;
    }

    @Override
    public Visibility render(PoseStack pose, ToastComponent toasts, long timeSinceLastVisible) {
        GuiGraphics g = new GuiGraphics(pose);
        Font font = toasts.getMinecraft().font;
        int w = width(), h = height();
        g.fill(0, 0, w, h, 0xF00D1420);
        g.renderOutline(0, 0, w, h, Theme.CARD_BORDER);
        g.fill(0, 0, 3, h, color);
        g.drawString(font, font.plainSubstrByWidth("Polycore - " + title, w - 14), 9, 6, color, false);
        g.drawString(font, font.plainSubstrByWidth(message, w - 14), 9, 18, Theme.TEXT, false);
        return timeSinceLastVisible >= DURATION_MS ? Visibility.HIDE : Visibility.SHOW;
    }
}
