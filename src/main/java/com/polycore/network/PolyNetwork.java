package com.polycore.network;

import com.polycore.Polycore;
import com.polycore.config.PolyConfig;
import com.polycore.gen.StepProfiler;
import com.polycore.lag.LagCapture;
import com.polycore.lag.LagMonitor;
import com.polycore.optim.Optimization;
import com.polycore.pregen.PregenManager;
import com.polycore.pregen.Reports;
import com.polycore.settings.Settings;
import com.polycore.util.Notifier;
import com.polycore.util.Permissions;
import com.polycore.util.StatsSnapshot;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.polycore.platform.Services;

/**
 * Network protocol between the Polycore client menu and the server.
 *
 * <p>Payloads and handling logic are loader-independent (vanilla {@link CustomPacketPayload} + {@link StreamCodec});
 * each loader only registers them (see neoforge/NeoForgeNetwork). Every payload is optional: vanilla clients can
 * join a Polycore server and the other way round.</p>
 */
public final class PolyNetwork {
    public static final String PROTOCOL = "2";

    private PolyNetwork() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Polycore.MODID, path);
    }

    /** Client -> server: send me the current state. */
    public record RequestState(boolean full) implements CustomPacketPayload {
        public static final Type<RequestState> TYPE = new Type<>(id("request_state"));
        public static final StreamCodec<ByteBuf, RequestState> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, RequestState::full, RequestState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: an action from the menu. */
    public record Action(String action, String arg, String value) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(id("action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Action::action,
                ByteBufCodecs.STRING_UTF8, Action::arg,
                ByteBufCodecs.STRING_UTF8, Action::value,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: full state for the menu and the HUD. */
    public record State(CompoundTag data) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(id("state"));
        public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, State::data, State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: content of a report file. */
    public record ReportContent(CompoundTag data) implements CustomPacketPayload {
        public static final Type<ReportContent> TYPE = new Type<>(id("report_content"));
        public static final StreamCodec<ByteBuf, ReportContent> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, ReportContent::data, ReportContent::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: open the Polycore menu on a page (sent by /polycore). */
    public record OpenMenu(String page) implements CustomPacketPayload {
        public static final Type<OpenMenu> TYPE = new Type<>(id("open_menu"));
        public static final StreamCodec<ByteBuf, OpenMenu> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenMenu::page, OpenMenu::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: a notification, shown as a toast instead of a chat message. */
    public record Notify(String level, String title, String message) implements CustomPacketPayload {
        public static final Type<Notify> TYPE = new Type<>(id("notify"));
        public static final StreamCodec<ByteBuf, Notify> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Notify::level,
                ByteBufCodecs.STRING_UTF8, Notify::title,
                ByteBufCodecs.STRING_UTF8, Notify::message,
                Notify::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void sendState(ServerPlayer player) {
        Services.PLATFORM.sendToPlayer(player, new State(StatsSnapshot.build(player)));
    }

    // ------------------------------------------------------------------ server-side handling (server thread)

    public static void onRequestState(ServerPlayer player, RequestState payload) {
        sendState(player);
    }

    public static void onAction(ServerPlayer player, Action a) {
        // Read-only action
        if (a.action().equals("report")) {
            if (Permissions.canView(player)) {
                Services.PLATFORM.sendToPlayer(player, new ReportContent(Reports.read(a.arg())));
            }
            return;
        }
        if (!Permissions.canEdit(player)) {
            Notifier.send(player, Notifier.Level.ERROR, "Read-only", "Only operators can change the server.");
            return;
        }
        switch (a.action()) {
            case "set" -> {
                String err = Settings.set(a.arg(), a.value());
                if (err != null) Notifier.send(player, Notifier.Level.ERROR, "Setting not saved", err);
            }
            case "toggle" -> {
                Optimization o = Optimization.byId(a.arg());
                String err = o == null ? "Unknown optimization." : o.setEnabled(Boolean.parseBoolean(a.value()));
                if (err != null) Notifier.send(player, Notifier.Level.ERROR, "Not changed", err);
                else Notifier.send(player, Notifier.Level.SUCCESS, o.displayName, o.isEnabled() ? "Enabled" : "Disabled");
            }
            case "pregen" -> {
                int radius = parseInt(a.arg(), 32);
                boolean keepTps = Boolean.parseBoolean(a.value());
                PregenManager.startPregen(player.serverLevel(), player.chunkPosition().x, player.chunkPosition().z,
                        Math.max(1, Math.min(5000, radius)), 0, keepTps, player, player.createCommandSourceStack());
            }
            case "stop" -> PregenManager.stop(player.createCommandSourceStack());
            case "bench" -> PregenManager.startBench(player.serverLevel(), Math.max(2, Math.min(64, parseInt(a.arg(), 10))),
                    player, player.createCommandSourceStack());
            case "hash" -> PregenManager.hash(player.serverLevel(), player.chunkPosition().x, player.chunkPosition().z,
                    Math.max(0, Math.min(8, parseInt(a.arg(), 4))), player.createCommandSourceStack());
            case "lag_capture" -> LagCapture.start(PolyConfig.get().lagCaptureSeconds, player.createCommandSourceStack(), false);
            case "lag_tp" -> LagCapture.teleport(player, parseInt(a.arg(), -1));
            case "lag_alerts" -> {
                boolean on = Boolean.parseBoolean(a.value());
                LagMonitor.setAlerts(player, on);
                Notifier.send(player, Notifier.Level.SUCCESS, "Lag alerts", on ? "On: you will be told when the server lags" : "Off");
            }
            case "profiler_reset" -> {
                StepProfiler.reset();
                Notifier.send(player, Notifier.Level.INFO, "Profiler", "Reset");
            }
            default -> Polycore.LOGGER.warn("[Polycore] Unknown action received: {}", a.action());
        }
        sendState(player);
    }

    // ------------------------------------------------------------------ client-side handling (client thread)

    public static void onState(State p) {
        ClientBridge.onState(p.data());
    }

    public static void onReportContent(ReportContent p) {
        ClientBridge.onReport(p.data());
    }

    public static void onOpenMenu(OpenMenu p) {
        ClientBridge.onOpenMenu(p.page());
    }

    public static void onNotify(Notify p) {
        ClientBridge.onNotify(p.level(), p.title(), p.message());
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
