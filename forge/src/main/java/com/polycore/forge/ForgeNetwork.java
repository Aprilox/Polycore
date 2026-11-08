package com.polycore.forge;

import com.polycore.Polycore;
import com.polycore.network.PolyNetwork;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkProtocol;

/**
 * Registers the Polycore payloads with Forge (one optional payload channel: vanilla peers may join) and forwards
 * them to the loader-independent handlers.
 */
public final class ForgeNetwork {
    private ForgeNetwork() {}

    private static Channel<CustomPacketPayload> channel;

    static void register() {
        channel = ChannelBuilder.named(Identifier.fromNamespaceAndPath(Polycore.MODID, "main"))
                .networkProtocolVersion(Integer.parseInt(PolyNetwork.PROTOCOL))
                .optional()
                .payloadChannel()
                .protocol(NetworkProtocol.PLAY)
                .flow(PacketFlow.SERVERBOUND)
                .add(PolyNetwork.RequestState.TYPE, PolyNetwork.RequestState.CODEC.cast(), (p, ctx) -> {
                    ServerPlayer player = ctx.getSender();
                    ctx.enqueueWork(() -> { if (player != null) PolyNetwork.onRequestState(player, p); });
                    ctx.setPacketHandled(true);
                })
                .add(PolyNetwork.Action.TYPE, PolyNetwork.Action.CODEC.cast(), (p, ctx) -> {
                    ServerPlayer player = ctx.getSender();
                    ctx.enqueueWork(() -> { if (player != null) PolyNetwork.onAction(player, p); });
                    ctx.setPacketHandled(true);
                })
                .flow(PacketFlow.CLIENTBOUND)
                .add(PolyNetwork.State.TYPE, PolyNetwork.State.CODEC.cast(), (p, ctx) -> {
                    ctx.enqueueWork(() -> PolyNetwork.onState(p));
                    ctx.setPacketHandled(true);
                })
                .add(PolyNetwork.ReportContent.TYPE, PolyNetwork.ReportContent.CODEC.cast(), (p, ctx) -> {
                    ctx.enqueueWork(() -> PolyNetwork.onReportContent(p));
                    ctx.setPacketHandled(true);
                })
                .add(PolyNetwork.OpenMenu.TYPE, PolyNetwork.OpenMenu.CODEC.cast(), (p, ctx) -> {
                    ctx.enqueueWork(() -> PolyNetwork.onOpenMenu(p));
                    ctx.setPacketHandled(true);
                })
                .add(PolyNetwork.Notify.TYPE, PolyNetwork.Notify.CODEC.cast(), (p, ctx) -> {
                    ctx.enqueueWork(() -> PolyNetwork.onNotify(p));
                    ctx.setPacketHandled(true);
                })
                .build();
    }

    /** Sends on the given connection; callers check {@link #remoteHasChannel} first (vanilla peers lack it). */
    public static void send(CustomPacketPayload payload, Connection connection) {
        channel.send(payload, connection);
    }

    /** True when the other side registered every Polycore payload (false for a vanilla peer). */
    public static boolean remoteHasChannel(Connection connection) {
        return channel != null && channel.isRemotePresent(connection);
    }
}
