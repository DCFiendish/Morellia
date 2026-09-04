package net.nodisium.server

import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.Instance

// Every nibble set to 15 (max light) — 2048 bytes per section, 2 light values per byte.
private val FULL_LIGHT = ByteArray(2048) { 0xFF.toByte() }

/**
 * Statically fills every section with max sky/block light once at generation instead of
 * running a real light engine (LightingChunk) — cheaper, and unlike sending no light data
 * at all (Minestom's actual default), this renders bright for every client, not just ones
 * running a fullbright mod that ignores server light data.
 */
class FullbrightChunk(instance: Instance, chunkX: Int, chunkZ: Int) : DynamicChunk(instance, chunkX, chunkZ) {
    override fun onGenerate() {
        super.onGenerate()
        for (section in minSection until maxSection) {
            val s = getSection(section)
            s.skyLight().set(FULL_LIGHT)
            s.blockLight().set(FULL_LIGHT)
        }
    }
}
