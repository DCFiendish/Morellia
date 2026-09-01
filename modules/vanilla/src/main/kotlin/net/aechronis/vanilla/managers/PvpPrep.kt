package net.aechronis.vanilla.managers

import net.aechronis.vanilla.Vanilla
import net.aechronis.vanilla.listeners.PvpPrepListener
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance

/**
 * No-damage/no-break boxes around warp landing spots. Only cancels PlayerBlockBreakEvent and
 * EntityDamageEvent -- doesn't touch Nodes' territory-claim build protection, which already blocks
 * breaking independently and never touches damage at all, so the two systems just stack (both may
 * cancel a break in the same claimed chunk; neither un-cancels, so there's no ordering hazard).
 */
object PvpPrep {
    fun init() {
        require(
            Vanilla.config.pvpPrepConfig.zones.map { it.name }.toSet().size ==
                Vanilla.config.pvpPrepConfig.zones.size,
        ) { "PvP prep zone names must be unique" }
        PvpPrepListener.init()
    }

    fun isInside(
        instance: Instance,
        position: Pos,
    ): Boolean = Vanilla.config.pvpPrepConfig.zones.any { it.instance === instance && it.zone.contains(position) }
}
