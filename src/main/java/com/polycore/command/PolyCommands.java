package com.polycore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.polycore.config.PolyConfig;
import com.polycore.gen.StepProfiler;
import com.polycore.gen.WorldgenPool;
import com.polycore.lag.LagCapture;
import com.polycore.lag.LagMonitor;
import com.polycore.menu.VanillaMenu;
import com.polycore.network.PolyNetwork;
import com.polycore.optim.Optimization;
import com.polycore.platform.Services;
import com.polycore.pregen.PregenManager;
import com.polycore.pregen.PregenTask;
import com.polycore.settings.Setting;
import com.polycore.settings.Settings;
import com.polycore.util.Notifier;
import com.polycore.util.Permissions;
import com.polycore.util.ServerMetrics;
import java.util.Arrays;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /polycore (alias /pc).
 *
 * <ul>
 *   <li>Players with Polycore on their client: the command opens the Polycore menu (the client also handles a bare
 *       "/polycore" locally, exactly like the K key).</li>
 *   <li>Players without it: a chest-style menu built from vanilla items.</li>
 *   <li>Console: text output, so servers can be managed from the console too.</li>
 * </ul>
 * Changing anything requires operator level 2 or being the singleplayer host. Feedback never uses the chat
 * for players (toasts or the action bar instead).
 */
