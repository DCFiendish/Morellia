package net.aechronis.vanilla.listeners

import net.aechronis.vanilla.Vanilla
import net.aechronis.vanilla.managers.Warp
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerMoveEvent

object WarpListener {
    fun onMove(event: PlayerMoveEvent) {
        Warp.cancelIfMoved(event.player, event.newPosition)
    }

    fun onDeath(event: PlayerDeathEvent) {
        Warp.cancel(event.player, notify = false)
    }

    fun onDisconnect(event: PlayerDisconnectEvent) {
        Warp.cancel(event.player, notify = false)
    }

    fun init() {
        Vanilla.eventNode.addListener(PlayerMoveEvent::class.java, ::onMove)
        Vanilla.eventNode.addListener(PlayerDeathEvent::class.java, ::onDeath)
        Vanilla.eventNode.addListener(PlayerDisconnectEvent::class.java, ::onDisconnect)
    }
}
