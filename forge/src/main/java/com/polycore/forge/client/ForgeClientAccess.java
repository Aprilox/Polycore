package com.polycore.forge.client;

import com.polycore.forge.ForgeNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import com.polycore.network.PolyNetwork;

/**
 * Client-only helpers for {@link com.polycore.forge.ForgePlatform}. Never loaded on a dedicated server.
 */
public final class ForgeClientAccess {
    private ForgeClientAccess() {}

    public static boolean serverHasChannel() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && ForgeNetwork.remoteHasChannel(connection.getConnection());
    }

    public static void sendToServer(PolyNetwork.Payload payload) {
        if (Minecraft.getInstance().getConnection() != null) ForgeNetwork.sendToServer(payload);
    }
}
