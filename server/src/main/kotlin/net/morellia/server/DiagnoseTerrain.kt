package net.morellia.server

import net.minestom.server.MinecraftServer
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block
import net.minestom.server.world.DimensionType
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

fun main() {
    MinecraftServer.init()
    val worldPath = Path.of("""C:\Users\USER\AppData\Local\Temp\claude\C--Users-patri-Aechronis\7a0026cb-26d8-4015-850e-29acf1848a39\scratchpad\worldzip\patched_world""")
    val instance = MinecraftServer.getInstanceManager().createInstanceContainer(DimensionType.OVERWORLD)
    @Suppress("DEPRECATION")
    instance.chunkLoader = AnvilLoader(worldPath)

    // Load a spread of region-aligned chunk coordinates across the world to sample broadly,
    // not just one corner -- region files are 32x32 chunks, so step by regions.
    val regionsPresent =
        worldPath.resolve("region").toFile().listFiles { f -> f.name.endsWith(".mca") }
            ?.map { f ->
                val parts = f.name.split(".")
                parts[1].toInt() to parts[2].toInt()
            } ?: emptyList()
    println("Total region files: ${regionsPresent.size}")
    println("Region X range: ${regionsPresent.minOf { it.first }}..${regionsPresent.maxOf { it.first }}")
    println("Region Z range: ${regionsPresent.minOf { it.second }}..${regionsPresent.maxOf { it.second }}")

    // (2,-1) is confirmed (via direct Python/anvil-parser read of the raw .mca) to contain real
    // terrain at chunk-local (0,0): deepslate/tuff/granite/stone/grass_block. Check it first as a
    // ground-truth cross-check before trusting any broader random sample.
    val knownGoodRegion = 2 to -1
    val sampleRegions = (listOf(knownGoodRegion) + regionsPresent.shuffled(kotlin.random.Random(42))).distinct().take(80)
    val sampleChunks = sampleRegions.flatMap { (rx, rz) -> listOf(rx * 32 + 0 to rz * 32 + 0, rx * 32 + 16 to rz * 32 + 16) }
    val futures = mutableListOf<CompletableFuture<*>>()
    for ((cx, cz) in sampleChunks) {
        futures.add(instance.loadChunk(cx, cz))
    }
    var failures = 0
    for (f in futures) {
        try {
            f.join()
        } catch (e: Exception) {
            failures++
            println("LOAD FAILURE: ${e.message}")
        }
    }
    println("Chunks attempted: ${futures.size}, failures: $failures")

    // Targeted debug: chunk (64,-32), block-local (0,0), confirmed via direct Python/anvil-parser
    // read of the raw .mca to contain deepslate at y=-60.
    val debugChunk = instance.getChunk(64, -32)
    println("debug getChunk(64,-32) = $debugChunk")
    if (debugChunk != null) {
        println("debug chunk.minSection=${debugChunk.minSection} maxSection=${debugChunk.maxSection}")
    }
    println("debug getBlock(1024,-60,-512) = ${instance.getBlock(1024, -60, -512)}")
    println("debug getBlock(1024,70,-512) = ${instance.getBlock(1024, 70, -512)}")

    var nonAirBlocks = 0
    var totalChecked = 0
    val blockNames = mutableSetOf<String>()
    for ((cx, cz) in sampleChunks) {
        for (dx in 0..15 step 4) {
            for (dz in 0..15 step 4) {
                val x = cx * 16 + dx
                val z = cz * 16 + dz
                for (y in -60..100 step 10) {
                    totalChecked++
                    val b = instance.getBlock(x, y, z)
                    if (b != Block.AIR) {
                        nonAirBlocks++
                        blockNames.add(b.key().value())
                    }
                }
            }
        }
    }
    println("Blocks checked: $totalChecked, non-air: $nonAirBlocks")
    println("Distinct block types seen: ${blockNames.size}")
    println(blockNames.sorted().joinToString(", "))

    kotlin.system.exitProcess(0)
}
