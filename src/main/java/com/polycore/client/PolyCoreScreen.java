package com.polycore.client;

import com.polycore.network.ClientStatsCache;
import com.polycore.network.PolyNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Menu Polycore (touche K) : optimisations, stats en direct, outils et dernier rapport de benchmark.
 */
public final class PolyCoreScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int DARK_GRAY = 0xFF777777;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int GOLD = 0xFFFFAA00;
    private static final int AQUA = 0xFF55FFFF;

    private static final String[] TABS = {"Optimizations", "Live stats", "Tools", "Report"};
    private static int lastTab = 0;

    private int tab;
    private int scroll = 0;
    private int contentHeight = 0;
    private int ticks = 0;
    private String signature = "";

    private int left, right, top, bottom;

    public PolyCoreScreen() {
        super(Component.literal("Polycore - multicore optimization"));
        this.tab = lastTab;
    }

    // ------------------------------------------------------------------ reseau

    private static boolean serverHasMod() {
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        return c != null && c.hasChannel(PolyNetwork.RequestStats.TYPE);
    }

    private static void requestStats() {
        if (serverHasMod()) PacketDistributor.sendToServer(PolyNetwork.RequestStats.INSTANCE);
    }

    private static void action(String action, String arg, int value) {
        if (serverHasMod()) PacketDistributor.sendToServer(new PolyNetwork.Action(action, arg, value));
    }

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public boolean isPauseScreen() {
        // En solo, une pause arreterait le serveur integre et donc les stats.
        return false;
    }

    @Override
    protected void init() {
        int panelW = Math.min(this.width - 20, 480);
        left = (this.width - panelW) / 2;
        right = left + panelW;
        top = 48;
        bottom = this.height - 32;

        int tabW = (panelW - 3 * 4) / 4;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            String label = (i == tab ? "> " : "") + TABS[i] + (i == tab ? " <" : "");
            addRenderableWidget(Button.builder(Component.literal(label), b -> switchTab(idx))
                    .bounds(left + i * (tabW + 4), 22, tabW, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(right - 70, this.height - 26, 70, 20).build());

        CompoundTag d = ClientStatsCache.get();
        signature = signatureOf(d);
        if (d == null) return;
        boolean canEdit = d.getBoolean("canEdit");
        switch (tab) {
            case 0 -> initOptimizationButtons(d, canEdit);
            case 1 -> {
                if (canEdit) {
                    addRenderableWidget(Button.builder(Component.literal("Reset profiler"),
                            b -> action("profiler_reset", "", 0)).bounds(left, this.height - 26, 170, 20).build());
                }
            }
            case 2 -> initToolButtons(d, canEdit);
            default -> {
            }
        }
        if (tab == 0 || tab == 1) {
            requestStats();
        }
    }

    private void switchTab(int idx) {
        tab = idx;
        lastTab = idx;
        scroll = 0;
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (ticks++ % 10 == 0) requestStats();
        String sig = signatureOf(ClientStatsCache.get());
        if (!sig.equals(signature)) rebuildWidgets();
    }

    /** Les boutons sont reconstruits seulement quand quelque chose qui les concerne change. */
    private String signatureOf(CompoundTag d) {
        if (d == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append(d.getBoolean("canEdit")).append('|').append(d.getBoolean("pregenRunning"))
                .append('|').append(d.getBoolean("benchRunning")).append('|').append(d.getInt("poolSize"));
        ListTag opts = d.getList("opts", Tag.TAG_COMPOUND);
        for (int i = 0; i < opts.size(); i++) {
            CompoundTag o = opts.getCompound(i);
            sb.append('|').append(o.getBoolean("enabled")).append(o.getBoolean("toggleable"));
        }
        return sb.toString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, contentHeight - (bottom - top));
        int before = scroll;
        scroll = Math.max(0, Math.min(max, scroll - (int) (scrollY * 14)));
        if (scroll != before && (tab == 0 || tab == 2)) rebuildWidgets();
        return true;
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 6, top - 6, right + 6, bottom + 4, 0xC0101010);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 8, GOLD);

        CompoundTag d = ClientStatsCache.get();
        g.enableScissor(left, top, right, bottom);
        int y = top - scroll;
        if (d == null) {
            y = text(g, serverHasMod() ? "Waiting for server data..."
                    : "This server does not have Polycore: stats and tools are unavailable.", left, y, YELLOW);
            contentHeight = y + scroll - top;
        } else {
            int end = switch (tab) {
                case 0 -> renderOptimizations(g, d, y);
                case 1 -> renderStats(g, d, y);
                case 2 -> renderTools(g, d, y);
                default -> renderReport(g, d, y);
            };
            contentHeight = end + scroll - top;
        }
        g.disableScissor();

        // Barre de defilement
        int view = bottom - top;
        if (contentHeight > view) {
            int barH = Math.max(12, view * view / contentHeight);
            int barY = top + (view - barH) * scroll / Math.max(1, contentHeight - view);
            g.fill(right + 1, top, right + 3, bottom, 0x40FFFFFF);
            g.fill(right + 1, barY, right + 3, barY + barH, 0xC0FFFFFF);
        }

        // Pied de page
        String footer;
        if (!serverHasMod()) footer = "Server without Polycore";
        else if (d == null) footer = "Connecting...";
        else footer = d.getBoolean("canEdit") ? "Operator mode: actions allowed" : "Read-only (operator required)";
        int footerX = tab == 1 && d != null && d.getBoolean("canEdit") ? left + 178 : left;
        g.drawString(this.font, footer, footerX, this.height - 20, DARK_GRAY);
    }

    private int text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(this.font, s, x, y, color);
        return y + 11;
    }

    private int wrapped(GuiGraphics g, String s, int x, int y, int width, int color) {
        for (FormattedCharSequence line : this.font.split(Component.literal(s), width)) {
            g.drawString(this.font, line, x, y, color);
            y += 10;
        }
        return y;
    }

    private int wrappedLines(String s, int width) {
        return this.font.split(Component.literal(s), width).size();
    }

    private void bar(GuiGraphics g, int x, int y, int w, double fraction, int color) {
        fraction = Math.max(0, Math.min(1, fraction));
        g.fill(x, y, x + w, y + 8, 0xFF303030);
        g.fill(x, y, x + (int) (w * fraction), y + 8, color);
    }

    private int section(GuiGraphics g, String title, int y) {
        y += 4;
        g.drawString(this.font, title, left, y, GOLD);
        g.fill(left, y + 10, right, y + 11, 0x60FFAA00);
        return y + 15;
    }

    // ------------------------------------------------------------------ onglet 0 : optimisations

    private record OptRow(CompoundTag tag, int y, int height) {}

    private List<OptRow> layoutOptimizations(CompoundTag d, int startY) {
        List<OptRow> rows = new ArrayList<>();
        int textW = (right - left) - 96;
        ListTag opts = d.getList("opts", Tag.TAG_COMPOUND);
        int y = startY;
        String lastCategory = "";
        for (int i = 0; i < opts.size(); i++) {
            CompoundTag o = opts.getCompound(i);
            String cat = o.getString("category");
            if (!cat.equals(lastCategory)) {
                y += 19; // titre de section
                lastCategory = cat;
            }
            int lines = wrappedLines(o.getString("desc"), textW);
            int h = 13 + lines * 10 + (o.contains("block") ? 10 : 0) + 8;
            rows.add(new OptRow(o, y, h));
            y += h;
        }
        return rows;
    }

    private void initOptimizationButtons(CompoundTag d, boolean canEdit) {
        if (!canEdit) return;
        for (OptRow row : layoutOptimizations(d, top - scroll)) {
            CompoundTag o = row.tag();
            if (!o.getBoolean("toggleable")) continue;
            int by = row.y();
            if (by < top || by + 20 > bottom) continue;
            boolean enabled = o.getBoolean("enabled");
            String id = o.getString("id");
            addRenderableWidget(Button.builder(Component.literal(enabled ? "Disable" : "Enable"),
                    b -> action("toggle", id, enabled ? 0 : 1)).bounds(right - 86, by, 86, 20).build());
        }
    }

    private int renderOptimizations(GuiGraphics g, CompoundTag d, int y) {
        int textW = (right - left) - 96;
        String lastCategory = "";
        int end = y;
        for (OptRow row : layoutOptimizations(d, y)) {
            CompoundTag o = row.tag();
            String cat = o.getString("category");
            if (!cat.equals(lastCategory)) {
                section(g, cat.equals("TOOL") ? "Analysis tools" : "Chunk generation (multicore)", row.y() - 19);
                lastCategory = cat;
            }
            int ry = row.y();
            g.drawString(this.font, o.getString("name"), left, ry, WHITE);
            String status;
            int color;
            if (o.getString("kind").equals("PLANNED")) {
                status = "PLANNED (v2)";
                color = YELLOW;
            } else if (o.contains("block")) {
                status = "BLOCKED";
                color = RED;
            } else if (o.getBoolean("active")) {
                status = o.getBoolean("hook") || o.getString("id").equals("parallel_pregen") ? "ACTIVE" : "ACTIVE (not used yet)";
                color = GREEN;
            } else {
                status = "INACTIVE";
                color = GRAY;
            }
            int nameW = this.font.width(o.getString("name"));
            g.drawString(this.font, "  " + status, left + nameW, ry, color);
            int ty = ry + 12;
            if (o.contains("block")) {
                g.drawString(this.font, "Reason: " + o.getString("block"), left + 6, ty, RED);
                ty += 10;
            }
            wrapped(g, o.getString("desc"), left + 6, ty, textW, GRAY);
            end = row.y() + row.height();
        }
        end = section(g, "Compatibility", end);
        boolean moonrise = d.getBoolean("moonrise"), c2me = d.getBoolean("c2me");
        if (!moonrise && !c2me) {
            end = text(g, "No conflicting mod detected (Moonrise, C2ME).", left, end, GREEN);
        } else {
            if (moonrise) end = text(g, "Moonrise detected: Polycore pool off, pre-generation and tools still active.", left, end, YELLOW);
            if (c2me) end = text(g, "C2ME detected: Polycore pool off, pre-generation and tools still active.", left, end, YELLOW);
        }
        return end + 4;
    }

    // ------------------------------------------------------------------ onglet 1 : stats

    private int renderStats(GuiGraphics g, CompoundTag d, int y) {
        int w = right - left;
        int barX = left + 150, barW = w - 150 - 60;

        y = section(g, "Server", y);
        double mspt = d.getDouble("mspt");
        y = text(g, String.format("Tick time: %.1f ms (max %.1f ms)   TPS: %.1f",
                mspt, d.getDouble("msptMax"), d.getDouble("tps")), left, y, mspt > 50 ? RED : mspt > 35 ? YELLOW : GREEN);
        double cpu = d.getDouble("cpu");
        g.drawString(this.font, "CPU (" + d.getInt("cores") + " cores)", left, y, WHITE);
        bar(g, barX, y, barW, cpu, 0xFF3399FF);
        g.drawString(this.font, cpu < 0 ? "?" : String.format("%.0f%%", cpu * 100), barX + barW + 6, y, WHITE);
        y += 12;
        long used = d.getLong("memUsed"), max = d.getLong("memMax");
        g.drawString(this.font, "Memory", left, y, WHITE);
        bar(g, barX, y, barW, max == 0 ? 0 : used / (double) max, 0xFFAA66FF);
        g.drawString(this.font, used + "/" + max + " MB", barX + barW + 6, y, WHITE);
        y += 12;

        y = section(g, "Chunk generation", y);
        y = text(g, String.format("Throughput: %.1f chunks/s (10 s average: %.1f)", d.getDouble("cps1"), d.getDouble("cps10")), left, y, AQUA);
        y = text(g, String.format("Chunks generated: %d   loaded from disk: %d",
                d.getLong("chunksGenerated"), d.getLong("chunksLoaded")), left, y, WHITE);

        y = section(g, "Polycore thread pool", y);
        boolean inUse = d.getBoolean("poolInUse");
        y = text(g, inUse ? "Used for noise and biomes" : "Not in use (disabled or blocked)", left, y, inUse ? GREEN : GRAY);
        y = text(g, String.format("%d threads - %d active - %d tasks queued - %d tasks done",
                d.getInt("poolSize"), d.getInt("poolActive"), d.getInt("poolQueued"), d.getLong("poolTasks")), left, y, WHITE);
        double util = d.getDouble("poolUtil");
        g.drawString(this.font, "Pool utilization", left, y, WHITE);
        bar(g, barX, y, barW, util, 0xFF55DD55);
        g.drawString(this.font, String.format("%.0f%%", util * 100), barX + barW + 6, y, WHITE);
        y += 12;

        y = section(g, "Time per worldgen stage", y);
        ListTag steps = d.getList("steps", Tag.TAG_COMPOUND);
        if (steps.isEmpty()) {
            y = text(g, "No data yet: generate new chunks (explore, or start a pre-generation).", left, y, GRAY);
        } else {
            int c1 = left, c2 = left + 140, c3 = left + 205, c4 = left + 275, c5 = left + 345;
            g.drawString(this.font, "Stage", c1, y, GRAY);
            g.drawString(this.font, "Count", c2, y, GRAY);
            g.drawString(this.font, "Avg ms", c3, y, GRAY);
            g.drawString(this.font, "Max ms", c4, y, GRAY);
            g.drawString(this.font, "Share", c5, y, GRAY);
            y += 12;
            long totalAll = 0;
            for (int i = 0; i < steps.size(); i++) {
                CompoundTag s = steps.getCompound(i);
                if (!s.getString("name").startsWith("calc:")) totalAll += s.getLong("total");
            }
            for (int i = 0; i < steps.size(); i++) {
                CompoundTag s = steps.getCompound(i);
                String name = s.getString("name");
                boolean calc = name.startsWith("calc:");
                long count = s.getLong("count");
                double avg = count == 0 ? 0 : s.getLong("total") / (double) count / 1e6;
                int color = calc ? AQUA : WHITE;
                g.drawString(this.font, name, c1, y, color);
                g.drawString(this.font, Long.toString(count), c2, y, color);
                g.drawString(this.font, String.format("%.2f", avg), c3, y, color);
                g.drawString(this.font, String.format("%.1f", s.getLong("max") / 1e6), c4, y, color);
                if (!calc && totalAll > 0) {
                    double share = s.getLong("total") / (double) totalAll;
                    bar(g, c5, y, Math.max(20, right - c5 - 36), share, 0xFFFFAA00);
                    g.drawString(this.font, String.format("%.0f%%", share * 100), right - 30, y, WHITE);
                }
                y += 11;
            }
            y += 2;
            y = wrapped(g, "Measured from request to end of stage (including waiting). The calc: lines "
                    + "(blue) are pure terrain and biome computation. The stage with the largest share is the one to optimize.",
                    left, y, right - left, DARK_GRAY);
        }
        return y + 4;
    }

    // ------------------------------------------------------------------ onglet 2 : outils

    /** Ajoute un bouton seulement s'il est dans la zone visible (le contenu de l'onglet defile). */
    private void addVisible(Button b) {
        if (b.getY() >= top && b.getY() + b.getHeight() <= bottom) addRenderableWidget(b);
    }

    private void initToolButtons(CompoundTag d, boolean canEdit) {
        if (!canEdit) return;
        boolean busy = d.getBoolean("pregenRunning") || d.getBoolean("benchRunning");
        int y = top - scroll + 14;
        int x = left;
        int[] radii = {16, 32, 64, 128};
        for (int r : radii) {
            final int radius = r;
            Button b = Button.builder(Component.literal("Radius " + r), btn -> action("pregen", "", radius))
                    .bounds(x, y, 70, 20).build();
            b.active = !busy;
            addVisible(b);
            x += 74;
        }

        y += 38;
        x = left;
        Button bench10 = Button.builder(Component.literal("A/B benchmark (radius 10)"), b -> action("bench", "", 10))
                .bounds(x, y, 160, 20).build();
        bench10.active = !busy;
        addVisible(bench10);
        Button bench16 = Button.builder(Component.literal("Precise benchmark (radius 16)"), b -> action("bench", "", 16))
                .bounds(x + 164, y, 170, 20).build();
        bench16.active = !busy;
        addVisible(bench16);
        Button stop = Button.builder(Component.literal("Stop"), b -> action("stop", "", 0))
                .bounds(right - 70, y, 70, 20).build();
        stop.active = busy;
        addVisible(stop);

        y += 38;
        int size = d.getInt("poolSize");
        addVisible(Button.builder(Component.literal("-"), b -> action("threads", "", Math.max(1, size - 1)))
                .bounds(left + 150, y, 20, 20).build());
        addVisible(Button.builder(Component.literal("+"), b -> action("threads", "", size + 1))
                .bounds(left + 200, y, 20, 20).build());
        addVisible(Button.builder(Component.literal("Reset profiler"), b -> action("profiler_reset", "", 0))
                .bounds(right - 170, y, 170, 20).build());
    }

    private int renderTools(GuiGraphics g, CompoundTag d, int y) {
        boolean canEdit = d.getBoolean("canEdit");
        g.drawString(this.font, "Pre-generate around me (in chunks):", left, y, GOLD);
        y += 52;
        g.drawString(this.font, "Measure the gain (fresh areas far from spawn, use a test world):", left, y - 14, GOLD);
        y += 38;
        g.drawString(this.font, "Pool threads:", left, y + 6, WHITE);
        g.drawString(this.font, Integer.toString(d.getInt("poolSize")), left + 180, y + 6, AQUA);
        y += 30;
        if (!canEdit) {
            y = text(g, "You must be an operator to use the tools.", left, y, RED);
        }

        y = section(g, "Current task", y);
        if (d.contains("pregenLabel")) {
            boolean running = d.getBoolean("pregenRunning");
            String label = d.getString("pregenLabel");
            if (d.getBoolean("benchRunning")) label += " - phase: " + d.getString("benchPhase");
            y = text(g, label + (running ? "" : " (" + d.getString("pregenState") + ")"), left, y, running ? GREEN : GRAY);
            bar(g, left, y, right - left, d.getDouble("pregenFraction"), running ? 0xFF55DD55 : 0xFF777777);
            y += 12;
            y = wrapped(g, d.getString("pregenLine"), left, y, right - left, WHITE);
        } else {
            y = text(g, "None.", left, y, GRAY);
        }

        y = section(g, "How to read the results", y);
        y = wrapped(g, "The benchmark generates 3 fresh areas: a warm-up (ignored), a 'baseline' phase "
                + "(no pool, 32 chunks in parallel like /neoforge generate) and a 'Polycore' phase. "
                + "It compares chunks/s, tick time and CPU. The report appears in the Report tab "
                + "and in the polycore-reports folder.", left, y, right - left, GRAY);
        y += 4;
        y = wrapped(g, "To check that generated terrain is unchanged: /polycore hash, same seed and same position, "
                + "with and without the optimizations. Both fingerprints must match.", left, y, right - left, GRAY);
        return y + 4;
    }

    // ------------------------------------------------------------------ onglet 3 : rapport

    private int renderReport(GuiGraphics g, CompoundTag d, int y) {
        ListTag lines = d.getList("report", Tag.TAG_STRING);
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.getString(i);
            int color = l.startsWith("RESULT") ? GREEN : l.startsWith("===") ? GOLD
                    : l.startsWith("[") ? AQUA : l.startsWith("    ") ? GRAY : WHITE;
            if (l.isEmpty()) {
                y += 6;
                continue;
            }
            y = wrapped(g, l, left, y, right - left, color);
        }
        String file = d.getString("reportFile");
        if (!file.isEmpty()) {
            y += 4;
            y = wrapped(g, "File: " + file, left, y, right - left, DARK_GRAY);
        }
        return y + 4;
    }
}
