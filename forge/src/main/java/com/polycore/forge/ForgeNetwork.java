package com.polycore.forge;

import com.polycore.Polycore;
import com.polycore.network.PolyNetwork;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Registers the Polycore messages on one Forge {@link SimpleChannel} that accepts a peer without it (vanilla clients
 * and servers may join) and forwards them to the loader-independent handlers.
 */
public final class ForgeNetwork {
    private ForgeNetwork() {}

    private static SimpleChannel channel;
    private static int nextId;

    static void register() {
        int version = Integer.parseInt(PolyNetwork.PROTOCOL);
        channel = ChannelBuilder.named(new ResourceLocation(Polycore.MODID, "main")).networkProtocolVersion(version)
                .acceptedVersions(Channel.VersionTest.ACCEPT_MISSING.or(Channel.VersionTest.ACCEPT_VANILLA)
                        .or(Channel.VersionTest.exact(version)))
                .simpleChannel();
        toServer(PolyNetwork.RequestState.class, PolyNetwork.RequestState::read, PolyNetwork::onRequestState);
        toServer(PolyNetwork.Action.class, PolyNetwork.Action::read, PolyNetwork::onAction);
        toClient(PolyNetwork.State.class, PolyNetwork.State::read, PolyNetwork::onState);
        toClient(PolyNetwork.ReportContent.class, PolyNetwork.ReportContent::read, PolyNetwork::onReportContent);
        toClient(PolyNetwork.OpenMenu.class, PolyNetwork.OpenMenu::read, PolyNetwork::onOpenMenu);
        toClient(PolyNetwork.Notify.class, PolyNetwork.Notify::read, PolyNetwork::onNotify);
    }

    private static <T extends PolyNetwork.Payload> void toServer(Class<T> type, Function<FriendlyByteBuf, T> read,
                                                                 BiConsumer<ServerPlayer, T> handler) {
        channel.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PolyNetwork.Payload::write).decoder(read)
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer player = ctx.getSender();
                    if (player != null) handler.accept(player, p);
                })
                .add();
    }

    private static <T extends PolyNetwork.Payload> void toClient(Class<T> type, Function<FriendlyByteBuf, T> read,
                                                                 java.util.function.Consumer<T> handler) {
        channel.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PolyNetwork.Payload::write).decoder(read)
                .consumerMainThread((p, ctx) -> handler.accept(p))
                .add();
    }

    /** Callers check {@link #remoteHasChannel} first (vanilla peers lack the channel). */
    public static void sendToPlayer(PolyNetwork.Payload payload, ServerPlayer player) {
        channel.send(payload, PacketDistributor.PLAYER.with(player));
    }

    public static void sendToServer(PolyNetwork.Payload payload) {
        channel.send(payload, PacketDistributor.SERVER.noArg());
    }

    /** True when the other side has the Polycore channel (false for a vanilla peer). */
    public static boolean remoteHasChannel(Connection connection) {
        return channel != null && channel.isRemotePresent(connection);
    }
}
