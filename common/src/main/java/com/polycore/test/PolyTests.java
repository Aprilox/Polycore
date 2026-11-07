package com.polycore.test;

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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;

/**
 * Bodies of the automated server-side tests (each loader registers them as game tests), run with {@code gradlew runGameTestServer} (or tools/test/gametests.bat).
 * Only registered when game tests are enabled for the "polycore" namespace, so they never run in normal play.
 *
 * <p>Batches run one after another; tests inside a batch run at the same time.</p>
 */
public final class PolyTests {
    private PolyTests() {}

    // ------------------------------------------------------------------ settings

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
        helper.succeed();
    }

    // ------------------------------------------------------------------ permissions, snapshot, vanilla menu

    /**
     * Named rather than anonymous: javac copies the super constructor's parameter names into an anonymous class, and
     * the recompiled Forge 1.20.1 jar of the CI names them all "o" (duplicate variable).
     */
    private static final class MockPlayer extends ServerPlayer {
        MockPlayer(MinecraftServer server, ServerLevel level, com.mojang.authlib.GameProfile profile) {
            super(server, level, profile);
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return true;
        }
    }

    /**
     * GameTestHelper.makeMockServerPlayerInLevel of 1.20.2+: in 1.20.1 its connection has no channel, which Forge
     * reads when the player joins.
     */
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        ServerPlayer player = new MockPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"));
        net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        // Leaves the server after this tick: a player left in the level would count as near for the later tests.
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 1,
                () -> level.getServer().getPlayerList().remove(player)));
        return player;
    }

    public static void snapshotForRegularPlayer(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        helper.assertFalse(Permissions.canEdit(player), "A regular player must not be able to edit");
        CompoundTag s = StatsSnapshot.build(player);
        helper.assertTrue(s.getBoolean("canView") == PolyConfig.get().allowNonOpView, "canView must follow the config");
        if (s.getBoolean("canView")) {
            helper.assertTrue(s.contains("opts") && s.contains("settings") && s.contains("history"), "Snapshot must contain the menu data");
            helper.assertTrue(s.getInt("poolMax") == PolyConfig.cores(), "Pool maximum must be the logical cores");
        }
        helper.succeed();
    }

    public static void vanillaMenuIsReadOnly(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
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

    public static void hudStateIsSmall(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        CompoundTag hud = StatsSnapshot.buildHud(player);
        CompoundTag full = StatsSnapshot.build(player);
        int hudBytes = wireBytes(hud), fullBytes = wireBytes(full);
        Polycore.LOGGER.info("[Polycore] State sizes sent over the network: HUD {} bytes, menu {} bytes", hudBytes, fullBytes);
        helper.assertTrue(hud.getBoolean("hudOnly"), "The HUD state must be marked so it never replaces the menu's data");
        if (hud.getBoolean("canView")) {
            helper.assertTrue(hud.contains("tps") && hud.contains("cps1") && hud.contains("mspt"), "The HUD state must hold the HUD values");
            helper.assertTrue(hudBytes * 5 < fullBytes, "The HUD state must be much smaller than the menu's");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ generation end to end

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
            helper.assertTrue(com.polycore.pregen.ParityHash.terrain(level.dimension(), net.minecraft.world.level.ChunkPos.asLong(cx, cx)) != null,
                    "The terrain fingerprint of a new chunk must be recorded after its carvers stage");
            if (Optimization.PARALLEL_STAGES.blockReason() == null) {
                helper.assertTrue(Optimization.PARALLEL_STAGES.isHookSeen(), "The parallel-stage hook must have run");
            }
        });
    }

    // ------------------------------------------------------------------ lag analyzer

    public static void lagAnalysisSeesEntitiesAndBlockEntities(GameTestHelper helper) {
        // The 1.20.1 test world keeps the mobs of the pre-generated chunks: they would push the hopper out of the top 10 types.
        ServerLevel level = helper.getLevel();
        GameRules.BooleanValue spawning = level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
        boolean spawned = spawning.get();
        spawning.set(false, level.getServer());
        java.util.List<net.minecraft.world.entity.Entity> others = new java.util.ArrayList<>();
        level.getAllEntities().forEach(e -> { if (!(e instanceof ServerPlayer)) others.add(e); });
        others.forEach(net.minecraft.world.entity.Entity::discard);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.HOPPER);
        helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        LagCapture.reset(); // a slow world generation tick may have started a 30 s automatic analysis
        boolean started = LagCapture.start(1, null, false);
        helper.assertTrue(started, "The analysis must start");
        helper.succeedWhen(() -> {
            helper.assertFalse(LagCapture.running(), "Analysis still running");
            CompoundTag last = LagCapture.last();
            helper.assertTrue(last != null && last.getInt("ticks") > 0, "The analysis must have measured ticks");
            String types = last.getList("types", 10).toString();
            helper.assertTrue(types.contains("minecraft:pig"), "Entity hook missing, types: " + types);
            helper.assertTrue(types.contains("minecraft:hopper"), "Block entity hook missing, types: " + types);
            helper.assertTrue(sectionMs(last, "Entities") > 0, "Entity tick time not measured (ServerLevelMixin)");
            helper.assertTrue(sectionMs(last, "Block entities") > 0, "Block entity tick time not measured (ServerLevelMixin)");
            helper.assertTrue(sectionMs(last, "Chunk loading & saving") > 0, "Chunk tick time not measured (ServerLevelMixin)");
            helper.assertTrue(com.polycore.lag.LagMonitor.tps() > 0, "TPS must be measured");
            spawning.set(spawned, level.getServer());
        });
    }

    // ------------------------------------------------------------------ TPS Guard

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

    public static void guardNeverSlowsProtectedMobs(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        Pig named = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        named.setCustomName(Component.literal("Bob"));
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
        helper.assertTrue(pig instanceof com.polycore.guard.GuardedMob, "The mob AI mixin must be applied");
        // No player on the game test server: every mob counts as far.
        helper.assertTrue(TpsGuard.aiInterval(pig) > 1, "A far pig must be slowed (players " + pig.level().players().size() + ", hurt " + pig.hurtTime + ", target " + pig.getTarget() + ", passenger " + pig.isPassenger() + ", leashed " + pig.isLeashed() + ")");
        helper.assertTrue(TpsGuard.aiInterval(named) == 1, "Named mobs are never slowed");
        helper.assertTrue(TpsGuard.aiInterval(villager) == 1, "Villagers are never slowed");
        helper.succeed();
    }

    // ------------------------------------------------------------------ fast aquifers

    /** Overworld terrain with and without the fast aquifers must be identical, blocks and scheduled fluids. */
    public static void fastAquifersMatchVanilla(GameTestHelper helper) {
        java.util.concurrent.atomic.AtomicReference<String> result = new java.util.concurrent.atomic.AtomicReference<>();
        boolean before = PolyConfig.get().fastAquiferEnabled;
        // Off the server thread: the generator asks the server thread for structure data.
        Thread worker = new Thread(() -> {
            try {
                NoiseHarness harness = new NoiseHarness(helper.getLevel(), 8675309L);
                int base = java.util.concurrent.ThreadLocalRandom.current().nextInt(-200_000, 200_000);
                long vanillaNs = 0, fastNs = 0, hashNs = 0;
                int differ = 0;
                for (int n = 0; n < 24; n++) {
                    int cx = base + n * 3, cz = base - n * 7;
                    PolyConfig.get().fastAquiferEnabled = false;
                    long t0 = noiseNanos();
                    long vanilla = NoiseHarness.hash(harness.fill(cx, cz));
                    long t1 = noiseNanos();
                    PolyConfig.get().fastAquiferEnabled = true;
                    net.minecraft.world.level.chunk.ProtoChunk fastChunk = harness.fill(cx, cz);
                    long fast = NoiseHarness.hash(fastChunk);
                    long t2 = noiseNanos();
                    long h0 = System.nanoTime();
                    com.polycore.pregen.ParityHash.hashChunk(fastChunk);
                    hashNs += System.nanoTime() - h0;
                    vanillaNs += t1 - t0;
                    fastNs += t2 - t1;
                    if (vanilla != fast) differ++;
                }
                Polycore.LOGGER.info(String.format(java.util.Locale.ROOT,
                        "[Polycore] Fast aquifers: noise stage %.2f ms -> %.2f ms per chunk (24 chunks, %d differ); terrain fingerprint %.2f ms per chunk",
                        vanillaNs / 24e6, fastNs / 24e6, differ, hashNs / 24e6));
                result.set(differ == 0 ? "ok" : differ + " of 24 chunks differ from vanilla");
            } catch (Throwable t) {
                result.set("error: " + t);
            } finally {
                PolyConfig.get().fastAquiferEnabled = before;
            }
        }, "polycore-aquifer-parity");
        worker.start();
        helper.succeedWhen(() -> {
            String r = result.get();
            helper.assertTrue(r != null, "still generating");
            helper.assertTrue(r.equals("ok"), r);
            helper.assertTrue(Optimization.FAST_AQUIFER.isHookSeen(), "The fast aquifer mixin must be applied");
        });
    }

    private static double sectionMs(CompoundTag analysis, String name) {
        net.minecraft.nbt.ListTag list = analysis.getList("sections", 10);
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).getString("name").equals(name)) return list.getCompound(i).getDouble("ms");
        return -1;
    }

    private static int wireBytes(CompoundTag tag) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.write(tag, new java.io.DataOutputStream(out));
            return out.size();
        } catch (java.io.IOException e) {
            return -1;
        }
    }

    private static long noiseNanos() {
        StepProfiler.Stat s = StepProfiler.raw().get("calc:noise");
        return s == null ? 0 : s.totalNanos.sum();
    }

    // ------------------------------------------------------------------ conflicts with other mods

    /** Polycore's own mixins never count; the self-test mixin (test runs only) plays another mod. */
    public static void conflictsSeeOtherModsOnly(GameTestHelper helper) {
        helper.assertTrue(com.polycore.util.Conflicts.targetName("computeSubstance").equals("computeSubstance"), "plain name");
        helper.assertTrue(com.polycore.util.Conflicts.targetName("computeSubstance(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;D)"
                + "Lnet/minecraft/world/level/block/state/BlockState;").equals("computeSubstance"), "name with descriptor");
        helper.assertTrue(com.polycore.util.Conflicts.targetName("Lnet/minecraft/world/entity/Mob;serverAiStep()V").equals("serverAiStep"), "owner-qualified");
        helper.assertTrue(com.polycore.util.Conflicts.modName("clumps.mixins.json").equals("clumps"), "config name");
        helper.assertTrue(com.polycore.util.Conflicts.modName("mixins.other.json").equals("other"), "config name, prefix form");
        // Fabric and Forge 1.20.1 jars bundle the Mojang -> intermediary/SRG table of a production game (polycore-spots.json).
        String mob = com.polycore.util.Conflicts.translated("net/minecraft/world/entity/Mob", "serverAiStep");
        helper.assertTrue(mob != null && mob.matches("net/minecraft/(class_\\d+\\.method_\\d+|world/entity/Mob\\.m_\\d+_)"),
                "The jar must bundle polycore-spots.json with intermediary or SRG names of Mob.serverAiStep, got " + mob);

        // An injection that found nothing leaves its handler uncalled (util/Hooks); every Polycore hook must be in place here.
        org.objectweb.asm.tree.ClassNode t = new org.objectweb.asm.tree.ClassNode();
        t.name = "T";
        org.objectweb.asm.tree.MethodNode m = new org.objectweb.asm.tree.MethodNode(0, "a", "()V", null, null);
        m.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, "T", "wrapOperation$zza000$polycore$onX", "()V"));
        t.methods.add(m);
        helper.assertTrue(com.polycore.util.Hooks.uncalled(t, java.util.List.of("onX", "onY")).equals(java.util.List.of("onY")),
                "Only the uncalled handler is reported");
        com.polycore.util.Conflicts.reasons();
        helper.assertTrue(com.polycore.util.Hooks.missing().isEmpty(), "Every Polycore hook must apply: " + com.polycore.util.Hooks.missing());

        java.util.Map<com.polycore.util.Conflicts.Feature, String> found = com.polycore.util.Conflicts.reasons();
        boolean selfTest = Boolean.getBoolean("polycore.selftest");
        for (com.polycore.util.Conflicts.Feature f : com.polycore.util.Conflicts.Feature.values()) {
            if (selfTest && f == com.polycore.util.Conflicts.Feature.SIM_DISTANCE) continue;
            helper.assertFalse(found.containsKey(f), "Polycore's own mixins must not count as a conflict: " + f + " " + found.get(f));
        }
        if (selfTest) {
            String why = found.get(com.polycore.util.Conflicts.Feature.SIM_DISTANCE);
            helper.assertTrue(why != null && why.contains("polycore-selftest") && why.contains("setSimulationDistance")
                            && why.contains("PlayerListSelfTestMixin"),
                    "Another mod changing PlayerList.setSimulationDistance must be found, got: " + why + "; Mixin reports: "
                            + com.polycore.util.Conflicts.applied("net/minecraft/server/players/PlayerList")
                            + "; ServerLevel: " + com.polycore.util.Conflicts.applied("net/minecraft/server/level/ServerLevel"));
            helper.assertTrue(TpsGuard.Measure.SIM_DISTANCE.blockReason() != null, "The simulation distance measure must be off");
            helper.assertTrue(TpsGuard.Measure.ITEM_MERGE.blockReason() == null,
                    "A '*' mixin whose injection point is not in the item merging code must not block it: "
                            + TpsGuard.Measure.ITEM_MERGE.blockReason());
            helper.assertTrue(com.polycore.util.Conflicts.applied("net/minecraft/world/entity/item/ItemEntity").contains("ItemEntitySelfTestMixin"),
                    "The '*' self-test mixin must be applied for this check to mean something");
        }
        helper.succeed();
    }
}
