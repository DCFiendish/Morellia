package net.nodisium.server

import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.anvil.AnvilLoader
import java.nio.file.Path

/**
 * Loads the Agadir Crisis map -- real elevation (SRTM15+) authored in WorldPainter at 1:750
 * scale, exported as a Minecraft 26.1-format Anvil world (see docs/HANDOFF.md for the full
 * pipeline). Chunks the loader has no data for fall through to whatever Generator the instance
 * was constructed with -- pass [StoneFlatTerrain.generator] for that.
 */
object AgadirWorld {
    // morellia-data/world is a full Minecraft world save folder (level.dat, data/, dimensions/,
    // session.lock) -- the export root produced by tools/agadir-mapgen/, copied in as-is.
    // Minestom's AnvilLoader(Path) resolves level.dat and dimensions/<namespace>/<value>/region
    // itself (confirmed by disassembling AnvilLoader.class), so PATH must be the world root, NOT
    // pre-flattened to the inner dimensions/minecraft/overworld folder -- an earlier version of
    // this comment (and of tools/agadir-mapgen/README.md's step 5) assumed the opposite and was
    // wrong; both are fixed now.
    //
    // Regenerate via tools/agadir-mapgen/ if this directory is ever missing (morellia-data/ is
    // gitignored -- this ~230MB+ world is not committed).
    //
    // MORELLIA_WORLD_PATH overrides this for fast iteration against a small test map (see
    // tools/agadir-mapgen/agadir-import-test.js) without touching the real world or needing a
    // separate Main-like entry point -- e.g.
    // MORELLIA_WORLD_PATH=morellia-data/test-world ./gradlew.bat :server:run
    val PATH: String = System.getenv("MORELLIA_WORLD_PATH") ?: "morellia-data/world"

    fun attach(instance: InstanceContainer) {
        instance.setChunkLoader(AnvilLoader(Path.of(PATH)))
    }
}
