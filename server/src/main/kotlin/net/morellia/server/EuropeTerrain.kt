package net.morellia.server

import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.Generator
import kotlin.math.roundToInt

/**
 * Real-world terrain generator driven by a preprocessed heightmap: NOAA ETOPO 2022 (15
 * arcsecond), cropped to Ireland/Portugal -> Greece, up to Denmark's north, reprojected to
 * EPSG:3035 (equal-area) at exactly 750m/pixel so one heightmap pixel = one world block with
 * no further resampling needed.
 *
 * Processing pipeline before compression:
 * 1. Light Gaussian smoothing (sigma 1.5 blocks) removes single-pixel elevation noise. Without
 *    this, the mountain-consolidation step below would inflate any noisy 1px spike into a
 *    plateau the size of its structuring element (grey closing of an isolated peak spreads it
 *    across the whole window before eroding back down) -- exactly what made every peak look like
 *    a sharp spike in an earlier pass.
 * 2. Morphological closing (dilate then erode, ~15km-radius circular structuring element),
 *    restricted to real elevation >1200m and ramped to full effect by 1800m, merges nearby
 *    peaks/ridges into one consolidated massif instead of a field of separate summits. It can't
 *    raise a peak above its neighborhood's existing max, so summit heights are essentially
 *    unchanged; it just fills in the saddles between them. Hills, plains, and the coastline (0m)
 *    are entirely below the threshold and untouched.
 * 3. Mountain prominence scaled down 30%: real elevation above 400m has its *excess* above 400m
 *    multiplied by 0.7 (so 400m stays 400m, and a 3960m peak becomes 400 + 3560*0.7 ~= 2892m).
 *    Scaling the excess rather than the raw value keeps the transition at 400m continuous (no
 *    cliff) and leaves ordinary hills/coastline alone -- only terrain that actually reads as
 *    "mountain" gets shorter.
 *
 * Vertical mapping (world -64..320, sea level at [SEA_LEVEL_Y]):
 * - Land (real elevation >= 0m): 1 block = 20m, so the highest peak in the crop lands well under
 *   the Y320 ceiling. Rounds toward *up* (ceil) so a barely-above-sea-level coastal point never
 *   quantizes down into the water band.
 * - Ocean (real elevation < 0m): compressed much harder, 1 block = 100m -- real bathymetry
 *   doesn't matter for a land-territory game. Rounds toward *down* (floor) for the opposite
 *   reason: naive nearest-block rounding let shallow water shallower than 50m real depth (the
 *   whole Dover Strait is 26-45m) round *up* to exactly SEA_LEVEL_Y, which the generator then
 *   read as dry beach instead of water -- Britain and France ended up land-connected. Floor for
 *   ocean / ceil for land guarantees the sign of the real elevation always survives quantization.
 *
 * Heightmap array is row-major, row 0 = northernmost world row (world Z=0), col 0 = westernmost
 * world column (world X=0) -- matches Minecraft's own -Z=north convention, so in-game north
 * points toward Denmark and south points toward Greece. See europe_heightmap_meta.json alongside
 * the source processing.
 *
 * Biome/vegetation is driven by a second real-world raster: WWF/RESOLVE "Ecoregions2017" (Dinerstein
 * et al. 2017), rasterized to the exact same 750m/pixel EPSG:3035 grid as the heightmap (same
 * pipeline: reproject then rasterize at our grid's affine transform, CC BY 4.0). This replaces an
 * earlier crude "3 bands by latitude" scheme -- a latitude band can't tell the Alps from the plains
 * around them, or hug the real (non-latitude-aligned) Mediterranean coastline. The real ecoregion
 * classification does both correctly: an area's tree species and ground cover follow what's
 * actually growing there, not just how far north it is.
 */
object EuropeTerrain {
    const val WIDTH = 4773
    const val HEIGHT = 4177
    const val SEA_LEVEL_Y = 64

