package com.polycore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.polycore.PolyConfig;
import com.polycore.gen.StepProfiler;
import com.polycore.gen.WorldgenHooks;
import com.polycore.gen.WorldgenPool;
import com.polycore.optim.Optimization;
import com.polycore.pregen.ParityHash;
import com.polycore.pregen.PregenManager;
import com.polycore.pregen.PregenTask;
import com.polycore.util.ServerMetrics;
import com.polycore.util.StatsSnapshot;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

/**
 * /polycore (alias /pc) : etat, bascules, stats, pre-generation, benchmark, hash de parite.
 */
public final class PolyCommands {
    private PolyCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralCommandNode<CommandSourceStack> root = d.register(Commands.literal("polycore")
                .requires(s -> s.hasPermission(2))
                .executes(PolyCommands::help)
                .then(Commands.literal("status").executes(PolyCommands::status))
                .then(Commands.literal("stats").executes(PolyCommands::stats))
                .then(Commands.literal("toggle")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Optimization.values()).filter(o -> o.kind == Optimization.Kind.ACTIVE).map(o -> o.id), b))
                                .executes(c -> toggle(c, null))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(c -> toggle(c, BoolArgumentType.getBool(c, "enabled"))))))
                .then(Commands.literal("threads")
                        .executes(PolyCommands::threadsShow)
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 256))
                                .executes(PolyCommands::threadsSet)))
                .then(Commands.literal("profiler")
                        .executes(PolyCommands::profilerShow)
                        .then(Commands.literal("reset").executes(PolyCommands::profilerReset)))
                .then(Commands.literal("pregen")
                        .then(Commands.literal("start")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 5000))
                                        .executes(c -> pregenStart(c, 0))
                                        .then(Commands.argument("concurrency", IntegerArgumentType.integer(1, 4096))
                                                .executes(c -> pregenStart(c, IntegerArgumentType.getInteger(c, "concurrency"))))))
                        .then(Commands.literal("stop").executes(PolyCommands::pregenStop))
                        .then(Commands.literal("status").executes(PolyCommands::pregenStatus)))
                .then(Commands.literal("bench")
                        .executes(c -> bench(c, PolyConfig.safeGet(PolyConfig.BENCH_DEFAULT_RADIUS, 10)))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(2, 64))
                                .executes(c -> bench(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("hash")
                        .executes(c -> hash(c, 4))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8))
                                .executes(c -> hash(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("report").executes(PolyCommands::report)));

        d.register(Commands.literal("pc").requires(s -> s.hasPermission(2)).executes(PolyCommands::help).redirect(root));
    }

    // ---------------------------------------------------------------------------------------------

    private static void send(CommandSourceStack s, Component c) {
        s.sendSuccess(() -> c, false);
    }

    private static void send(CommandSourceStack s, String text) {
        send(s, Component.literal(text));
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        send(s, Component.literal("=== Polycore - multicore optimization ===").withStyle(ChatFormatting.GOLD));
        send(s, "/polycore status - list of optimizations");
        send(s, "/polycore stats - MSPT, CPU, chunks/s, thread pool");
        send(s, "/polycore toggle <id> [true|false] - enable/disable");
        send(s, "/polycore threads [n] - show/change the pool size");
        send(s, "/polycore profiler [reset] - time per worldgen stage");
        send(s, "/polycore pregen start <radius> [concurrency] | stop | status");
        send(s, "/polycore bench [radius] - A/B benchmark (fresh, distant area)");
        send(s, "/polycore hash [radius] - block fingerprint to check vanilla parity");
        send(s, "/polycore report - latest benchmark report");
        send(s, Component.literal("In game: press K to open the menu (requires the mod on the client).").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        send(s, Component.literal("=== Polycore optimizations ===").withStyle(ChatFormatting.GOLD));
        for (Optimization o : Optimization.values()) {
            MutableComponent line = Component.literal(" - " + o.displayName + " [" + o.id + "]: ");
            if (o.kind == Optimization.Kind.PLANNED) {
                line.append(Component.literal("PLANNED (v2)").withStyle(ChatFormatting.YELLOW));
            } else if (o.blockReason() != null) {
                line.append(Component.literal("BLOCKED - " + o.blockReason()).withStyle(ChatFormatting.RED));
            } else if (o.isActive()) {
                line.append(Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN));
                if (o != Optimization.PARALLEL_PREGEN) {
                    line.append(Component.literal(o.isHookSeen() ? " (hook OK)" : " (hook not triggered yet)")
                            .withStyle(ChatFormatting.GRAY));
                }
            } else {
                line.append(Component.literal("INACTIVE").withStyle(ChatFormatting.GRAY));
            }
            send(s, line);
        }
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        double cpu = ServerMetrics.processCpu();
        send(s, Component.literal("=== Stats Polycore ===").withStyle(ChatFormatting.GOLD));
        send(s, String.format("Server: %.1f ms/tick (max %.1f), %.1f TPS", ServerMetrics.mspt(), ServerMetrics.maxMspt(), ServerMetrics.tps()));
        send(s, String.format("Process CPU: %s of %d logical cores", cpu < 0 ? "?" : String.format("%.0f%%", cpu * 100), ServerMetrics.cores()));
        send(s, String.format("Memory: %d / %d MB", ServerMetrics.usedMemoryMb(), ServerMetrics.maxMemoryMb()));
        send(s, String.format("Generation: %.1f chunks/s (1 s), %.1f chunks/s (10 s), %d generated, %d loaded from disk",
                StepProfiler.chunksPerSecond(1), StepProfiler.chunksPerSecond(10), StepProfiler.totalChunks(), StepProfiler.totalLoaded()));
        send(s, String.format("Polycore pool: %s, %d threads, %d active, %d queued, %.0f%% utilization",
                WorldgenHooks.usePool() ? "in use" : "not in use", WorldgenPool.size(), WorldgenPool.active(),
                WorldgenPool.queued(), StatsSnapshot.poolUtilization() * 100));
        PregenTask t = PregenManager.current();
        if (t != null && t.state() == PregenTask.State.RUNNING) send(s, t.label + ": " + t.progressLine());
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> c, Boolean value) {
        CommandSourceStack s = c.getSource();
        String id = StringArgumentType.getString(c, "id");
        Optimization o = Optimization.byId(id);
        if (o == null) {
            s.sendFailure(Component.literal("Unknown optimization: " + id));
            return 0;
        }
        if (!o.isToggleable()) {
            s.sendFailure(Component.literal(o.displayName + " cannot be toggled"
                    + (o.blockReason() != null ? " (" + o.blockReason() + ")" : o.kind == Optimization.Kind.PLANNED ? " (planned for v2)" : "")));
            return 0;
        }
        boolean v = value != null ? value : !o.isEnabled();
        o.setEnabled(v);
        send(s, Component.literal(o.displayName + ": " + (v ? "ENABLED" : "DISABLED"))
                .withStyle(v ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        send(s, Component.literal("(temporary: edit config/polycore-common.toml to make it permanent)").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int threadsShow(CommandContext<CommandSourceStack> c) {
        send(c.getSource(), String.format("Polycore pool: %d threads (auto = %d on this machine)",
                WorldgenPool.size(), PolyConfig.autoPoolThreads()));
        return 1;
    }

    private static int threadsSet(CommandContext<CommandSourceStack> c) {
        int n = IntegerArgumentType.getInteger(c, "count");
        WorldgenPool.resize(n);
        send(c.getSource(), "Polycore pool: " + n + " threads (temporary, see the config to make it permanent).");
        return 1;
    }

    private static int profilerShow(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        send(s, Component.literal("=== Time per stage (including waiting; calc: = pure computation) ===").withStyle(ChatFormatting.GOLD));
        for (String l : StepProfiler.formatTable()) send(s, l);
        return 1;
    }

    private static int profilerReset(CommandContext<CommandSourceStack> c) {
        StepProfiler.reset();
        send(c.getSource(), "Profiler reset.");
        return 1;
    }

    private static int pregenStart(CommandContext<CommandSourceStack> c, int concurrency) {
        CommandSourceStack s = c.getSource();
        int radius = IntegerArgumentType.getInteger(c, "radius");
        ServerLevel level = s.getLevel();
        BlockPos pos = BlockPos.containing(s.getPosition());
        String err = PregenManager.startPregen(level, pos.getX() >> 4, pos.getZ() >> 4, radius, concurrency,
                comp -> s.sendSystemMessage(comp));
        if (err != null) {
            s.sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    private static int pregenStop(CommandContext<CommandSourceStack> c) {
        boolean any = PregenManager.stop();
        send(c.getSource(), any ? "Stopped." : "Nothing was running.");
        return 1;
    }

    private static int pregenStatus(CommandContext<CommandSourceStack> c) {
        PregenTask t = PregenManager.current();
        if (t == null) {
            send(c.getSource(), "No pre-generation has been started.");
        } else {
            send(c.getSource(), t.label + " (" + t.state() + "): " + t.progressLine());
        }
        return 1;
    }

    private static int bench(CommandContext<CommandSourceStack> c, int radius) {
        CommandSourceStack s = c.getSource();
        String err = PregenManager.startBench(s.getLevel(), radius, comp -> s.sendSystemMessage(comp));
        if (err != null) {
            s.sendFailure(Component.literal(err));
            return 0;
        }
        send(s, Component.literal("Benchmark started. Use a test world: it generates areas far from spawn.")
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int hash(CommandContext<CommandSourceStack> c, int radius) {
        CommandSourceStack s = c.getSource();
        BlockPos pos = BlockPos.containing(s.getPosition());
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        send(s, String.format("Computing the fingerprint of %d chunks around chunk (%d, %d)...", (2 * radius + 1) * (2 * radius + 1), cx, cz));
        ParityHash.Result r = ParityHash.compute(s.getLevel(), cx, cz, radius);
        send(s, Component.literal("Fingerprint: " + r.hash()).withStyle(ChatFormatting.AQUA));
        if (r.file() != null) send(s, "Per-chunk details: " + r.file());
        send(s, Component.literal("Compare it with the same command, same seed and same position, with optimizations disabled.")
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int report(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        for (String l : PregenManager.lastReport()) {
            if (!l.startsWith("    ")) send(s, l);
        }
        if (!PregenManager.lastReportFile().isEmpty()) send(s, "File: " + PregenManager.lastReportFile());
        return 1;
    }
}
