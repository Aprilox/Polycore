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
        PayloadTypeRegistry.serverboundPlay().register(PolyNetwork.RequestState.TYPE, PolyNetwork.RequestState.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PolyNetwork.Action.TYPE, PolyNetwork.Action.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PolyNetwork.State.TYPE, PolyNetwork.State.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PolyNetwork.ReportContent.TYPE, PolyNetwork.ReportContent.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PolyNetwork.OpenMenu.TYPE, PolyNetwork.OpenMenu.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PolyNetwork.Notify.TYPE, PolyNetwork.Notify.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PolyNetwork.RequestState.TYPE, (p, ctx) -> PolyNetwork.onRequestState(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(PolyNetwork.Action.TYPE, (p, ctx) -> PolyNetwork.onAction(ctx.player(), p));
    }
}