    // Elevation bands, in block-Y (land only, landHeight >= SEA_LEVEL_Y). Roughly: sandy
    // coastline up to ~40m real elevation, forest up to ~1720m, bare alpine rock up to ~2500m,
    // permanent snow above that -- ordinary temperate-mountain altitudinal zonation.
    private const val BEACH_MAX_Y = SEA_LEVEL_Y + 2
    private const val FOREST_MIN_Y = BEACH_MAX_Y + 1
    private const val FOREST_MAX_Y = SEA_LEVEL_Y + 86
    private const val ALPINE_MAX_Y = SEA_LEVEL_Y + 125
    // Above ALPINE_MAX_Y: snow.

    // Deepslate below vanilla's usual transition point; everything above is stone. No ore veins
    // or caves here -- resource gathering runs on Vanilla's block-break drop tables, not on real
    // ore blocks placed in the world.
    private const val DEEPSLATE_Y = 0

    // Real WWF/RESOLVE biome categories present in this crop (see europe/biome.bin build script),
    // collapsed to the handful that matter for tree species / ground cover. treeChanceOutOf16 is
    // this biome's share of TREE_CELL cells that spawn a tree at all -- grassland/steppe and true
    // alpine/tundra are sparse-to-treeless in reality, not just a color swap on the same forest.
    private enum class Biome(val treeChanceOutOf16: Int) {
        BOREAL(5), // taiga
        CONIFER(4), // temperate conifer forest
        BROADLEAF(4), // temperate broadleaf/mixed forest
        MEDITERRANEAN(3), // mediterranean forest/scrub
        GRASSLAND(1), // temperate grassland/steppe/flooded grassland
        MONTANE(0), // alpine grassland/shrub -- real high-mountain vegetation, not forest
        TUNDRA(0),
        DESERT(1), // semi-arid/xeric shrubland
    }

    private fun biomeAt(x: Int, z: Int): Biome =
        when (rawBiomeCode(x, z)) {
            5 -> Biome.CONIFER
            6 -> Biome.BOREAL
            8, 9 -> Biome.GRASSLAND
            10 -> Biome.MONTANE
            11 -> Biome.TUNDRA
            12 -> Biome.MEDITERRANEAN
            13 -> Biome.DESERT
            else -> Biome.BROADLEAF // code 4, out-of-crop, and small unclassified slivers/islands
        }

    private const val TREE_CELL = 6
    // Widest possible canopy reach from a tree's root, across all species (dark oak's radius-4
    // canopy, and acacia's radius-3 canopy off-center by up to 1 block) -- a cheap per-cell reject
    // before doing the real per-species shape check.
    private const val MAX_CANOPY_RADIUS_SQ = 25

