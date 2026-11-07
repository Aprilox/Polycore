![Polycore](branding/banner/polycore-banner.png)

# Polycore

**Multicore optimization for Minecraft.**

Minecraft leaves most CPU cores idle. Polycore spreads the game's work across all of them, without changing the game:
same seed, same world, same gameplay. It also ships with the tools to measure the gain on your own machine.

Chunk generation is optimized first. Other parts of the game follow, as long as they can run in parallel safely.

| | |
|---|---|
| **Minecraft** | 1.21.1 (NeoForge and Fabric also 1.21) |
| **Loader** | NeoForge 21.1.235 or newer (21.0.167+ on 1.21), Fabric Loader 0.16+ with Fabric API, or Forge 52.1.21 or newer |
| **Java** | 21 |
| **Side** | Server side is enough for every optimization; on the client it adds the menu and HUD (see below). |
| **Status** | Alpha, in active development |

## Server and client

- **Server** (a dedicated server, or your own game in singleplayer): this is where Polycore does its work. Faster
  chunk generation, fast aquifers, the lag analyzer and the TPS Guard all run there. **Installing it on the server
  alone gives you every optimization.**
- **Client** (each player's game): adds the full Polycore menu (key K), toasts, the HUD and one-click actions
  (teleport to a lag source, copy a report). It makes running and watching a server much easier, especially for
  operators. It does not change your FPS: Polycore optimizes the server side.
- **Mixing is fine**: players without Polycore can join a Polycore server (operators then get a chest menu with
  `/polycore`), and players with Polycore can join servers that do not have it.
- **Singleplayer**: your game runs its own server, so installing Polycore once gives you everything.

## Optimizations

| Optimization | What it does |
|---|---|
| **Dedicated worldgen pool** | Terrain noise and biomes are computed on Polycore's own threads instead of Minecraft's shared background pool, which also runs the generation queue and lighting. |
| **Parallel surface and carvers** | Vanilla runs every generation stage except noise and biomes on a single queue, one chunk at a time. Surface and carvers only touch their own chunk, so Polycore runs them in parallel (the same stages Paper and Moonrise treat as parallel-safe). |
| **Fast aquifers** | Underground water and lava are decided for every air block. Polycore reuses the work shared by neighbouring blocks: aquifers about 25% faster, noise stage about 10% faster, same world. |
| **Parallel pre-generation** | Pre-generates an area with every core busy (cores × 8 chunks in flight, versus 32 for `/neoforge generate`). The "Keep TPS stable" mode slows down by itself when the server starts to lag. |

Polycore never uses more threads than the machine has logical cores.

Measured on a 20-thread CPU (radius 16): **28.6 → 67.4 chunks/s (+135%)**, average tick time 30 → 10 ms.

## TPS Guard

When the server starts to fall behind (ticks above 45 ms by default, before the TPS drops), the TPS Guard turns on
measures one at a time, from the least visible, and turns them off again once the server is fine. Nothing changes
while the server runs normally, and the view distance is never touched.

| Measure | While active |
|---|---|
| **Item & XP merging** | Items and experience orbs on the ground merge within 2 blocks: fewer entities. |
| **Far mob AI** | Mobs far from every player choose what to do less often but keep moving normally. Villagers, tamed, named or leashed mobs, bosses and fighting mobs are never slowed. |
| **Mob spawn limit** | Natural mob caps are lowered (50% by default). |
| **Simulation distance** | Off by default. Lowers the simulation distance; farms far from players pause. |

Each measure and its setting can be switched from the menu (Server > TPS Guard) and applies at once, without a restart.

## Tools

- **Stage profiler**: time spent in each generation stage (structures, biomes, noise, surface, carvers, features,
  light), so the bottleneck is visible.
- **A/B benchmark**: generates fresh areas with Polycore off, then on, and reports chunks/s and tick time.
- **Parity fingerprint**: hashes the generated blocks around you, to prove the terrain is identical to vanilla.
- **Lag analyzer**: where each tick's time goes (chunks, mob spawning, redstone, entities, block entities, network,
  autosave, other mods), the real TPS and a log of lag spikes. A detailed analysis times every entity and block
  entity and lists the costliest ones with their coordinates; operators can teleport to them. It can start by
  itself when the server lags, and operators can opt in to lag alerts.
- **Reports**: every benchmark, fingerprint and lag analysis is saved in `polycore-reports/`.

## In-game menu

Press **K**, or type `/polycore` (alias `/pc`).

- **This game**: FPS, memory, connection, and your own preferences (toasts, HUD overlay, refresh rate). Always
  editable.
- **Server**: live dashboard, optimizations, profiler, tools, reports and server settings.

| Situation | What you get |
|---|---|
| Polycore on the client and the server | The full menu. |
| Singleplayer | The full menu, everything editable (no cheats needed). |
| Polycore on the client only | Client pages work; server pages explain that the server does not have Polycore. |
| Polycore on the server only | `/polycore` opens a chest menu made of vanilla items. |
| Player who is not an operator | Read-only: stats, profiler and reports are visible, nothing can be changed. Servers can turn this off (`permissions.allowNonOpView`). |

