package com.polycore.client;

import com.polycore.client.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

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

    private Visibility visibility = Visibility.SHOW;

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager toasts, long timeSinceLastVisible) {
        visibility = timeSinceLastVisible >= DURATION_MS ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, Font font, long timeSinceLastVisible) {
        int w = width(), h = height();
        g.fill(0, 0, w, h, 0xF00D1420);
        g.outline(0, 0, w, h, Theme.CARD_BORDER);
        g.fill(0, 0, 3, h, color);
        g.text(font, font.plainSubstrByWidth("Polycore - " + title, w - 14), 9, 6, color, false);
        g.text(font, font.plainSubstrByWidth(message, w - 14), 9, 18, Theme.TEXT, false);
    }
}
