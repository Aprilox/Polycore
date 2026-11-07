package com.polycore.neoforge;

import com.polycore.network.PolyNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the Polycore payloads with NeoForge and forwards them to the loader-independent handlers.
 */
public final class NeoForgeNetwork {
    private NeoForgeNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(PolyNetwork.PROTOCOL).optional();

        r.playToServer(PolyNetwork.RequestState.TYPE, PolyNetwork.RequestState.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) PolyNetwork.onRequestState(player, p);
        }));
        r.playToServer(PolyNetwork.Action.TYPE, PolyNetwork.Action.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) PolyNetwork.onAction(player, p);
        }));

        r.playToClient(PolyNetwork.State.TYPE, PolyNetwork.State.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PolyNetwork.onState(p)));
        r.playToClient(PolyNetwork.ReportContent.TYPE, PolyNetwork.ReportContent.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PolyNetwork.onReportContent(p)));
        r.playToClient(PolyNetwork.OpenMenu.TYPE, PolyNetwork.OpenMenu.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PolyNetwork.onOpenMenu(p)));
        r.playToClient(PolyNetwork.Notify.TYPE, PolyNetwork.Notify.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PolyNetwork.onNotify(p)));
    }
}
