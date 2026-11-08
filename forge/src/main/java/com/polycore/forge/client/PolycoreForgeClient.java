package com.polycore.forge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.polycore.Polycore;
import com.polycore.client.HudOverlay;
import com.polycore.client.PolycoreClient;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/**
 * Forge client side (never loaded on a dedicated server): key, HUD layer, client command and ticks.
 */
public final class PolycoreForgeClient {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.polycore.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.polycore");

    private PolycoreForgeClient() {}

    public static void init(IEventBus modBus) {
        PolycoreClient.init();
        PolycoreClient.openKey = OPEN_MENU;
        modBus.addListener(PolycoreForgeClient::onRegisterKeys);
        modBus.addListener(PolycoreForgeClient::onAddLayers);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForgeClient::onLogout);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForgeClient::onClientCommands);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }

    private static void onAddLayers(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("hud", (gui, pose, partialTick, width, height) -> HudOverlay.render(new com.polycore.client.ui.GuiGraphics(pose)));
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean pressed = false;
        while (OPEN_MENU.consumeClick()) pressed = true;
        PolycoreClient.onClientTick(pressed);
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PolycoreClient.onLogout();
    }

    private static void onClientCommands(RegisterClientCommandsEvent event) {
        PolycoreClient.registerClientCommands(event.getDispatcher());
    }
}