    private val heights: ShortArray by lazy {
        val bytes = requireNotNull(EuropeTerrain::class.java.getResourceAsStream("/europe/heightmap.bin")) {
            "Missing europe/heightmap.bin resource"
        }.use { it.readBytes() }
        require(bytes.size == WIDTH * HEIGHT * 2) { "Unexpected heightmap.bin size: ${bytes.size}" }
        val out = ShortArray(WIDTH * HEIGHT)
        for (i in out.indices) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
        }
        out
    }

    // Outside the painted region: flat sea level, so the world doesn't crash/glitch past the
    // crop's edges -- there's nothing there to explore, just open water.
    fun heightAt(worldX: Int, worldZ: Int): Int {
        if (worldX < 0 || worldX >= WIDTH || worldZ < 0 || worldZ >= HEIGHT) return SEA_LEVEL_Y
        return heights[worldZ * WIDTH + worldX].toInt()
    }

    private val biomeCodes: ByteArray by lazy {
        requireNotNull(EuropeTerrain::class.java.getResourceAsStream("/europe/biome.bin")) {
            "Missing europe/biome.bin resource"
        }.use { it.readBytes() }
    }

    // 0 = ocean/unclassified in the source data; biomeAt() already treats that as a safe
    // BROADLEAF fallback, so out-of-bounds (e.g. the 3x3 tree-cell search spilling past a crop
    // edge) can share the same code instead of needing its own branch.
    private fun rawBiomeCode(x: Int, z: Int): Int {
        if (x < 0 || x >= WIDTH || z < 0 || z >= HEIGHT) return 0
        return biomeCodes[z * WIDTH + x].toInt() and 0xFF
    }

    // Cheap integer hash (splitmix-ish), used for tree-cell density gating, jittering a tree's
    // position within its cell, ground-texture variation, and (via its high bits) picking
    // between the two broadleaf species -- one hash, several independent-looking uses.
    private fun hash(x: Int, z: Int): Int {
        var h = x * 374761393 + z * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return h
    }

    // Coarser-grained hash for texture *patches* rather than per-block speckle: rounds x/z down
    // to a patchSize grid before hashing, so ground-texture variation (podzol/coarse dirt/gravel
    // mixed into grass) forms small blotches like a real forest floor instead of isolated
    // single-block flecks scattered uniformly.
    private fun patchHash(
        x: Int,
        z: Int,
        patchSize: Int,
    ): Int = hash(Math.floorDiv(x, patchSize), Math.floorDiv(z, patchSize))

    // Smooth (bilinear-interpolated) value noise in 0..1, built on the same hash rather than
    // pulling in a noise library -- only needs continuous low-frequency variation, not any
    // particular spectral shape. `scale` is the cell size in blocks; `seed` decorrelates
    // different uses (tree-density clustering vs. snow-line creep) from each other.
    private fun noiseCellValue(
        cellX: Int,
        cellZ: Int,
        seed: Int,
    ): Double {
        val h = hash(cellX * 92_821 + seed, cellZ * 51_137 - seed)
        return (h and 0xFFFF) / 65535.0
    }

    private fun smoothNoise01(
        x: Int,
        z: Int,
        scale: Int,
        seed: Int,
    ): Double {
        val cellX = Math.floorDiv(x, scale)
        val cellZ = Math.floorDiv(z, scale)
        val fx = Math.floorMod(x, scale).toDouble() / scale
        val fz = Math.floorMod(z, scale).toDouble() / scale
        val sx = fx * fx * (3 - 2 * fx)
        val sz = fz * fz * (3 - 2 * fz)
        val v00 = noiseCellValue(cellX, cellZ, seed)
        val v10 = noiseCellValue(cellX + 1, cellZ, seed)
        val v01 = noiseCellValue(cellX, cellZ + 1, seed)
        val v11 = noiseCellValue(cellX + 1, cellZ + 1, seed)
        val vx0 = v00 + (v10 - v00) * sx
        val vx1 = v01 + (v11 - v01) * sx
        return vx0 + (vx1 - vx0) * sz
    }

    // How many blocks below ALPINE_MAX_Y the bare-rock band starts probabilistically mixing in
    // snow patches, so the treeline/snowline reads as a ragged, patchy creep instead of a single
    // ruler-straight ring at one exact elevation.
    private const val SNOWLINE_RAMP = 15

    private fun surfaceBlock(
        landHeight: Int,
        x: Int,
        z: Int,
    ): Block =
        when {
            landHeight < SEA_LEVEL_Y -> beachBlockAt(x, z) // underwater / lakebed
            landHeight <= BEACH_MAX_Y -> beachBlockAt(x, z)
            landHeight <= FOREST_MAX_Y -> groundBlockFor(biomeAt(x, z), x, z)
            landHeight <= ALPINE_MAX_Y -> alpineBlockAt(x, z, landHeight)
            else -> Block.SNOW_BLOCK
        }

    private fun beachBlockAt(
        x: Int,
        z: Int,
    ): Block = if (patchHash(x, z, 4) and 7 == 0) Block.GRAVEL else Block.SAND

    // Bare rock talus, textured by real position (x/z) rather than pure elevation -- the old
    // version keyed the gravel/stone split only on landHeight, which forms perfectly flat,
    // repeating horizontal bands around every single mountain at the same elevations (reads as
    // contour rings, not natural scree). Also probabilistically bleeds in snow patches as
    // elevation nears the true snow line, instead of a hard cutoff ring.
    private fun alpineBlockAt(
        x: Int,
        z: Int,
        landHeight: Int,
    ): Block {
        val rampStart = ALPINE_MAX_Y - SNOWLINE_RAMP
        if (landHeight > rampStart) {
            val t = (landHeight - rampStart).toDouble() / SNOWLINE_RAMP // 0..1, ramps toward the true line
            val noise = smoothNoise01(x, z, 6, seed = 42)
            if (noise < t * 0.85) return Block.SNOW_BLOCK
        }
        return if (patchHash(x, z, 3) and 3 == 0) Block.GRAVEL else Block.STONE
    }

    private fun groundBlockFor(
        biome: Biome,
        x: Int,
        z: Int,
    ): Block =
        when (biome) {
            Biome.BOREAL -> if (patchHash(x, z, 4) and 3 == 0) Block.PODZOL else Block.GRASS_BLOCK
            Biome.CONIFER -> if (patchHash(x, z, 4) and 7 == 0) Block.PODZOL else Block.GRASS_BLOCK
            // Leaf litter / bare dirt patches under dense canopy, and occasional moss in damper
            // hollows -- broadleaf is the single biggest biome in this crop, so it's the one that
            // most needed real ground variety instead of flat, uniform grass.
            Biome.BROADLEAF ->
                when {
                    patchHash(x, z, 5) and 7 == 0 -> Block.COARSE_DIRT
                    patchHash(x, z, 7) and 15 == 0 -> Block.MOSS_BLOCK
                    else -> Block.GRASS_BLOCK
                }
            // Dry patches in open grassland/steppe -- sparser and coarser-grained than forest
            // litter since there's no canopy to shed it.
            Biome.GRASSLAND -> if (patchHash(x, z, 6) and 15 == 0) Block.COARSE_DIRT else Block.GRASS_BLOCK
            Biome.MEDITERRANEAN -> if (patchHash(x, z, 4) and 3 == 0) Block.COARSE_DIRT else Block.GRASS_BLOCK
            Biome.DESERT -> if (patchHash(x, z, 4) and 1 == 0) Block.COARSE_DIRT else Block.GRASS_BLOCK
            Biome.MONTANE -> if (patchHash(x, z, 3) and 1 == 0) Block.GRAVEL else Block.GRASS_BLOCK
            Biome.TUNDRA -> if (patchHash(x, z, 3) and 1 == 0) Block.SNOW_BLOCK else Block.GRASS_BLOCK
        }

    // Gnaws irregular notches out of a canopy's outer ring so it reads as a hand-shaped blob
    // instead of a perfect circle -- combines treeHash (per-tree) with dx/dz (per-block-offset)
    // so different trees get different notch patterns instead of every tree at the same relative
    // offset being notched identically.
    private fun isEdgeNotched(
        dx: Int,
        dz: Int,
        distSq: Int,
        radius: Int,
        treeHash: Int,
    ): Boolean {
        // Never notch the exact center column, even for a radius-0/1 top layer -- otherwise a
        // canopy's peak can roll a hole punched straight through its own tip.
        if (distSq == 0) return false
        if (distSq < (radius - 1) * (radius - 1)) return false // interior: never notched
        return hash(dx * 131_071 + treeHash, dz * 131_071 - treeHash) and 7 == 0
    }

    // A branch is a single log block jutting sideways off the trunk with a small leaf tuft at its
    // tip -- breaks up the "perfectly straight trunk" silhouette that read as generic/vanilla.
    private fun branchBlockAt(
        dx: Int,
        dz: Int,
        y: Int,
        branchY: Int,
        branchDx: Int,
        branchDz: Int,
        log: Block,
        leaves: Block,
    ): Block? {
        if (dx == branchDx && dz == branchDz && y == branchY) return log
        val ldx = dx - branchDx
        val ldz = dz - branchDz
        if (y == branchY + 1 && ldx * ldx + ldz * ldz <= 1) return leaves
        return null
    }

    private enum class BroadleafSpecies(val log: Block, val leaves: Block) {
        OAK(Block.OAK_LOG, Block.OAK_LEAVES),
        BIRCH(Block.BIRCH_LOG, Block.BIRCH_LEAVES),
        DARK_OAK(Block.DARK_OAK_LOG, Block.DARK_OAK_LEAVES),
    }

    // Three genuinely different silhouettes, not one shape recolored: oak is the "classic" round
    // canopy with an occasional branch, birch is tall/thin with a small canopy and no branches,
    // dark oak is short and squat with a wide, blocky canopy (real vanilla dark oaks *are*
    // flat-topped -- that one's intentional). Trunk height, canopy radius, and branch placement
    // all vary per tree via treeHash, so no two trees of the same species are identical clones
    // either.
    //
    // The trunk's own column only short-circuits for the log itself (rootGround+1..topY); above
    // that it falls through to the same per-layer canopy check every other column uses, so the
    // canopy actually caps the trunk instead of leaving a hole punched through its own peak.
    private fun broadleafBlockAt(
        dx: Int,
        dz: Int,
        y: Int,
        rootGround: Int,
        treeHash: Int,
    ): Block? {
        val species = BroadleafSpecies.entries[((treeHash ushr 20) and 0xFF) % 3]
        val trunkH =
            when (species) {
                BroadleafSpecies.OAK -> 4 + ((treeHash ushr 4) and 3) // 4..7
                BroadleafSpecies.BIRCH -> 5 + ((treeHash ushr 4) and 3) // 5..8
                BroadleafSpecies.DARK_OAK -> 3 + ((treeHash ushr 4) and 1) // 3..4
            }
        val topY = rootGround + trunkH
        val baseRadius =
            when (species) {
                BroadleafSpecies.OAK -> 2 + ((treeHash ushr 6) and 1) // 2..3
                BroadleafSpecies.BIRCH -> 2
                BroadleafSpecies.DARK_OAK -> 3 + ((treeHash ushr 6) and 1) // 3..4
            }
        val hasBranch = species != BroadleafSpecies.BIRCH && (treeHash ushr 7) and 3 == 0
        val branchDx = if ((treeHash ushr 9) and 1 == 0) 1 else -1
        val branchDz = if ((treeHash ushr 10) and 1 == 0) 1 else -1
        val branchY = topY - trunkH / 2

        if (dx == 0 && dz == 0 && y in (rootGround + 1)..topY) return species.log
        if (hasBranch) {
            branchBlockAt(dx, dz, y, branchY, branchDx, branchDz, species.log, species.leaves)?.let { return it }
        }
        val layerFromTop = topY - y
        if (layerFromTop !in -1..2) return null
        val radius =
            if (species == BroadleafSpecies.DARK_OAK) {
                if (layerFromTop <= 0) baseRadius - 1 else baseRadius
            } else {
                // Real taper instead of a two-tier plateau: narrow point, then widen down to
                // baseRadius, then narrow again at the very bottom for a rounded/oval canopy.
                when (layerFromTop) {
                    -1 -> 1
                    0 -> baseRadius - 1
                    1 -> baseRadius
                    else -> baseRadius - 1
                }
            }
        val distSq = dx * dx + dz * dz
        if (distSq > radius * radius) return null
        if (isEdgeNotched(dx, dz, distSq, radius, treeHash)) return null
        return species.leaves
    }

    // Tall, narrow, multi-tier conical canopy -- a conifer silhouette, clearly distinct from the
    // broadleaf shapes. Height and canopy width both vary per tree.
    private fun spruceBlockAt(
        dx: Int,
        dz: Int,
        y: Int,
        rootGround: Int,
        treeHash: Int,
    ): Block? {
        val trunkH = 6 + ((treeHash ushr 8) and 3) // 6..9
        val topY = rootGround + trunkH
        if (dx == 0 && dz == 0) {
            return when {
                y in (rootGround + 1)..topY -> Block.SPRUCE_LOG
                y == topY + 1 -> Block.SPRUCE_LEAVES
                else -> null
            }
        }
        val canopyBase = rootGround + 2
        if (y < canopyBase || y > topY) return null
        val levelsFromTop = topY - y
        val maxRadius = 2 + ((treeHash ushr 10) and 1) // 2..3
        val radius = if (levelsFromTop <= 1) 1 else if (levelsFromTop <= 4) 2 else maxRadius
        val distSq = dx * dx + dz * dz
        if (distSq > radius * radius) return null
        if (isEdgeNotched(dx, dz, distSq, radius, treeHash)) return null
        return Block.SPRUCE_LEAVES
    }

    // Short trunk topped with one wide, flat canopy layer -- an umbrella silhouette for sparse
    // Mediterranean scrubland, clearly distinct from both other families. Canopy is offset
    // slightly off-center per tree for a "windswept" asymmetric look instead of a dead-centered
    // umbrella every time.
    private fun acaciaBlockAt(
        dx: Int,
        dz: Int,
        y: Int,
        rootGround: Int,
        treeHash: Int,
    ): Block? {
        val trunkH = 3 + ((treeHash ushr 11) and 3) // 3..6
        val topY = rootGround + trunkH
        if (dx == 0 && dz == 0 && y in (rootGround + 1)..topY) return Block.ACACIA_LOG
        if (y !in topY..(topY + 1)) return null
        val offsetDx = ((treeHash ushr 13) and 1) - if ((treeHash ushr 14) and 1 == 0) 0 else 1
        val offsetDz = ((treeHash ushr 15) and 1) - if ((treeHash ushr 16) and 1 == 0) 0 else 1
        val radius = 2 + ((treeHash ushr 17) and 1) // 2..3
        val cdx = dx - offsetDx
        val cdz = dz - offsetDz
        val distSq = cdx * cdx + cdz * cdz
        if (distSq > radius * radius) return null
        // isEdgeNotched treats (cdx,cdz)==0 as protected interior, but that's the *offset*
        // canopy's own center, not the trunk -- when the canopy is shifted off-center (the
        // "windswept" effect below), the column directly above the trunk (dx=dz=0) can land on
        // the notch-eligible ring instead, occasionally punching a hole right where it's most
        // visible. The trunk position is always within radius here (max offset magnitude is
        // sqrt(2), well under the minimum radius of 2), so just exempt it from notching outright.
        val isTrunkColumn = dx == 0 && dz == 0
        if (!isTrunkColumn && isEdgeNotched(cdx, cdz, distSq, radius, treeHash)) return null
        return Block.ACACIA_LEAVES
    }

    // Deterministic jittered-grid tree scatter: each TREE_CELL x TREE_CELL cell either has no
    // tree or exactly one, at a hashed position inside the cell -- avoids the overhead (and the
    // undo/session machinery) of a real structure-placement pass while still giving an irregular,
    // non-repeating forest. Checks the 3x3 neighborhood of cells so canopies can overhang a cell
    // boundary. Density and species are both picked from the root's own biome, not the querying
    // column's, so a single tree is never a chimera of two biomes.
    //
    // Canopy shape is computed relative to the tree's *root* elevation, but a querying column a
    // few blocks away can easily sit on higher ground (the uphill side of any slope) than the
    // root. Rejecting every canopy block that falls at/below that column's own local terrain --
    // the old behavior -- silently deletes whichever side of the canopy faces uphill, which is
    // exactly the "leaves only on half the tree" bug: most of Europe's forested elevation band is
    // hillside, not flat ground. Real tree generators (including BuildTheEarth's TerraPlusMinus)
    // just stamp the canopy and let it embed into adjacent terrain, so we only bail out on the
    // pathological case (terrain more than a trunk-height above this block, i.e. a near-vertical
    // drop) rather than any terrain at all being higher.
    // Tree-cell scale (TREE_CELL, ~6 blocks) is deliberately small so individual tree positions
    // look irregular; layering a much larger-scale noise field on top of the flat per-biome
    // density makes whole *regions* denser or sparser -- real forest patches and clearings,
    // rather than a uniform "medium" density smeared evenly across an entire ecoregion.
    private const val DENSITY_CLUSTER_SCALE = 64

    // Treeline thinning: density ramps down to 0 over the last this-many blocks below
    // FOREST_MAX_Y instead of cutting off abruptly right at the elevation band's edge.
    private const val TREELINE_RAMP = 15

    private fun treeDensityOutOf16(
        biome: Biome,
        rootX: Int,
        rootZ: Int,
        rootGround: Int,
    ): Int {
        val base = biome.treeChanceOutOf16
        if (base == 0) return 0
        val cluster = smoothNoise01(rootX, rootZ, DENSITY_CLUSTER_SCALE, seed = 777)
        val clusterMultiplier = 0.25 + cluster * 1.75 // 0.25x (near-clearing) .. 2.0x (dense patch)
        val treelineFactor =
            if (rootGround > FOREST_MAX_Y - TREELINE_RAMP) {
                (FOREST_MAX_Y - rootGround).toDouble() / TREELINE_RAMP
            } else {
                1.0
            }
        return (base * clusterMultiplier * treelineFactor).roundToInt().coerceIn(0, 16)
    }

    private fun treeBlockAt(
        x: Int,
        y: Int,
        z: Int,
        localGround: Int,
    ): Block? {
        if (localGround - y > 8) return null
        val cellX = Math.floorDiv(x, TREE_CELL)
        val cellZ = Math.floorDiv(z, TREE_CELL)
        for (ccx in cellX - 1..cellX + 1) {
            for (ccz in cellZ - 1..cellZ + 1) {
                val h = hash(ccx, ccz)
                val rootX = ccx * TREE_CELL + ((h ushr 4) and 0xFF) % TREE_CELL
                val rootZ = ccz * TREE_CELL + ((h ushr 12) and 0xFF) % TREE_CELL
                val biome = biomeAt(rootX, rootZ)
                val rootGround = heightAt(rootX, rootZ)
                if (rootGround < FOREST_MIN_Y || rootGround > FOREST_MAX_Y) continue
                if ((h and 0xF) >= treeDensityOutOf16(biome, rootX, rootZ, rootGround)) continue

                val dx = x - rootX
                val dz = z - rootZ
                if (dx * dx + dz * dz > MAX_CANOPY_RADIUS_SQ) continue

                val block =
                    when (biome) {
                        Biome.BOREAL, Biome.CONIFER -> spruceBlockAt(dx, dz, y, rootGround, h)
                        Biome.MEDITERRANEAN, Biome.DESERT -> acaciaBlockAt(dx, dz, y, rootGround, h)
                        Biome.BROADLEAF, Biome.GRASSLAND -> broadleafBlockAt(dx, dz, y, rootGround, h)
                        Biome.MONTANE, Biome.TUNDRA -> null
                    }
                if (block != null) return block
            }
        }
        return null
    }

    // Single-block undergrowth (grass/ferns/flowers/dead bush), one roll per column at ground
    // level -- unlike trees this needs no multi-cell search since it's always exactly 1 block,
    // so a per-column hash is enough for an irregular, non-repeating scatter. Offset the hash
    // input so this doesn't correlate 1:1 with the ground-texture hash at the same column (that
    // would make every podzol/dry-dirt patch also be the undergrowth boundary). Biomes that are
    // realistically bare (montane scree, tundra) stay sparse or empty; only single-tall plants are
    // used since double-tall plants (tall_grass, sunflower, ...) need paired upper/lower
    // blockstates this generator doesn't set up.
    private fun undergrowthBlockAt(
        x: Int,
        z: Int,
        biome: Biome,
    ): Block? {
        val roll = hash(x + 91031, z - 91031) and 0xFF
        return when (biome) {
            Biome.BOREAL, Biome.CONIFER ->
                when {
                    roll < 40 -> Block.FERN
                    roll < 70 -> Block.SHORT_GRASS
                    else -> null
                }
            Biome.BROADLEAF ->
                when {
                    roll < 60 -> Block.SHORT_GRASS
                    roll < 68 -> Block.DANDELION
                    roll < 74 -> Block.POPPY
                    roll < 78 -> Block.CORNFLOWER
                    roll < 82 -> Block.OXEYE_DAISY
                    else -> null
                }
            Biome.GRASSLAND ->
                when {
                    roll < 110 -> Block.SHORT_GRASS
                    roll < 122 -> Block.DANDELION
                    roll < 132 -> Block.POPPY
                    roll < 140 -> Block.ALLIUM
                    roll < 148 -> Block.CORNFLOWER
                    roll < 155 -> Block.OXEYE_DAISY
                    else -> null
                }
            Biome.MEDITERRANEAN ->
                when {
                    roll < 30 -> Block.SHORT_DRY_GRASS
                    roll < 42 -> Block.DEAD_BUSH
                    roll < 46 -> Block.POPPY
                    else -> null
                }
            Biome.DESERT ->
                when {
                    roll < 20 -> Block.DEAD_BUSH
                    roll < 30 -> Block.SHORT_DRY_GRASS
                    else -> null
                }
            Biome.MONTANE -> if (roll < 25) Block.SHORT_GRASS else null
            Biome.TUNDRA -> if (roll < 15) Block.SHORT_GRASS else null
        }
    }

    // Rare forest-floor accents beyond ground-level plants: boulders, dead-tree stumps, and
    // mushrooms. Checked before undergrowth (same y == landHeight+1 slot) and much rarer -- these
    // are occasional focal points, not a texture layer. Uses its own hash offset so it doesn't
    // correlate with either the ground-texture or undergrowth rolls at the same column.
    private fun forestFloorDecorationAt(
        x: Int,
        z: Int,
        biome: Biome,
    ): Block? {
        val roll = hash(x + 40_507, z - 40_507) and 0xFF
        return when (biome) {
            Biome.BOREAL, Biome.CONIFER ->
                when {
                    roll < 3 -> Block.BROWN_MUSHROOM
                    roll < 5 -> Block.RED_MUSHROOM
                    roll < 8 -> Block.MOSSY_COBBLESTONE // boulder
                    roll < 10 -> Block.SPRUCE_LOG // stump
                    else -> null
                }
            Biome.BROADLEAF, Biome.GRASSLAND ->
                when {
                    roll < 2 -> Block.BROWN_MUSHROOM
                    roll < 5 -> Block.COBBLESTONE // boulder
                    roll < 7 -> Block.OAK_LOG // stump
                    else -> null
                }
            Biome.MEDITERRANEAN, Biome.DESERT -> if (roll < 6) Block.STONE else null // sun-bleached boulder
            Biome.MONTANE -> if (roll < 15) Block.STONE else null
            Biome.TUNDRA -> if (roll < 8) Block.STONE else null
        }
    }

    // Widest a canopy can reach above its own root's ground (spruce's center-top leaf, the
    // tallest case: trunkH up to 9, plus the capping leaf) -- bounds how far above FOREST_MAX_Y a
    // root planted right at the top of the forest band could still paint a block, so the tree
    // check below only runs across a real forest-elevation window instead of every y level.
    private const val TREE_SCAN_MAX_Y = FOREST_MAX_Y + 10

    val generator = Generator { unit ->
        unit.modifier().setAll { x, y, z ->
            val landHeight = heightAt(x, z)
            // Checked before the terrain-fill branches, and allowed to win over them: a canopy
            // block belongs to a specific tree regardless of what the *querying* column's own
            // terrain looks like, so it must be able to override a neighboring column's
            // higher/lower ground rather than being silently clipped by it (see treeBlockAt).
            val tree = if (y in FOREST_MIN_Y..TREE_SCAN_MAX_Y) treeBlockAt(x, y, z, landHeight) else null
            tree ?: when {
                y < landHeight - 4 -> if (y < DEEPSLATE_Y) Block.DEEPSLATE else Block.STONE
                y < landHeight -> if (landHeight >= SEA_LEVEL_Y) Block.DIRT else Block.SAND
                y == landHeight -> surfaceBlock(landHeight, x, z)
                y <= SEA_LEVEL_Y -> Block.WATER
                else ->
                    if (y == landHeight + 1 && landHeight in FOREST_MIN_Y..FOREST_MAX_Y) {
                        val biome = biomeAt(x, z)
                        forestFloorDecorationAt(x, z, biome) ?: undergrowthBlockAt(x, z, biome)
                    } else {
                        null
                    }
            } ?: Block.AIR
        }
    }
}
