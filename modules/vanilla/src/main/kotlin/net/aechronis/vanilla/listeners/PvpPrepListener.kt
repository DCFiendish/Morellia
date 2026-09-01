package net.aechronis.vanilla.listeners

import net.aechronis.vanilla.Vanilla
import net.aechronis.vanilla.managers.PvpPrep
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent

object PvpPrepListener {
    fun onDamage(event: EntityDamageEvent) {
        val victim = event.entity as? Player ?: return
        val instance = victim.instance ?: return
        if (PvpPrep.isInside(instance, victim.position)) event.isCancelled = true
    }

    fun onBlockBreak(event: PlayerBlockBreakEvent) {
        val player = event.player
        val instance = player.instance ?: return
        if (PvpPrep.isInside(instance, player.position)) event.isCancelled = true
    }

    fun init() {
        Vanilla.eventNode.addListener(EntityDamageEvent::class.java, ::onDamage)
        Vanilla.eventNode.addListener(PlayerBlockBreakEvent::class.java, ::onBlockBreak)
    }
}
