package com.polycore.menu;

import com.polycore.config.PolyConfig;
import com.polycore.gen.StepProfiler;
import com.polycore.gen.WorldgenPool;
import com.polycore.lag.LagCapture;
import com.polycore.lag.LagMonitor;
import com.polycore.optim.Optimization;
import com.polycore.platform.Services;
import com.polycore.pregen.PregenManager;
import com.polycore.pregen.PregenTask;
import com.polycore.settings.Settings;
import com.polycore.util.Notifier;
import com.polycore.util.Permissions;
import com.polycore.util.ServerMetrics;
import com.polycore.util.StatsSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Polycore menu for players whose client does not have the mod: a 6-row chest made of vanilla items.
 * Items cannot be taken; clicking an item runs its action. Refreshed every second while open.
 */
public final class VanillaMenu extends ChestMenu {
    private static final int SIZE = 54;

    // Layout (slot indexes)
    private static final int INFO = 0, GUARD = 1, SERVER = 2, GENERATION = 4, POOL = 6, LAG = 7, CLOSE = 8;
    private static final int OPT_POOL = 19, OPT_STAGES = 21, OPT_PROFILER = 23, OPT_PREGEN = 25;
    private static final int PREGEN_32 = 37, PREGEN_64 = 38, PREGEN_128 = 39, BENCH = 41, HASH = 42, STOP = 43;
    private static final int THREADS_MINUS = 46, THREADS = 47, THREADS_PLUS = 48, KEEP_TPS = 50, REPORT = 52, LAG_ALERTS = 53;

    private final ServerPlayer viewer;
    private final SimpleContainer box;

    private VanillaMenu(int id, Inventory inventory, ServerPlayer viewer, SimpleContainer box) {
        super(MenuType.GENERIC_9x6, id, inventory, box, 6);
        this.viewer = viewer;
        this.box = box;
        refresh();
    }

