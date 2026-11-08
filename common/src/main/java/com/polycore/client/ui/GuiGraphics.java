package com.polycore.client.ui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * The subset of Minecraft 1.20's GuiGraphics that Polycore draws with, over the PoseStack of Minecraft 1.19.4 and
 * older (no GuiGraphics there), so the drawing code stays the same on every line.
 */
public final class GuiGraphics {
    private final PoseStack pose;

    public GuiGraphics(PoseStack pose) {
        this.pose = pose;
    }

    public PoseStack pose() {
        return pose;
    }

    public int guiWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public int guiHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(pose, x1, y1, x2, y2, color);
    }

    public void renderOutline(int x, int y, int w, int h, int color) {
        fill(x, y, x + w, y + 1, color);
        fill(x, y + h - 1, x + w, y + h, color);
        fill(x, y + 1, x + 1, y + h - 1, color);
        fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return drawString(font, text.getVisualOrderText(), x, y, color, shadow);
    }

    public void blit(ResourceLocation texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw, int th) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        GuiComponent.blit(pose, x, y, w, h, u, v, uw, vh, tw, th);
        RenderSystem.disableBlend();
    }

    /** One level only (Polycore never nests them), in GUI coordinates like GuiGraphics. */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        Window win = Minecraft.getInstance().getWindow();
        double s = win.getGuiScale();
        RenderSystem.enableScissor((int) (x1 * s), (int) (win.getHeight() - y2 * s),
                (int) Math.max(0, (x2 - x1) * s), (int) Math.max(0, (y2 - y1) * s));
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }
}
