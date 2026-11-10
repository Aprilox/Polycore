package com.polycore.neoforge;

import com.polycore.Polycore;
import com.polycore.network.PolyNetwork;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.PlayNetworkDirection;
import net.neoforged.neoforge.network.simple.MessageFunctions;
import net.neoforged.neoforge.network.simple.SimpleChannel;

/**
 * Registers the Polycore messages on one NeoForge 20.2 / 20.3 {@link SimpleChannel} (the network API before
 * NeoForge 20.4's payloads) that accepts a peer without it (vanilla clients and servers may join) and forwards them
 * to the loader-independent handlers, on the game thread.
 */
public final class NeoForgeNetwork {
    private NeoForgeNetwork() {}

    private static SimpleChannel channel;
    private static int nextId;

    static void register() {
        channel = NetworkRegistry.newSimpleChannel(new ResourceLocation(Polycore.MODID, "main"), () -> PolyNetwork.PROTOCOL,
                NetworkRegistry.acceptMissingOr(PolyNetwork.PROTOCOL), NetworkRegistry.acceptMissingOr(PolyNetwork.PROTOCOL));
        toServer(PolyNetwork.RequestState.class, PolyNetwork.RequestState::read, PolyNetwork::onRequestState);
        toServer(PolyNetwork.Action.class, PolyNetwork.Action::read, PolyNetwork::onAction);
        toClient(PolyNetwork.State.class, PolyNetwork.State::read, PolyNetwork::onState);
        toClient(PolyNetwork.ReportContent.class, PolyNetwork.ReportContent::read, PolyNetwork::onReportContent);
        toClient(PolyNetwork.OpenMenu.class, PolyNetwork.OpenMenu::read, PolyNetwork::onOpenMenu);
        toClient(PolyNetwork.Notify.class, PolyNetwork.Notify::read, PolyNetwork::onNotify);
    }

    private static <T extends PolyNetwork.Payload> void toServer(Class<T> type, MessageFunctions.MessageDecoder<T> read,
                                                                 BiConsumer<ServerPlayer, T> handler) {
        channel.messageBuilder(type, nextId++, PlayNetworkDirection.PLAY_TO_SERVER)
                .encoder(PolyNetwork.Payload::write).decoder(read)
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer player = ctx.getSender();
                    if (player != null) handler.accept(player, p);
                })
                .add();
    }

    private static <T extends PolyNetwork.Payload> void toClient(Class<T> type, MessageFunctions.MessageDecoder<T> read,
                                                                 Consumer<T> handler) {
        channel.messageBuilder(type, nextId++, PlayNetworkDirection.PLAY_TO_CLIENT)
                .encoder(PolyNetwork.Payload::write).decoder(read)
                .consumerMainThread((p, ctx) -> handler.accept(p))
                .add();
    }

    /** Callers check {@link #remoteHasChannel} first (vanilla peers lack the channel). */
    static void sendToPlayer(PolyNetwork.Payload payload, ServerPlayer player) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(PolyNetwork.Payload payload) {
        channel.sendToServer(payload);
    }

    /** True when the other side has the Polycore channel (false for a vanilla peer). */
    public static boolean remoteHasChannel(Connection connection) {
        return channel != null && channel.isRemotePresent(connection);
    }
}
