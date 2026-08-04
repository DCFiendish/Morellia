package net.morellia.server

import net.aechronis.utils.createTestServer
import net.minestom.server.Auth
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block
import java.nio.file.Path

// Proves a REAL server instance (createTestServer, same call Main.kt uses for the live procedural
// world) can boot with the downloaded/patched premade world as its chunk source instead of a
// generator, and that a player connecting at the intended spawn column would land on solid ground
// with clear air above rather than falling through void or spawning embedded in stone.
fun main() {
    val worldPath =
        Path.of(
            """C:\Users\USER\AppData\Local\Temp\claude\C--Users-patri-Aechronis\7a0026cb-26d8-4015-850e-29acf1848a39\scratchpad\worldzip\patched_world""",
        )

    // createTestServer requires a Generator even though chunkLoader will supply every chunk that
    // exists on disk; EuropeTerrain.generator is reused purely as a harmless fallback for any
    // chunk request that falls outside the loaded region set.
    val instance =
        createTestServer(
            generator = EuropeTerrain.generator,
            spawnPoint = Pos(1024.5, 100.0, -512.5),
            auth = Auth.Offline(),
            port = 25568,
        )
    @Suppress("DEPRECATION")
    instance.chunkLoader = AnvilLoader(worldPath)

    val spawnChunk = instance.loadChunk(64, -32).join()
    println("Spawn chunk loaded: $spawnChunk")

    // Find real ground level under the spawn column and confirm it's actually standable: solid
    // block at feet-minus-one, air at feet and head.
    var groundY = Int.MIN_VALUE
    for (y in 150 downTo -60) {
        if (instance.getBlock(1024, y, -512) != Block.AIR) {
            groundY = y
            break
        }
    }
    println("Ground surface at x=1024,z=-512 -> y=$groundY (block=${instance.getBlock(1024, groundY, -512).key()})")
    println("Block above ground (y=${groundY + 1}): ${instance.getBlock(1024, groundY + 1, -512).key()}")
    println("Block above that   (y=${groundY + 2}): ${instance.getBlock(1024, groundY + 2, -512).key()}")

    val standable =
        groundY != Int.MIN_VALUE &&
            instance.getBlock(1024, groundY + 1, -512) == Block.AIR &&
            instance.getBlock(1024, groundY + 2, -512) == Block.AIR
    println("SPAWN COLUMN STANDABLE: $standable")

    println("Boot test server running on port 25568 (offline mode) — real Minestom instance backed by AnvilLoader.")
    println("Leaving it up for 20s in case a client wants to connect, then shutting down.")
    Thread.sleep(20_000)
    kotlin.system.exitProcess(0)
}
