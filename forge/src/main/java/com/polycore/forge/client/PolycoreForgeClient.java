package com.polycore.forge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.polycore.Polycore;
import com.polycore.client.HudOverlay;
import com.polycore.client.PolycoreClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
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
        if (hasLayerEvent()) Layers.register(modBus);
        // Forge 51 (Minecraft 1.21) has no HUD layer event: draw during the chat layer, rendered every HUD frame
        else MinecraftForge.EVENT_BUS.addListener((CustomizeGuiOverlayEvent.Chat e) -> HudOverlay.render(e.getGuiGraphics()));
        MinecraftForge.EVENT_BUS.addListener(PolycoreForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForgeClient::onLogout);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForgeClient::onClientCommands);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }

    private static boolean hasLayerEvent() {
        try {
            Class.forName("net.minecraftforge.client.event.AddGuiOverlayLayersEvent", false, PolycoreForgeClient.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Forge 52+ HUD layer; only loaded when the event class exists. */
    private static final class Layers {
        static void register(IEventBus modBus) {
            modBus.addListener(Layers::onAddLayers);
        }

        private static void onAddLayers(AddGuiOverlayLayersEvent event) {
            event.getLayeredDraw().add(ResourceLocation.fromNamespaceAndPath(Polycore.MODID, "hud"),
                    (graphics, deltaTracker) -> HudOverlay.render(graphics));
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
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
