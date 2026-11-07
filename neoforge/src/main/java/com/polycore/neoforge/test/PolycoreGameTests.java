package com.polycore.neoforge.test;

import com.polycore.Polycore;
import com.polycore.test.PolyTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registers the game tests of {@link PolyTests} with NeoForge (namespace "polycore", template "polycore:empty"). */
@GameTestHolder(Polycore.MODID)
@PrefixGameTestTemplate(false)
public final class PolycoreGameTests {
    private PolycoreGameTests() {}

    @GameTest(template = "empty", batch = "a_settings")
    public static void settingsRejectOutOfRange(GameTestHelper helper) {
        PolyTests.settingsRejectOutOfRange(helper);
    }

    @GameTest(template = "empty", batch = "a_settings")
    public static void settingsApplyAndCapThreads(GameTestHelper helper) {
        PolyTests.settingsApplyAndCapThreads(helper);
    }

    @GameTest(template = "empty", batch = "a_settings")
    public static void optimizationTogglePersists(GameTestHelper helper) {
        PolyTests.optimizationTogglePersists(helper);
    }

    @GameTest(template = "empty", batch = "b_players")
    public static void snapshotForRegularPlayer(GameTestHelper helper) {
        PolyTests.snapshotForRegularPlayer(helper);
    }

    @GameTest(template = "empty", batch = "b_players")
    public static void vanillaMenuIsReadOnly(GameTestHelper helper) {
        PolyTests.vanillaMenuIsReadOnly(helper);
    }

    @GameTest(template = "empty", batch = "b_players")
    public static void hudStateIsSmall(GameTestHelper helper) {
        PolyTests.hudStateIsSmall(helper);
    }

    @GameTest(template = "empty", batch = "c_pregen", timeoutTicks = 2400)
    public static void pregenGeneratesAndUsesParallelStages(GameTestHelper helper) {
        PolyTests.pregenGeneratesAndUsesParallelStages(helper);
    }

    @GameTest(template = "empty", batch = "d_lag", timeoutTicks = 6000) // the test server may run far above 20 TPS
    public static void lagAnalysisSeesEntitiesAndBlockEntities(GameTestHelper helper) {
        PolyTests.lagAnalysisSeesEntitiesAndBlockEntities(helper);
    }

    @GameTest(template = "empty", batch = "e_guard")
    public static void guardStepsUpAndBackDown(GameTestHelper helper) {
        PolyTests.guardStepsUpAndBackDown(helper);
    }

    @GameTest(template = "empty", batch = "e_guard")
    public static void guardNeverSlowsProtectedMobs(GameTestHelper helper) {
        PolyTests.guardNeverSlowsProtectedMobs(helper);
    }

    @GameTest(template = "empty", batch = "f_aquifer", timeoutTicks = 20000)
    public static void fastAquifersMatchVanilla(GameTestHelper helper) {
        PolyTests.fastAquifersMatchVanilla(helper);
    }

    @GameTest(template = "empty", batch = "g_conflicts")
    public static void conflictsSeeOtherModsOnly(GameTestHelper helper) {
        PolyTests.conflictsSeeOtherModsOnly(helper);
    }
}
