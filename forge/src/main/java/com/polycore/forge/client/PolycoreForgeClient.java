package com.polycore.forge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.polycore.Polycore;
import com.polycore.client.HudOverlay;
import com.polycore.client.PolycoreClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;

/**
 * Forge client side (never loaded on a dedicated server): key, HUD layer, client command and ticks.
 */
public final class PolycoreForgeClient {
    // 1.21.9+: categories are identifiers, labelled by "key.category.polycore.main".
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Polycore.MODID, "main"));
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.polycore.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY);

    private PolycoreForgeClient() {}

    public static void init() {
        PolycoreClient.init();
        PolycoreClient.openKey = OPEN_MENU;
        RegisterKeyMappingsEvent.BUS.addListener(e -> e.register(OPEN_MENU));
        AddGuiOverlayLayersEvent.BUS.addListener(e -> e.getLayeredDraw().add(
                Identifier.fromNamespaceAndPath(Polycore.MODID, "hud"), (graphics, deltaTracker) -> HudOverlay.extractRenderState(graphics)));
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> {
            boolean pressed = false;
            while (OPEN_MENU.consumeClick()) pressed = true;
            PolycoreClient.onClientTick(pressed);
        });
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> PolycoreClient.onLogout());
        RegisterClientCommandsEvent.BUS.addListener(e -> PolycoreClient.registerClientCommands(e.getDispatcher()));
    }
}
