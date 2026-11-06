# Polycore — Multicore optimization for Minecraft

Minecraft leaves most of your CPU cores idle. **Polycore** spreads the game's work across all of them, starting with chunk generation, and gives you the tools to **measure the gain yourself** instead of trusting a promise.

## What it does

- **Dedicated worldgen thread pool** — terrain noise and biome computation run on their own threads, freeing Minecraft's shared pool for the generation queue and lighting.
- **Parallel pre-generation** — `/polycore pregen start <radius>` keeps every core busy (cores × 8 chunks in flight, versus 32 for `/neoforge generate`). Skips existing chunks and saves as it goes.
- **Stage profiler** — see exactly where generation time goes: structures, biomes, noise, surface, carvers, features, light.
- **Built-in A/B benchmark** — `/polycore bench` generates fresh areas with and without Polycore and reports chunks/s, tick time and CPU usage.
- **Vanilla parity check** — `/polycore hash` fingerprints generated terrain so you can verify nothing changes.
- **In-game menu** — press **K** to see every optimization, toggle them, watch live stats and run the tools.

## Requirements

- Minecraft **1.21.1**
- NeoForge **21.1.235** or newer
- Works in **singleplayer** and on **dedicated servers**. Server-side only is enough; install it on the client too for the menu.

## Compatibility

Polycore checks at every start which game code other mods change. If another mod changes the same code as one of its optimizations, that optimization turns itself off and the menu says which mod; it comes back by itself once that mod is removed. The profiler, pre-generation and benchmark always work.

## Roadmap

- v2: parallel stage scheduler (surface, carvers and structures in parallel)
- v2: SIMD terrain noise, verified bit-for-bit against vanilla
- Later: other parts of the game beyond chunk generation

## Feedback

Please share your `/polycore bench` report (in `polycore-reports/`) with your CPU model and mod list — it directly shapes what gets optimized next.

*Made by Aprilox · All rights reserved: free to use, sharing requires notifying the author (see the LICENSE file)*
