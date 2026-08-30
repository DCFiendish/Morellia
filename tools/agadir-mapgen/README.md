# Agadir Crisis map generation pipeline

Scripted WorldPainter pipeline that replaced the two abandoned real-terrain attempts described
in `docs/HANDOFF.md` (a stale pre-flattening Anvil download, and a from-scratch procedural
NOAA/WWF heightmap generator). Real SRTM15+ elevation, imported and shaped via WorldPainter's
`wpscript` scripting host (not the GUI, so every step here is reproducible from the command
line) rather than a hand-rolled generator.

## Status as of this commit

- **`morellia-data/world/` (gitignored, ~230MB) holds the "vegetated" pass** — real elevation
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
   cp -r agadir-world-export-v2/<generated-name>/. ../../server/morellia-data/world/
   ```
   `AgadirWorld.kt`'s `PATH` constant expects `morellia-data/world` to directly contain
   `region/` — WorldPainter's 26.1 export nests it one level deeper
   (`<export-root>/dimensions/minecraft/overworld/region/`), so copy the *inner*
   `dimensions/minecraft/overworld` folder's contents, not the export root itself.

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
  `morellia-data/vanilla/playerdata/` wins. Autosaves every 300s and on disconnect, so editing
  that file while a session is still live gets silently overwritten — stop the server first.

## Open items (in priority order the user cares about)

1. **A real "tree painting" script using the TreeForge custom schematics**, not just
   WorldPainter's built-in procedural Deciduous/Pine layers. The user explicitly wants the big
   custom trees (`treeforge-trees/`) scattered across the map programmatically, not a static
   handful of samples. Unconfirmed: whether `wpscript`'s API can place custom `.schem`/Bo2/Bo3
   objects the way it paints built-in layers (`Bo2Layer`/`Bo2Object`/`Schem` classes exist in
   `WPCore.jar`, and the `CustomObjects` wiki page documents the GUI workflow, but no scripting
   op for "install a custom object collection as a paintable layer" was found in the documented
   `Scripting/API` reference) — needs real investigation, possibly requires a one-time manual
   GUI import step that scripting alone can't replace.
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
