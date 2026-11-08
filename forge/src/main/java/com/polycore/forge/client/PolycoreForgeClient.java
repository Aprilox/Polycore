package com.polycore.forge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.polycore.Polycore;
import com.polycore.client.HudOverlay;
import com.polycore.client.PolycoreClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
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
        AddGuiOverlayLayersEvent.getBus(modBus).addListener(e -> e.getLayeredDraw().add(
                ResourceLocation.fromNamespaceAndPath(Polycore.MODID, "hud"), (graphics, deltaTracker) -> HudOverlay.render(graphics)));
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> {
            boolean pressed = false;
            while (OPEN_MENU.consumeClick()) pressed = true;
            PolycoreClient.onClientTick(pressed);
        });
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> PolycoreClient.onLogout());
        RegisterClientCommandsEvent.BUS.addListener(e -> PolycoreClient.registerClientCommands(e.getDispatcher()));
    }
}
