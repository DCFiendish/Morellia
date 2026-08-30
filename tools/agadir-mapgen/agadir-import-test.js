// Small fast-iteration test map -- NOT the real Agadir Crisis map. Crops a real 800x800px
// (~360x360km, exports to ~480x480 blocks) slice of the same downloaded SRTM15+ data covering
// the Adriatic coast rising into the Dolomites (44.5-47.83N, 10.67-14E) -- chosen for genuine
// elevation variety (coast near sea level up to ~3200m peaks) in a small, fast-to-regenerate
// area, not for geographic/historical accuracy to the real Agadir Crisis theme.
//
// Exercises the exact same custom-object-layer pipeline agadir-import-v2.js will eventually use
// for the real map: treeforge-oak.layer and treeforge-pine.layer (built via MakeLayer.java from
// TreeForge .schem files) plus Lerfing's community-layers/ (palm, taiga). Purpose: validate tree
// density/placement/look before spending a full regen cycle on the real 5880x6120 map.
//
// Run with: wpscript agadir-import-test.js
var heightMap = wp.getHeightMap().fromFile('test-crop-heightmap.png').go();
var latMap = wp.getHeightMap().fromFile('test-crop-latitude-mask.png').go();
var noiseMap = wp.getHeightMap().fromFile('test-crop-texture-noise.png').go();
var mapFormat = wp.getMapFormat().withId('org.pepsoft.anvil.26.1').go();

var world = wp.createWorld()
    .fromHeightMap(heightMap)
    .scale(60)
    .fromLevels(0, 65535).toLevels(45, 210)
    .withWaterLevel(64)
    .withMapFormat(mapFormat)
    .withLowerBuildLimit(-64)
    .withUpperBuildLimit(320)
    .go();

// --- Step 1: base terrain by elevation (same bands as agadir-import-v2.js) ---
wp.applyHeightMap(heightMap)
    .toWorld(world)
    .applyToTerrain()
    .fromLevels(0, 1393).toTerrain(36)      // Beaches
    .fromLevels(1394, 12288).toTerrain(0)   // Grass (lowland)
    .fromLevels(12289, 25940).toTerrain(3)  // Coarse Dirt (highland)
    .fromLevels(25941, 65535).toTerrain(75) // Stone Mix (peaks)
    .go();

// --- Step 2: latitude-refined lowland (same threshold as v2, for mechanism parity --
// this crop's actual latitude band means little of it will hit the "Mediterranean" side) ---
var lowlandFilter = wp.createFilter().onlyOnTerrain(0).go();
wp.applyHeightMap(latMap)
    .toWorld(world)
    .withFilter(lowlandFilter)
    .applyToTerrain()
    .fromLevels(0, 10286).toTerrain(1)
    .fromLevels(10287, 65535).toTerrain(0)
    .go();

// --- Step 3: noise-patched texture variety (same as v2) ---
var grassFilter = wp.createFilter().onlyOnTerrain(0).go();
wp.applyHeightMap(noiseMap)
    .toWorld(world)
    .withFilter(grassFilter)
    .applyToTerrain()
    .fromLevels(57500, 65535).toTerrain(2)
    .go();

var bareGrassFilter = wp.createFilter().onlyOnTerrain(1).go();
wp.applyHeightMap(noiseMap)
    .toWorld(world)
    .withFilter(bareGrassFilter)
    .applyToTerrain()
    .fromLevels(57500, 65535).toTerrain(5)
    .go();

var highlandFilter = wp.createFilter().onlyOnTerrain(3).go();
wp.applyHeightMap(noiseMap)
    .toWorld(world)
    .withFilter(highlandFilter)
    .applyToTerrain()
    .fromLevels(57500, 65535).toTerrain(34)
    .go();

// --- Step 4: Frost above ~2500m ---
var frostLayer = wp.getLayer().withName('Frost').go();
wp.applyHeightMap(heightMap)
    .toWorld(world)
    .applyToLayer(frostLayer)
    .fromLevels(0, 35518).toLevel(0)
    .fromLevels(35519, 65535).toLevel(1)
    .go();

// --- Step 5: REAL custom-object tree layers, not WorldPainter's built-in Deciduous/Pine ---
var oakLayer = wp.getLayer().fromFile('treeforge-oak.layer').go();
var pineLayer = wp.getLayer().fromFile('treeforge-pine.layer').go();
var palmLayer = wp.getLayer().fromFile('community-layers/aPalm Trees-boosted.layer').go();
var taigaLayer = wp.getLayer().fromFile('community-layers/aTaiga.layer').go();

// Oak forest on temperate lowland (Grass)
var grassTerrainFilter = wp.createFilter().onlyOnTerrain(0).go();
wp.applyLayer(oakLayer)
    .toWorld(world)
    .withFilter(grassTerrainFilter)
    .toLevel(15)
    .applyToSurface()
    .setAlways()
    .go();

// Palm on Mediterranean-band lowland (Bare Grass) -- mechanism test, not a geography claim for
// this specific crop (Dolomites foothills aren't palm country in reality).
var bareGrassTerrainFilter = wp.createFilter().onlyOnTerrain(1).go();
wp.applyLayer(palmLayer)
    .toWorld(world)
    .withFilter(bareGrassTerrainFilter)
    .toLevel(15)
    .applyToSurface()
    .setAlways()
    .go();

// Pine on the lower highland band (Coarse Dirt, up to ~2000m)
var highlandLowFilter = wp.createFilter().onlyOnTerrain(3).go();
wp.applyHeightMap(heightMap)
    .toWorld(world)
    .withFilter(highlandLowFilter)
    .applyToLayer(pineLayer)
    .fromLevels(0, 28900).toLevel(15)
    .fromLevels(28901, 65535).toLevel(0)
    .go();

// Taiga on the upper highland band (Coarse Dirt, ~2000m up to the Frost line) -- second
// species so the pine/taiga transition itself is visible in-game.
wp.applyHeightMap(heightMap)
    .toWorld(world)
    .withFilter(highlandLowFilter)
    .applyToLayer(taigaLayer)
    .fromLevels(0, 28900).toLevel(0)
    .fromLevels(28901, 65535).toLevel(15)
    .go();

wp.exportWorld(world)
    .toDirectory('test-map-export')
    .go();

print('done');