Feedback appears as toasts (or in the action bar for players without the client mod), never in the chat.

## Screenshots

Captured in a real game at 1920×1080 (NeoForge 1.21.1, dedicated server, right after an A/B benchmark).

**Dashboard**: live chunk speed, tick time, CPU and memory, last benchmark, thread pool and recent activity.
![Dashboard](branding/screenshots/menu-server-dashboard.png)

**Lag analyzer**: where every tick goes, detailed analysis and the log of lag spikes.
![Lag analyzer](branding/screenshots/menu-server-lag.png)

**Worldgen profiler**: time spent in each chunk generation stage.
![Worldgen profiler](branding/screenshots/menu-server-profiler.png)

**Tools**: pre-generation, A/B benchmark and parity fingerprint.
![Tools](branding/screenshots/menu-server-tools.png)

**Optimizations**: every optimization with its live state and an on/off switch.
![Optimizations](branding/screenshots/menu-server-optimizations.png)

**TPS Guard**: the measures it takes while the server falls behind.
![TPS Guard](branding/screenshots/menu-server-guard.png)

**HUD overlay** (top left): TPS, chunk speed and tick time while you play.
![HUD overlay](branding/screenshots/hud-in-game.png)

More pages: [this game](branding/screenshots/menu-client-overview.png), [preferences](branding/screenshots/menu-client-settings.png),
[reports](branding/screenshots/menu-server-reports.png), [server settings](branding/screenshots/menu-server-settings.png), [about](branding/screenshots/menu-about.png).

## Commands

| Command | Who | Effect |
|---|---|---|
| `/polycore` | everyone (configurable) | Opens the menu (text summary in the server console) |
| `/polycore status` · `stats` · `profiler` · `report` · `lag` · `guard` | everyone | Opens the matching page (`lag` and `guard` print a summary in the console) |
| `/polycore lag analyze [seconds]` | operators | Detailed lag analysis (5 to 300 s, default 30) |
| `/polycore lag alerts <true\|false>` | operators | Lag alerts for yourself |
| `/polycore toggle <id> [true\|false]` | operators | Turns an optimization on or off (saved) |
| `/polycore set <key> <value>` | operators | Changes a setting (saved) |
| `/polycore threads <n>` | operators | Pool size (0 = automatic, at most the logical cores) |
| `/polycore pregen start <radius> [concurrency]` · `stop` · `status` | operators | Pre-generation around you |
| `/polycore bench [radius]` | operators | A/B benchmark in fresh areas (use a test world) |
| `/polycore hash [radius]` | operators | Parity fingerprint of the blocks around you |
| `/polycore reload` | operators | Reloads `config/polycore.json` |

## Configuration

- Server: `config/polycore.json`. Edit it from the menu, with `/polycore set`, or by hand followed by
  `/polycore reload`.
- Client: `config/polycore-client.json` (toasts, HUD, refresh rate).

## Compatibility

Polycore only adds to Minecraft's code (it never replaces a method), and checks at every start which game methods
other mods change. When another mod changes the same code as one of its optimizations, that optimization stays off,
the menu (Optimizations, TPS Guard) and the console say which mod and which method, and operators get a toast when
they join. Remove that mod and the optimization comes back by itself at the next start. No mod is checked by name:
any mod that changes the same code is seen. The tools (analyzer, pre-generation, benchmark) always work.

## Building from source

Ready-made jars are attached to each [GitHub release](https://github.com/Aprilox/Polycore/releases) (and published on
CurseForge and Modrinth). To build them yourself you need a JDK 21.

```
./gradlew build          Linux / macOS
gradlew.bat build        Windows
```

The mods are written to `neoforge/build/libs/polycore-neoforge-1.21.1-<version>.jar` and
`fabric/build/libs/polycore-fabric-1.21.1-<version>.jar` (needs Fabric API) and
`forge/build/libs/polycore-forge-1.21.1-<version>.jar`. Put the one for your loader in the `mods`
folder of a 1.21.1 server (and client, for the menu). If the build complains about `JAVA_HOME`, point it to a JDK 21 or clear it.

Development runs: `:neoforge:runClient`, `:neoforge:runServer`, `:fabric:runClient`, `:fabric:runServer`,
`:forge:runClient`, `:forge:runServer`; automated tests: `:neoforge:runGameTestServer`, `:fabric:runGametest` and
`:forge:runGameTestServer`.
A production client started with `-Dpolycore.selftest.client=true` checks itself and quits: menu key, menu at the
title screen (screenshot copied to `-Dpolycore.selftest.shot=<file>`), then each server of
`-Dpolycore.selftest.servers=polycore@host:port;vanilla@host:port` (stays connected, HUD, right server section).
Results are log lines starting with `[Polycore selftest]`.

