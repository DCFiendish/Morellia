# Agadir Crisis map generation pipeline

Scripted WorldPainter pipeline that replaced the two abandoned real-terrain attempts described
in `docs/HANDOFF.md` (a stale pre-flattening Anvil download, and a from-scratch procedural
NOAA/WWF heightmap generator). Real SRTM15+ elevation, imported and shaped via WorldPainter's
`wpscript` scripting host (not the GUI, so every step here is reproducible from the command
line) rather than a hand-rolled generator.

## Status as of this commit

- **`nodisium-data/world/` (gitignored, ~230MB) holds the "vegetated" pass** — real elevation
  (smoothed), latitude-agnostic elevation-banded terrain, WorldPainter's built-in Deciduous/Pine
  forest layers (density too low — see Open items), Frost above ~2500m. This is the last
  version actually verified booting cleanly in Minestom with a connected client.
- **`agadir-import-v2.js` is written but NOT yet run/verified.** It fixes tree density (was
  1/15 — nearly invisible, confirmed root cause of "trees look terrible" / "very little
  foliage"), adds ground texture variety (Stone Mix instead of solid Rock, noise-patched
  Dirt/Gravel/Sand), and adds real latitude-based biome zoning (Mediterranean south vs.
  temperate center) — elevation alone was putting pine forest on Moroccan mountains and
  deciduous forest on Mediterranean coastline, which the user flagged as wrong.
