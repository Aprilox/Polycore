package com.polycore.neoforge.client;

import com.polycore.neoforge.NeoForgeNetwork;
import com.polycore.network.PolyNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-only helpers for {@link com.polycore.neoforge.NeoForgePlatform}. Never loaded on a dedicated server.
 */
public final class NeoForgeClientAccess {
    private NeoForgeClientAccess() {}

    public static boolean serverHasChannel(ResourceLocation channel) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.isConnected(channel);
    }

    public static void sendToServer(PolyNetwork.Payload payload) {
        if (Minecraft.getInstance().getConnection() != null) NeoForgeNetwork.sendToServer(payload);
    }
}