    public static void open(ServerPlayer p) {
        if (!Permissions.canView(p)) {
            Notifier.send(p, Notifier.Level.ERROR, "Polycore", "This server only lets operators open the menu.");
            return;
        }
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new VanillaMenu(id, inv, p, new SimpleContainer(SIZE)),
                Component.literal("Polycore " + Services.PLATFORM.modVersion())));
    }

    /** Called once per second: refreshes every open Polycore chest menu. */
    public static void refreshOpenMenus(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.containerMenu instanceof VanillaMenu m) {
                m.refresh();
                m.broadcastChanges();
            }
        }
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Never let items move: handle the click ourselves and resync the client.
        if (slotId >= 0 && slotId < SIZE && clickType == ClickType.PICKUP && player instanceof ServerPlayer sp) {
            handle(slotId, button, sp);
        }
        setCarried(ItemStack.EMPTY);
        refresh();
        broadcastFullState();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private void handle(int slot, int button, ServerPlayer p) {
        if (slot == CLOSE) {
            p.closeContainer();
            return;
        }
        boolean edit = Permissions.canEdit(p);
        boolean isAction = slot == OPT_POOL || slot == OPT_STAGES || slot == OPT_PROFILER || slot == PREGEN_32
                || slot == PREGEN_64 || slot == PREGEN_128 || slot == BENCH || slot == HASH || slot == STOP
                || slot == THREADS_MINUS || slot == THREADS_PLUS || slot == KEEP_TPS || slot == LAG || slot == LAG_ALERTS || slot == GUARD;
        if (isAction && !edit) {
            Notifier.send(p, Notifier.Level.ERROR, "Read-only", "Only operators can change the server.");
            return;
        }
        int cx = p.chunkPosition().x, cz = p.chunkPosition().z;
        boolean keepTps = PolyConfig.get().pregenKeepTps;
        switch (slot) {
            case OPT_POOL -> toggle(p, Optimization.WORLDGEN_POOL);
            case OPT_STAGES -> toggle(p, Optimization.PARALLEL_STAGES);
            case OPT_PROFILER -> toggle(p, Optimization.STEP_PROFILER);
            case PREGEN_32 -> PregenManager.startPregen(p.serverLevel(), cx, cz, 32, 0, keepTps, p, p.createCommandSourceStack());
            case PREGEN_64 -> PregenManager.startPregen(p.serverLevel(), cx, cz, 64, 0, keepTps, p, p.createCommandSourceStack());
            case PREGEN_128 -> PregenManager.startPregen(p.serverLevel(), cx, cz, 128, 0, keepTps, p, p.createCommandSourceStack());
            case BENCH -> {
                p.closeContainer();
                PregenManager.startBench(p.serverLevel(), PolyConfig.get().benchDefaultRadius, p, p.createCommandSourceStack());
            }
            case HASH -> PregenManager.hash(p.serverLevel(), cx, cz, 4, p.createCommandSourceStack());
            case STOP -> PregenManager.stop(p.createCommandSourceStack());
            case THREADS_MINUS, THREADS_PLUS -> {
                int step = button == 1 ? 4 : 1; // right click = 4 at a time
                int current = PolyConfig.get().worldgenPoolThreads <= 0 ? PolyConfig.poolThreads() : PolyConfig.get().worldgenPoolThreads;
                int next = slot == THREADS_PLUS ? current + step : current - step;
                next = PolyConfig.clamp(next, 1, WorldgenPool.maxThreads());
                String err = Settings.set(Settings.POOL_THREADS, Integer.toString(next));
                if (err != null) Notifier.send(p, Notifier.Level.ERROR, "Not changed", err);
            }
            case KEEP_TPS -> Settings.set(Settings.PREGEN_KEEP_TPS, Boolean.toString(!keepTps));
            case GUARD -> Settings.set(Settings.GUARD_ENABLED, Boolean.toString(!PolyConfig.get().guardEnabled));
            case LAG -> LagCapture.start(PolyConfig.get().lagCaptureSeconds, p.createCommandSourceStack(), false);
            case LAG_ALERTS -> LagMonitor.setAlerts(p, !LagMonitor.alertsOn(p)); // the item name shows the state
            default -> {
            }
        }
    }

    private static void toggle(ServerPlayer p, Optimization o) {
        String err = o.setEnabled(!o.isEnabled());
        if (err != null) Notifier.send(p, Notifier.Level.ERROR, "Not changed", err);
        else Notifier.send(p, Notifier.Level.SUCCESS, o.displayName, o.isEnabled() ? "Enabled" : "Disabled");
    }

    // ------------------------------------------------------------------ content

    private void refresh() {
        boolean edit = Permissions.canEdit(viewer);
        String readOnly = edit ? null : "Read-only: operator required";
        for (int i = 0; i < SIZE; i++) box.setItem(i, ItemStack.EMPTY);
        for (int i = 9; i < 18; i++) box.setItem(i, pane(Items.CYAN_STAINED_GLASS_PANE));
        for (int i = 27; i < 36; i++) box.setItem(i, pane(Items.GRAY_STAINED_GLASS_PANE));

        double cpu = ServerMetrics.processCpu();
        box.setItem(INFO, item(Items.NETHER_STAR, "Polycore " + Services.PLATFORM.modVersion(), ChatFormatting.AQUA,
                "Multicore optimization",
                "Loader: " + Services.PLATFORM.loaderName() + " " + Services.PLATFORM.minecraftVersion(),
                edit ? "Access: full" : "Access: read-only",
                "Install Polycore on your client",
                "for the full menu (key K)."));
        box.setItem(SERVER, item(Items.COMPARATOR, "Server", ChatFormatting.GREEN,
                String.format("Tick time: %.1f ms (max %.1f)", ServerMetrics.mspt(), ServerMetrics.maxMspt()),
                String.format("TPS: %.1f", ServerMetrics.tps()),
                String.format("CPU: %s (%d logical cores)", cpu < 0 ? "?" : String.format("%.0f%%", cpu * 100), ServerMetrics.cores()),
                String.format("Memory: %s / %s", gb(ServerMetrics.usedMemoryMb()), gb(ServerMetrics.maxMemoryMb()))));
        box.setItem(GENERATION, item(Items.GRASS_BLOCK, "Chunk generation", ChatFormatting.GREEN,
                String.format("Now: %.1f chunks/s", StepProfiler.chunksPerSecond(1)),
                String.format("10 s average: %.1f chunks/s", StepProfiler.chunksPerSecond(10)),
                "Generated: " + StepProfiler.totalChunks(),
                "Loaded from disk: " + StepProfiler.totalLoaded()));
        box.setItem(POOL, item(Items.HOPPER, "Polycore thread pool", ChatFormatting.GREEN,
                WorldgenPool.size() + " / " + WorldgenPool.maxThreads() + " threads",
                WorldgenPool.active() + " active, " + WorldgenPool.queued() + " queued",
                String.format("Utilization: %.0f%%", StatsSnapshot.poolUtilization() * 100)));
        box.setItem(LAG, lagItem(readOnly));
        box.setItem(GUARD, guardItem(readOnly));
        box.setItem(CLOSE, item(Items.BARRIER, "Close", ChatFormatting.RED));

        box.setItem(OPT_POOL, optimization(Optimization.WORLDGEN_POOL, readOnly));
        box.setItem(OPT_STAGES, optimization(Optimization.PARALLEL_STAGES, readOnly));
        box.setItem(OPT_PROFILER, optimization(Optimization.STEP_PROFILER, readOnly));
        box.setItem(OPT_PREGEN, item(Items.MAP, "Parallel pre-generation", ChatFormatting.AQUA,
                "Use the buttons below to pre-generate",
                "around your position."));

        String hint = readOnly != null ? readOnly : "Click to start";
        PregenTask t = PregenManager.current();
        boolean busy = PregenManager.isBusy();
        box.setItem(PREGEN_32, item(Items.MAP, "Pre-generate radius 32", ChatFormatting.YELLOW, "4,225 chunks around you", busy ? "A task is already running" : hint));
        box.setItem(PREGEN_64, item(Items.MAP, "Pre-generate radius 64", ChatFormatting.YELLOW, "16,641 chunks around you", busy ? "A task is already running" : hint));
        box.setItem(PREGEN_128, item(Items.FILLED_MAP, "Pre-generate radius 128", ChatFormatting.YELLOW, "66,049 chunks around you", busy ? "A task is already running" : hint));
        box.setItem(BENCH, item(Items.CLOCK, "A/B benchmark", ChatFormatting.YELLOW,
                "Generates fresh areas far from spawn,",
                "with and without Polycore. Use a test world.",
                "Radius " + PolyConfig.get().benchDefaultRadius, busy ? "A task is already running" : hint));
        box.setItem(HASH, item(Items.SPYGLASS, "Parity fingerprint", ChatFormatting.YELLOW,
                "Hashes the blocks around you (radius 4)",
                "to check generation is unchanged.", readOnly != null ? readOnly : "Click to compute"));
        if (busy && t != null) {
            box.setItem(STOP, item(Items.RED_DYE, "Stop: " + t.label, ChatFormatting.RED,
                    String.format("%.0f%% - %.1f chunks/s", t.progressFraction() * 100, t.rate()),
                    t.ok() + " / " + t.total() + " chunks",
                    readOnly != null ? readOnly : "Click to stop"));
        } else {
            box.setItem(STOP, item(Items.GRAY_DYE, "No task running", ChatFormatting.GRAY));
        }

        int threads = WorldgenPool.size();
        box.setItem(THREADS_MINUS, item(Items.GRAY_DYE, "Fewer threads", ChatFormatting.WHITE, "Left click: -1, right click: -4", readOnly));
        box.setItem(THREADS, item(Items.HOPPER, "Pool threads: " + threads, ChatFormatting.AQUA,
                PolyConfig.get().worldgenPoolThreads <= 0 ? "Mode: automatic (cores - 2)" : "Mode: manual",
                "Maximum on this machine: " + WorldgenPool.maxThreads()));
        box.setItem(THREADS_PLUS, item(Items.LIME_DYE, "More threads", ChatFormatting.WHITE, "Left click: +1, right click: +4", readOnly));
        boolean keepTps = PolyConfig.get().pregenKeepTps;
        box.setItem(KEEP_TPS, item(keepTps ? Items.COMPASS : Items.RECOVERY_COMPASS,
                "Keep TPS stable: " + (keepTps ? "ON" : "OFF"), keepTps ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                "Pre-generation slows down automatically",
                "when the server starts to lag.", readOnly != null ? readOnly : "Click to switch"));

        CompoundTag last = PregenManager.lastBench();
        if (last != null && last.contains("gain")) {
            box.setItem(REPORT, item(Items.BOOK, "Last benchmark", ChatFormatting.GOLD,
                    String.format("Baseline: %.1f chunks/s", last.getDouble("baseline")),
                    String.format("Polycore: %.1f chunks/s", last.getDouble("polycore")),
                    String.format("Gain: %+.0f%%", last.getDouble("gain")),
                    "Full report in polycore-reports/"));
        } else {
            box.setItem(REPORT, item(Items.BOOK, "No benchmark yet", ChatFormatting.GRAY, "Run the A/B benchmark to get one."));
        }
        boolean alerts = LagMonitor.alertsOn(viewer);
        box.setItem(LAG_ALERTS, item(Items.BELL, "Lag alerts: " + (alerts ? "ON" : "OFF"), alerts ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                "Tells you (action bar) when the server",
                "drops below 20 TPS, with the main cause.", readOnly != null ? readOnly : "Click to switch"));
    }

    private static ItemStack guardItem(String readOnly) {
        boolean on = PolyConfig.get().guardEnabled;
        int level = com.polycore.guard.TpsGuard.level();
        List<String> lore = new ArrayList<>();
        lore.add(!on ? "Off" : level == 0 ? "Standing by: the server runs normally" : "Acting: level " + level);
        lore.add("Starts above " + PolyConfig.get().guardThresholdMs + " ms per tick.");
        lore.add("");
        for (com.polycore.guard.TpsGuard.Measure m : com.polycore.guard.TpsGuard.Measure.values()) {
            lore.add("  " + m.label + ": " + (m.blockReason() != null ? "off (" + m.blockReason() + ")" : !on || !m.isEnabled() ? "off"
                    : m.isActive() ? "ACTIVE" : "standby"));
        }
        lore.add("");
        lore.add(readOnly != null ? readOnly : "Click to turn the Guard " + (on ? "off" : "on"));
        ChatFormatting color = !on ? ChatFormatting.GRAY : level > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        return item(Items.SHIELD, "TPS Guard: " + (on ? "ON" : "OFF"), color, lore.toArray(new String[0]));
    }

    private ItemStack lagItem(String readOnly) {
        CompoundTag lag = LagMonitor.toTag(viewer);
        List<String> lore = new ArrayList<>();
        lore.add(String.format("%.1f TPS - %.1f ms per tick (worst %.0f)", lag.getDouble("tps"), lag.getDouble("mspt"), lag.getDouble("worst")));
        lore.add("Last 5 s, average per tick:");
        List<CompoundTag> sections = new ArrayList<>();
        ListTag list = lag.getList("sections", 10);
        for (int i = 0; i < list.size(); i++) sections.add(list.getCompound(i));
        sections.sort((a, b) -> Double.compare(b.getDouble("ms"), a.getDouble("ms")));
        for (int i = 0; i < Math.min(4, sections.size()); i++) {
            lore.add(String.format("  %s: %.1f ms", sections.get(i).getString("name"), sections.get(i).getDouble("ms")));
        }
        lore.add("");
        CompoundTag capture = lag.getCompound("capture");
        if (capture.getBoolean("running")) {
            lore.add("Analysis running: " + capture.getInt("remaining") + " s left");
        } else if (capture.contains("last")) {
            CompoundTag last = capture.getCompound("last");
            lore.add("Last analysis (" + last.getString("date") + "):");
            ListTag top = last.getList("top", 10);
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                CompoundTag t = top.getCompound(i);
                lore.add(String.format("  %.2f ms %s", t.getDouble("ms"), t.getString("type")));
                lore.add(String.format("    at %s %d %d %d", t.getString("dim").replace("minecraft:", ""), t.getInt("x"), t.getInt("y"), t.getInt("z")));
            }
            lore.add("Full report in polycore-reports/");
        } else {
            lore.add("No detailed analysis yet.");
        }
        lore.add(readOnly != null ? readOnly : "Click: analyze for " + PolyConfig.get().lagCaptureSeconds + " s");
        boolean lagging = lag.getDouble("mspt") > PolyConfig.get().lagThresholdMs;
        return item(Items.REDSTONE_TORCH, "Lag analyzer", lagging ? ChatFormatting.RED : ChatFormatting.GREEN, lore.toArray(new String[0]));
    }

    private static ItemStack optimization(Optimization o, String readOnly) {
        String status = o.status();
        Item icon = switch (status) {
            case "ACTIVE" -> Items.LIME_DYE;
            case "BLOCKED" -> Items.RED_DYE;
            default -> Items.GRAY_DYE;
        };
        ChatFormatting color = switch (status) {
            case "ACTIVE" -> ChatFormatting.GREEN;
            case "BLOCKED" -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
        List<String> lore = new ArrayList<>(wrap(o.description, 38));
        lore.add("");
        lore.add("Status: " + status);
        if (o.blockReason() != null) lore.add(o.blockReason());
        else if (readOnly != null) lore.add(readOnly);
        else if (o.isToggleable()) lore.add("Click to " + (o.isEnabled() ? "disable" : "enable"));
        return item(icon, o.displayName, color, lore.toArray(new String[0]));
    }

    private static ItemStack pane(Item item) {
        ItemStack s = new ItemStack(item);
        s.setHoverName(Component.literal(" "));
        s.getOrCreateTag().putInt("HideFlags", 127);
        return s;
    }

    private static ItemStack item(Item item, String name, ChatFormatting color, String... lore) {
        ItemStack s = new ItemStack(item);
        s.setHoverName(Component.literal(name).withStyle(Style.EMPTY.withItalic(false).withColor(color)));
        net.minecraft.nbt.ListTag lines = new net.minecraft.nbt.ListTag();
        for (String l : lore) {
            if (l == null) continue;
            lines.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(
                    Component.literal(l).withStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY)))));
        }
        if (!lines.isEmpty()) s.getOrCreateTagElement("display").put("Lore", lines);
        return s;
    }

    private static String gb(long mb) {
        return String.format("%.1f GB", mb / 1024.0);
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width && line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
