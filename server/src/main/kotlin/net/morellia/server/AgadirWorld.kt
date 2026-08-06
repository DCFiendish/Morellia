package net.morellia.server

import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.anvil.AnvilLoader
import java.nio.file.Path

/**
 * Loads the Agadir Crisis map -- Britain through Morocco, trimmed from the user's real-world
 * Europe terrain download down to the confirmed play area (region x -8192..2559, z -5632..3071;
 * see research-todo/04-world-and-data-architecture.md). Chunks the loader has no data for (i.e.
 * anything outside that trimmed box) fall through to whatever Generator the instance was
 * constructed with -- pass [StoneFlatTerrain.generator] for that so out-of-bounds always renders
 * as flat stone instead of void or a crash, per the same pattern [EuropeTerrain] used for its own
 * out-of-crop fallback.
 */
object AgadirWorld {
    const val PATH = "morellia-data/world"

    fun attach(instance: InstanceContainer) {
        instance.setChunkLoader(AnvilLoader(Path.of(PATH)))
    }
}
