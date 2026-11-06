package com.polycore.neoforge.test;

import com.polycore.Polycore;
import com.polycore.config.PolyConfig;
import com.polycore.gen.StepProfiler;
import com.polycore.guard.TpsGuard;
import com.polycore.lag.LagCapture;
import com.polycore.menu.VanillaMenu;
import com.polycore.optim.Optimization;
import com.polycore.pregen.PregenManager;
import com.polycore.pregen.PregenTask;
import com.polycore.settings.Settings;
import com.polycore.util.Permissions;
import com.polycore.util.StatsSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
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
        // Far and random: the test world is kept between runs, already generated chunks would be skipped.
        int cx = java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
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

    // ------------------------------------------------------------------ lag analyzer

    @GameTest(template = "empty", batch = "d_lag", timeoutTicks = 6000) // the test server may run far above 20 TPS
    public static void lagAnalysisSeesEntitiesAndBlockEntities(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.HOPPER);
        helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        boolean started = LagCapture.start(1, null, false);
        helper.assertTrue(started, "The analysis must start");
        helper.succeedWhen(() -> {
            helper.assertFalse(LagCapture.running(), "Analysis still running");
            CompoundTag last = LagCapture.last();
            helper.assertTrue(last != null && last.getInt("ticks") > 0, "The analysis must have measured ticks");
            String types = last.getList("types", 10).toString();
            helper.assertTrue(types.contains("minecraft:pig"), "Entity hook missing, types: " + types);
            helper.assertTrue(types.contains("minecraft:hopper"), "Block entity hook missing, types: " + types);
            String sections = last.getList("sections", 10).toString();
            helper.assertTrue(sections.contains("Entities"), "Tick sections missing: " + sections);
            helper.assertTrue(com.polycore.lag.LagMonitor.tps() > 0, "TPS must be measured");
        });
    }

    // ------------------------------------------------------------------ TPS Guard

    @GameTest(template = "empty", batch = "e_guard")
    public static void guardStepsUpAndBackDown(GameTestHelper helper) {
        PolyConfig.Data c = PolyConfig.get();
        boolean enabled = c.guardEnabled, items = c.guardItemMerge, ai = c.guardMobAi, spawn = c.guardSpawnLimit, sim = c.guardSimDistance;
        int threshold = c.guardThresholdMs;
        MinecraftServer server = helper.getLevel().getServer();
        try {
            c.guardEnabled = c.guardItemMerge = c.guardMobAi = c.guardSpawnLimit = true;
            c.guardSimDistance = false;
            c.guardThresholdMs = 45;
            TpsGuard.reset(server);
            int normalCap = MobCategory.MONSTER.getMaxInstancesPerChunk();
            long t = 1_000_000L;
            TpsGuard.update(server, 10, t);
            helper.assertTrue(TpsGuard.level() == 0, "The Guard must stay idle while the server is fine");
            TpsGuard.update(server, 60, t += 1000);
            helper.assertTrue(TpsGuard.level() == 1 && TpsGuard.itemMergeActive() && !TpsGuard.mobAiActive(),
                    "Lag must turn on the least visible measure only");
            TpsGuard.update(server, 60, t += 5000);
            helper.assertTrue(TpsGuard.level() == 1, "One more measure every 10 s, not sooner");
            TpsGuard.update(server, 60, t += 5000);
            helper.assertTrue(TpsGuard.level() == 2 && TpsGuard.mobAiActive(), "Still lagging after 10 s: the next measure");
            TpsGuard.update(server, 60, t += 10000);
            helper.assertTrue(MobCategory.MONSTER.getMaxInstancesPerChunk() == Math.max(1, normalCap * c.guardSpawnPercent / 100),
                    "The spawn limit must lower the mob caps");
            c.guardMobAi = false;
            TpsGuard.refresh();
            helper.assertFalse(TpsGuard.mobAiActive(), "A measure switched off must stop at once");
            TpsGuard.update(server, 10, t += 1000);
            TpsGuard.update(server, 10, t += 30000);
            helper.assertTrue(TpsGuard.level() == 1, "After 30 s of calm, one measure less");
            TpsGuard.update(server, 10, t += 30000);
            helper.assertTrue(TpsGuard.level() == 0 && !TpsGuard.itemMergeActive(), "Back to normal");
            helper.assertTrue(MobCategory.MONSTER.getMaxInstancesPerChunk() == normalCap, "Mob caps must be restored");
        } finally {
            c.guardEnabled = enabled;
            c.guardItemMerge = items;
            c.guardMobAi = ai;
            c.guardSpawnLimit = spawn;
            c.guardSimDistance = sim;
            c.guardThresholdMs = threshold;
            TpsGuard.reset(server);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "e_guard")
    public static void guardNeverSlowsProtectedMobs(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        Pig named = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        named.setCustomName(Component.literal("Bob"));
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
        helper.assertTrue(pig instanceof com.polycore.guard.GuardedMob, "The mob AI mixin must be applied");
        // No player on the game test server: every mob counts as far.
        helper.assertTrue(TpsGuard.aiInterval(pig) > 1, "A far pig must be slowed");
        helper.assertTrue(TpsGuard.aiInterval(named) == 1, "Named mobs are never slowed");
        helper.assertTrue(TpsGuard.aiInterval(villager) == 1, "Villagers are never slowed");
        helper.succeed();
    }
}
