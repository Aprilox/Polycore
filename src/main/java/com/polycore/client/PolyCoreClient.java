package com.polycore.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.polycore.PolyCore;
import com.polycore.network.ClientStatsCache;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * Partie client : touche K pour ouvrir le menu Polycore. Jamais chargee sur un serveur dedie.
 */
@Mod(value = PolyCore.MODID, dist = Dist.CLIENT)
public final class PolyCoreClient {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.polycore.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.polycore");

    public PolyCoreClient(IEventBus modBus) {
        modBus.addListener(PolyCoreClient::onRegisterKeys);
        NeoForge.EVENT_BUS.addListener(PolyCoreClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(PolyCoreClient::onLogout);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_MENU.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new PolyCoreScreen());
            }
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStatsCache.clear();
    }
}
