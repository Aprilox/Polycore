package com.polycore.neoforge.test;

import com.polycore.Polycore;
import com.polycore.test.PolyTests;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the game tests of {@link PolyTests} with NeoForge (1.21.5+ system: test functions in a registry,
 * one test instance each on structure "polycore:empty", one environment per former batch so they stay grouped).
 */
public final class PolycoreGameTests {
    private PolycoreGameTests() {}

    private record Test(String name, String batch, int maxTicks, Consumer<GameTestHelper> run) {}

    // The test server ticks as fast as it can (thousands of ticks per second): long tests get very large budgets.
    private static final List<Test> TESTS = List.of(
            new Test("settingsrejectoutofrange", "a_settings", 100, PolyTests::settingsRejectOutOfRange),
            new Test("settingsapplyandcapthreads", "a_settings", 100, PolyTests::settingsApplyAndCapThreads),
            new Test("optimizationtogglepersists", "a_settings", 100, PolyTests::optimizationTogglePersists),
            new Test("snapshotforregularplayer", "b_players", 100, PolyTests::snapshotForRegularPlayer),
            new Test("vanillamenuisreadonly", "b_players", 100, PolyTests::vanillaMenuIsReadOnly),
            new Test("hudstateissmall", "b_players", 100, PolyTests::hudStateIsSmall),
            new Test("pregengeneratesandusesparallelstages", "c_pregen", 2000000, PolyTests::pregenGeneratesAndUsesParallelStages),
            new Test("laganalysisseesentitiesandblockentities", "d_lag", 200000, PolyTests::lagAnalysisSeesEntitiesAndBlockEntities),
            new Test("guardstepsupandbackdown", "e_guard", 100, PolyTests::guardStepsUpAndBackDown),
            new Test("guardneverslowsprotectedmobs", "e_guard", 100, PolyTests::guardNeverSlowsProtectedMobs),
            new Test("fastaquifersmatchvanilla", "f_aquifer", 400000, PolyTests::fastAquifersMatchVanilla),
            new Test("conflictsseeothermodsonly", "g_conflicts", 100, PolyTests::conflictsSeeOtherModsOnly));

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Polycore.MODID);

    public static void register(IEventBus modBus) {
        for (Test t : TESTS) FUNCTIONS.register(t.name(), t::run);
        FUNCTIONS.register(modBus);
        modBus.addListener(PolycoreGameTests::onRegisterTests);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> envs = new HashMap<>();
        for (Test t : TESTS) {
            Holder<TestEnvironmentDefinition<?>> env = envs.computeIfAbsent(t.batch(), b -> event.registerEnvironment(id(b)));
            ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, id(t.name()));
            event.registerTest(id(t.name()), new FunctionGameTestInstance(fn, new TestData<>(env, id("empty"), t.maxTicks(), 0, true)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Polycore.MODID, path);
    }
}
