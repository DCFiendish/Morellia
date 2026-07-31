/**
 * Cache broken block locations used for hidden ore
 * Used to avoid exploits of placing and re-breaking blocks
 * to get ore.
 *
 * Object is wrapper around hashset, client should only add
 * blocks into cache (removing handled internally), and use
 * contains to check if block exists
 *
 * Size input: number of blocks to cache before overwriting
 */

package net.aechronis.nodes.objects

import com.google.gson.JsonParser
import net.minestom.server.coordinate.BlockVec
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

class OreBlockCache(val maxSize: Int) {
    private val cache: MutableSet<BlockVec> = Collections.newSetFromMap(object : LinkedHashMap<BlockVec, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BlockVec, Boolean>): Boolean = this.size > maxSize
    })

    fun add(block: BlockVec) {
        this.cache.add(block)
    }

    fun contains(block: BlockVec): Boolean = cache.contains(block)

    // Was entirely in-memory: a restart, or just hitting maxSize once, silently forgot which
    // blocks had already been mined and reopened the place-then-rebreak ore dupe. Persisting
    // this alongside the rest of Nodes' world state closes that gap.
    fun save(path: Path) {
        val json = StringBuilder("[")
        for ((i, block) in cache.withIndex()) {
            if (i > 0) json.append(",")
            json.append("[${block.blockX},${block.blockY},${block.blockZ}]")
        }
        json.append("]")
        Files.createDirectories(path.parent)
        Files.writeString(path, json.toString())
    }

    fun load(path: Path) {
        cache.clear()
        if (!Files.exists(path)) return
        val json = JsonParser.parseReader(FileReader(path.toString())).asJsonArray
        for (entry in json) {
            val coords = entry.asJsonArray
            cache.add(BlockVec(coords[0].asInt, coords[1].asInt, coords[2].asInt))
        }
    }
}
