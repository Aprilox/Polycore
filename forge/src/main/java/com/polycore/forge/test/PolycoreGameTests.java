package com.polycore.forge.test;

import com.polycore.Polycore;
import com.polycore.test.PolyTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.ForgeConfig;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.gametest.GameTestHolder;

/** Game tests of {@link PolyTests} for Forge (namespace "polycore", template "polycore:empty"). */
@GameTestHolder(Polycore.MODID)
public final class PolycoreGameTests {
    private PolycoreGameTests() {}

    // shortcut: Forge 50's game test server never loads the Forge server config, so a moving mob throws; drop once Forge loads it.
    private static void loadForgeServerConfig(GameTestHelper helper) {
        try {
            ForgeConfig.SERVER.fullBoundingBoxLadders.get();
        } catch (IllegalStateException notLoaded) {
            java.nio.file.Path dir = helper.getLevel().getServer().getWorldPath(new LevelResource("serverconfig"));
            try {
                java.nio.file.Files.createDirectories(dir);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, dir);
        }
    }

    @GameTest(template = "polycore:empty", batch = "a_settings")
    public static void settingsRejectOutOfRange(GameTestHelper helper) {
        PolyTests.settingsRejectOutOfRange(helper);
    }

    @GameTest(template = "polycore:empty", batch = "a_settings")
    public static void settingsApplyAndCapThreads(GameTestHelper helper) {
        PolyTests.settingsApplyAndCapThreads(helper);
    }

    @GameTest(template = "polycore:empty", batch = "a_settings")
    public static void optimizationTogglePersists(GameTestHelper helper) {
        PolyTests.optimizationTogglePersists(helper);
    }

    @GameTest(template = "polycore:empty", batch = "b_players")
    public static void snapshotForRegularPlayer(GameTestHelper helper) {
        PolyTests.snapshotForRegularPlayer(helper);
    }

    @GameTest(template = "polycore:empty", batch = "b_players")
    public static void vanillaMenuIsReadOnly(GameTestHelper helper) {
        PolyTests.vanillaMenuIsReadOnly(helper);
    }

    @GameTest(template = "polycore:empty", batch = "b_players")
    public static void hudStateIsSmall(GameTestHelper helper) {
        PolyTests.hudStateIsSmall(helper);
    }

    @GameTest(template = "polycore:empty", batch = "c_pregen", timeoutTicks = 2400)
    public static void pregenGeneratesAndUsesParallelStages(GameTestHelper helper) {
        PolyTests.pregenGeneratesAndUsesParallelStages(helper);
    }

    @GameTest(template = "polycore:empty", batch = "d_lag", timeoutTicks = 6000) // the test server may run far above 20 TPS
    public static void lagAnalysisSeesEntitiesAndBlockEntities(GameTestHelper helper) {
        loadForgeServerConfig(helper);
        PolyTests.lagAnalysisSeesEntitiesAndBlockEntities(helper);
    }

    @GameTest(template = "polycore:empty", batch = "e_guard")
    public static void guardStepsUpAndBackDown(GameTestHelper helper) {
        PolyTests.guardStepsUpAndBackDown(helper);
    }

    @GameTest(template = "polycore:empty", batch = "e_guard")
    public static void guardNeverSlowsProtectedMobs(GameTestHelper helper) {
        PolyTests.guardNeverSlowsProtectedMobs(helper);
    }

    @GameTest(template = "polycore:empty", batch = "f_aquifer", timeoutTicks = 20000)
    public static void fastAquifersMatchVanilla(GameTestHelper helper) {
        PolyTests.fastAquifersMatchVanilla(helper);
    }

    @GameTest(template = "polycore:empty", batch = "g_conflicts")
    public static void conflictsSeeOtherModsOnly(GameTestHelper helper) {
        PolyTests.conflictsSeeOtherModsOnly(helper);
    }
}
