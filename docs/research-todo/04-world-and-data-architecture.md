# World & Data Architecture

Two related but distinct open areas: the physical world/map, and the data layer underneath it (player data, territory data, permissions, logs). Grouped together here because the world-height and persistence-format decisions in the first half directly constrain the storage-format questions in the second half.

## World generation & map design — REVERTED (2026-08-25)

**The real-terrain map described below has been abandoned**, not shipped. Two separate real-world
terrain sources were tried and both are now removed from the codebase:

- `AgadirWorld.kt`'s `AnvilLoader` over a trimmed "Rise of Rome" Europe download — broke on a
  Minestom version bump (block-palette mismatch, "Unknown block minecraft:grass" per chunk) and was
  never fixed.
- `EuropeTerrain.kt`'s procedural generator, driven by real NOAA/WWF heightmap+biome raster data —
  already superseded by flat terrain before this revert, kept around unused, now deleted along with
  its `heightmap.bin`/`biome.bin` resources.

The real-geodata 1911 border/territory pipeline (Natural Earth country boundaries, organic
geodesic-partition tiling, described in the paragraph below) **only ever wrote to the production
server's own `nodes` data files directly over SSH — it was never brought into this repo's local
`nodisium-data/nodes/`**, which still holds the small 2-town flat-world test fixture it always had.
So no local nodes/territory data needs to change as part of this revert.

The server now runs on **plain flat stone superflat terrain** (`StoneFlatTerrain.generator`,
surface at y=64) as the deliberate baseline, local-only, while terrain gets replanned from scratch.
See `HANDOFF.md` for current status.

<details>
<summary>Original resolved write-up (2026-08-06), kept for history — describes the abandoned map</summary>

- **World data**: real Anvil terrain (trimmed from a "Rise of Rome" Europe download) loaded directly via `AnvilLoader`, not converted to Polar — see the updated recommendation in `minestom-server-setup/04-world-generation-and-persistence.md`. Trimmed box: `x: -8192..2559, z: -5632..3071` block coordinates, covering Britain through Morocco. Anything outside the box falls through to a flat-stone `Generator` fallback.
- **Map/nation layout**: theme is **the Agadir Crisis (1911), alternate history** — Germany, France, UK, Spain, Italy, Morocco, Switzerland, Netherlands, Belgium, Portugal. Borders are drawn from real geodata (Natural Earth country boundaries + real French department boundaries for the Alsace-Lorraine carve, not hand-typed polygons; two deliberate period corrections: Alsace-Lorraine German, UK includes all Ireland), tiled into organic (geodesic-partition, compactness-optimized) territories deployed as real `nodes` `world.json`/`towns.json` data — one town+nation per country. Portugal's territory count is low because its real coastline sits at the trimmed box's western edge — accepted limitation, not a bug.
- **World-height target**: not separately re-decided — the real Anvil terrain uses whatever height range the source world was generated at (standard modern Minecraft world height), so this is settled by the terrain source itself rather than needing an independent decision. The `nodes`/`OreSampler` Y-bound audit flagged below is still open.
- **Still open**: audit `nodes`' `OreSampler` and `Territory.defaultSpawnLocation` (legacy 0–255 assumptions) and `vanilla`/`combat`'s hardcoded Y-bounds against the real terrain's actual height range — this also affects `NODES_DEEP_DIVE.md`'s flagged "ore-sampler Y-range off-by-one" bug, which still needs the real range confirmed before it can be properly fixed.

</details>

## Persistence & data architecture

The aspirational architecture in `RESEARCH.md` §7a/§16 (Redis for cross-shard pub/sub + shard registry, MongoDB scoped to `PlayerData` only) needs to be reconciled against what's actually confirmed in the three deep-dive docs:
- `nodes` — hand-rolled JSON, single-process, file-lock (`NODES_DEEP_DIVE.md` §2.2).
- `vanilla` — real gzip NBT `.dat` files per player (`VANILLA_DEEP_DIVE.md` §1.6), also single-process.
- `logger` — embedded H2, also single-process (file-lock), per `RESEARCH.md` §13.

None of these three are naturally multi-shard-safe. Is the intent "flagship world = single dedicated process, only hub-adjacent/cross-shard state (chat, shard registry) ever touches Mongo/Redis," or is this an unreconciled gap between an aspirational architecture doc and the libraries' actual implementation? This needs an explicit answer before scaffolding `world-server`, since it determines whether `world-server` can ever legitimately run as more than one process.

Specific sub-questions:
- **LuckPerms storage backend** — unconfirmed from source whether the community LuckPerms-Minestom fork defaults to per-process embedded storage (H2/SQLite). If so, permissions would silently desync the moment there's a second shard process. Needs an operational check (spin up the bridge, inspect its config/storage options) before launch, not just a source-read.
- **Cross-shard `/invsee`/live inventory viewing** — undecided between a small inter-shard RPC call vs. accepting a slightly-stale view read from Mongo's `PlayerData` documents. Pick one; this is a staff-tooling requirement, not a nice-to-have, once there's more than one shard.
- **`NodesConfig` save/backup period fields** — mentioned only in passing ("paths, save/backup periods") with no elaboration anywhere. Needs real answers: how often does it autosave, is the hand-rolled JSON writer atomic (temp-file + rename, or a truncate-in-place that can corrupt on crash mid-write — `VANILLA_DEEP_DIVE.md`'s PlayerData save-race bug suggests the latter pattern is already a known risk elsewhere in the codebase), and what happens on crash-during-write.
- **`LoggerConfig`** — actual fields, retention/rotation policy for the embedded H2 database, and projected disk growth at 200+ concurrent players over weeks/months of uptime are all unresearched. This matters operationally (disk fills up silently) more than architecturally.
- **Does `logger` data ever need cross-shard querying?** E.g. staff sitting on a hub shard running `/logger lookup` for an incident that happened on the world shard. Unlike `PlayerData`, this cross-shard-access question was never even raised for `logger` — worth deciding now while the answer is still "no" and simple, rather than after staff workflows assume otherwise.
- **`nodes-map`'s exported JSON files** (`world.json`/`towns.json`/`war.json`/`buildings.json`) are architecturally load-bearing — the hub reads them read-only, and planned chat/roster features resolve town/nation membership from them — but their update frequency/staleness window has never been measured or designed. If hub-side chat filtering depends on "is this player in an enemy nation," a stale export could produce wrong friendly-fire/chat-visibility decisions.
