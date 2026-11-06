package com.polycore.network;

import com.polycore.PolyCore;
import com.polycore.gen.StepProfiler;
import com.polycore.gen.WorldgenPool;
import com.polycore.optim.Optimization;
import com.polycore.pregen.PregenManager;
import com.polycore.util.StatsSnapshot;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Reseau du menu : le client demande l'etat, le serveur repond ; le client peut declencher des actions
 * (reservees aux operateurs ou au proprietaire de la partie solo).
 * Les paquets sont optionnels : un client sans le mod peut rejoindre un serveur qui l'a, et inversement.
 */
public final class PolyNetwork {
    private PolyNetwork() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PolyCore.MODID, path);
    }

    /** Client -> serveur : "envoie-moi l'etat". */
    public record RequestStats() implements CustomPacketPayload {
        public static final RequestStats INSTANCE = new RequestStats();
        public static final Type<RequestStats> TYPE = new Type<>(id("request_stats"));
        public static final StreamCodec<ByteBuf, RequestStats> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> serveur : une action du menu. */
    public record Action(String action, String arg, int value) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(id("action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Action::action,
                ByteBufCodecs.STRING_UTF8, Action::arg,
                ByteBufCodecs.VAR_INT, Action::value,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Serveur -> client : l'etat complet. */
    public record Stats(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Stats> TYPE = new Type<>(id("stats"));
        public static final StreamCodec<ByteBuf, Stats> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, Stats::data,
                Stats::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(RequestStats.TYPE, RequestStats.CODEC, PolyNetwork::handleRequest);
        registrar.playToServer(Action.TYPE, Action.CODEC, PolyNetwork::handleAction);
        registrar.playToClient(Stats.TYPE, Stats.CODEC, PolyNetwork::handleStats);
    }

    private static boolean canEdit(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    private static void handleRequest(RequestStats payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, new Stats(StatsSnapshot.build(canEdit(player))));
            }
        });
    }

    private static void handleAction(Action a, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!canEdit(player)) {
                player.sendSystemMessage(Component.literal("[Polycore] You must be an operator to do that."));
                return;
            }
            switch (a.action()) {
                case "toggle" -> {
                    Optimization o = Optimization.byId(a.arg());
                    if (o != null && o.isToggleable()) {
                        o.setEnabled(a.value() != 0);
                        player.sendSystemMessage(Component.literal("[Polycore] " + o.displayName + ": "
                                + (o.isEnabled() ? "ENABLED" : "DISABLED")));
                    }
                }
                case "pregen" -> {
                    String err = PregenManager.startPregen(player.serverLevel(),
                            player.chunkPosition().x, player.chunkPosition().z,
                            Math.max(1, Math.min(5000, a.value())), 0, player::sendSystemMessage);
                    if (err != null) player.sendSystemMessage(Component.literal("[Polycore] " + err));
                }
                case "bench" -> {
                    String err = PregenManager.startBench(player.serverLevel(), Math.max(2, Math.min(64, a.value())),
                            player::sendSystemMessage);
                    if (err != null) player.sendSystemMessage(Component.literal("[Polycore] " + err));
                }
                case "stop" -> {
                    boolean any = PregenManager.stop();
                    player.sendSystemMessage(Component.literal("[Polycore] " + (any ? "Stopped." : "Nothing was running.")));
                }
                case "profiler_reset" -> StepProfiler.reset();
                case "threads" -> WorldgenPool.resize(a.value());
                default -> PolyCore.LOGGER.warn("[Polycore] Unknown action received: {}", a.action());
            }
            PacketDistributor.sendToPlayer(player, new Stats(StatsSnapshot.build(true)));
        });
    }

    private static void handleStats(Stats payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientStatsCache.set(payload.data()));
    }
}