public final class PolyCommands {
    private PolyCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralCommandNode<CommandSourceStack> root = d.register(Commands.literal("polycore")
                .requires(Permissions::canView)
                .executes(c -> openMenu(c, "server.dashboard"))
                .then(Commands.literal("menu").executes(c -> openMenu(c, "server.dashboard")))
                .then(Commands.literal("status").executes(c -> openMenu(c, "server.optimizations")))
                .then(Commands.literal("stats").executes(c -> openMenu(c, "server.dashboard")))
                .then(Commands.literal("profiler")
                        .executes(c -> openMenu(c, "server.profiler"))
                        .then(Commands.literal("reset").requires(Permissions::canEdit).executes(PolyCommands::profilerReset)))
                .then(Commands.literal("report").executes(c -> openMenu(c, "server.reports")))
                .then(Commands.literal("lag")
                        .executes(c -> openMenu(c, "server.lag"))
                        .then(Commands.literal("analyze").requires(Permissions::canEdit)
                                .executes(c -> lagAnalyze(c, PolyConfig.get().lagCaptureSeconds))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 300))
                                        .executes(c -> lagAnalyze(c, IntegerArgumentType.getInteger(c, "seconds")))))
                        .then(Commands.literal("alerts").requires(Permissions::canEdit)
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(PolyCommands::lagAlerts))))
                .then(Commands.literal("toggle").requires(Permissions::canEdit)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Optimization.values()).filter(o -> o.kind == Optimization.Kind.ACTIVE).map(o -> o.id), b))
                                .executes(c -> toggle(c, null))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(c -> toggle(c, BoolArgumentType.getBool(c, "enabled"))))))
                .then(Commands.literal("set").requires(Permissions::canEdit)
                        .then(Commands.argument("key", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Settings.all().stream().map(s -> s.key), b))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(PolyCommands::set))))
                .then(Commands.literal("threads").requires(Permissions::canEdit)
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, WorldgenPool.maxThreads()))
                                .executes(c -> setValue(c, Settings.POOL_THREADS, Integer.toString(IntegerArgumentType.getInteger(c, "count"))))))
                .then(Commands.literal("reload").requires(Permissions::canEdit).executes(PolyCommands::reload))
                .then(Commands.literal("pregen").requires(Permissions::canEdit)
                        .then(Commands.literal("start")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 5000))
                                        .executes(c -> pregenStart(c, 0))
                                        .then(Commands.argument("concurrency", IntegerArgumentType.integer(1, 4096))
                                                .executes(c -> pregenStart(c, IntegerArgumentType.getInteger(c, "concurrency"))))))
                        .then(Commands.literal("stop").executes(c -> {
                            PregenManager.stop(c.getSource());
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(PolyCommands::pregenStatus)))
                .then(Commands.literal("bench").requires(Permissions::canEdit)
                        .executes(c -> bench(c, PolyConfig.get().benchDefaultRadius))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(2, 64))
                                .executes(c -> bench(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("hash").requires(Permissions::canEdit)
                        .executes(c -> hash(c, 4))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8))
                                .executes(c -> hash(c, IntegerArgumentType.getInteger(c, "radius"))))));

        d.register(Commands.literal("pc").requires(Permissions::canView)
                .executes(c -> openMenu(c, "server.dashboard"))
                .redirect(root));
    }

    // ------------------------------------------------------------------ menu

    private static int openMenu(CommandContext<CommandSourceStack> c, String page) {
        CommandSourceStack s = c.getSource();
        ServerPlayer p = s.getPlayer();
        if (p == null) {
            if (page.equals("server.lag")) {
                for (String l : LagMonitor.textSummary()) s.sendSuccess(() -> Component.literal(l), false);
            } else {
                printConsoleSummary(s);
            }
            return 1;
        }
        if (Notifier.hasClientMod(p)) {
            Services.PLATFORM.sendToPlayer(p, new PolyNetwork.OpenMenu(page));
            PolyNetwork.sendState(p);
        } else {
            VanillaMenu.open(p);
        }
        return 1;
    }

    /** Console only: a compact text summary. */
    private static void printConsoleSummary(CommandSourceStack s) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("=== Polycore " + Services.PLATFORM.modVersion() + " ===");
        lines.add(String.format("Server: %.1f ms/tick (max %.1f), %.1f TPS, CPU %s of %d cores, memory %d / %d MB",
                ServerMetrics.mspt(), ServerMetrics.maxMspt(), ServerMetrics.tps(),
                ServerMetrics.processCpu() < 0 ? "?" : String.format("%.0f%%", ServerMetrics.processCpu() * 100),
                ServerMetrics.cores(), ServerMetrics.usedMemoryMb(), ServerMetrics.maxMemoryMb()));
        lines.add(String.format("Generation: %.1f chunks/s, %d generated, pool %d/%d threads",
                StepProfiler.chunksPerSecond(10), StepProfiler.totalChunks(), WorldgenPool.size(), WorldgenPool.maxThreads()));
        for (Optimization o : Optimization.values()) lines.add(" - " + o.displayName + " [" + o.id + "]: " + o.status());
        PregenTask t = PregenManager.current();
        if (t != null && t.state() == PregenTask.State.RUNNING) lines.add(t.label + ": " + t.progressLine());
        lines.add("Settings: /polycore set <key> <value> - keys: " + String.join(", ", Settings.all().stream().map(x -> x.key).toList()));
        for (String l : lines) s.sendSuccess(() -> Component.literal(l), false);
    }

    // ------------------------------------------------------------------ edits

    private static int toggle(CommandContext<CommandSourceStack> c, Boolean value) {
        CommandSourceStack s = c.getSource();
        String id = StringArgumentType.getString(c, "id");
        Optimization o = Optimization.byId(id);
        if (o == null) {
            Notifier.send(s, Notifier.Level.ERROR, "Unknown optimization", id);
            return 0;
        }
        boolean v = value != null ? value : !o.isEnabled();
        String err = o.setEnabled(v);
        if (err != null) {
            Notifier.send(s, Notifier.Level.ERROR, "Not changed", err);
            return 0;
        }
        Notifier.send(s, Notifier.Level.SUCCESS, o.displayName, v ? "Enabled (saved)" : "Disabled (saved)");
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> c) {
        return setValue(c, StringArgumentType.getString(c, "key"), StringArgumentType.getString(c, "value"));
    }

    private static int setValue(CommandContext<CommandSourceStack> c, String key, String value) {
        CommandSourceStack s = c.getSource();
        String err = Settings.set(key, value);
        if (err != null) {
            Notifier.send(s, Notifier.Level.ERROR, "Setting not saved", err);
            return 0;
        }
        Setting setting = Settings.get(key);
        Notifier.send(s, Notifier.Level.SUCCESS, setting.label, setting.displayValue() + " (saved)");
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        PolyConfig.load();
        Settings.applyRuntime();
        Notifier.send(c.getSource(), Notifier.Level.SUCCESS, "Config reloaded", PolyConfig.file().getFileName().toString());
        return 1;
    }

    private static int profilerReset(CommandContext<CommandSourceStack> c) {
        StepProfiler.reset();
        Notifier.send(c.getSource(), Notifier.Level.INFO, "Profiler", "Reset");
        return 1;
    }

    // ------------------------------------------------------------------ lag analyzer

    private static int lagAnalyze(CommandContext<CommandSourceStack> c, int seconds) {
        return LagCapture.start(seconds, c.getSource(), false) ? 1 : 0;
    }

    private static int lagAlerts(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) {
            Notifier.send(c.getSource(), Notifier.Level.ERROR, "Lag alerts", "Only players receive alerts; the console always gets them.");
            return 0;
        }
        boolean on = BoolArgumentType.getBool(c, "enabled");
        LagMonitor.setAlerts(p, on);
        Notifier.send(p, Notifier.Level.SUCCESS, "Lag alerts", on ? "On: you will be told when the server lags" : "Off");
        return 1;
    }

    // ------------------------------------------------------------------ tools

    private static int pregenStart(CommandContext<CommandSourceStack> c, int concurrency) {
        CommandSourceStack s = c.getSource();
        int radius = IntegerArgumentType.getInteger(c, "radius");
        BlockPos pos = BlockPos.containing(s.getPosition());
        PregenManager.startPregen(s.getLevel(), pos.getX() >> 4, pos.getZ() >> 4, radius, concurrency,
                PolyConfig.get().pregenKeepTps, s.getPlayer(), s);
        return 1;
    }

    private static int pregenStatus(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        PregenTask t = PregenManager.current();
        if (s.getPlayer() != null) return openMenu(c, "server.tools");
        s.sendSuccess(() -> Component.literal(t == null ? "No pre-generation has been started."
                : t.label + " (" + t.state() + "): " + t.progressLine()), false);
        return 1;
    }

    private static int bench(CommandContext<CommandSourceStack> c, int radius) {
        CommandSourceStack s = c.getSource();
        PregenManager.startBench(s.getLevel(), radius, s.getPlayer(), s);
        return 1;
    }

    private static int hash(CommandContext<CommandSourceStack> c, int radius) {
        CommandSourceStack s = c.getSource();
        BlockPos pos = BlockPos.containing(s.getPosition());
        PregenManager.hash(s.getLevel(), pos.getX() >> 4, pos.getZ() >> 4, radius, s);
        return 1;
    }
}
