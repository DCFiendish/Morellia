package net.morellia.server

import net.minestom.server.MinecraftServer
import net.minestom.server.instance.block.Block
import net.minestom.server.world.DimensionType

// One-shot diagnostic: finds real ground elevation at the war-flag load-test's border chunks
// (territories 440/275) under the actual EuropeTerrain generator, so LoadTestBots/rust-mc-bot can
// target real solid ground instead of a hardcoded flat-world assumption that doesn't hold here.
fun main() {
    MinecraftServer.init()
    val instance = MinecraftServer.getInstanceManager().createInstanceContainer(DimensionType.OVERWORLD)
    instance.setGenerator(EuropeTerrain.generator)

    val chunks275 = listOf(142 to 128, 143 to 123, 143 to 124, 143 to 125, 143 to 126, 143 to 127, 144 to 122)
    val chunks440 = listOf(143 to 128, 144 to 123, 144 to 124, 144 to 125, 144 to 126, 144 to 127, 145 to 122)

    for ((label, chunks) in listOf("275 (TownA target)" to chunks275, "440 (TownB target)" to chunks440)) {
        println("--- $label ---")
        for ((cx, cz) in chunks) {
            instance.loadChunk(cx, cz).join()
            val x = cx * 16 + 8
            val z = cz * 16 + 8
            var groundY = Int.MIN_VALUE
            for (y in 200 downTo -60) {
                val block = instance.getBlock(x, y, z)
                val name = block.key().value()
                if (block == Block.AIR || !block.isSolid) continue
                if (name.endsWith("_leaves") || name.endsWith("_log") || name.endsWith("_wood")) continue
                groundY = y
                break
            }
            println("chunk ($cx,$cz) block ($x,$z) -> ground y=$groundY block=${instance.getBlock(x, groundY, z).key()}")
        }
    }
    kotlin.system.exitProcess(0)
}
