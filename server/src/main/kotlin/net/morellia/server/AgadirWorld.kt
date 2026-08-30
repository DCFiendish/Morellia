package net.morellia.server

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
    // TEMP: pointing directly at the scratchpad export for the first boot test. Move under
    // morellia-data/world (matching the old convention) once this is confirmed working.
    // WorldPainter 2.27's Minecraft-26.1-format export uses the newer per-dimension layout
    // (region/ lives under dimensions/minecraft/overworld/, not at the world root) -- Minestom's
    // AnvilLoader wants a folder that directly contains region/, so point one level deeper than
    // the actual world root.
    // Smoothed + vegetated pass: real SRTM15+ elevation (smoothed), WorldPainter forest layers
    // (Deciduous below 800m, Pine 800-1800m, elevation as a climate proxy) and Grass terrain's
    // own default flower/tall-grass population. Verified all 64 distinct block names in the
    // export are valid modern IDs (no other stale legacy names beyond the grass fix, which is
    // reapplied on every WorldPainter export -- see tools/agadir-mapgen/README.md).
    //
    // Regenerate via tools/agadir-mapgen/ if this directory is ever missing (morellia-data/ is
    // gitignored -- this ~230MB world is not committed). world/ itself is the *dimension* root
    // Minestom's AnvilLoader expects (contains region/ directly), one level inside WorldPainter's
    // actual world export -- see the mapgen README for why.
    const val PATH = "morellia-data/world"

    fun attach(instance: InstanceContainer) {
        instance.setChunkLoader(AnvilLoader(Path.of(PATH)))
    }
}
