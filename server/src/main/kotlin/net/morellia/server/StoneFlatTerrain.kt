package net.morellia.server

import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.Generator

/**
 * Solid stone superflat, top surface at [SURFACE_Y] -- used in place of [EuropeTerrain] for the
 * war-flag load test so every attack-target chunk has an identical, predictable ground height
 * (no trees/leaves/biome variation to trip up ground-detection), isolating the flag-attack logic
 * itself from terrain as a variable.
 */
object StoneFlatTerrain {
    const val SURFACE_Y = 64

    val generator = Generator { unit ->
        unit.modifier().setAll { _, y, _ ->
            if (y <= SURFACE_Y) Block.STONE else Block.AIR
        }
    }
}
