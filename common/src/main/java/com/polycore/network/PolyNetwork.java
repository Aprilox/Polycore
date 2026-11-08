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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.polycore.platform.Services;

/**
 * Network protocol between the Polycore client menu and the server.
 *
 * <p>Payloads and handling logic are loader-independent (records written to a vanilla {@link FriendlyByteBuf});
 * each loader only registers them (see forge/ForgeNetwork). Every payload is optional: vanilla clients can
 * join a Polycore server and the other way round.</p>
 */
public final class PolyNetwork {
    public static final String PROTOCOL = "2";

    private PolyNetwork() {}

    private static ResourceLocation channel(String path) {
        return new ResourceLocation(Polycore.MODID, path);
    }

    /** A Polycore message: its channel id and its encoder (each record also has a static {@code read}). */
    public interface Payload {
        ResourceLocation id();

        void write(FriendlyByteBuf buf);
    }

    /** Client -> server: send me the current state. */
    public record RequestState(boolean full) implements Payload {
        public static final ResourceLocation ID = channel("request_state");

        public static RequestState read(FriendlyByteBuf buf) {
            return new RequestState(buf.readBoolean());
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(full);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }
    }

    /** Client -> server: an action from the menu. */
    public record Action(String action, String arg, String value) implements Payload {
        public static final ResourceLocation ID = channel("action");

        public static Action read(FriendlyByteBuf buf) {
            return new Action(buf.readUtf(), buf.readUtf(), buf.readUtf());
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(action);
            buf.writeUtf(arg);
            buf.writeUtf(value);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }
    }

    /** Server -> client: full state for the menu and the HUD. */
    public record State(CompoundTag data) implements Payload {
        public static final ResourceLocation ID = channel("state");

        public static State read(FriendlyByteBuf buf) {
            return new State(nbt(buf));
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeNbt(data);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }
    }

    /** Server -> client: content of a report file. */
    public record ReportContent(CompoundTag data) implements Payload {
        public static final ResourceLocation ID = channel("report_content");

        public static ReportContent read(FriendlyByteBuf buf) {
            return new ReportContent(nbt(buf));
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeNbt(data);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }
    }

    /** Server -> client: open the Polycore menu on a page (sent by /polycore). */
    public record OpenMenu(String page) implements Payload {
        public static final ResourceLocation ID = channel("open_menu");

        public static OpenMenu read(FriendlyByteBuf buf) {
            return new OpenMenu(buf.readUtf());
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(page);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }
    }

    /** Server -> client: a notification, shown as a toast instead of a chat message. */
    public record Notify(String level, String title, String message) implements Payload {
        public static final ResourceLocation ID = channel("notify");

        public static Notify read(FriendlyByteBuf buf) {
            return new Notify(buf.readUtf(), buf.readUtf(), buf.readUtf());
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(level);
            buf.writeUtf(title);
            buf.writeUtf(message);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }
    }

    private static CompoundTag nbt(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return tag == null ? new CompoundTag() : tag;
    }

    public static void sendState(ServerPlayer player) {
        Services.PLATFORM.sendToPlayer(player, new State(StatsSnapshot.build(player)));
    }

    // ------------------------------------------------------------------ server-side handling (server thread)

    public static void onRequestState(ServerPlayer player, RequestState payload) {
        if (payload.full()) sendState(player);
        else Services.PLATFORM.sendToPlayer(player, new State(StatsSnapshot.buildHud(player)));
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
                PregenManager.startPregen(player.getLevel(), player.chunkPosition().x, player.chunkPosition().z,
                        Math.max(1, Math.min(5000, radius)), 0, keepTps, player, player.createCommandSourceStack());
            }
            case "stop" -> PregenManager.stop(player.createCommandSourceStack());
            case "bench" -> PregenManager.startBench(player.getLevel(), Math.max(2, Math.min(64, parseInt(a.arg(), 10))),
                    player, player.createCommandSourceStack());
            case "hash" -> PregenManager.hash(player.getLevel(), player.chunkPosition().x, player.chunkPosition().z,
                    Math.max(0, Math.min(8, parseInt(a.arg(), 4))), player.createCommandSourceStack());
            case "lag_capture" -> LagCapture.start(PolyConfig.get().lagCaptureSeconds, player.createCommandSourceStack(), false);
            case "lag_tp" -> LagCapture.teleport(player, parseInt(a.arg(), -1));
            case "lag_back" -> LagCapture.back(player, "go".equals(a.value()));
            case "lag_alerts" -> LagMonitor.setAlerts(player, Boolean.parseBoolean(a.value())); // the toggle shows the state
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
