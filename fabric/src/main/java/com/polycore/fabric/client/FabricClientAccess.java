package com.polycore.fabric.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-only helpers for {@link com.polycore.fabric.FabricPlatform}. Never loaded on a dedicated server.
 */
public final class FabricClientAccess {
    private FabricClientAccess() {}

    public static boolean serverHasChannel(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
    }
}
