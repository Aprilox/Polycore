package com.polycore.forge.test;

import com.polycore.Polycore;
import com.polycore.test.PolyTests;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Game tests of {@link PolyTests} for Forge (1.21.5+ system). Forge 61 does not wire its @GameTest annotation, so the
 * functions go into the TEST_FUNCTION registry here and their test instances (structure "polycore:empty", one
 * environment per former batch, tick budgets) are the JSON files of forge/src/gametest/resources, kept out of the jar.
 */
public final class PolycoreGameTests {
    private PolycoreGameTests() {}

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Polycore.MODID);

    public static void register(IEventBus modBus) {
        FUNCTIONS.register("settingsrejectoutofrange", () -> PolyTests::settingsRejectOutOfRange);
        FUNCTIONS.register("settingsapplyandcapthreads", () -> PolyTests::settingsApplyAndCapThreads);
        FUNCTIONS.register("optimizationtogglepersists", () -> PolyTests::optimizationTogglePersists);
        FUNCTIONS.register("snapshotforregularplayer", () -> PolyTests::snapshotForRegularPlayer);
        FUNCTIONS.register("vanillamenuisreadonly", () -> PolyTests::vanillaMenuIsReadOnly);
        FUNCTIONS.register("hudstateissmall", () -> PolyTests::hudStateIsSmall);
        FUNCTIONS.register("pregengeneratesandusesparallelstages", () -> PolyTests::pregenGeneratesAndUsesParallelStages);
        FUNCTIONS.register("laganalysisseesentitiesandblockentities", () -> PolyTests::lagAnalysisSeesEntitiesAndBlockEntities);
        FUNCTIONS.register("guardstepsupandbackdown", () -> PolyTests::guardStepsUpAndBackDown);
        FUNCTIONS.register("guardneverslowsprotectedmobs", () -> PolyTests::guardNeverSlowsProtectedMobs);
        FUNCTIONS.register("fastaquifersmatchvanilla", () -> PolyTests::fastAquifersMatchVanilla);
        FUNCTIONS.register("conflictsseeothermodsonly", () -> PolyTests::conflictsSeeOtherModsOnly);
        FUNCTIONS.register(modBus);
    }
}
