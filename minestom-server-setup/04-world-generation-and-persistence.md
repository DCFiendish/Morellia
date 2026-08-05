# World Generation & Persistence

Minestom ships with **zero default terrain** — an empty `InstanceContainer` generates nothing until you attach something. Three real paths exist.

## (a) Procedural generation via `Generator`

Call `instance.setGenerator(generator)` (or `null` to disable). The generator receives a `GenerationUnit` representing a batch of sections/chunks and exposes a `modifier()` for block placement, plus `absoluteStart()`/`size()` for coordinates. Simple cases use helpers like `unit -> unit.modifier().fillHeight(0, 40, Block.STONE)`; real terrain typically integrates JNoise for heightmap-based generation, iterating unit coordinates and filling up to a noise-derived height. Structures crossing unit boundaries need `fork()` (sized or `Block.Setter`-based) to safely place across chunk edges.

Sources: [Generation docs](https://minestom.net/docs/world/generation), [Instances docs](https://minestom.net/docs/world/instances), [InstanceContainer javadoc](https://javadoc.minestom.net/net.minestom.server/net/minestom/server/instance/InstanceContainer.html).

## (b) Vanilla Anvil loading

Minestom's own `AnvilLoader` reads real region-file worlds: `instance.setChunkLoader(new AnvilLoader("worlds/world"))`. It relies on Minestom's own **Hephaistos** NBT/Anvil library. Anvil load/save is comparatively slow, and users report edge cases (dimension mismatches, chunk-supplier bugs).

Sources: [AnvilLoader wiki](https://github.com/Minestom/wiki/blob/master/world/anvilloader.md), [Hephaistos](https://github.com/Minestom/Hephaistos), [Issue #1634](https://github.com/Minestom/Minestom/issues/1634).

## (c) Polar — the community-favored format for real persistent maps

Polar is a Minestom-native, single-file, non-NBT binary world format built for **fast load and small size**, explicitly *not* intended for huge open worlds since the whole file loads at once (no random per-chunk access) — it targets small/instanced maps (arenas, lobbies, minigame maps).

Setup: `instance.setChunkLoader(new PolarLoader(Path.of("world.polar")))`, save with `instance.saveChunksToStorage()`. Convert existing Anvil worlds via `AnvilPolar.anvilToPolar(path)` + `PolarWriter.write(...)`, optionally filtering with a `ChunkSelector`.

The project's own benchmarks show large gains — roughly ~9.65s/iteration for Anvil vs. ~0.61s/iteration for Polar-with-zstd-compression on a region — which is why community tooling (`PolarConverter`, `PolarPaper`) exists to bring the format to other loaders too.

Sources: [polar repo](https://github.com/hollow-cube/polar), [FORMAT.md](https://github.com/hollow-cube/polar/blob/main/FORMAT.md), [PolarConverter](https://github.com/BitByLogics/PolarConverter).

## Recommendation, and how it fits this project

- **Procedural `Generator`** for infinite/ephemeral worlds — not the fit for the nations/territory game, which needs a real, hand-designed, persistent, bounded map (per the map-design discussion in [../RESEARCH.md](../RESEARCH.md) — theme now decided as the First Balkan War (1912–1913), see `research-todo/00-index.md`; exact map dimensions still open).
- **Polar** for the actual world once you design it — this is the community-favored production path for exactly what this project needs: a real, hand-built, persistent, explorable map, not something regenerated on every boot. Its "whole file loads at once, not built for huge open worlds" caveat is worth keeping in mind when you get to sizing the map — a genuinely enormous continent (e.g. a literal full-scale Balkan peninsula) might push against Polar's sweet spot, in which case Anvil (with its slower but truly chunk-random-access model) could become the better fit despite the load-time cost. Worth revisiting once you have real map dimensions in mind.
- **Anvil** mainly for one-off conversion (e.g., if the map gets built in vanilla Minecraft/WorldEdit first, then converted to Polar via `AnvilPolar` for actual production use), or if true random per-chunk access at very large world sizes turns out to matter more than Polar's speed advantage.

**Relevant to the hub/world-server split** (RESEARCH.md §8): the hub shard(s) are small, static lobby spaces — an obvious fit for Polar regardless of what the flagship world shard ends up using, given Polar's explicit design target of "small/instanced maps."
