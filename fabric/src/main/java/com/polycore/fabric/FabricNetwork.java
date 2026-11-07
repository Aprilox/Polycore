package com.polycore.fabric;

import com.polycore.network.PolyNetwork;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registers the Polycore payloads with Fabric API and forwards them to the loader-independent handlers. Fabric API
 * runs the handlers on the game thread. A vanilla peer is fine: nothing is sent unless it has the channel.
 */
public final class FabricNetwork {
    private FabricNetwork() {}

    /** Both sides: payload types, and the server's receivers. */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(PolyNetwork.RequestState.TYPE, PolyNetwork.RequestState.CODEC);
        PayloadTypeRegistry.playC2S().register(PolyNetwork.Action.TYPE, PolyNetwork.Action.CODEC);
        PayloadTypeRegistry.playS2C().register(PolyNetwork.State.TYPE, PolyNetwork.State.CODEC);
        PayloadTypeRegistry.playS2C().register(PolyNetwork.ReportContent.TYPE, PolyNetwork.ReportContent.CODEC);
        PayloadTypeRegistry.playS2C().register(PolyNetwork.OpenMenu.TYPE, PolyNetwork.OpenMenu.CODEC);
        PayloadTypeRegistry.playS2C().register(PolyNetwork.Notify.TYPE, PolyNetwork.Notify.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PolyNetwork.RequestState.TYPE, (p, ctx) -> PolyNetwork.onRequestState(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(PolyNetwork.Action.TYPE, (p, ctx) -> PolyNetwork.onAction(ctx.player(), p));
    }
}