- **16 real `.schem` tree schematics already downloaded** from
  [TreeForge](https://treeforge.meowbeard.com) (8 pine, 8 oak) in `treeforge-trees/` — genuinely
  big/custom-shaped trees (65-66 blocks tall), not WorldPainter's plain built-in generator.
  **Not yet wired into the pipeline** — see Open items.

## Reproducing from scratch

1. **Download elevation data** (requires a free OpenTopography API key —
   account signup at portal.opentopography.org, can't be automated):
   ```
   curl -o agadir-europe-srtm15plus.tif "https://portal.opentopography.org/API/globaldem?demtype=SRTM15Plus&south=33&north=58.5&west=-10&east=14.5&outputFormat=GTiff&API_Key=YOUR_KEY"
   ```
   This is the real trimmed box from `docs/HANDOFF.md`'s original attempt (Britain through
   Morocco), SRTM15+ chosen specifically because ~450m/px is a near-exact match for the
   approved 1:750 scale target — see the conversation history for why other resolutions/sources
   were rejected (SRTM GL1/GL3 need multi-tile requests over this area; Terra 1-to-1 is fixed
   1:1 scale and stuck on old Minecraft versions).

2. **Convert to a WorldPainter-importable heightmap**:
   ```
   python convert_heightmap.py agadir-europe-srtm15plus.tif agadir-heightmap-16bit-smoothed.png
   python generate_latitude_mask.py agadir-latitude-mask.png --width 5880 --height 6120
   python generate_texture_noise.py agadir-texture-noise.png --width 5880 --height 6120
   ```
   (width/height must match the source GeoTIFF's actual pixel dimensions — check with
   `rasterio` or any image tool if you re-download with different bounds.)

3. **Run the WorldPainter import script** (requires WorldPainter 2.27.0+ installed —
   `wpscript.exe` ships alongside the main app, e.g.
   `C:\Program Files\WorldPainter\wpscript.exe`):
   ```
   wpscript agadir-import-v2.js
   ```
   Run from this directory (or adjust the file paths inside the script). Creates
   `agadir-world-export-v2/`.

4. **Patch the grass block name** (WorldPainter's 26.1 export writes the invalid legacy
   `minecraft:grass` for its default vegetation feature on every single export — this is not a
   one-time fix, it recurs every run):
   ```
   python patch_grass_names.py agadir-world-export-v2/<generated-name>/dimensions/minecraft/overworld/region
   ```

5. **Copy into place and point `AgadirWorld.kt` at it**:
   ```
   cp -r agadir-world-export-v2/<generated-name>/. ../../server/nodisium-data/world/
   ```
   Copy the **export root itself** (the folder containing `level.dat`, `data/`, `dimensions/`,
   `session.lock`) — `nodisium-data/world` should end up looking exactly like a normal Minecraft
   world save folder. (An earlier version of this doc said to copy only the inner
   `dimensions/minecraft/overworld` folder's contents instead, i.e. flatten `region/` up to
   `nodisium-data/world/region`. That was wrong — confirmed by disassembling Minestom's actual
   `AnvilLoader.class`: its constructor does
   `path.resolve("level.dat")` and separately
   `path.resolve("dimensions").resolve(key.namespace()).resolve(key.value()).resolve("region")`,
   so the `Path` you give it must be the full world root, not pre-flattened. `AgadirWorld.kt`'s
   own comments had the same wrong assumption baked in — both are fixed now.)

## Key facts worth not re-discovering

- **Minestom pin `2026.07.12-26.2` targets official Minecraft 26.2** (year.drop numbering,
  confirmed via Minestom's own release notes). Not a snapshot.
- **WorldPainter's newest bundled platform is `JAVA_ANVIL_26_1`** (id
  `org.pepsoft.anvil.26.1`) — confirmed by inspecting the installed `WPCore.jar` directly, no
  separate 26.2 entry exists (consistent with 26.2 not changing the world save format).
- **World height is real modern Overworld (-64..320)**, matching Minestom's
  `DimensionType.OVERWORLD` — this required fixing `modules/nodes/.../OreSampler.kt`'s
  hardcoded legacy `Y_WORLD_MIN=0`/`Y_WORLD_MAX=255` (already fixed, see that file) plus
  `OreDeposit.kt`'s matching defaults.
- **The imported world is NOT centered on the origin** — it occupies roughly block
  X:[0,3583] Z:[0,3711] (confirmed by scanning the exported `.mca` region files' chunk headers
  directly). `Main.kt`'s spawn point is the box's true center, not (0,0).
- **WorldPainter's `TreeLayer` (and subclasses `DeciduousForest`/`PineForest`) use NIBBLE
  data (0-15 density), not binary on/off** — confirmed via `WPCore.jar` bytecode inspection.
  Always use a real density value (10-14ish), not `1`.
- **Vanilla's `PlayerData` restores saved position on every join** (`modules/vanilla/.../
  managers/PlayerData.kt`, `PlayerSpawnEvent` + `isFirstSpawn`) — changing `Main.kt`'s spawn
  point does nothing for a player who has ever joined before; their saved `.dat` file under
  `nodisium-data/vanilla/playerdata/` wins. Autosaves every 300s and on disconnect, so editing
  that file while a session is still live gets silently overwritten — stop the server first.

## Custom object layers — RESOLVED, `wpscript` can paint and export them

The open item below about scripted custom-tree placement is **answered**: `wp.getLayer()
.fromFile('some.layer').go()` loads a real `org.pepsoft.worldpainter.layers.Bo2Layer` from a
`.layer` file (confirmed via `wpscript`, not just the GUI's "+" layer-import button), and
`wp.applyLayer(layer).toWorld(world).toLevel(n).applyToSurface().setAlways().go()` paints it the
same way `applyToLayer` paints `Deciduous`/`Pine`/`Frost`. Verified end-to-end with a throwaway
test world: exporting after painting a Bo2Layer actually bakes real objects into the `.mca` region
(confirmed via `anvil-parser2` block-scanning the output — real `jungle_log`/`oak_leaves` blocks
present, not just a GUI-preview effect). So this does NOT need a manual GUI import step — a
`.layer` file's path can be passed straight to `fromFile()` from any script.

**`community-layers/` now holds three real Bo2Layer files** sourced from Lerfing's free
WorldPainter tutorial content (Patreon), not TreeForge: `aPalm Trees.layer`, `aTaiga.layer`,
`aSwamp Generic.layer`. License terms for these specific files weren't confirmed (Patreon is
unreachable from tooling here — 403/blocked) — worth getting an explicit statement from Lerfing's
post before treating this as settled the way the memava/TastyTony credits are. Also installed
separately: Lerfing's **Custom Brushes pack** (47 terrain-shaping greyscale brushes — Mountain,
Plateau, Cliff, Desert Mountain, etc.) into WorldPainter's real brushes folder,
`%APPDATA%\WorldPainter\brushes\Custom Brushes\` (confirmed via `WPGUI.jar`/`Configuration.class`
bytecode inspection — restart WorldPainter's GUI to see them as brush buttons; this one is GUI-only
convenience, not something `wpscript` needs a path to).

**Not yet done**: actually wiring `aPalm Trees.layer`/`aTaiga.layer`/`aSwamp Generic.layer` into
`agadir-import-v2.js`'s real biome zoning (e.g. palm on the Mediterranean/Moroccan coast instead of
just Bare Grass, taiga replacing the built-in Pine layer on the highland band, swamp on
low-elevation flats once rivers exist) — this is a design decision on which zone gets which layer
and at what density now, not a technical blocker. The TreeForge `.schem` files in
`treeforge-trees/` are a separate, still-open case — see item 1 below, which now has a much more
promising angle than blind Bo2/Bo3 API archaeology.

## `.schem` → `.layer` conversion — RESOLVED, fully scriptable, no GUI needed at all

`MakeLayer.java` (this directory) converts a set of TreeForge (or any Sponge `.schem`) files
straight into a real WorldPainter `.layer` file, entirely outside the GUI. Verified end-to-end:
converted all 8 `treeforge-trees/oak/*.schem` into `treeforge-oak.layer`, loaded it back with
`wp.getLayer().fromFile()`, painted it on a 512x512 test world, exported, and confirmed real
`oak_wood`/`oak_leaves` blocks in the output (164/157 blocks — a small first test area showed zero,
which was just bad luck at that sample size, not a real failure; re-ran bigger and they showed up).

**Why this was harder than it looked, for whoever revisits it**: WorldPainter's *modern* Sponge
Schematic support lives in `org.pepsoft.worldpainter.layers.bo2.Schem` (note: no "atic") — a
completely different class from the legacy `Schematic` class (which expects the old pre-1.13
MCEdit `.schematic` format's `Materials` tag and throws `IllegalArgumentException` on a real
`.schem`). `Schem.load(File)` handles decompression itself and returns a `WPObject` directly.
Feed a `List<WPObject>` into `new Bo2ObjectTube(name, objects)` (the weighted-random multi-object
provider — confirmed by inspecting a real Lerfing/sijmen `.layer` file's decompressed bytes, which
embed full per-object voxel data inline, not external file paths, so `.layer` files are completely
self-contained), wrap that in `new Bo2Layer(tube, name, someColor)`, then serialize with a plain
`ObjectOutputStream` wrapped in `GZIPOutputStream` — that gzip+Java-serialization pair is the
entire `.layer` file format (confirmed via `javap` on `GetLayerOp.class`, which reads it back the
same way).

**How to run it** — `make-layer.sh` wraps the classpath (needs a JDK — used Eclipse Adoptium 21
here — and WorldPainter's own jars, none of which need modification):
```
./make-layer.sh "<Layer Name>" output.layer schem1.schem schem2.schem ...
```
A wildcard `lib\*` classpath entry did NOT expand correctly through Git Bash calling the Windows
`javac.exe`/`java.exe` — the script lists needed jars explicitly, semicolon-separated. `Schem`'s
static initializer pulls in WorldPainter's full `Configuration`/`Material`/`Layer` class-init
chain, which is why the jar list is longer than "just the schematic-reading classes" would suggest
— trimmed down by trial and error, don't assume it's minimal if extending it.

**Done so far**: `treeforge-oak.layer` and `treeforge-pine.layer` (both default density 20, no
per-species tuning yet). Palm and any other species/biome zone are the same one-command conversion
away — genuinely just a matter of picking which `treeforge-trees/<species>/*.schem` (or any other
`.schem` source, including `sijmenvb/worldpainter-trees`'s per-biome folders — see below) to feed
in next, and what density/name/color to give each resulting layer, before wiring them into
`agadir-import-v2.js`'s biome zoning.

## Open items (in priority order the user cares about)

1. ~~Get the TreeForge schematics into a real paintable layer~~ — **done, see above.**
   Remaining sub-items: generate more TreeForge species (Palm for the Moroccan/Mediterranean coast
   is the obvious next one) the same way, and actually wire the resulting `.layer` files into
   `agadir-import-v2.js`'s biome-zoning logic (which zone gets which layer, what density) — a
   design decision now, not a technical blocker.
1b. **Biome/feature coverage gap in the community assets currently on hand.** Lerfing's free
   layers (`community-layers/`) are good quality but only cover 3 biomes (palm/taiga/swamp) — the
   user confirmed this directly. Two ways to fill the rest, both now fully scriptable via
   `MakeLayer.java`: (a) generate more TreeForge species/variants (8 species total, only Oak/Pine
   downloaded — see the site's own "Batch Export" panel), or (b) pull from
   [sijmenvb/worldpainter-trees](https://github.com/sijmenvb/worldpainter-trees) (MIT licensed,
   16 layers across 11 biomes as ready-made `.schem` sets: badlands, dead trees, jungle
   edge/thick, lush oak, mushrooms, old living forest, palm trees, rocks and bushes, roofed
   forest, savanna, small trees x5, spruce, swamp, wooded plateau) — cloned to a scratchpad
   already this session for inspection; not yet copied into this repo. Between the two, (b)
   already has more biome breadth pre-organized; (a) has more per-species variant depth. Worth
   deciding per-biome rather than picking one source exclusively.
2. **Run and verify `agadir-import-v2.js`** (density/texture/latitude fixes) — written, not
   yet executed or boot-tested.
3. **Biome accuracy is elevation+latitude only** — cannot distinguish Morocco from Southern
   Spain (same latitude band). Needs real country-boundary geodata (Natural Earth, same source
   the deferred border-painting pipeline needs) to fix properly.
4. **Border/territory painting** (`nodes`' `world.json`/`towns.json`) is a fully separate,
   not-yet-started task — see `docs/HANDOFF.md` for the confirmed-unrecoverable prior pipeline
   and the exact schema already reverse-engineered from `Deserializer.kt`.
5. **Rivers** were explicitly deferred from the start — no hydrology data sourced yet.
6. The user raised a legitimate open question: is scripting every visual-polish decision
   (texture variety, tree density, biome zoning) actually the right approach, vs. using
   WorldPainter's GUI directly now that the real data is correctly imported at the right
   scale/position/version? Not resolved — the user hasn't chosen between "keep scripting" and
   "take over in the GUI" as of this commit.
