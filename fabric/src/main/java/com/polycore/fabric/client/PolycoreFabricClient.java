package com.polycore.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.polycore.Polycore;
import com.polycore.client.ClientConfig;
import com.polycore.client.HudOverlay;
import com.polycore.client.PolycoreClient;
import com.polycore.network.PolyNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric client entry point (never loaded on a dedicated server): key, HUD, client command, ticks and the
 * receivers of the server's packets.
 */
public final class PolycoreFabricClient implements ClientModInitializer {
    // 1.21.9+: categories are identifiers, labelled by "key.category.polycore.main".
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Polycore.MODID, "main"));

    @Override
    public void onInitializeClient() {
        KeyMapping openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.polycore.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        PolycoreClient.init();
        PolycoreClient.openKey = openMenu;

        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> HudOverlay.render(graphics));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean pressed = false;
            while (openMenu.consumeClick()) pressed = true;
            PolycoreClient.onClientTick(pressed);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> PolycoreClient.onLogout());
        // Same commands as PolycoreClient.registerClientCommands, on Fabric's own client command source.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            for (String name : new String[] {"polycore", "pc"}) {
                dispatcher.register(ClientCommandManager.literal(name).executes(c -> {
                    PolycoreClient.requestOpen(ClientConfig.get().lastPage);
                    return 1;
                }));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(PolyNetwork.State.TYPE, (p, ctx) -> PolyNetwork.onState(p));
        ClientPlayNetworking.registerGlobalReceiver(PolyNetwork.ReportContent.TYPE, (p, ctx) -> PolyNetwork.onReportContent(p));
        ClientPlayNetworking.registerGlobalReceiver(PolyNetwork.OpenMenu.TYPE, (p, ctx) -> PolyNetwork.onOpenMenu(p));
        ClientPlayNetworking.registerGlobalReceiver(PolyNetwork.Notify.TYPE, (p, ctx) -> PolyNetwork.onNotify(p));
    }
}
