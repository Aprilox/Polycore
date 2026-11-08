# Changelog

## 0.5.0

- **Minecraft 1.21.9 – 1.21.10** (Java 21): NeoForge (21.9.16-beta or later) and Fabric (requires Fabric API) on 1.21.9 and
  1.21.10, Forge (60.1.18 or later) on 1.21.10.
- **Fast aquifers** (on by default): underground water and lava are decided for every air block during terrain
  generation; Polycore now reuses the work shared by neighbouring blocks. Aquifers are about 25% faster and the
  noise stage about 10% faster, with exactly the same world (checked chunk by chunk, blocks and scheduled fluids,
  by an automated test). Switch: Optimizations page, or `/polycore toggle fast_aquifer`.
- **Compatibility with other mods**, checked at every start without knowing any mod by name: Polycore reads which
  Minecraft methods other mods change. An optimization whose code another mod also changes stays off; the menu
  (Optimizations, TPS Guard), the chest menu and the console say which mod and which method, and operators get a
  toast when they join, naming the mod, the method and its mixin class. It comes back by itself once that mod is
  removed. Item and XP orb merging are checked apart: with a mod that manages XP orbs, items still merge. The simulation distance measure also
  steps aside when something else changes the simulation distance. Polycore no longer replaces any method (the
  fast aquifers now add to vanilla's code), so other mods' changes to the same code keep working.
- HUD: asks the server only for the values it shows (about 0.2 KB instead of about 19 KB per refresh).
- TPS Guard, far mob AI: only the choice of goals and the brain sensors are slowed; running goals and behaviors
  tick exactly as in vanilla, so far mobs no longer stutter. Item merging keeps vanilla height.
- Menu: the "This server does not have Polycore" card grows with its text instead of letting the last line
  overflow in small windows.
- Menu: fits small windows (1280x720 at GUI scale 2): button labels are clipped, report lines wrap at words,
  tables shrink their columns instead of overflowing.
- Parity fingerprint: `/polycore hash` now reports a **terrain** fingerprint (relief, caves, aquifers, taken
  right after the carvers stage) next to the full one. Decorations spill into neighbouring chunks, so finished
  chunks depend on the order they were generated in; the terrain does not. Same seed and place always give the
  same terrain fingerprint, which is what proves an optimization changes nothing. Recording is on by default
  (setting `parity.recordTerrain`, measured at about 0.5 ms per new chunk on the generation threads, never on the
  server tick; off during benchmarks; the measured cost is written in each fingerprint report).
- Reports page: a Copy button puts the open report in the clipboard. Fixed the Copy and Profiler Reset buttons
  being drawn just outside the visible area (they never showed).
- Removed the planned SIMD terrain noise: measured in Java 21, it was 3.5 to 4 times slower than vanilla (the noise
  table lookups do not vectorize), and vanilla's noise code is already well optimized by the JVM.

## 0.4.0

- **TPS Guard** (menu: Server > TPS Guard, or `/polycore guard`): only while ticks stay above a threshold
  (45 ms by default, before the TPS drops), turns on measures one at a time, from the least visible, and turns them
  off again once the server is fine. Nothing changes while the server runs normally; the view distance never
  changes.
  - Item & XP merging: items and experience orbs merge within 2 blocks.
  - Far mob AI: mobs far from every player (64 blocks by default) choose what to do less often but keep moving
    normally. Villagers, tamed, named or leashed mobs, bosses and fighting mobs are never slowed; Create
    contraptions are not mobs.
  - Mob spawn limit: natural mob caps lowered (50% by default).
  - Simulation distance (off by default): lowered while the server lags.
- Removed the planned parallel lighting: lighting is about 4% of generation time and runs outside the server tick,
  so a new light engine is not worth the risk.
- Every measure, the threshold and each measure's setting can be changed from the menu and apply at once, with no
  restart. The page shows what the Guard is doing live and its recent activations. Chest menu: TPS Guard item.

## 0.3.0

- New voxel logo and banner with a transparent background: banner in the mods list, logo in the Polycore menu.
- **Lag analyzer** (menu: Server > Lag analyzer, or `/polycore lag`): shows where each tick's time goes (chunk
  loading and saving, mob spawning, redstone, entities, block entities, network, autosave, datapacks, other mods),
  the real TPS, and a log of lag spikes with their main cause. Always on, near-zero cost.
- **Detailed analysis** (button, `/polycore lag analyze [seconds]` or the chest menu): times every entity and block
  entity for 30 s and lists the costliest sources with their coordinates, plus the costliest types, mods and chunks.
  Operators can teleport to a source after a confirmation that shows its coordinates; reopening the menu then
  offers to go back to where they were. The report is saved in `polycore-reports/`.
- The detailed analysis starts by itself when the server lags (setting `lag.autoCapture`). Operators can turn on
  lag alerts (toast, or action bar without the client mod); they are off by default. Lag is detected within
  seconds, even when single ticks take several seconds.
- The K key now also closes the Polycore menu.
- TPS is now measured from the real time between ticks instead of being estimated from the tick duration.
- Fixed a server freeze of about 100 ms every second on Windows, caused by reading the CPU usage.
- New license: Polycore is now all rights reserved. It stays free to use; sharing it, modified or not,
  requires notifying the author first (see LICENSE).
- Fixed the benchmark numbers on the dashboard being drawn over a full bar.
- HUD: smaller (3 lines, reduced on large GUI scales with sharp text), shows the TPS in its corner (green, amber or red), says whether chunks are
  generated by Polycore or by vanilla Minecraft, and the number of chunks generated. Chunks are now counted even
  when the stage profiler is off.

## 0.2.1

- Fixed a crash ("Ticking screen") when opening menu pages with editable toggles.
- Automated game tests (settings, permissions, pre-generation) run with `runGameTestServer`.
- Test helper scripts and an `installTestInstance` Gradle task for a continuous test loop.
- GitHub Actions workflows: build on every push, GitHub Release with the jar for every version tag.

## 0.2.0

- Surface and carver generation stages now run in parallel (same stages Paper/Moonrise parallelize).
- New Polycore menu: "This game" and "Server" sections, live graphs, profiler, tools, reports, settings.
- Non-operators can view server stats but not change anything; client settings are always editable.
- Chest-style menu for players without the client mod; feedback through toasts or the action bar, never chat.
- Number fields accept typed values. Thread counts can never exceed the machine's logical cores.
- Fixed the memory display. Code split into a loader-independent core and a NeoForge layer.

## 0.1.0

- First release: dedicated worldgen pool, parallel pre-generation, benchmark, step profiler, parity hash,
  status menu.
