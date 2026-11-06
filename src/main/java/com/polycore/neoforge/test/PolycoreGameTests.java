package com.polycore.neoforge.test;

import com.polycore.Polycore;
import com.polycore.config.PolyConfig;
import com.polycore.gen.StepProfiler;
import com.polycore.menu.VanillaMenu;
import com.polycore.optim.Optimization;
import com.polycore.pregen.PregenManager;
import com.polycore.pregen.PregenTask;
import com.polycore.settings.Settings;
import com.polycore.util.Permissions;
import com.polycore.util.StatsSnapshot;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Automated server-side tests, run with {@code gradlew runGameTestServer} (or tools/test/gametests.bat).
 * Only registered when game tests are enabled for the "polycore" namespace, so they never run in normal play.
 *
 * <p>Batches run one after another; tests inside a batch run at the same time.</p>
 */
@GameTestHolder(Polycore.MODID)
@PrefixGameTestTemplate(false)
public final class PolycoreGameTests {
    private PolycoreGameTests() {}

    // ------------------------------------------------------------------ settings

    @GameTest(template = "empty", batch = "a_settings")
    public static void settingsRejectOutOfRange(GameTestHelper helper) {
        int before = PolyConfig.get().worldgenPoolThreads;
        String tooMany = Settings.set(Settings.POOL_THREADS, Integer.toString(PolyConfig.cores() + 1));
        helper.assertTrue(tooMany != null, "More threads than logical cores must be refused");
        helper.assertTrue(PolyConfig.get().worldgenPoolThreads == before, "A refused value must not be stored");
        helper.assertTrue(Settings.set(Settings.POOL_THREADS, "abc") != null, "Text must be refused for a number");
        helper.assertTrue(Settings.set(Settings.PREGEN_KEEP_TPS, "maybe") != null, "Invalid boolean must be refused");
        helper.assertTrue(Settings.set("does.not.exist", "1") != null, "Unknown key must be refused");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "a_settings")
    public static void settingsApplyAndCapThreads(GameTestHelper helper) {
        int before = PolyConfig.get().worldgenPoolThreads;
        try {
            helper.assertTrue(Settings.set(Settings.POOL_THREADS, "1") == null, "1 thread must be accepted");
            helper.assertTrue(PolyConfig.poolThreads() == 1, "Pool size must follow the setting");
            PolyConfig.get().worldgenPoolThreads = 10_000; // e.g. a hand-edited file
            helper.assertTrue(PolyConfig.poolThreads() <= PolyConfig.cores(), "Pool size must never exceed the cores");
        } finally {
            PolyConfig.get().worldgenPoolThreads = before;
            PolyConfig.save();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "a_settings")
    public static void optimizationTogglePersists(GameTestHelper helper) {
        boolean before = Optimization.PARALLEL_STAGES.isEnabled();
        try {
            helper.assertTrue(Optimization.PARALLEL_STAGES.setEnabled(!before) == null, "Toggle must succeed");
            helper.assertTrue(Optimization.PARALLEL_STAGES.isEnabled() == !before, "Toggle must change the state");
            PolyConfig.load();
            helper.assertTrue(Optimization.PARALLEL_STAGES.isEnabled() == !before, "Toggle must survive a config reload");
        } finally {
            Optimization.PARALLEL_STAGES.setEnabled(before);
        }
        helper.assertTrue(Optimization.SIMD_NOISE.setEnabled(true) != null, "Planned optimizations cannot be toggled");
        helper.succeed();
    }

    // ------------------------------------------------------------------ permissions, snapshot, vanilla menu

    @GameTest(template = "empty", batch = "b_players")
    @SuppressWarnings("removal") // still the simplest way to get a mock player in 1.21.1
    public static void snapshotForRegularPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertFalse(Permissions.canEdit(player), "A regular player must not be able to edit");
        CompoundTag s = StatsSnapshot.build(player);
        helper.assertTrue(s.getBoolean("canView") == PolyConfig.get().allowNonOpView, "canView must follow the config");
        if (s.getBoolean("canView")) {
            helper.assertTrue(s.contains("opts") && s.contains("settings") && s.contains("history"), "Snapshot must contain the menu data");
            helper.assertTrue(s.getInt("poolMax") == PolyConfig.cores(), "Pool maximum must be the logical cores");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "b_players")
    @SuppressWarnings("removal")
    public static void vanillaMenuIsReadOnly(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        if (!PolyConfig.get().allowNonOpView) {
            helper.succeed();
            return;
        }
        VanillaMenu.open(player);
        helper.assertTrue(player.containerMenu instanceof VanillaMenu, "The chest menu must open for a vanilla client");
        boolean before = Optimization.WORLDGEN_POOL.isEnabled();
        // Slot 19 = worldgen pool toggle; a regular player must not change it.
        player.containerMenu.clicked(19, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(Optimization.WORLDGEN_POOL.isEnabled() == before, "A regular player must not toggle optimizations");
        helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "Items must never be taken out of the menu");
        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------ generation end to end

    @GameTest(template = "empty", batch = "c_pregen", timeoutTicks = 2400)
    public static void pregenGeneratesAndUsesParallelStages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long before = StepProfiler.totalChunks();
        int cx = 40_000 + (int) (System.nanoTime() % 1000);
        boolean started = PregenManager.startPregen(level, cx, cx, 3, 64, false, null,
                level.getServer().createCommandSourceStack());
        helper.assertTrue(started, "Pre-generation must start");
        helper.succeedWhen(() -> {
            PregenTask t = PregenManager.current();
            helper.assertTrue(t != null && t.state() == PregenTask.State.DONE, "Pre-generation still running");
            helper.assertTrue(t.ok() == 49, "Expected 49 generated chunks, got " + t.ok() + " (" + t.errors() + " errors)");
            helper.assertTrue(StepProfiler.totalChunks() > before, "The profiler must have seen new chunks");
            if (Optimization.PARALLEL_STAGES.blockReason() == null) {
                helper.assertTrue(Optimization.PARALLEL_STAGES.isHookSeen(), "The parallel-stage hook must have run");
            }
        });
    }
}
