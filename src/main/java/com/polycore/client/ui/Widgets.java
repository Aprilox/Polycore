package com.polycore.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Flat widgets in the Polycore style.
 */
public final class Widgets {
    private Widgets() {}

    public enum Style { PRIMARY, NEUTRAL, DANGER, NAV, NAV_SELECTED }

    /** Flat button. */
    public static final class FlatButton extends AbstractWidget {
        private final Runnable action;
        private final Style style;

        public FlatButton(int x, int y, int w, int h, String label, Style style, Runnable action) {
            super(x, y, w, h, Component.literal(label));
            this.action = action;
            this.style = style;
        }

        // Vanilla callback, kept on purpose: it exists on every loader (NeoForge only adds an overload).
        @Override
        @SuppressWarnings("deprecation")
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            boolean hover = this.isHoveredOrFocused() && this.active;
            int bg, border, text;
            switch (style) {
                case PRIMARY -> {
                    bg = hover ? 0xFF0891B2 : 0xFF0E7490;
                    border = 0xFF22D3EE;
                    text = 0xFFFFFFFF;
                }
                case DANGER -> {
                    bg = hover ? 0xFF991B1B : 0xFF7F1D1D;
                    border = 0xFFF87171;
                    text = 0xFFFFFFFF;
                }
                case NAV -> {
                    bg = hover ? 0xFF16233A : 0x00000000;
                    border = 0x00000000;
                    text = hover ? Theme.TEXT : Theme.MUTED;
                }
                case NAV_SELECTED -> {
                    bg = 0xFF16304A;
                    border = 0x00000000;
                    text = 0xFFFFFFFF;
                }
                default -> {
                    bg = hover ? 0xFF22324A : 0xFF1A2638;
                    border = 0xFF2C3E5A;
                    text = Theme.TEXT;
                }
            }
            if (!this.active) {
                bg = 0xFF141C28;
                border = 0xFF1E2B40;
                text = Theme.DIM;
            }
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            if ((bg >>> 24) != 0) g.fill(x, y, x + w, y + h, bg);
            if ((border >>> 24) != 0) g.renderOutline(x, y, w, h, border);
            if (style == Style.NAV_SELECTED) g.fill(x, y, x + 2, y + h, Theme.ACCENT);
            String label = getMessage().getString();
            if (style == Style.NAV || style == Style.NAV_SELECTED) {
                g.drawString(font, label, x + 8, y + (h - 8) / 2, text, false);
            } else {
                g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, text, false);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            this.defaultButtonNarrationText(out);
        }
    }

    /** On/off switch. */
    public static final class Toggle extends AbstractWidget {
        private boolean value;
        private final Consumer<Boolean> onChange;

        public Toggle(int x, int y, boolean value, String narration, Consumer<Boolean> onChange) {
            super(x, y, 28, 14, Component.literal(narration));
            this.value = value;
            this.onChange = onChange;
        }

        // Vanilla callback, kept on purpose: it exists on every loader (NeoForge only adds an overload).
        @Override
        @SuppressWarnings("deprecation")
        public void onClick(double mouseX, double mouseY) {
            value = !value;
            onChange.accept(value);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int track = value ? (this.active ? 0xFF0E7490 : 0xFF1F3B47) : 0xFF1A2638;
            int border = value ? (this.active ? Theme.ACCENT : 0xFF2B4F5C) : 0xFF2C3E5A;
            if (this.isHoveredOrFocused() && this.active) border = 0xFFFFFFFF;
            g.fill(x, y, x + w, y + h, track);
            g.renderOutline(x, y, w, h, border);
            int knobX = value ? x + w - 12 : x + 2;
            int knob = this.active ? 0xFFE2E8F0 : 0xFF64748B;
            g.fill(knobX, y + 2, knobX + 10, y + h - 2, knob);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            this.defaultButtonNarrationText(out);
        }
    }

    /** Whole-number field: type a value, Enter applies it. Out-of-range values turn red and are not sent. */
    public static final class NumberField extends EditBox {
        private final int min;
        private final int max;
        private final Consumer<Integer> onSubmit;

        public NumberField(Font font, int x, int y, int w, int h, int min, int max, String initial,
                           Consumer<String> onEdit, Consumer<Integer> onSubmit) {
            super(font, x, y, w, h, Component.literal("value"));
            this.min = min;
            this.max = max;
            this.onSubmit = onSubmit;
            setMaxLength(7);
            setFilter(s -> s.matches("\\d{0,7}"));
            setValue(initial);
            setResponder(s -> {
                setTextColor(isValid() ? 0xFFE0E0E0 : Theme.RED);
                if (onEdit != null) onEdit.accept(s);
            });
            setTextColor(isValid() ? 0xFFE0E0E0 : Theme.RED);
        }

        public boolean isValid() {
            try {
                int v = Integer.parseInt(getValue());
                return v >= min && v <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        public void submit() {
            if (isValid()) onSubmit.accept(Integer.parseInt(getValue()));
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.isFocused()) {
                submit();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
