/**
 * Cache broken block locations used for hidden ore
 * Used to avoid exploits of placing and re-breaking blocks
 * to get ore.
 *
 * Object is wrapper around hashset, client should only add
 * blocks into cache (removing handled internally), and use
 * contains to check if block exists
 */

package net.aechronis.nodes.objects

import com.google.gson.JsonParser
import net.minestom.server.coordinate.BlockVec
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class OreBlockCache {
    // Used to be a fixed-size LRU (LinkedHashMap + removeEldestEntry) capped at 2000 entries.
    // This set records positions that must never trigger hidden ore again -- once a real server
    // exceeds the cap (any sustained mining does, quickly), the oldest entries silently evict and
    // those exact positions become farmable again via place-then-rebreak. Correctness here
    // requires the ledger to never forget, so there's no cap; ConcurrentHashMap.newKeySet() also
    // makes add()/contains() safe from the per-chunk worker threads block break/place events fire on.
    private val cache: MutableSet<BlockVec> = ConcurrentHashMap.newKeySet()

    fun add(block: BlockVec) {
        this.cache.add(block)
    }

    fun contains(block: BlockVec): Boolean = cache.contains(block)

    // Was entirely in-memory: a restart silently forgot which blocks had already been mined and
    // reopened the place-then-rebreak ore dupe. Persisting this alongside the rest of Nodes'
    // world state closes that gap.
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
