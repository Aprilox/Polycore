# Polycore architecture

Polycore is written so that most of the code does not depend on a mod loader. Supporting Fabric or Forge (and other
Minecraft versions) means adding a thin loader layer, not rewriting the mod.

## Layers

The project has one Gradle module per side: `common/` holds every package below except the loader glue
(compiled alone against vanilla Minecraft, so no loader API can leak in), `neoforge/` holds `com.polycore.neoforge`,
`fabric/` holds `com.polycore.fabric` and `forge/` holds `com.polycore.forge`; each builds `common`'s sources into its
own jar.

```
com.polycore
├── Polycore.java            Loader-independent entry point (init, server ticks, commands)
├── platform/                Platform interface + ServiceLoader lookup (the only loader abstraction)
├── config/                  config/polycore.json, plain Gson (works on every loader)
├── settings/                Editable settings exposed to the menu, chest menu and /polycore set
├── optim/                   Optimization registry (status, toggles)
├── gen/                     Worldgen pool, parallel stages, profiler, mixin logic
├── lag/                     Lag analyzer: tick breakdown, spikes, detailed analysis, alerts
├── guard/                   TPS Guard: measures turned on one at a time while the server lags
├── pregen/                  Pre-generation, benchmark, parity hash, reports
├── network/                 Payload records + handling (vanilla CustomPacketPayload / StreamCodec)
├── command/                 Brigadier command tree (vanilla)
├── menu/                    Chest menu for players without the client mod (vanilla)
├── client/                  Client logic, menu screen, toasts, HUD (vanilla client classes)
├── mixin/                   Mixins (Mojang mappings, MixinExtras)
├── test/                    Game test bodies (PolyTests), registered by each loader
├── selftest/                Client self-test, idle unless -Dpolycore.selftest.client=true
└── neoforge/                NeoForge-only glue  <-- the only package that imports net.neoforged.*
    ├── PolycoreNeoForge         @Mod entry, forwards events to Polycore
    ├── NeoForgePlatform         Platform implementation
    ├── NeoForgeNetwork          Payload registration
    └── client/                  Key mapping, HUD layer, client command, client ticks
└── fabric/                  Fabric-only glue  <-- the only package that imports net.fabricmc.*
    ├── PolycoreFabric           ModInitializer, forwards Fabric API events to Polycore
    ├── FabricPlatform           Platform implementation
    ├── FabricNetwork            Payload types and server receivers
    └── client/                  Key binding, HUD callback, client command, client receivers
└── forge/                   Forge-only glue  <-- the only package that imports net.minecraftforge.*
    ├── PolycoreForge            @Mod entry, forwards Forge events to Polycore
    ├── ForgePlatform            Platform implementation
    ├── ForgeNetwork             One optional payload channel (vanilla peers may join)
    └── client/                  Key mapping, HUD overlay, client command, client ticks
```

Rule: **nothing outside `com.polycore.neoforge` imports `net.neoforged`**, and the same for `net.fabricmc` /
`com.polycore.fabric` and `net.minecraftforge` / `com.polycore.forge`. Check with:

```
grep -rl "net.neoforged" common neoforge fabric forge --include=*.java | grep -v /com/polycore/neoforge/
grep -rl "net.fabricmc" common neoforge fabric forge --include=*.java | grep -v /com/polycore/fabric/
grep -rl "net.minecraftforge" common neoforge fabric forge --include=*.java | grep -v /com/polycore/forge/
```

### Names at run time (Fabric)

A production Fabric game runs with intermediary names (`class_1308.method_6023`), not Mojang's. The mixins are
remapped by Loom in the jar; `util/Conflicts`, which compares names by itself, reads `polycore-spots.json`: the task
`:fabric:generatePolycoreSpots` writes it from Loom's mappings for every class and method listed in the `Spot` table
(an inherited method takes the names of the class declaring it). Other mods' mixin targets go through their refmap.

### Forge specifics

Forge runs on Mojang names since 1.20.6, so neither a refmap nor a name table is needed. ForgeGradle 7 has no jarJar:
`forge/build.gradle` nests MixinExtras (not shipped by Forge 52) under `META-INF/jarjar/` with its `metadata.json`.
Forge's dev launcher turns each classpath folder into a module, so classes and resources are built into one folder.

## Adding a loader

1. Add the loader project next to `common/`, `neoforge/`, `fabric/` and `forge/` and include `common`'s sources.
2. In the new loader project, write:
   - an entry point that calls `Polycore.init()`, forwards server start/tick/stop events and registers the commands
     with `Polycore.registerCommands(...)`;
   - a `Platform` implementation (mod list, paths, sending payloads, channel checks) declared in
     `META-INF/services/com.polycore.platform.Platform`;
   - payload registration that forwards to the `PolyNetwork.on...` handlers;
   - client glue: key mapping, HUD render callback (`HudOverlay.render`), client ticks
     (`PolycoreClient.onClientTick`), client command (`PolycoreClient.registerClientCommands`).
3. The mixins, config, menu and tools are shared as-is.

## Other Minecraft versions

The generation hooks target 1.21.1 internals (`ChunkStep`, `NoiseBasedChunkGenerator`). Each Minecraft version gets
its own branch (`mc/1.21.1`, `mc/1.21.4`, ...); the mixin targets are re-checked against that version's sources and
the vanilla parity hash (`/polycore hash`) must match before a release.

## Continuous integration and releases

- `.github/workflows/build.yml` builds every loader (JDK 21, `./gradlew build`) on each push and pull request and
  keeps the jars as a workflow artifact.
- `.github/workflows/release.yml` runs on a tag `v<mod_version>` (`main`) or `v<mod_version>+<minecraft>` (an
  `mc/<version>` branch). It refuses a tag that does not match `mod_version` in `gradle.properties`, builds, and
  attaches the loader jars to a GitHub Release whose notes are the matching `## <version>` section of `CHANGELOG.md`.
  It only uses the built-in GitHub token; CurseForge and Modrinth uploads are done separately, never from a workflow.

## Threading rules

- Tickets, chunk access and menus: server thread only.
- Worldgen pool: noise, biomes, surface and carvers (the stages Paper/Moonrise also run in parallel).
- FEATURES and LIGHT stay on vanilla's serial queues (they write to neighbouring chunks).
- The pool never has more threads than the machine's logical cores.
