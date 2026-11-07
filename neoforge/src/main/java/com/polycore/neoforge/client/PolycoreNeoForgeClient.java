package com.polycore.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.polycore.Polycore;
import com.polycore.client.HudOverlay;
import com.polycore.client.PolycoreClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.bus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/**
 * Forge client side (never loaded on a dedicated server): key, HUD layer, client command and ticks.
 */
public final class PolycoreNeoForgeClient {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.polycore.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.polycore");

    private PolycoreNeoForgeClient() {}

    public static void init(IEventBus modBus) {
        PolycoreClient.init();
        PolycoreClient.openKey = OPEN_MENU;
        modBus.addListener(PolycoreNeoForgeClient::onRegisterKeys);
        modBus.addListener(PolycoreNeoForgeClient::onAddLayers);
        NeoForge.EVENT_BUS.addListener(PolycoreNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(PolycoreNeoForgeClient::onLogout);
        NeoForge.EVENT_BUS.addListener(PolycoreNeoForgeClient::onClientCommands);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }

    private static void onAddLayers(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(new ResourceLocation(Polycore.MODID, "hud"), (gui, graphics, partialTick, width, height) -> HudOverlay.render(graphics));
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
