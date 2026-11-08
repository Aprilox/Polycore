package com.polycore.client.ui;

import com.polycore.Polycore;
import com.polycore.client.ClientConfig;
import com.polycore.client.PolycoreClient;
import com.polycore.network.ClientBridge;
import com.polycore.platform.Services;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Polycore menu: a sidebar with "This game" (client) and "Server" sections.
 *
 * <p>Each page is written once and run in three passes: LAYOUT adds the widgets, BACKGROUND draws cards and
 * charts under the widgets, FOREGROUND draws the text on top. This keeps widget positions and drawings in sync.</p>
 */
public final class PolyScreen extends Screen {
    private static final ResourceLocation ICON = new ResourceLocation(Polycore.MODID, "textures/gui/icon.png");
    private static final int GAP = 6;
    private static final int PAD = 8;
    private static final int LINE = 11;

    private record Nav(String id, String label, String group) {}

    private static final List<Nav> NAV = List.of(
            new Nav("client.overview", "Overview", "THIS GAME"),
            new Nav("client.settings", "Preferences", "THIS GAME"),
            new Nav("server.dashboard", "Dashboard", "SERVER"),
            new Nav("server.lag", "Lag analyzer", "SERVER"),
            new Nav("server.guard", "TPS Guard", "SERVER"),
            new Nav("server.optimizations", "Optimizations", "SERVER"),
            new Nav("server.profiler", "Profiler", "SERVER"),
            new Nav("server.tools", "Tools", "SERVER"),
            new Nav("server.reports", "Reports", "SERVER"),
            new Nav("server.settings", "Settings", "SERVER"),
            new Nav("about", "About", "POLYCORE"));

    private enum Pass { LAYOUT, BACKGROUND, FOREGROUND }

    private String page;
    private int scroll = 0;
    private int contentHeight = 0;
    private String signature = "";
    private final Map<String, String> drafts = new HashMap<>();
    private String selectedReport = null;

    /** Confirmation box drawn over the page (teleport, going back). Null when closed. */
    private record Confirm(String title, List<String> lines, String okLabel, Runnable ok, String cancelLabel, Runnable cancel) {}

    private Confirm confirm = null;
    private Widgets.FlatButton confirmOk, confirmCancel;
    private int boxX, boxY, boxW, boxH;
    private final long openedAt = System.currentTimeMillis();
    private boolean backOffered = false;

    private Pass pass = Pass.LAYOUT;
    private GuiGraphics g;

    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentBottom;

    public PolyScreen(String page) {
        super(new TextComponent("Polycore"));
        this.page = isKnownPage(page) ? page : "server.dashboard";
    }

    /** Every menu page id, in navigation order. */
    public static List<String> pageIds() {
        return NAV.stream().map(Nav::id).toList();
    }

    private static boolean isKnownPage(String id) {
        for (Nav n : NAV) if (n.id.equals(id)) return true;
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        // In singleplayer, pausing would stop the integrated server and freeze the stats.
        return false;
    }

    // ------------------------------------------------------------------ data helpers

    private static CompoundTag state() {
        return ClientBridge.state();
    }

    private static boolean serverHasMod() {
        return PolycoreClient.serverHasPolycore();
    }

    private static boolean canEdit() {
        CompoundTag s = state();
        return s != null && s.getBoolean("canEdit");
    }

    private void go(String id) {
        page = id;
        scroll = 0;
        ClientConfig.get().lastPage = id;
        ClientConfig.save();
        requestRebuild();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        tips.clear();
        panelW = Math.min(this.width - 16, 720);
        panelH = Math.min(this.height - 16, 420);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        int sideW = 112;
        contentX = panelX + sideW + 10;
        contentY = panelY + 34;
        contentW = panelX + panelW - 10 - contentX;
        contentBottom = panelY + panelH - 8;

        // Sidebar
        int y = panelY + 34;
        String group = "";
        for (Nav n : NAV) {
            if (!n.group.equals(group)) {
                group = n.group;
                y += 12;
            }
            boolean selected = n.id.equals(page);
            boolean dim = n.id.startsWith("server.") && !serverHasMod();
            Widgets.FlatButton b = new Widgets.FlatButton(panelX + 4, y, sideW - 8, 16, n.label,
                    selected ? Widgets.Style.NAV_SELECTED : Widgets.Style.NAV, () -> go(n.id));
            if (dim) tip(b, "This server does not have Polycore");
            addRenderableWidget(b);
            y += 17;
        }
        addRenderableWidget(new Widgets.FlatButton(panelX + panelW - 22, panelY + 6, 16, 16, "x", Widgets.Style.NEUTRAL, this::onClose));

        pass = Pass.LAYOUT;
        g = null;
        runPage();
        signature = computeSignature();
        layoutConfirm();
    }

    // ------------------------------------------------------------------ confirmation box

    private void openConfirm(Confirm c) {
        confirm = c;
        requestRebuild();
    }

    private void layoutConfirm() {
        confirmOk = confirmCancel = null;
        if (confirm == null) return;
        boxW = Math.min(320, panelW - 40);
        int lines = 0;
        for (String l : confirm.lines()) lines += wrapLines(l, boxW - 2 * PAD);
        boxH = 26 + lines * 10 + 32;
        boxX = panelX + (panelW - boxW) / 2;
        boxY = panelY + (panelH - boxH) / 2;
        int bw = 90, by = boxY + boxH - 24;
        Confirm c = confirm;
        confirmOk = new Widgets.FlatButton(boxX + boxW - PAD - bw, by, bw, 16, c.okLabel(), Widgets.Style.PRIMARY, () -> {
            confirm = null;
            requestRebuild();
            c.ok().run();
        });
        confirmCancel = new Widgets.FlatButton(boxX + boxW - PAD - 2 * bw - 6, by, bw, 16, c.cancelLabel(), Widgets.Style.NEUTRAL, () -> {
            confirm = null;
            requestRebuild();
            c.cancel().run();
        });
    }

