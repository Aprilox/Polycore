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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import org.lwjgl.glfw.GLFW;

/**
 * Forge client side (never loaded on a dedicated server): key, HUD layer, client command and ticks.
 */
public final class PolycoreForgeClient {
    // Before 1.21.9: the category is its translation key.
    private static final String CATEGORY = "key.categories.polycore";
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.polycore.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    private PolycoreForgeClient() {}

    public static void init(BusGroup modBus) {
        PolycoreClient.init();
        PolycoreClient.openKey = OPEN_MENU;
        RegisterKeyMappingsEvent.getBus(modBus).addListener(e -> e.register(OPEN_MENU));
        if (hasLayerEvent()) Layers.register(modBus);
        // Forge 56/57 (Minecraft 1.21.6/1.21.7) have no HUD layer event: draw during the chat layer, rendered every HUD frame
        else CustomizeGuiOverlayEvent.Chat.BUS.addListener((java.util.function.Consumer<CustomizeGuiOverlayEvent.Chat>) e -> HudOverlay.render(e.getGuiGraphics()));
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> {
            boolean pressed = false;
            while (OPEN_MENU.consumeClick()) pressed = true;
            PolycoreClient.onClientTick(pressed);
        });
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> PolycoreClient.onLogout());
        RegisterClientCommandsEvent.BUS.addListener(e -> PolycoreClient.registerClientCommands(e.getDispatcher()));
    }

    private static boolean hasLayerEvent() {
        try {
            Class.forName("net.minecraftforge.client.event.AddGuiOverlayLayersEvent", false, PolycoreForgeClient.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Forge 58+ HUD layer; only loaded when the event class exists. */
    private static final class Layers {
        static void register(BusGroup modBus) {
            AddGuiOverlayLayersEvent.getBus(modBus).addListener(e -> e.getLayeredDraw().add(
                    ResourceLocation.fromNamespaceAndPath(Polycore.MODID, "hud"), (graphics, deltaTracker) -> HudOverlay.render(graphics)));
        }
    }
}
