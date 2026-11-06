# Polycore architecture

Polycore is written so that most of the code does not depend on a mod loader. Supporting Fabric or Forge (and other
Minecraft versions) means adding a thin loader layer, not rewriting the mod.

## Layers

```
com.polycore
├── Polycore.java            Loader-independent entry point (init, server ticks, commands)
├── platform/                Platform interface + ServiceLoader lookup (the only loader abstraction)
├── config/                  config/polycore.json, plain Gson (works on every loader)
├── settings/                Editable settings exposed to the menu, chest menu and /polycore set
├── optim/                   Optimization registry (status, toggles)
├── gen/                     Worldgen pool, parallel stages, profiler, mixin logic
├── lag/                     Lag analyzer: tick breakdown, spikes, detailed analysis, alerts
├── pregen/                  Pre-generation, benchmark, parity hash, reports
├── network/                 Payload records + handling (vanilla CustomPacketPayload / StreamCodec)
├── command/                 Brigadier command tree (vanilla)
├── menu/                    Chest menu for players without the client mod (vanilla)
├── client/                  Client logic, menu screen, toasts, HUD (vanilla client classes)
├── mixin/                   Mixins (Mojang mappings, MixinExtras)
└── neoforge/                NeoForge-only glue  <-- the only package that imports net.neoforged.*
    ├── PolycoreNeoForge         @Mod entry, forwards events to Polycore
    ├── NeoForgePlatform         Platform implementation
    ├── NeoForgeNetwork          Payload registration
    └── client/                  Key mapping, HUD layer, client command, client ticks
```

Rule: **nothing outside `com.polycore.neoforge` imports `net.neoforged`**. Check with:

```
grep -rl "net.neoforged" src/main/java --include=*.java | grep -v /neoforge/
```

## Adding a loader (Fabric, Forge)

1. Move to a multi-project layout (e.g. the MultiLoader template): `common/` (everything except `neoforge/`),
   `neoforge/`, `fabric/`, `forge/`.
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

## Threading rules

- Tickets, chunk access and menus: server thread only.
- Worldgen pool: noise, biomes, surface and carvers (the stages Paper/Moonrise also run in parallel).
- FEATURES and LIGHT stay on vanilla's serial queues (they write to neighbouring chunks).
- The pool never has more threads than the machine's logical cores.
