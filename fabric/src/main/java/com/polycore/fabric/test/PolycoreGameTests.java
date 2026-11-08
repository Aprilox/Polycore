package com.polycore.fabric.test;

import com.polycore.test.PolyTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Registers the game tests of {@link PolyTests} with Fabric (entrypoint "fabric-gametest" of fabric.mod.json, structure
 * "polycore:empty"). One test environment per former batch (data/polycore/test_environment) keeps them grouped.
 * The test server ticks as fast as it can (thousands of ticks per second): long tests get very large budgets.
 */
public final class PolycoreGameTests {
    // Fabric instantiates the entrypoint class and calls instance methods: keep the implicit public constructor.

    @GameTest(structure = "polycore:empty", environment = "polycore:a_settings", maxTicks = 100)
    public void settingsRejectOutOfRange(GameTestHelper helper) {
        PolyTests.settingsRejectOutOfRange(helper);
    }

    @GameTest(structure = "polycore:empty", environment = "polycore:a_settings", maxTicks = 100)
    public void settingsApplyAndCapThreads(GameTestHelper helper) {
        PolyTests.settingsApplyAndCapThreads(helper);
    }

    @GameTest(structure = "polycore:empty", environment = "polycore:a_settings", maxTicks = 100)
    public void optimizationTogglePersists(GameTestHelper helper) {
        PolyTests.optimizationTogglePersists(helper);
    }

    @GameTest(structure = "polycore:empty", environment = "polycore:b_players", maxTicks = 100)
    public void snapshotForRegularPlayer(GameTestHelper helper) {
        PolyTests.snapshotForRegularPlayer(helper);
    }

    @GameTest(structure = "polycore:empty", environment = "polycore:b_players", maxTicks = 100)
    public void vanillaMenuIsReadOnly(GameTestHelper helper) {
        PolyTests.vanillaMenuIsReadOnly(helper);
    }

    @GameTest(structure = "polycore:empty", environment = "polycore:b_players", maxTicks = 100)
    public void hudStateIsSmall(GameTestHelper helper) {
        PolyTests.hudStateIsSmall(helper);
    }

    @GameTest(structure = "polycore:empty", environment = "polycore:c_pregen", maxTicks = 200000)
    public void pregenGeneratesAndUsesParallelStages(GameTestHelper helper) {
        PolyTests.pregenGeneratesAndUsesParallelStages(helper);
    }

    @GameTest(structure = "polycore:empty", environment = "polycore:d_lag", maxTicks = 200000)
    public void lagAnalysisSeesEntitiesAndBlockEntities(GameTestHelper helper) {
        PolyTests.lagAnalysisSeesEntitiesAndBlockEntities(helper);
    }

    @GameTest(structure = "polycore:empty", environment = "polycore:e_guard", maxTicks = 100)
    public void guardStepsUpAndBackDown(GameTestHelper helper) {
        PolyTests.guardStepsUpAndBackDown(helper);
    }

    @GameTest(structure = "polycore:empty", environment = "polycore:e_guard", maxTicks = 100)
    public void guardNeverSlowsProtectedMobs(GameTestHelper helper) {
        PolyTests.guardNeverSlowsProtectedMobs(helper);
    }

    @GameTest(structure = "polycore:empty", environment = "polycore:f_aquifer", maxTicks = 400000)
    public void fastAquifersMatchVanilla(GameTestHelper helper) {
        PolyTests.fastAquifersMatchVanilla(helper);
    }

    @GameTest(structure = "polycore:empty", environment = "polycore:g_conflicts", maxTicks = 100)
    public void conflictsSeeOtherModsOnly(GameTestHelper helper) {
        PolyTests.conflictsSeeOtherModsOnly(helper);
    }
}