    private void renderConfirm(GuiGraphics gr, int mouseX, int mouseY, float partialTick) {
        if (confirm == null || confirmOk == null) return;
        gr.pose().pushPose();
        gr.pose().translate(0, 0, 300); // above the page widgets and text
        gr.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC0000000);
        Theme.card(gr, boxX, boxY, boxW, boxH);
        gr.drawString(font, confirm.title(), boxX + PAD, boxY + 8, Theme.TEXT, false);
        int ty = boxY + 24;
        for (String l : confirm.lines()) {
            for (FormattedCharSequence line : font.split(new TextComponent(l), boxW - 2 * PAD)) {
                gr.drawString(font, line, boxX + PAD, ty, Theme.MUTED, false);
                ty += 10;
            }
        }
        confirmOk.render(gr.pose(), mouseX, mouseY, partialTick);
        confirmCancel.render(gr.pose(), mouseX, mouseY, partialTick);
        gr.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirm != null && confirmOk != null) {
            // The box is modal: its buttons, or a click outside to dismiss it.
            if (!confirmOk.mouseClicked(mouseX, mouseY, button) && !confirmCancel.mouseClicked(mouseX, mouseY, button)
                    && (mouseX < boxX || mouseX > boxX + boxW || mouseY < boxY || mouseY > boxY + boxH)) {
                confirm = null;
                requestRebuild();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirm != null && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            confirm = null;
            requestRebuild();
            return true;
        }
        // The open-menu key (K) closes it again, unless the player is typing in a field.
        net.minecraft.client.KeyMapping key = PolycoreClient.openKey;
        if (key != null && key.matches(keyCode, scanCode) && !(getFocused() instanceof EditBox)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** After a teleport from the lag analyzer, reopening the menu offers to go back. Fresh server data only. */
    private void offerBack() {
        if (backOffered || confirm != null) return;
        CompoundTag s = state();
        if (s == null || System.currentTimeMillis() - ClientBridge.stateAgeMillis() < openedAt) return;
        backOffered = true;
        CompoundTag b = s.getCompound("lag").getCompound("back");
        if (!s.getBoolean("canEdit") || b.isEmpty()) return;
        openConfirm(new Confirm("Go back to where you were?", List.of(
                "You teleported from the lag analyzer. Your previous position:",
                shortDim(b.getString("dim")) + " " + b.getInt("x") + " " + b.getInt("y") + " " + b.getInt("z")),
                "Go back", () -> {
                    PolycoreClient.sendAction("lag_back", "", "go");
                    onClose();
                },
                "Stay here", () -> PolycoreClient.sendAction("lag_back", "", "forget")));
    }

    private boolean rebuildRequested = false;

    /** Rebuilds the widgets on the next tick (never during a click, which would invalidate the widget list). */
    private void requestRebuild() {
        rebuildRequested = true;
    }

    @Override
    public void tick() {
        super.tick();
        offerBack();
        if (rebuildRequested) {
            rebuildRequested = false;
            init(minecraft, width, height); // 1.18.2: no rebuildWidgets
            return;
        }
        if (getFocused() instanceof EditBox) return; // do not steal focus while typing
        String sig = computeSignature();
        if (!sig.equals(signature)) init(minecraft, width, height); // 1.18.2: no rebuildWidgets
    }

    /** Values that change the widgets (not just the text). */
    private String computeSignature() {
        CompoundTag s = state();
        StringBuilder sb = new StringBuilder(page).append('|').append(scroll).append('|').append(serverHasMod());
        if (s == null) return sb.append("|null").toString();
        sb.append('|').append(s.getBoolean("canEdit")).append(s.getBoolean("canView"))
                .append(s.getBoolean("taskRunning")).append(s.getBoolean("benchRunning"));
        switch (page) {
            case "server.optimizations", "server.dashboard" -> {
                ListTag opts = s.getList("opts", Tag.TAG_COMPOUND);
                for (int i = 0; i < opts.size(); i++) sb.append(opts.getCompound(i).getString("status"));
            }
            case "server.guard" -> {
                CompoundTag gd = s.getCompound("guard");
                sb.append(gd.getBoolean("enabled")).append(gd.getInt("level")).append(gd.getInt("threshold"));
                ListTag ms = gd.getList("measures", Tag.TAG_COMPOUND);
                for (int i = 0; i < ms.size(); i++) sb.append(ms.getCompound(i).getBoolean("enabled"));
                ListTag set = s.getList("settings", Tag.TAG_COMPOUND);
                for (int i = 0; i < set.size(); i++) sb.append(set.getCompound(i).get("value"));
            }
            case "server.settings", "server.tools" -> {
                ListTag set = s.getList("settings", Tag.TAG_COMPOUND);
                for (int i = 0; i < set.size(); i++) sb.append(set.getCompound(i).get("value"));
            }
            case "server.reports" -> sb.append(s.getList("reports", Tag.TAG_COMPOUND).size()).append(selectedReport);
            case "server.profiler" -> sb.append(s.getList("steps", Tag.TAG_COMPOUND).size());
            case "server.lag" -> {
                CompoundTag cap = s.getCompound("lag").getCompound("capture");
                sb.append(s.getCompound("lag").getBoolean("alerts")).append(cap.getBoolean("running"))
                        .append(cap.getInt("seconds")).append(cap.getBoolean("auto"))
                        .append(cap.getCompound("last").getString("date")).append(cap.getCompound("last").getString("file"));
            }
            default -> {
            }
        }
        return sb.toString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (confirm != null) return true;
        int max = Math.max(0, contentHeight - (contentBottom - contentY));
        int before = scroll;
        scroll = Math.max(0, Math.min(max, scroll - (int) (scrollY * 16)));
        if (scroll != before) init(minecraft, width, height); // 1.18.2: no rebuildWidgets
        return true;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(PoseStack pose) {
        super.renderBackground(pose);
        GuiGraphics graphics = new GuiGraphics(pose);
        graphics.fill(0, 0, this.width, this.height, Theme.BACKDROP);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, Theme.PANEL);
        graphics.fill(panelX, panelY + 28, panelX + 112, panelY + panelH, Theme.SIDEBAR);
        graphics.fill(panelX, panelY + 28, panelX + panelW, panelY + 29, Theme.DIVIDER);
        graphics.renderOutline(panelX, panelY, panelW, panelH, Theme.CARD_BORDER);
        pass = Pass.BACKGROUND;
        g = graphics;
        graphics.enableScissor(contentX - 2, contentY, contentX + contentW + 2, contentBottom);
        runPage();
        graphics.disableScissor();
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        GuiGraphics graphics = new GuiGraphics(pose);
        g = graphics;
        renderHeader(graphics);
        renderSidebarGroups(graphics);
        pass = Pass.FOREGROUND;
        graphics.enableScissor(contentX - 2, contentY, contentX + contentW + 2, contentBottom);
        runPage();
        graphics.disableScissor();

        int view = contentBottom - contentY;
        if (contentHeight > view) {
            int barH = Math.max(14, view * view / contentHeight);
            int barY = contentY + (view - barH) * scroll / Math.max(1, contentHeight - view);
            int x = panelX + panelW - 6;
            graphics.fill(x, contentY, x + 2, contentBottom, 0x30FFFFFF);
            graphics.fill(x, barY, x + 2, barY + barH, 0xA0FFFFFF);
        }
        renderTips(pose, mouseX, mouseY);
        renderConfirm(graphics, mouseX, mouseY, partialTick);
    }

    // 1.19.2 has no widget Tooltip: the screen keeps each widget's text and draws the one under the mouse
    private final Map<AbstractWidget, Component> tips = new java.util.IdentityHashMap<>();

    private void tip(AbstractWidget w, String text) {
        tips.put(w, new TextComponent(text));
    }

    private void renderTips(PoseStack pose, int mouseX, int mouseY) {
        if (confirm != null) return;
        for (Map.Entry<AbstractWidget, Component> e : tips.entrySet()) {
            AbstractWidget w = e.getKey();
            if (w.visible && mouseX >= w.x && mouseY >= w.y && mouseX < w.x + w.getWidth() && mouseY < w.y + w.getHeight()
                    && children().contains(w)) {
                renderTooltip(pose, font.split(e.getValue(), 200), mouseX, mouseY);
                return;
            }
        }
    }

    // 1.19.2 has no Minecraft.getFps(): fpsString starts with the frame rate ("60 fps T: ...")
    private static int fps(Minecraft mc) {
        try { return Integer.parseInt(mc.fpsString.substring(0, mc.fpsString.indexOf(' '))); } catch (RuntimeException e) { return 0; }
    }

    private void renderHeader(GuiGraphics gr) {
        gr.blit(ICON, panelX + 6, panelY + 5, 18, 18, 0f, 0f, 128, 128, 128, 128);
        gr.drawString(font, "Polycore", panelX + 28, panelY + 6, Theme.TEXT, false);
        gr.drawString(font, "v" + Services.PLATFORM.modVersion() + " - Multicore optimization", panelX + 28, panelY + 16, Theme.DIM, false);

        int x = panelX + panelW - 28;
        CompoundTag s = state();
        String server;
        int serverColor;
        if (!serverHasMod()) {
            server = "Server: no Polycore";
            serverColor = Theme.AMBER;
        } else if (s == null) {
            server = "Server: connecting";
            serverColor = Theme.MUTED;
        } else {
            server = (s.getBoolean("dedicated") ? "Dedicated server" : "Singleplayer") + " - v" + s.getString("version");
            serverColor = Theme.ACCENT;
        }
        String access;
        int accessColor;
        if (s == null || !serverHasMod()) {
            access = "Local only";
            accessColor = Theme.MUTED;
        } else if (s.getBoolean("owner")) {
            access = "Owner";
            accessColor = Theme.GREEN;
        } else if (s.getBoolean("canEdit")) {
            access = "Operator";
            accessColor = Theme.GREEN;
        } else {
            access = "Read-only";
            accessColor = Theme.AMBER;
        }
        x -= font.width(access) + 8;
        Theme.pill(gr, font, access, x, panelY + 9, accessColor);
        x -= font.width(server) + 14;
        Theme.pill(gr, font, server, x, panelY + 9, serverColor);
    }

    private void renderSidebarGroups(GuiGraphics gr) {
        int y = panelY + 34;
        String group = "";
        for (Nav n : NAV) {
            if (!n.group.equals(group)) {
                group = n.group;
                gr.drawString(font, group, panelX + 10, y + 2, Theme.DIM, false);
                y += 12;
            }
            y += 17;
        }
    }

    // ------------------------------------------------------------------ pass helpers

    private boolean visible(int y, int h) {
        return y >= contentY && y + h <= contentBottom;
    }

    private void add(AbstractWidget w) {
        if (pass == Pass.LAYOUT && visible(w.y, w.getHeight())) addRenderableWidget(w);
    }

    private void button(int x, int y, int w, String label, Widgets.Style style, boolean enabled, String disabledTip, Runnable action) {
        if (pass != Pass.LAYOUT) return;
        Widgets.FlatButton b = new Widgets.FlatButton(x, y, w, 16, label, style, action);
        b.active = enabled;
        if (!enabled && disabledTip != null) tip(b, disabledTip);
        add(b);
    }

    private void toggle(int x, int y, boolean value, boolean enabled, String tip, java.util.function.Consumer<Boolean> onChange) {
        if (pass != Pass.LAYOUT) return;
        Widgets.Toggle t = new Widgets.Toggle(x, y, value, tip, onChange);
        t.active = enabled;
        if (tip != null) tip(t, tip);
        add(t);
    }

    private void card(int x, int y, int w, int h) {
        if (pass == Pass.BACKGROUND) Theme.card(g, x, y, w, h);
    }

    private void text(String s, int x, int y, int color) {
        if (pass == Pass.FOREGROUND) g.drawString(font, s, x, y, color, false);
    }

    private void textRight(String s, int right, int y, int color) {
        if (pass == Pass.FOREGROUND) g.drawString(font, s, right - font.width(s), y, color, false);
    }

    private int wrapLines(String s, int w) {
        return font.split(new TextComponent(s), Math.max(10, w)).size();
    }

    private int wrapped(String s, int x, int y, int w, int color) {
        List<FormattedCharSequence> lines = font.split(new TextComponent(s), Math.max(10, w));
        if (pass == Pass.FOREGROUND) {
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x, y + i * 10, color, false);
        }
        return y + lines.size() * 10;
    }

    private void bar(int x, int y, int w, int h, double f, int color) {
        if (pass == Pass.BACKGROUND) Theme.bar(g, x, y, w, h, f, color);
    }

    private void sparkline(int x, int y, int w, int h, int[] values, int minMax, int color) {
        if (pass == Pass.BACKGROUND) Theme.sparkline(g, x, y, w, h, values, minMax, color);
    }

    private void pill(String s, int x, int y, int color) {
        if (pass == Pass.FOREGROUND) Theme.pill(g, font, s, x, y, color);
    }

    private int title(String s, int y) {
        text(s, contentX, y, Theme.TEXT);
        if (pass == Pass.BACKGROUND) g.fill(contentX, y + 11, contentX + contentW, y + 12, Theme.DIVIDER);
        return y + 17;
    }

    // ------------------------------------------------------------------ pages

    private void runPage() {
        int y = contentY - scroll;
        int end = switch (page) {
            case "client.overview" -> pageClientOverview(y);
            case "client.settings" -> pageClientSettings(y);
            case "about" -> pageAbout(y);
            default -> serverPage(y);
        };
        if (pass == Pass.LAYOUT) contentHeight = end + scroll - contentY + 4;
    }

    /** Server pages first check that the server has Polycore and lets us look. */
    private int serverPage(int y) {
        if (!serverHasMod()) {
            y = title("Server", y);
            // card() draws in the background pass, so the height can come from the wrapped text drawn after it
            text("This server does not have Polycore", contentX + PAD, y + PAD, Theme.AMBER);
            int end = wrapped("Polycore is installed on your game but not on the server you are connected to, so server "
                    + "statistics, optimizations and tools are not available here. The client part (preferences, "
                    + "HUD) still works. Server owners only need to install Polycore on the server.",
                    contentX + PAD, y + PAD + 14, contentW - 2 * PAD, Theme.MUTED);
            card(contentX, y, contentW, end + PAD - y);
            return end + PAD;
        }
        CompoundTag s = state();
        if (s == null) {
            y = title("Server", y);
            text("Connecting to Polycore on the server...", contentX, y, Theme.MUTED);
            return y + LINE;
        }
        if (!s.getBoolean("canView")) {
            y = title("Server", y);
            int h = 48;
            card(contentX, y, contentW, h);
            text("Access restricted", contentX + PAD, y + PAD, Theme.AMBER);
            wrapped("This server only lets operators open the Polycore menu.", contentX + PAD, y + PAD + 14, contentW - 2 * PAD, Theme.MUTED);
            return y + h;
        }
        return switch (page) {
            case "server.optimizations" -> pageOptimizations(s, y);
            case "server.lag" -> pageLag(s, y);
            case "server.guard" -> pageGuard(s, y);
            case "server.profiler" -> pageProfiler(s, y);
            case "server.tools" -> pageTools(s, y);
            case "server.reports" -> pageReports(s, y);
            case "server.settings" -> pageServerSettings(s, y);
            default -> pageDashboard(s, y);
        };
    }

    // ---- Dashboard

    private int pageDashboard(CompoundTag s, int y) {
        y = title("Dashboard", y);
        CompoundTag hist = s.getCompound("history");
        int tileW = (contentW - 3 * GAP) / 4;
        int tileH = 62;
        int x = contentX;

        double cps = s.getDouble("cps1");
        tile(x, y, tileW, tileH, "CHUNKS / S", String.format("%.1f", cps), Theme.ACCENT,
                String.format("10 s avg %.1f", s.getDouble("cps10")), hist.getIntArray("cps"), 10, Theme.ACCENT);
        x += tileW + GAP;

        double mspt = s.getDouble("mspt");
        int msptColor = mspt > 50 ? Theme.RED : mspt > 35 ? Theme.AMBER : Theme.GREEN;
        tile(x, y, tileW, tileH, "TICK TIME", String.format("%.1f ms", mspt), msptColor,
                String.format("%.1f TPS", s.getDouble("tps")), hist.getIntArray("mspt"), 500, msptColor);
        x += tileW + GAP;

        double cpu = s.getDouble("cpu");
        int cores = s.getInt("cores");
        tile(x, y, tileW, tileH, "CPU", cpu < 0 ? "?" : String.format("%.0f%%", cpu * 100), Theme.VIOLET,
                cpu < 0 ? cores + " cores" : String.format("~%.1f of %d cores", cpu * cores, cores),
                hist.getIntArray("cpu"), 100, Theme.VIOLET);
        x += tileW + GAP;

        long used = s.getLong("memUsed"), max = s.getLong("memMax");
        card(x, y, tileW, tileH);
        text("MEMORY", x + PAD, y + 6, Theme.DIM);
        if (pass == Pass.FOREGROUND) Theme.bigText(g, font, Theme.gb(used), x + PAD, y + 18, Theme.AMBER, 1.5f);
        text("of " + Theme.gb(max), x + PAD, y + 34, Theme.MUTED);
        bar(x + PAD, y + tileH - 14, tileW - 2 * PAD, 6, max == 0 ? 0 : (double) used / max, Theme.AMBER);
        y += tileH + GAP;

        // Current task
        y = taskCard(s, y);

        // Last benchmark + pool side by side
        int halfW = (contentW - GAP) / 2;
        int h = 74;
        card(contentX, y, halfW, h);
        text("Last benchmark", contentX + PAD, y + 6, Theme.TEXT);
        if (s.contains("lastBench") && s.getCompound("lastBench").contains("gain")) {
            CompoundTag b = s.getCompound("lastBench");
            double base = b.getDouble("baseline"), poly = b.getDouble("polycore");
            double top = Math.max(1, Math.max(base, poly));
            String baseText = String.format("%.1f", base), polyText = String.format("%.1f", poly);
            // Leave room for the widest number so a full bar never runs under it.
            int bw = halfW - 2 * PAD - 60 - Math.max(font.width(baseText), font.width(polyText)) - 6;
            text("Baseline", contentX + PAD, y + 22, Theme.MUTED);
            bar(contentX + PAD + 60, y + 22, bw, 8, base / top, 0xFF64748B);
            textRight(baseText, contentX + halfW - PAD, y + 22, Theme.MUTED);
            text("Polycore", contentX + PAD, y + 36, Theme.ACCENT);
            bar(contentX + PAD + 60, y + 36, bw, 8, poly / top, Theme.ACCENT);
            textRight(polyText, contentX + halfW - PAD, y + 36, Theme.ACCENT);
            text(String.format("%+.0f%% generation speed (chunks/s)", b.getDouble("gain")), contentX + PAD, y + 54, Theme.GREEN);
        } else {
            wrapped("No benchmark yet. Run one from Tools to measure the gain on this machine.", contentX + PAD, y + 22, halfW - 2 * PAD, Theme.MUTED);
        }

        int px = contentX + halfW + GAP;
        card(px, y, halfW, h);
        text("Polycore thread pool", px + PAD, y + 6, Theme.TEXT);
        boolean inUse = s.getBoolean("poolInUse");
        pill(inUse ? "IN USE" : "IDLE", px + halfW - PAD - font.width(inUse ? "IN USE" : "IDLE") - 8, y + 4, inUse ? Theme.GREEN : Theme.MUTED);
        text(s.getInt("poolSize") + " / " + s.getInt("poolMax") + " threads - " + s.getInt("poolActive") + " busy - "
                + s.getInt("poolQueued") + " queued", px + PAD, y + 22, Theme.MUTED);
        double util = s.getDouble("poolUtil");
        text(String.format("Utilization %.0f%%", util * 100), px + PAD, y + 36, Theme.TEXT);
        sparkline(px + PAD, y + 48, halfW - 2 * PAD, 18, hist.getIntArray("pool"), 100, Theme.GREEN);
        y += h + GAP;

        // Optimizations summary
        ListTag opts = s.getList("opts", Tag.TAG_COMPOUND);
        int rows = 0;
        for (int i = 0; i < opts.size(); i++) if (!opts.getCompound(i).getString("kind").equals("PLANNED")) rows++;
        h = 22 + rows * 13 + 4;
        card(contentX, y, contentW, h);
        text("Optimizations", contentX + PAD, y + 6, Theme.TEXT);
        button(contentX + contentW - PAD - 60, y + 3, 60, "Manage", Widgets.Style.NEUTRAL, true, null, () -> go("server.optimizations"));
        int ry = y + 22;
        for (int i = 0; i < opts.size(); i++) {
            CompoundTag o = opts.getCompound(i);
            if (o.getString("kind").equals("PLANNED")) continue;
            text(o.getString("name"), contentX + PAD, ry, Theme.MUTED);
            String st = o.getString("status");
            pill(st, contentX + contentW - PAD - font.width(st) - 8, ry - 2, statusColor(st));
            ry += 13;
        }
        y += h + GAP;

        // Activity
        List<ClientBridge.Notification> activity = ClientBridge.activity();
        int n = Math.min(6, activity.size());
        h = 22 + Math.max(1, n) * 11 + 4;
        card(contentX, y, contentW, h);
        text("Recent activity", contentX + PAD, y + 6, Theme.TEXT);
        if (n == 0) text("Nothing yet.", contentX + PAD, y + 22, Theme.DIM);
        for (int i = 0; i < n; i++) {
            ClientBridge.Notification a = activity.get(i);
            String line = a.title() + " - " + a.message();
            text(font.plainSubstrByWidth(line, contentW - 2 * PAD), contentX + PAD, y + 22 + i * 11, levelColor(a.level()));
        }
        return y + h;
    }

    private void tile(int x, int y, int w, int h, String label, String value, int valueColor, String sub,
                      int[] history, int minMax, int lineColor) {
        card(x, y, w, h);
        text(label, x + PAD, y + 6, Theme.DIM);
        if (pass == Pass.FOREGROUND) Theme.bigText(g, font, value, x + PAD, y + 18, valueColor, 1.5f);
        text(sub, x + PAD, y + 34, Theme.MUTED);
        sparkline(x + PAD, y + h - 16, w - 2 * PAD, 12, history, minMax, lineColor);
    }

    private int taskCard(CompoundTag s, int y) {
        boolean running = s.getBoolean("taskRunning");
        int h = running ? 58 : 30;
        card(contentX, y, contentW, h);
        if (!running) {
            text("No task running", contentX + PAD, y + 11, Theme.MUTED);
            button(contentX + contentW - PAD - 80, y + 7, 80, "Open tools", Widgets.Style.NEUTRAL, true, null, () -> go("server.tools"));
            return y + h + GAP;
        }
        CompoundTag t = s.getCompound("task");
        String label = t.getString("label");
        if (s.getBoolean("benchRunning")) label = "Benchmark - phase " + s.getString("benchPhase");
        text(label, contentX + PAD, y + 6, Theme.TEXT);
        double f = t.getDouble("fraction");
        textRight(String.format("%.0f%%", f * 100), contentX + contentW - PAD - 60, y + 6, Theme.ACCENT);
        bar(contentX + PAD, y + 20, contentW - 2 * PAD - 66, 8, f, Theme.ACCENT);
        double rate = t.getDouble("rate");
        long remaining = t.getLong("total") - t.getInt("done");
        String eta = rate > 0 ? Theme.duration(remaining / rate) : "?";
        text(String.format("%d / %d chunks - %.1f chunks/s - %s left - %d/%d in flight%s",
                t.getInt("done"), t.getLong("total"), rate, eta, t.getInt("inFlight"), t.getInt("limit"),
                t.getBoolean("keepTps") ? " (keeping TPS stable)" : ""), contentX + PAD, y + 36, Theme.MUTED);
        button(contentX + contentW - PAD - 56, y + 16, 56, "Stop", Widgets.Style.DANGER, canEdit(), "Operator required",
                () -> PolycoreClient.sendAction("stop", "", ""));
        return y + h + GAP;
    }

    // ---- TPS Guard

    private static CompoundTag settingTag(CompoundTag s, String key) {
        ListTag list = s.getList("settings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).getString("key").equals(key)) return list.getCompound(i);
        return null;
    }

    private static String clock(long millis) {
        return java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).format(CLOCK);
    }

    private int pageGuard(CompoundTag s, int y) {
        y = title("TPS Guard", y);
        CompoundTag gd = s.getCompound("guard");
        if (!gd.contains("measures")) {
            text("This server runs an older Polycore without the TPS Guard.", contentX, y, Theme.MUTED);
            return y + LINE;
        }
        boolean edit = s.getBoolean("canEdit");
        boolean on = gd.getBoolean("enabled");
        int level = gd.getInt("level"), max = gd.getInt("max"), threshold = gd.getInt("threshold");

        // Status, master switch and threshold
        String intro = "Only while ticks stay above the threshold, the Guard turns on the measures below one at a time, "
                + "from the least to the most visible (one more every 10 s while needed), and turns them off again after 30 s "
                + "under " + (threshold * 3 / 4) + " ms. Nothing changes while the server runs normally, and the view distance "
                + "never changes. Every switch applies at once.";
        int il = wrapLines(intro, contentW - 2 * PAD);
        int h = 34 + il * 10 + 36;
        card(contentX, y, contentW, h);
        text("TPS Guard", contentX + PAD, y + 6, Theme.TEXT);
        toggle(contentX + contentW - PAD - 28, y + 4, on, edit, edit ? "Turn the TPS Guard on or off" : "Operator required",
                v -> PolycoreClient.sendAction("set", "guard.enabled", Boolean.toString(v)));
        textRight(String.format(contentW < 400 ? "%.1f ms" : "Last 3 s: %.1f ms per tick", gd.getDouble("recent")), contentX + contentW - PAD - 36, y + 6, Theme.MUTED);
        String state;
        int color;
        if (!on) {
            state = "Off";
            color = Theme.MUTED;
        } else if (max == 0) {
            state = "On, but every measure below is switched off";
            color = Theme.AMBER;
        } else if (level == 0) {
            state = "Standing by: the server runs normally";
            color = Theme.GREEN;
        } else {
            state = "Acting: level " + level + " of " + max + " since " + clock(gd.getLong("since"));
            color = Theme.AMBER;
        }
        text(state, contentX + PAD, y + 20, color);
        wrapped(intro, contentX + PAD, y + 34, contentW - 2 * PAD, Theme.DIM);
        int fy = y + 34 + il * 10 + 6;
        text("Starts above", contentX + PAD, fy + 4, Theme.TEXT);
        CompoundTag th = settingTag(s, "guard.thresholdMs");
        if (th != null) settingControl(th, contentX + PAD + 110, fy, 150, edit);
        y += h + GAP;

        // Measures, in the order the Guard turns them on
        ListTag measures = gd.getList("measures", Tag.TAG_COMPOUND);
        int textW = contentW - 2 * PAD - 40;
        for (int i = 0; i < measures.size(); i++) {
            CompoundTag m = measures.getCompound(i);
            String desc = m.getString("block").isEmpty() ? m.getString("desc") : "Off: " + m.getString("block") + ". " + m.getString("desc");
            int dl = wrapLines(desc, textW);
            boolean hasParam = m.contains("param");
            h = 20 + dl * 10 + (hasParam ? 34 : 0) + 6;
            card(contentX, y, contentW, h);
            String name = (i + 1) + ". " + m.getString("name");
            boolean en = m.getBoolean("enabled"), act = m.getBoolean("active");
            String block = m.getString("block");
            String st = !block.isEmpty() ? "BLOCKED" : !on || !en ? "OFF" : act ? "ACTIVE" : "STANDBY";
            text(name, contentX + PAD, y + 6, Theme.TEXT);
            pill(st, contentX + PAD + font.width(name) + 6, y + 4, !block.isEmpty() ? Theme.RED : act ? Theme.AMBER : on && en ? Theme.GREEN : Theme.MUTED);
            int detailW = contentW - 2 * PAD - 36 - font.width(name) - font.width(st) - 20;
            textRight(font.plainSubstrByWidth(m.getString("detail"), Math.max(0, detailW)), contentX + contentW - PAD - 36, y + 6, act ? Theme.AMBER : Theme.DIM);
            final String key = m.getString("key");
            toggle(contentX + contentW - PAD - 28, y + 4, en, edit && block.isEmpty(),
                    !edit ? "Operator required" : !block.isEmpty() ? block : "Use this measure when the server lags",
                    v -> PolycoreClient.sendAction("set", key, Boolean.toString(v)));
            wrapped(desc, contentX + PAD, y + 20, textW, Theme.MUTED);
            CompoundTag p = hasParam ? settingTag(s, m.getString("param")) : null;
            if (p != null) {
                int py = y + 20 + dl * 10 + 4;
                text(p.getString("label"), contentX + PAD, py + 4, Theme.TEXT);
                settingControl(p, contentX + PAD + 190, py, 150, edit);
            }
            y += h + GAP;
        }

        // Recent activations
        ListTag hist = gd.getList("history", Tag.TAG_COMPOUND);
        h = 22 + Math.max(1, hist.size()) * 12 + 4;
        card(contentX, y, contentW, h);
        text("Recent activations", contentX + PAD, y + 6, Theme.TEXT);
        if (hist.isEmpty()) text("None since the server started.", contentX + PAD, y + 22, Theme.DIM);
        for (int i = 0; i < hist.size(); i++) {
            CompoundTag e = hist.getCompound(i);
            text(String.format("%s - %s, up to level %d", clock(e.getLong("start")),
                    Theme.duration((e.getLong("end") - e.getLong("start")) / 1000.0), e.getInt("peak")), contentX + PAD, y + 22 + i * 12, Theme.MUTED);
        }
        return y + h;
    }

    // ---- Lag analyzer

    private static final java.time.format.DateTimeFormatter CLOCK = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");

    private int pageLag(CompoundTag s, int y) {
        y = title("Lag analyzer", y);
        CompoundTag lag = s.getCompound("lag");
        if (!lag.contains("sections")) {
            text("This server runs an older Polycore without the lag analyzer.", contentX, y, Theme.MUTED);
            return y + LINE;
        }
        boolean edit = s.getBoolean("canEdit");
        int threshold = Math.max(1, lag.getInt("threshold"));

        // Live breakdown of the tick
        ListTag sections = lag.getList("sections", Tag.TAG_COMPOUND);
        String note = "Average per tick over the last 5 s. A full bar is the 50 ms a tick has at 20 TPS.";
        int noteLines = wrapLines(note, contentW - 2 * PAD);
        int h = 22 + noteLines * 10 + 6 + sections.size() * 12 + 4;
        card(contentX, y, contentW, h);
        double mspt = lag.getDouble("mspt");
        int color = mspt > threshold ? Theme.RED : mspt > threshold * 0.7 ? Theme.AMBER : Theme.GREEN;
        text("Where the tick time goes", contentX + PAD, y + 6, Theme.TEXT);
        String figures = String.format("%.1f TPS - %.1f ms per tick - worst %.0f ms", lag.getDouble("tps"), mspt, lag.getDouble("worst"));
        if (font.width(figures) > contentW - 2 * PAD - 150) figures = String.format("%.1f TPS - %.1f ms", lag.getDouble("tps"), mspt);
        textRight(figures, contentX + contentW - PAD, y + 6, color);
        wrapped(note, contentX + PAD, y + 20, contentW - 2 * PAD, Theme.DIM);
        int labelW = 160, valueW = 56;
        int barX = contentX + PAD + labelW, barW = Math.max(20, contentW - 2 * PAD - labelW - valueW);
        int ry = y + 22 + noteLines * 10 + 6;
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag e = sections.getCompound(i);
            double ms = e.getDouble("ms");
            text(e.getString("name"), contentX + PAD, ry, Theme.MUTED);
            bar(barX, ry + 1, barW, 7, ms / 50.0, msColor(ms));
            textRight(String.format("%.2f ms", ms), contentX + contentW - PAD, ry, Theme.TEXT);
            ry += 12;
        }
        y += h + GAP;

        // Detailed analysis: start it, alerts
        CompoundTag cap = lag.getCompound("capture");
        String desc = "Times every entity and block entity for " + cap.getInt("seconds") + " s to find what costs the most, "
                + "with its position. " + (cap.getBoolean("auto") ? "Also starts by itself when the server lags."
                : "Automatic analysis is off (Settings).");
        int dl = wrapLines(desc, contentW - 2 * PAD);
        h = 20 + dl * 10 + 24;
        card(contentX, y, contentW, h);
        text("Detailed analysis", contentX + PAD, y + 6, Theme.TEXT);
        wrapped(desc, contentX + PAD, y + 20, contentW - 2 * PAD, Theme.MUTED);
        int fy = y + 20 + dl * 10 + 4;
        if (cap.getBoolean("running")) {
            text("Analyzing... " + cap.getInt("remaining") + " s left", contentX + PAD, fy + 4, Theme.AMBER);
        } else {
            button(contentX + PAD, fy, 110, "Analyze now", Widgets.Style.PRIMARY, edit, "Operator required",
                    () -> PolycoreClient.sendAction("lag_capture", "", ""));
        }
        int ax = contentX + contentW - PAD - 28;
        textRight("Alert me when the server lags", ax - 6, fy + 3, Theme.TEXT);
        toggle(ax, fy, lag.getBoolean("alerts"), edit,
                edit ? "A toast when the server drops below 20 TPS, with the main cause" : "Operator required",
                v -> PolycoreClient.sendAction("lag_alerts", "", Boolean.toString(v)));
        y += h + GAP;

        if (cap.contains("last")) y = lastAnalysis(cap.getCompound("last"), edit, y);

        // Spikes
        ListTag spikes = lag.getList("spikes", Tag.TAG_COMPOUND);
        h = 22 + Math.max(1, spikes.size()) * 12 + 4;
        card(contentX, y, contentW, h);
        text("Lag spikes (ticks over " + threshold + " ms)", contentX + PAD, y + 6, Theme.TEXT);
        if (spikes.isEmpty()) text("No spike recorded yet.", contentX + PAD, y + 22, Theme.DIM);
        for (int i = 0; i < spikes.size(); i++) {
            CompoundTag e = spikes.getCompound(i);
            int sy = y + 22 + i * 12;
            boolean ongoing = e.getBoolean("ongoing");
            String when = ongoing ? "now" : java.time.Instant.ofEpochMilli(e.getLong("start"))
                    .atZone(java.time.ZoneId.systemDefault()).format(CLOCK);
            text(when, contentX + PAD, sy, ongoing ? Theme.RED : Theme.MUTED);
            int causeX = Math.min(236, (contentW - 2 * PAD) / 2);
            text(font.plainSubstrByWidth(String.format("%d slow ticks, worst %.0f ms", e.getInt("ticks"), e.getDouble("worst")), causeX - 60),
                    contentX + PAD + 56, sy, Theme.TEXT);
            text(font.plainSubstrByWidth(String.format("%s (%.1f ms)", e.getString("cause"), e.getDouble("causeMs")),
                    contentW - 2 * PAD - causeX), contentX + PAD + causeX, sy, Theme.AMBER);
        }
        return y + h;
    }

    private int lastAnalysis(CompoundTag a, boolean edit, int y) {
        // Costliest sources, with a teleport button for operators
        ListTag top = a.getList("top", Tag.TAG_COMPOUND);
        int rowH = 17;
        int h = 48 + Math.max(1, top.size()) * rowH;
        card(contentX, y, contentW, h);
        text("Last analysis - " + a.getString("date") + (a.getBoolean("auto") ? " (automatic)" : ""), contentX + PAD, y + 6, Theme.TEXT);
        if (a.contains("file")) {
            String file = a.getString("file");
            button(contentX + contentW - PAD - 80, y + 3, 80, "Full report", Widgets.Style.NEUTRAL, true, null, () -> {
                selectedReport = file;
                PolycoreClient.sendAction("report", file, "");
                go("server.reports");
            });
        }
        text(String.format("%d s - %.1f ms per tick - %.1f TPS - worst tick %.0f ms", a.getInt("seconds"), a.getDouble("mspt"),
                a.getDouble("tps"), a.getDouble("worst")), contentX + PAD, y + 20, Theme.MUTED);
        int goW = 26;
        int c1 = contentX + PAD;
        int cMs = contentX + contentW - PAD - goW - 8;
        int cWhere = Math.max(c1 + 90, cMs - 50 - 150);
        text("Costliest sources", c1, y + 34, Theme.DIM);
        text("Where", cWhere, y + 34, Theme.DIM);
        textRight("ms/tick", cMs, y + 34, Theme.DIM);
        if (top.isEmpty()) text("Nothing was measured.", c1, y + 48, Theme.DIM);
        for (int i = 0; i < top.size(); i++) {
            CompoundTag e = top.getCompound(i);
            int ry = y + 48 + i * rowH;
            String name = (i + 1) + ". " + e.getString("type") + (e.getBoolean("entity") ? "" : " (block)");
            text(font.plainSubstrByWidth(name, cWhere - c1 - 6), c1, ry + 4, Theme.TEXT);
            text(font.plainSubstrByWidth(shortDim(e.getString("dim")) + " " + e.getInt("x") + " " + e.getInt("y") + " " + e.getInt("z"), cMs - 50 - cWhere),
                    cWhere, ry + 4, Theme.MUTED);
            double ms = e.getDouble("ms");
            textRight(String.format("%.2f", ms), cMs, ry + 4, msColor(ms * 5));
            final String index = Integer.toString(i);
            final String where = shortDim(e.getString("dim")) + " " + e.getInt("x") + " " + e.getInt("y") + " " + e.getInt("z");
            final String what = e.getString("type") + (e.getBoolean("entity") ? " (entity)" : " (block entity)");
            final String cost = String.format("%.2f ms per tick", ms);
            button(contentX + contentW - PAD - goW, ry, goW, "TP", Widgets.Style.NEUTRAL, edit, "Operator required",
                    () -> openConfirm(new Confirm("Teleport to this source?", List.of(what, where + " - " + cost,
                            "Your current position is saved: open Polycore again to come back."),
                            "Teleport", () -> {
                                PolycoreClient.sendAction("lag_tp", index, "");
                                onClose();
                            },
                            "Cancel", () -> {})));
        }
        y += h + GAP;

        // By type and by mod, side by side
        ListTag types = a.getList("types", Tag.TAG_COMPOUND), mods = a.getList("mods", Tag.TAG_COMPOUND);
        int halfW = (contentW - GAP) / 2;
        h = 22 + Math.max(1, Math.max(types.size(), mods.size())) * 12 + 4;
        int mx = contentX + halfW + GAP;
        card(contentX, y, halfW, h);
        card(mx, y, halfW, h);
        text("By type", contentX + PAD, y + 6, Theme.TEXT);
        text("By mod", mx + PAD, y + 6, Theme.TEXT);
        for (int i = 0; i < types.size(); i++) {
            CompoundTag e = types.getCompound(i);
            int ry = y + 22 + i * 12;
            text(font.plainSubstrByWidth(e.getLong("count") + "x " + e.getString("type"), halfW - 2 * PAD - 44), contentX + PAD, ry, Theme.MUTED);
            textRight(String.format("%.2f", e.getDouble("ms")), contentX + halfW - PAD, ry, Theme.TEXT);
        }
        double total = Math.max(0.001, a.getDouble("mspt"));
        for (int i = 0; i < mods.size(); i++) {
            CompoundTag e = mods.getCompound(i);
            int ry = y + 22 + i * 12;
            text(font.plainSubstrByWidth(e.getString("mod"), 90), mx + PAD, ry, Theme.MUTED);
            bar(mx + PAD + 94, ry + 1, Math.max(10, halfW - 2 * PAD - 94 - 44), 7, e.getDouble("ms") / total, Theme.VIOLET);
            textRight(String.format("%.2f", e.getDouble("ms")), mx + halfW - PAD, ry, Theme.TEXT);
        }
        y += h + GAP;

        // Busiest chunks
        ListTag chunks = a.getList("chunks", Tag.TAG_COMPOUND);
        if (chunks.isEmpty()) return y;
        h = 22 + chunks.size() * 12 + 4;
        card(contentX, y, contentW, h);
        text("Busiest chunks", contentX + PAD, y + 6, Theme.TEXT);
        for (int i = 0; i < chunks.size(); i++) {
            CompoundTag e = chunks.getCompound(i);
            int ry = y + 22 + i * 12;
            int cx = e.getInt("x"), cz = e.getInt("z");
            text(String.format("%s, blocks %d %d", shortDim(e.getString("dim")), cx * 16 + 8, cz * 16 + 8), contentX + PAD, ry, Theme.MUTED);
            int countX = Math.min(200, (contentW - 2 * PAD) / 2);
            text(font.plainSubstrByWidth(String.format("%d entities, %d block entities", e.getLong("entities"), e.getLong("blocks")),
                    contentW - 2 * PAD - countX - 60), contentX + PAD + countX, ry, Theme.TEXT);
            textRight(String.format("%.2f ms", e.getDouble("ms")), contentX + contentW - PAD, ry, Theme.TEXT);
        }
        return y + h + GAP;
    }

    private static int msColor(double ms) {
        return ms > 25 ? Theme.RED : ms > 10 ? Theme.AMBER : Theme.ACCENT;
    }

    private static String shortDim(String dim) {
        return dim.startsWith("minecraft:") ? dim.substring(10) : dim;
    }

    // ---- Optimizations

    private int pageOptimizations(CompoundTag s, int y) {
        y = title("Optimizations", y);
        boolean edit = s.getBoolean("canEdit");
        ListTag opts = s.getList("opts", Tag.TAG_COMPOUND);
        int textW = contentW - 2 * PAD - 50;
        for (int i = 0; i < opts.size(); i++) {
            CompoundTag o = opts.getCompound(i);
            String desc = o.getString("desc");
            boolean blocked = o.contains("block");
            int h = 20 + wrapLines(desc, textW) * 10 + (blocked ? 11 : 0) + 8;
            card(contentX, y, contentW, h);
            text(o.getString("name"), contentX + PAD, y + 6, Theme.TEXT);
            String st = o.getString("status");
            pill(st, contentX + PAD + font.width(o.getString("name")) + 6, y + 4, statusColor(st));
            if (o.getString("kind").equals("ACTIVE") && !blocked && !st.equals("OFF")) {
                text(o.getBoolean("hook") ? "hook active" : "waiting for new chunks", contentX + PAD + font.width(o.getString("name"))
                        + font.width(st) + 22, y + 6, Theme.DIM);
            }
            int ty = y + 20;
            if (blocked) {
                text(o.getString("block"), contentX + PAD, ty, Theme.RED);
                ty += 11;
            }
            wrapped(desc, contentX + PAD, ty, textW, Theme.MUTED);
            if (o.getString("kind").equals("ACTIVE")) {
                String id = o.getString("id");
                boolean on = o.getBoolean("enabled");
                toggle(contentX + contentW - PAD - 28, y + 6, on, edit && o.getBoolean("toggleable"),
                        !edit ? "Operator required" : blocked ? o.getString("block") : null,
                        v -> PolycoreClient.sendAction("toggle", id, Boolean.toString(v)));
            }
            y += h + GAP;
        }
        ListTag conflicts = s.getList("conflicts", Tag.TAG_COMPOUND);
        String note = "Checked at every start: an optimization whose game code another mod also changes stays off, and "
                + "comes back by itself once that mod is removed.";
        int lines = 0;
        for (int i = 0; i < conflicts.size(); i++) {
            CompoundTag c = conflicts.getCompound(i);
            lines += wrapLines(c.getString("feature") + ": " + c.getString("reason"), contentW - 2 * PAD);
        }
        int nl = wrapLines(note, contentW - 2 * PAD);
        int h = 20 + Math.max(1, lines) * 10 + 4 + nl * 10 + 6;
        card(contentX, y, contentW, h);
        text("Compatibility with other mods", contentX + PAD, y + 6, Theme.TEXT);
        int cy = y + 20;
        if (conflicts.isEmpty()) {
            text("No other mod changes the code Polycore optimizes.", contentX + PAD, cy, Theme.GREEN);
            cy += 10;
        }
        for (int i = 0; i < conflicts.size(); i++) {
            CompoundTag c = conflicts.getCompound(i);
            cy = wrapped(c.getString("feature") + ": " + c.getString("reason"), contentX + PAD, cy, contentW - 2 * PAD, Theme.AMBER);
        }
        wrapped(note, contentX + PAD, cy + 4, contentW - 2 * PAD, Theme.DIM);
        return y + h;
    }

    // ---- Profiler

    private int pageProfiler(CompoundTag s, int y) {
        y = title("Worldgen profiler", y);
        button(contentX + contentW - 90, y - 17, 90, "Reset", Widgets.Style.NEUTRAL, s.getBoolean("canEdit"), "Operator required",
                () -> PolycoreClient.sendAction("profiler_reset", "", ""));
        ListTag steps = s.getList("steps", Tag.TAG_COMPOUND);
        if (steps.isEmpty()) {
            int h = 40;
            card(contentX, y, contentW, h);
            wrapped("No data yet: generate new chunks (explore, or start a pre-generation from Tools).", contentX + PAD, y + PAD,
                    contentW - 2 * PAD, Theme.MUTED);
            return y + h;
        }
        long total = 0;
        for (int i = 0; i < steps.size(); i++) {
            CompoundTag st = steps.getCompound(i);
            if (!st.getString("name").startsWith("calc:")) total += st.getLong("total");
        }
        int h = 22 + steps.size() * 13 + 6;
        card(contentX, y, contentW, h);
        int stageW = Math.min(120, Math.max(70, contentW - 2 * PAD - 230));
        int c1 = contentX + PAD, c2 = c1 + stageW, c3 = c2 + 60, c4 = c3 + 60, c5 = c4 + 60;
        text("Stage", c1, y + 6, Theme.DIM);
        textRight("Count", c2 + 50, y + 6, Theme.DIM);
        textRight("Avg ms", c3 + 50, y + 6, Theme.DIM);
        textRight("Max ms", c4 + 50, y + 6, Theme.DIM);
        text("Share of time", c5, y + 6, Theme.DIM);
        int ry = y + 22;
        int barW = contentX + contentW - PAD - 30 - c5;
        for (int i = 0; i < steps.size(); i++) {
            CompoundTag st = steps.getCompound(i);
            String name = st.getString("name");
            boolean calc = name.startsWith("calc:");
            long count = st.getLong("count");
            double avg = count == 0 ? 0 : st.getLong("total") / (double) count / 1e6;
            int color = calc ? Theme.ACCENT : Theme.TEXT;
            text(font.plainSubstrByWidth(name, stageW - 6), c1, ry, color);
            textRight(Long.toString(count), c2 + 50, ry, Theme.MUTED);
            textRight(String.format("%.2f", avg), c3 + 50, ry, color);
            textRight(String.format("%.1f", st.getLong("max") / 1e6), c4 + 50, ry, Theme.MUTED);
            if (!calc && total > 0) {
                double share = st.getLong("total") / (double) total;
                bar(c5, ry + 1, Math.max(20, barW), 7, share, Theme.AMBER);
                textRight(String.format("%.0f%%", share * 100), contentX + contentW - PAD, ry, Theme.TEXT);
            }
            ry += 13;
        }
        y += h + GAP;
        int nh = 14 + wrapLines(PROFILER_NOTE, contentW - 2 * PAD) * 10;
        card(contentX, y, contentW, nh);
        wrapped(PROFILER_NOTE, contentX + PAD, y + 7, contentW - 2 * PAD, Theme.MUTED);
        return y + nh;
    }

    private static final String PROFILER_NOTE = "Times are measured from the moment a stage is requested until it finishes "
            + "(waiting included). Blue calc: lines are pure computation on the Polycore pool. The stage with the "
            + "largest share is the current bottleneck.";

    // ---- Tools

    private int pageTools(CompoundTag s, int y) {
        y = title("Tools", y);
        boolean edit = s.getBoolean("canEdit");
        boolean busy = s.getBoolean("taskRunning") || s.getBoolean("benchRunning");
        String tip = !edit ? "Operator required" : busy ? "A task is already running" : null;

        y = taskCard(s, y);

        // Pre-generation
        int h = 98;
        card(contentX, y, contentW, h);
        text("Pre-generation", contentX + PAD, y + 6, Theme.TEXT);
        wrapped("Generates every chunk in a square around you before players get there.", contentX + PAD, y + 18, contentW - 2 * PAD, Theme.MUTED);
        int fy = y + 34;
        text("Radius (chunks)", contentX + PAD, fy + 4, Theme.TEXT);
        Widgets.NumberField radius = numberField("tool.pregenRadius", contentX + PAD + 92, fy, 52, 1, 5000, "32", edit && !busy, null);
        int qx = contentX + PAD + 150;
        for (int r : new int[] {16, 32, 64, 128, 256}) {
            final int rr = r;
            button(qx, fy, 30, Integer.toString(r), Widgets.Style.NEUTRAL, edit && !busy, tip, () -> {
                drafts.put("tool.pregenRadius", Integer.toString(rr));
                requestRebuild();
            });
            qx += 33;
        }
        int r = parseOr(drafts.getOrDefault("tool.pregenRadius", "32"), 32);
        long chunks = (2L * r + 1) * (2L * r + 1);
        String count = String.format("%,d chunks", chunks);
        if (qx + 6 + font.width(count) <= contentX + contentW - PAD) text(count, qx + 6, fy + 4, Theme.DIM);
        else text(count, contentX + PAD + 150, fy + 18, Theme.DIM);
        fy += 22;
        boolean keepTps = settingBool(s, "pregen.keepTps");
        text("Keep TPS stable", contentX + PAD, fy + 3, Theme.TEXT);
        toggle(contentX + PAD + 92, fy, keepTps, edit, edit ? "Slows down automatically when the server lags" : "Operator required",
                v -> PolycoreClient.sendAction("set", "pregen.keepTps", Boolean.toString(v)));
        fy += 20;
        button(contentX + PAD, fy, 120, "Start pre-generation", Widgets.Style.PRIMARY, edit && !busy, tip, () -> {
            int rad = parseOr(drafts.getOrDefault("tool.pregenRadius", "32"), 32);
            PolycoreClient.sendAction("pregen", Integer.toString(Math.max(1, Math.min(5000, rad))), Boolean.toString(settingBool(state(), "pregen.keepTps")));
        });
        if (radius != null) tip(radius, "1 to 5000. Press Enter or use the button.");
        y += h + GAP;

        // Benchmark
        h = 68;
        card(contentX, y, contentW, h);
        text("A/B benchmark", contentX + PAD, y + 6, Theme.TEXT);
        wrapped("Generates fresh areas far from spawn, once with Polycore off (baseline) and once with it on. Use a test world.",
                contentX + PAD, y + 18, contentW - 2 * PAD, Theme.MUTED);
        fy = y + 44;
        text("Radius", contentX + PAD, fy + 4, Theme.TEXT);
        String defRadius = Integer.toString(s.getInt("benchDefaultRadius") > 0 ? s.getInt("benchDefaultRadius") : 10);
        numberField("tool.benchRadius", contentX + PAD + 42, fy, 40, 2, 64, defRadius, edit && !busy, null);
        button(contentX + PAD + 90, fy, 100, "Run benchmark", Widgets.Style.PRIMARY, edit && !busy, tip, () -> {
            int rad = parseOr(drafts.getOrDefault("tool.benchRadius", defRadius), 10);
            PolycoreClient.sendAction("bench", Integer.toString(Math.max(2, Math.min(64, rad))), "");
        });
        y += h + GAP;

        // Parity hash
        h = 58;
        card(contentX, y, contentW, h);
        text("Parity fingerprint", contentX + PAD, y + 6, Theme.TEXT);
        wrapped("Hashes the blocks around you. Same seed + same place with optimizations off must give the same value.",
                contentX + PAD, y + 18, contentW - 2 * PAD, Theme.MUTED);
        fy = y + 36;
        text("Radius", contentX + PAD, fy + 4, Theme.TEXT);
        numberField("tool.hashRadius", contentX + PAD + 42, fy, 40, 0, 8, "4", edit, null);
        button(contentX + PAD + 90, fy, 100, "Compute", Widgets.Style.NEUTRAL, edit, edit ? null : "Operator required", () -> {
            int rad = parseOr(drafts.getOrDefault("tool.hashRadius", "4"), 4);
            PolycoreClient.sendAction("hash", Integer.toString(Math.max(0, Math.min(8, rad))), "");
        });
        return y + h;
    }

    // ---- Reports

    private int pageReports(CompoundTag s, int y) {
        y = title("Reports", y);
        ListTag reports = s.getList("reports", Tag.TAG_COMPOUND);
        if (reports.isEmpty()) {
            int h = 34;
            card(contentX, y, contentW, h);
            text("No report yet. Benchmarks and fingerprints are saved here.", contentX + PAD, y + 12, Theme.MUTED);
            return y + h;
        }
        int listW = Math.min(190, contentW / 3);
        int ly = y;
        for (int i = 0; i < reports.size(); i++) {
            CompoundTag r = reports.getCompound(i);
            String name = r.getString("name");
            boolean sel = name.equals(selectedReport);
            button(contentX, ly, listW, r.getString("kind") + " " + prettyDate(name),
                    sel ? Widgets.Style.NAV_SELECTED : Widgets.Style.NEUTRAL, true, null, () -> {
                        selectedReport = name;
                        PolycoreClient.sendAction("report", name, "");
                        requestRebuild();
                    });
            ly += 18;
        }
        int rx = contentX + listW + GAP;
        int rw = contentW - listW - GAP;
        CompoundTag rep = ClientBridge.report();
        int ry = y;
        if (selectedReport == null) {
            card(rx, ry, rw, 30);
            text("Select a report on the left.", rx + PAD, ry + 11, Theme.MUTED);
            return Math.max(ly, ry + 30);
        }
        if (rep == null || !rep.getString("name").equals(selectedReport)) {
            card(rx, ry, rw, 30);
            text("Loading...", rx + PAD, ry + 11, Theme.MUTED);
            return Math.max(ly, ry + 30);
        }
        ListTag lines = rep.getList("lines", Tag.TAG_STRING);
        // Copy the whole report to the clipboard (the title row has room for it).
        button(contentX + contentW - 60, y - 17, 60, "Copy", Widgets.Style.NEUTRAL, true, null, () -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) sb.append(lines.getString(i)).append(System.lineSeparator());
            Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
            ClientBridge.localNotify("SUCCESS", "Copied", lines.size() + " lines of " + selectedReport);
        });
        int h = PAD;
        for (int i = 0; i < lines.size(); i++) h += lines.getString(i).isEmpty() ? 5 : wrapLines(lines.getString(i), rw - 2 * PAD) * 10;
        h += PAD;
        card(rx, ry, rw, h);
        int ty = ry + PAD;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.getString(i);
            if (l.isEmpty()) {
                ty += 5;
                continue;
            }
            int color = l.startsWith("RESULT") ? Theme.GREEN : l.startsWith("===") || l.startsWith("#") ? Theme.AMBER
                    : l.startsWith("[") ? Theme.ACCENT : l.startsWith("    ") ? Theme.DIM : Theme.TEXT;
            // Reports align columns with spaces; when a line does not fit, collapse them so it wraps at words.
            String shown = font.width(l) > rw - 2 * PAD ? l.strip().replaceAll(" {2,}", " ") : l;
            ty = wrapped(shown, rx + PAD, ty, rw - 2 * PAD, color);
        }
        return Math.max(ly, ry + h);
    }

    private static String prettyDate(String name) {
        // bench-20261106-152254.txt -> 11-06 15:22 (the year never helps and does not fit on small screens)
        try {
            int i = name.indexOf('-');
            String d = name.substring(i + 1, i + 9), t = name.substring(i + 10, i + 14);
            return d.substring(4, 6) + "-" + d.substring(6, 8) + " " + t.substring(0, 2) + ":" + t.substring(2, 4);
        } catch (Exception e) {
            return name;
        }
    }

    // ---- Server settings

    private int pageServerSettings(CompoundTag s, int y) {
        y = title("Server settings", y);
        boolean edit = s.getBoolean("canEdit");
        if (!edit) {
            text("Read-only: only operators can change these settings.", contentX, y, Theme.AMBER);
            y += 14;
        }
        ListTag settings = s.getList("settings", Tag.TAG_COMPOUND);
        Map<String, List<CompoundTag>> byCategory = new LinkedHashMap<>();
        for (int i = 0; i < settings.size(); i++) {
            CompoundTag t = settings.getCompound(i);
            byCategory.computeIfAbsent(t.getString("category"), k -> new ArrayList<>()).add(t);
        }
        int controlW = 150;
        int textW = contentW - 2 * PAD - controlW - 8;
        for (Map.Entry<String, List<CompoundTag>> e : byCategory.entrySet()) {
            text(e.getKey().toUpperCase(), contentX, y + 2, Theme.DIM);
            y += 14;
            for (CompoundTag t : e.getValue()) {
                String desc = t.getString("desc");
                int h = Math.max(36, 20 + wrapLines(desc, textW) * 10 + 4);
                card(contentX, y, contentW, h);
                text(t.getString("label"), contentX + PAD, y + 6, Theme.TEXT);
                wrapped(desc, contentX + PAD, y + 18, textW, Theme.MUTED);
                settingControl(t, contentX + contentW - PAD - controlW, y + 6, controlW, edit);
                y += h + 4;
            }
            y += 4;
        }
        text("Saved on the server in config/polycore.json.", contentX, y + 2, Theme.DIM);
        return y + 14;
    }

    private void settingControl(CompoundTag t, int x, int y, int w, boolean edit) {
        String key = t.getString("key");
        String tip = edit ? null : "Operator required";
        if (t.getString("type").equals("BOOL")) {
            boolean v = t.getBoolean("value");
            text(v ? "On" : "Off", x + w - 36 - font.width(v ? "On" : "Off"), y + 3, v ? Theme.GREEN : Theme.MUTED);
            toggle(x + w - 28, y, v, edit, tip, nv -> PolycoreClient.sendAction("set", key, Boolean.toString(nv)));
            return;
        }
        int min = t.getInt("min"), max = t.getInt("max"), value = t.getInt("value");
        String zero = t.contains("zero") ? t.getString("zero") : null;
        // [-] [field] [+] [Set]
        button(x, y, 16, "-", Widgets.Style.NEUTRAL, edit && value > min, tip,
                () -> PolycoreClient.sendAction("set", key, Integer.toString(Math.max(min, value - 1))));
        Widgets.NumberField f = numberField("set." + key, x + 19, y, w - 19 - 19 - 38, min, max, Integer.toString(value), edit,
                v -> PolycoreClient.sendAction("set", key, Integer.toString(v)));
        if (f != null) {
            tip(f, min + " to " + max + (zero != null ? " (0 = " + zero + ")" : "")
                    + ". Type a value and press Enter.");
        }
        button(x + w - 19 - 38, y, 16, "+", Widgets.Style.NEUTRAL, edit && value < max, tip,
                () -> PolycoreClient.sendAction("set", key, Integer.toString(Math.min(max, value + 1))));
        button(x + w - 36, y, 36, "Set", Widgets.Style.PRIMARY, edit, tip, () -> {
            String raw = drafts.getOrDefault("set." + key, Integer.toString(value));
            int v = parseOr(raw, value);
            if (v >= min && v <= max) {
                PolycoreClient.sendAction("set", key, Integer.toString(v));
                drafts.remove("set." + key);
            }
        });
        String shown = value == 0 && zero != null ? zero : t.getString("unit").isEmpty() ? "" : t.getString("unit");
        if (!shown.isEmpty()) text(shown, x - 4 - font.width(shown), y + 4, Theme.DIM);
        text(min + "-" + max, x + 19, y + 18, Theme.DIM);
    }

    /** Adds a number field (layout pass only) whose text survives rebuilds through {@link #drafts}. */
    private Widgets.NumberField numberField(String draftKey, int x, int y, int w, int min, int max, String initial, boolean enabled,
                                            java.util.function.Consumer<Integer> onSubmit) {
        if (pass != Pass.LAYOUT || !visible(y, 16)) return null;
        String text = drafts.getOrDefault(draftKey, initial);
        Widgets.NumberField f = new Widgets.NumberField(font, x, y, w, 16, min, max, text,
                s -> drafts.put(draftKey, s),
                v -> {
                    if (onSubmit != null) {
                        onSubmit.accept(v);
                        drafts.remove(draftKey);
                    }
                });
        f.setEditable(enabled);
        f.active = enabled;
        addRenderableWidget(f);
        return f;
    }

    // ---- Client pages

    private int pageClientOverview(int y) {
        y = title("This game", y);
        Minecraft mc = Minecraft.getInstance();
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024), max = rt.maxMemory() / (1024 * 1024);
        int tileW = (contentW - 3 * GAP) / 4;
        int tileH = 52;
        int x = contentX;
        smallTile(x, y, tileW, tileH, "FPS", Integer.toString(fps(mc)), Theme.ACCENT);
        x += tileW + GAP;
        smallTile(x, y, tileW, tileH, "MEMORY", Theme.gb(used) + " / " + Theme.gb(max), Theme.AMBER);
        x += tileW + GAP;
        smallTile(x, y, tileW, tileH, "CPU CORES", Integer.toString(rt.availableProcessors()), Theme.VIOLET);
        x += tileW + GAP;
        smallTile(x, y, tileW, tileH, "RENDER DISTANCE", mc.options.renderDistance + " chunks", Theme.GREEN);
        y += tileH + GAP;

        int h = 92;
        card(contentX, y, contentW, h);
        text("Connection", contentX + PAD, y + 6, Theme.TEXT);
        String where = mc.hasSingleplayerServer() ? "Singleplayer (integrated server)"
                : mc.getCurrentServer() != null ? "Multiplayer - " + mc.getCurrentServer().name : "Multiplayer";
        row("World", where, y + 22);
        row("Polycore on this game", "v" + Services.PLATFORM.modVersion() + " (" + Services.PLATFORM.loaderName() + " "
                + Services.PLATFORM.minecraftVersion() + ")", y + 36);
        CompoundTag s = state();
        String server = !serverHasMod() ? "Not installed on this server" : s == null ? "Connecting..." : "v" + s.getString("version")
                + " (" + s.getString("loader") + ")";
        row("Polycore on the server", server, y + 50);
        String access = !serverHasMod() || s == null ? "-" : s.getBoolean("owner") ? "Owner (full access)"
                : s.getBoolean("canEdit") ? "Operator (full access)" : "Read-only";
        row("Your access", access, y + 64);
        y += h + GAP;

        h = 44;
        card(contentX, y, contentW, h);
        wrapped("Generation happens on the server. In singleplayer, that server runs inside this game, so every server "
                + "page applies to your world and you can change everything without being an operator.",
                contentX + PAD, y + 8, contentW - 2 * PAD, Theme.MUTED);
        return y + h;
    }

    private void row(String label, String value, int y) {
        text(label, contentX + PAD, y, Theme.MUTED);
        text(value, contentX + PAD + 140, y, Theme.TEXT);
    }

    private void smallTile(int x, int y, int w, int h, String label, String value, int color) {
        card(x, y, w, h);
        text(label, x + PAD, y + 6, Theme.DIM);
        if (pass == Pass.FOREGROUND) {
            float scale = font.width(value) * 1.5f > w - 2 * PAD ? 1.0f : 1.5f;
            Theme.bigText(g, font, value, x + PAD, y + 22, color, scale);
        }
    }

    private int pageClientSettings(int y) {
        y = title("Preferences (this game)", y);
        ClientConfig.Data c = ClientConfig.get();
        y = prefRow(y, "Notifications", "Show Polycore notifications as toasts in the corner of the screen.",
                c.toastsEnabled, v -> {
                    c.toastsEnabled = v;
                    ClientConfig.save();
                });
        y = prefRow(y, "HUD overlay", "Small always-on panel with chunks/s, tick time and task progress (needs Polycore on the server).",
                c.hudEnabled, v -> {
                    c.hudEnabled = v;
                    ClientConfig.save();
                });
        int h = 34;
        card(contentX, y, contentW, h);
        text("HUD position", contentX + PAD, y + 6, Theme.TEXT);
        text(cornerName(c.hudCorner), contentX + PAD, y + 18, Theme.MUTED);
        button(contentX + contentW - PAD - 90, y + 9, 90, "Change", Widgets.Style.NEUTRAL, true, null, () -> {
            ClientConfig.Corner[] all = ClientConfig.Corner.values();
            c.hudCorner = all[(c.hudCorner.ordinal() + 1) % all.length];
            ClientConfig.save();
        });
        y += h + 4;

        h = 34;
        card(contentX, y, contentW, h);
        text("Refresh interval", contentX + PAD, y + 6, Theme.TEXT);
        text("How often the menu and HUD update, in ticks (20 = 1 second). 5 to 100.", contentX + PAD, y + 18, Theme.MUTED);
        numberField("client.refresh", contentX + contentW - PAD - 80, y + 9, 40, 5, 100, Integer.toString(c.refreshTicks), true, v -> {
            c.refreshTicks = v;
            ClientConfig.save();
        });
        button(contentX + contentW - PAD - 36, y + 9, 36, "Set", Widgets.Style.PRIMARY, true, null, () -> {
            int v = parseOr(drafts.getOrDefault("client.refresh", Integer.toString(c.refreshTicks)), c.refreshTicks);
            if (v >= 5 && v <= 100) {
                c.refreshTicks = v;
                ClientConfig.save();
                drafts.remove("client.refresh");
            }
        });
        y += h + 4;

        h = 30;
        card(contentX, y, contentW, h);
        text("Open the menu: K key (change it in Options > Controls) or /polycore", contentX + PAD, y + 11, Theme.MUTED);
        y += h + 4;
        text("Saved in config/polycore-client.json. These preferences never need operator rights.", contentX, y + 2, Theme.DIM);
        return y + 14;
    }

    private int prefRow(int y, String label, String desc, boolean value, java.util.function.Consumer<Boolean> onChange) {
        int textW = contentW - 2 * PAD - 40;
        int h = Math.max(30, 20 + wrapLines(desc, textW) * 10 + 4);
        card(contentX, y, contentW, h);
        text(label, contentX + PAD, y + 6, Theme.TEXT);
        wrapped(desc, contentX + PAD, y + 18, textW, Theme.MUTED);
        toggle(contentX + contentW - PAD - 28, y + 8, value, true, null, onChange);
        return y + h + 4;
    }

    private static String cornerName(ClientConfig.Corner c) {
        return switch (c) {
            case TOP_LEFT -> "Top left";
            case TOP_RIGHT -> "Top right";
            case BOTTOM_LEFT -> "Bottom left";
            case BOTTOM_RIGHT -> "Bottom right";
        };
    }

    // ---- About

    private int pageAbout(int y) {
        y = title("About Polycore", y);
        String body = "Polycore spreads Minecraft's work across every CPU core, starting with chunk generation, and ships "
                + "with the tools to measure the gain yourself: stage profiler, A/B benchmark and parity fingerprint.";
        int h = 20 + wrapLines(body, contentW - 2 * PAD) * 10 + 52;
        card(contentX, y, contentW, h);
        if (pass == Pass.FOREGROUND) g.blit(ICON, contentX + contentW - PAD - 40, y + PAD, 40, 40, 0f, 0f, 128, 128, 128, 128);
        text("Polycore v" + Services.PLATFORM.modVersion(), contentX + PAD, y + 6, Theme.TEXT);
        int ty = wrapped(body, contentX + PAD, y + 20, contentW - 2 * PAD - 50, Theme.MUTED);
        text("Author: Aprilox", contentX + PAD, ty + 8, Theme.TEXT);
        text("License: MIT", contentX + PAD, ty + 20, Theme.MUTED);
        text("Loader: " + Services.PLATFORM.loaderName() + " - Minecraft " + Services.PLATFORM.minecraftVersion(),
                contentX + PAD, ty + 32, Theme.MUTED);
        y += h + GAP;
        String credits = "Parallel stages follow the thread-safety analysis of Paper/Moonrise (Spottedleaf). Pre-generation "
                + "is inspired by NeoForge's /neoforge generate and fabric-chunkpregenerator.";
        h = 20 + wrapLines(credits, contentW - 2 * PAD) * 10 + 4;
        card(contentX, y, contentW, h);
        text("Credits", contentX + PAD, y + 6, Theme.TEXT);
        wrapped(credits, contentX + PAD, y + 20, contentW - 2 * PAD, Theme.MUTED);
        return y + h;
    }

    // ------------------------------------------------------------------ misc

    private static int statusColor(String status) {
        return switch (status) {
            case "ACTIVE", "READY" -> Theme.GREEN;
            case "BLOCKED" -> Theme.RED;
            case "PLANNED" -> Theme.AMBER;
            default -> Theme.MUTED;
        };
    }

    private static int levelColor(String level) {
        return switch (level) {
            case "SUCCESS" -> Theme.GREEN;
            case "WARN" -> Theme.AMBER;
            case "ERROR" -> Theme.RED;
            default -> Theme.TEXT;
        };
    }

    private static boolean settingBool(CompoundTag s, String key) {
        if (s == null) return false;
        ListTag list = s.getList("settings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.getString("key").equals(key)) return t.getBoolean("value");
        }
        return false;
    }

    private static int parseOr(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
