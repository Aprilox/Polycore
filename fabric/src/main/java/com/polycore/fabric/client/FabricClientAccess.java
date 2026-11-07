package com.polycore.fabric.client;

import com.polycore.fabric.FabricNetwork;
import com.polycore.network.PolyNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-only helpers for {@link com.polycore.fabric.FabricPlatform}. Never loaded on a dedicated server.
 */
public final class FabricClientAccess {
    private FabricClientAccess() {}

    public static boolean serverHasChannel(ResourceLocation channel) {
        return ClientPlayNetworking.canSend(channel);
    }

    public static void sendToServer(PolyNetwork.Payload payload) {
        if (ClientPlayNetworking.canSend(payload.id())) ClientPlayNetworking.send(payload.id(), FabricNetwork.encode(payload));
    }
}
