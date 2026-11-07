package com.polycore.forge.client;

import com.polycore.forge.ForgeNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-only helpers for {@link com.polycore.forge.ForgePlatform}. Never loaded on a dedicated server.
 */
public final class ForgeClientAccess {
    private ForgeClientAccess() {}

    public static boolean serverHasChannel() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && ForgeNetwork.remoteHasChannel(connection.getConnection());
    }

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) ForgeNetwork.send(payload, connection.getConnection());
    }
}