## Checking vanilla parity

1. Create a world with a fixed seed, run `/tp @s 5000 120 5000`, wait for the terrain to load, then
   `/polycore hash 4`.
2. Create the same world again, turn the optimizations off first (`/polycore toggle worldgen_pool false`,
   `/polycore toggle parallel_stages false`, `/polycore toggle fast_aquifer false`), go to the same place and run
   `/polycore hash 4`.
3. Both **terrain** fingerprints must match.

The report lists two fingerprints per chunk. *Terrain* is taken right after the carvers stage (relief, caves,
aquifers) and only depends on the seed. *Full* is the finished chunk: decorations such as trees spill into
neighbouring chunks, so it also depends on the order chunks were generated in, in vanilla too. Compare the terrain
one.

## Roadmap

1. **More work off the main thread**: chunk saving and chunk packets.
2. **Every Minecraft version from 1.18.2 to the latest**, on NeoForge, Fabric and Forge (see
   [Versions and branches](#versions-and-branches)).

See [CHANGELOG.md](CHANGELOG.md) for what changed in each version and [docs/architecture.md](docs/architecture.md)
for how the code is organized.

## Versions and branches

Each Minecraft version line has its own branch, so versions never mix: `main` holds the newest Minecraft version
Polycore supports, `mc/<version>` the others. New features are made on `main` first, then carried to the version
branches. Every jar is the same Polycore version (0.5.0); only the Minecraft version and the loader differ.

**Available** means published, **in progress** built and tested but not published yet, **planned** not ported yet.

| Minecraft | NeoForge | Fabric | Forge | Java | Branch | Download |
|---|---|---|---|---|---|---|
| 26.3 | planned | planned | planned | 25 | `main` (when ported) | |
| 26.2 | planned | planned | planned | 25 | `mc/26.2` | |
| 26.1.x | planned | planned | planned | 25 | `mc/26.1` | |
| 1.21.9 – 1.21.11 | planned | planned | planned | 21 | `mc/1.21.11` | |
| 1.21.6 – 1.21.8 | planned | planned | planned | 21 | `mc/1.21.8` | |
| 1.21.5 | planned | planned | planned | 21 | `mc/1.21.5` | |
| 1.21.4 | **available** | **available** | **available** | 21 | `main` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/polycore/files), [Modrinth](https://modrinth.com/mod/polycore/versions) |
| 1.21.2 – 1.21.3 | **available** | **available** | **available** (1.21.3) | 21 | `main` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/polycore/files), [Modrinth](https://modrinth.com/mod/polycore/versions) |
| 1.21.1 | **available** | in progress | in progress | 21 | `main` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/polycore/files) |
| 1.21 | in progress | in progress | | 21 | `main` | |
| 1.20.5 – 1.20.6 | planned | planned | planned | 21 | `mc/1.20.6` | |
| 1.20.3 – 1.20.4 | planned | planned | planned | 17 | `mc/1.20.4` | |
| 1.20.2 | planned | planned | planned | 17 | `mc/1.20.2` | |
| 1.20.1 | | **available** | **available** | 17 | `mc/1.20.1` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/polycore/files), [Modrinth](https://modrinth.com/mod/polycore/versions) |
| 1.20 | | **available** | **available** | 17 | `mc/1.20.1` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/polycore/files), [Modrinth](https://modrinth.com/mod/polycore/versions) |
| 1.19.4 | | planned | planned | 17 | `mc/1.19.4` | |
| 1.19.3 | | planned | planned | 17 | `mc/1.19.3` | |
| 1.19 – 1.19.2 | | planned | planned | 17 | `mc/1.19.2` | |
| 1.18.2 | | planned | planned | 17 | `mc/1.18.2` | |

The 1.21 jars are the 1.21.1 ones: NeoForge and Fabric declare both versions after a test on a real 1.21 server.
Forge is not planned for 1.21 (Forge 51 fails to load it). An empty cell means that loader does not exist, or is not
targeted, for that Minecraft version.
The 1.20 jars are the 1.20.1 ones (Forge 46 and Fabric tested on a real 1.20 server).

## AI assistance

Polycore is developed with the help of Claude (Anthropic): code, build setup, and porting to the different Minecraft
versions and loaders. The author directs the project, reviews every change and tests the result in game.

## Feedback

Bug reports and benchmark results are welcome in the issues. Please include your CPU, your mod list and, for
performance, the report from `polycore-reports/`.

## License

Copyright (c) 2026 Aprilox. All rights reserved.

You are free to use Polycore, on any computer or server. Sharing it, modified or not, as source or compiled
(including in modpacks), requires notifying the author first and crediting the original project. See
[LICENSE](LICENSE) for the full terms.

## Credits

The parallel stages follow the thread-safety analysis of Paper and Moonrise (Spottedleaf). The ticket-based
pre-generation is inspired by NeoForge's `/neoforge generate` and fabric-chunkpregenerator. Polycore's code is
original.
