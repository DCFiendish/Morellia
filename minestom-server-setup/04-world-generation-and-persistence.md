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

## What was actually shipped (2026-08-06) — Anvil directly, not Polar

**Decision reversed from the original recommendation below.** The real map — a "Rise of Rome" Europe
terrain download, trimmed to a box spanning Britain through Morocco (`x: -8192..2559, z:
-5632..3071` in block coordinates, confirmed by direct landmark inspection: Gibraltar Strait, Dover
Strait) — is loaded straight via `AnvilLoader` in production
([`server/AgadirWorld.kt`](../server/src/main/kotlin/net/morellia/server/AgadirWorld.kt)), with
**no conversion to Polar**. Reasoning: the trimmed box (~1.5GB) never showed a measured load-time
problem worth the extra `AnvilPolar` conversion step in practice — the "genuinely enormous
continent" caveat flagged below turned out not to apply once the map was actually trimmed down to
the ten-nation box rather than kept at full-continent scale. A `Generator` fallback
(`StoneFlatTerrain`) still covers anything outside the trimmed box, so `setChunkLoader` +
`setGenerator` are both wired on the instance — the loader takes priority per chunk, the generator
only fires for chunks it has no data for.

**Revisit Polar only if Anvil's load time becomes a measured problem** (e.g. if the box needs to
grow substantially, or cold-start time at scale becomes noticeable) — the conversion path
(`AnvilPolar.anvilToPolar` + `PolarWriter.write`) documented above is still the right tool for that
if it's ever needed.

## Original recommendation (superseded above for the flagship world; still applies to hub shards)

- **Procedural `Generator`** for infinite/ephemeral worlds — not the fit for the nations/territory game, which needs a real, hand-designed, persistent, bounded map. This is what the trimmed-box terrain now covers; the generator's remaining role is purely the out-of-bounds flat-stone fallback described above.
- **Polar** was the original production recommendation for the flagship world — superseded above. Its "whole file loads at once, not built for huge open worlds" caveat is still worth keeping in mind for any *other* map this project builds — e.g. hub/lobby worlds, per the note below.
- **Anvil** — turned out to be the actual production choice for the flagship world's real terrain, not just a one-off conversion step, per the decision above.

**Relevant to the hub/world-server split** (RESEARCH.md §8): the hub shard(s) are small, static lobby spaces — Polar is still a good fit there regardless of the flagship world's Anvil choice, given Polar's explicit design target of "small/instanced maps." Hub shards don't exist yet, so this is unbuilt, not contradicted by the flagship's Anvil decision.
