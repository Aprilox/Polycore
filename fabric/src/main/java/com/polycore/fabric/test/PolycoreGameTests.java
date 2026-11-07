package com.polycore.fabric.test;

import com.polycore.test.PolyTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Registers the game tests of {@link PolyTests} with Fabric (entrypoint "fabric-gametest" of fabric.mod.json, template "polycore:empty"). */
public final class PolycoreGameTests {
    // Fabric instantiates the entrypoint class: keep the implicit public constructor.

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

    @GameTest(template = "polycore:empty", batch = "c_pregen", timeoutTicks = 20000) // the 1.20.1 test server ticks as fast as it can
    public static void pregenGeneratesAndUsesParallelStages(GameTestHelper helper) {
        PolyTests.pregenGeneratesAndUsesParallelStages(helper);
    }

    @GameTest(template = "polycore:empty", batch = "d_lag", timeoutTicks = 6000) // the test server may run far above 20 TPS
    public static void lagAnalysisSeesEntitiesAndBlockEntities(GameTestHelper helper) {
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
