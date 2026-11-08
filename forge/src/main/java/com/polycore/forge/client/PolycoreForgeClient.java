package com.polycore.forge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.polycore.Polycore;
import com.polycore.client.HudOverlay;
import com.polycore.client.PolycoreClient;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.gui.OverlayRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
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
        modBus.addListener(PolycoreForgeClient::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForgeClient::onLogout);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForgeClient::onClientCommands);
    }

    // Forge 40 (1.18.2): key and HUD layer are registered at client setup (no registration events yet).
    private static void onClientSetup(FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(OPEN_MENU);
        OverlayRegistry.registerOverlayTop("Polycore HUD", (gui, pose, partialTick, width, height) -> HudOverlay.render(new com.polycore.client.ui.GuiGraphics(pose)));
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean pressed = false;
        while (OPEN_MENU.consumeClick()) pressed = true;
        PolycoreClient.onClientTick(pressed);
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        PolycoreClient.onLogout();
    }

    private static void onClientCommands(RegisterClientCommandsEvent event) {
        PolycoreClient.registerClientCommands(event.getDispatcher());
    }
}
