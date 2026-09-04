// Agadir Crisis map -- v2: fixes tree density (was 1/15, basically invisible), adds ground
// texture variety (Stone Mix instead of solid Rock, noise-patched Dirt/Gravel), and real
// latitude-based biome zoning (elevation alone incorrectly put pine forest on Moroccan
// mountains and deciduous forest on Mediterranean coastline -- real Europe's vegetation is
// latitude-driven as much as elevation-driven).
//
// NOT YET RUN as of this commit -- v1 (agadir-world-export-vegetated, currently what
// nodisium-data/world/ holds) is the last version actually verified booting in Minestom.
// This is the next step, written but not yet executed/verified. Run with:
//   wpscript agadir-import-v2.js
// from a directory containing agadir-heightmap-16bit-smoothed.png (convert_heightmap.py),
// agadir-latitude-mask.png (generate_latitude_mask.py), and agadir-texture-noise.png
// (generate_texture_noise.py). After running, re-apply patch_grass_names.py to the output
// (WorldPainter's grass-vegetation feature writes the invalid legacy "minecraft:grass" name
// on every export -- see that script's docstring) before pointing AgadirWorld.kt at it.
//
// Known limitation, not fixed here: latitude alone can't distinguish Morocco from Southern
// Spain (both ~33-36N) -- that needs real country-boundary geodata (the same Natural Earth
// data the deferred border-painting pass will use), not attempted in this pass.
//
// ALSO STILL OPEN per the user: this only uses WorldPainter's built-in procedural
// Deciduous/Pine tree exporters. The user wants a proper scripted "tree painting" system using
// the big custom TreeForge schematics (tools/agadir-mapgen/treeforge-trees/, 8 pine + 8 oak
// .schem files already downloaded) instead of just these two options -- NOT yet implemented.
// Whether wpscript's API can place custom Bo2/Bo3/.schem objects the way it paints built-in
// layers is unconfirmed; needs research (see CustomObjects wiki page, Bo2Layer/Bo2Object
// classes confirmed present in WPCore.jar) before this can be scripted the same way.
var MAP_FORMAT_ID = 'org.pepsoft.anvil.26.1';

var heightMap = wp.getHeightMap().fromFile('agadir-heightmap-16bit-smoothed.png').go();
var latMap = wp.getHeightMap().fromFile('agadir-latitude-mask.png').go();
var noiseMap = wp.getHeightMap().fromFile('agadir-texture-noise.png').go();
var mapFormat = wp.getMapFormat().withId(MAP_FORMAT_ID).go();

var world = wp.createWorld()
    .fromHeightMap(heightMap)
    .scale(60)
    .fromLevels(0, 65535).toLevels(45, 210)
    .withWaterLevel(64)
    .withMapFormat(mapFormat)
    .withLowerBuildLimit(-64)
    .withUpperBuildLimit(320)
    .go();

// --- Step 1: base terrain by elevation ---
// px 0-1393=-100..2m Beach | 1394-12288=2..800m lowland | 12289-25940=800..1800m highland |
// 25941-65535=1800..4700m peaks. Peaks use Stone Mix (75), not solid Rock (29), for texture.
wp.applyHeightMap(heightMap)
    .toWorld(world)
    .applyToTerrain()
    .fromLevels(0, 1393).toTerrain(36)      // Beaches
    .fromLevels(1394, 12288).toTerrain(0)   // Grass (lowland, refined by latitude below)
    .fromLevels(12289, 25940).toTerrain(3)  // Coarse Dirt (highland)
    .fromLevels(25941, 65535).toTerrain(75) // Stone Mix (peaks -- textured, not solid Rock)
    .go();

// --- Step 2: latitude refines lowland (Grass=0) -- Mediterranean south (<37N, px<10286)
// gets Bare Grass (1, sparser-looking) instead of lush Grass. ~37N is roughly the northern
// edge of Mediterranean climate (southern Spain/Italy/Greece).
var lowlandFilter = wp.createFilter().onlyOnTerrain(0).go();
wp.applyHeightMap(latMap)
    .toWorld(world)
    .withFilter(lowlandFilter)
    .applyToTerrain()
    .fromLevels(0, 10286).toTerrain(1)      // Mediterranean south -> Bare Grass
    .fromLevels(10287, 65535).toTerrain(0)  // rest stays Grass
    .go();

// --- Step 3: noise-patched texture variety (natural-looking patches, not solid single
// materials) -- ~12% of each open terrain gets a different material mixed in.
var grassFilter = wp.createFilter().onlyOnTerrain(0).go();
wp.applyHeightMap(noiseMap)
    .toWorld(world)
    .withFilter(grassFilter)
    .applyToTerrain()
    .fromLevels(57500, 65535).toTerrain(2) // Dirt patches in Grass
    .go();

var bareGrassFilter = wp.createFilter().onlyOnTerrain(1).go();
wp.applyHeightMap(noiseMap)
    .toWorld(world)
    .withFilter(bareGrassFilter)
    .applyToTerrain()
    .fromLevels(57500, 65535).toTerrain(5) // Sand patches in Bare Grass (Mediterranean/dry look)
    .go();

var highlandFilter = wp.createFilter().onlyOnTerrain(3).go();
wp.applyHeightMap(noiseMap)
    .toWorld(world)
    .withFilter(highlandFilter)
    .applyToTerrain()
    .fromLevels(57500, 65535).toTerrain(34) // Gravel patches in the highland band
    .go();

// --- Step 4: Frost above ~2500m (unchanged from v1) ---
var frostLayer = wp.getLayer().withName('Frost').go();
wp.applyHeightMap(heightMap)
    .toWorld(world)
    .applyToLayer(frostLayer)
    .fromLevels(0, 35518).toLevel(0)
    .fromLevels(35519, 65535).toLevel(1)
    .go();

// --- Step 5: trees, real density this time. TreeLayer is a NIBBLE (0-15) layer -- v1 used
// toLevel(1), ~7% density, which read as sparse/scattered/"terrible" individual trees rather
// than an actual forest. Using 12 (dense but not a solid wall) here. STILL WorldPainter's
// built-in procedural trees, not the custom TreeForge schematics -- see the note at the top.
var deciduousLayer = wp.getLayer().withName('Deciduous').go();
var pineLayer = wp.getLayer().withName('Pine').go();

// Full deciduous forest on temperate lowland (Grass)
var grassTerrainFilter = wp.createFilter().onlyOnTerrain(0).go();
wp.applyHeightMap(heightMap)
    .toWorld(world)
    .withFilter(grassTerrainFilter)
    .applyToLayer(deciduousLayer)
    .fromLevels(0, 65535).toLevel(12)
    .go();

// Sparse deciduous on Mediterranean lowland (Bare Grass) -- real Mediterranean vegetation is
// much less densely forested than temperate Europe, not zero.
var bareGrassTerrainFilter = wp.createFilter().onlyOnTerrain(1).go();
wp.applyHeightMap(heightMap)
    .toWorld(world)
    .withFilter(bareGrassTerrainFilter)
    .applyToLayer(deciduousLayer)
    .fromLevels(0, 65535).toLevel(3)
    .go();

// Dense pine on the highland band (Coarse Dirt terrain, 800-1800m)
var highlandTerrainFilter = wp.createFilter().onlyOnTerrain(3).go();
wp.applyHeightMap(heightMap)
    .toWorld(world)
    .withFilter(highlandTerrainFilter)
    .applyToLayer(pineLayer)
    .fromLevels(0, 65535).toLevel(12)
    .go();

wp.exportWorld(world)
    .toDirectory('agadir-world-export-v2')
    .go();
