package com.polycore.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Consumer;
import java.util.function.Function;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric client entry point (never loaded on a dedicated server): key, HUD, client command, ticks and the
 * receivers of the server's packets.
 */
public final class PolycoreFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMapping openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.polycore.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.polycore"));
        PolycoreClient.init();
        PolycoreClient.openKey = openMenu;

        HudRenderCallback.EVENT.register((pose, partialTick) -> HudOverlay.render(new com.polycore.client.ui.GuiGraphics(pose)));
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

        toClient(PolyNetwork.State.ID, PolyNetwork.State::read, PolyNetwork::onState);
        toClient(PolyNetwork.ReportContent.ID, PolyNetwork.ReportContent::read, PolyNetwork::onReportContent);
        toClient(PolyNetwork.OpenMenu.ID, PolyNetwork.OpenMenu::read, PolyNetwork::onOpenMenu);
        toClient(PolyNetwork.Notify.ID, PolyNetwork.Notify::read, PolyNetwork::onNotify);
    }

    private static <T> void toClient(ResourceLocation id, Function<FriendlyByteBuf, T> read, Consumer<T> handler) {
        // Fabric calls this on the network thread: decode here, handle on the client thread.
        ClientPlayNetworking.registerGlobalReceiver(id, (client, listener, buf, sender) -> {
            T payload = read.apply(buf);
            client.execute(() -> handler.accept(payload));
        });
    }
}
