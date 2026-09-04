package net.nodisium.combat.listeners

import net.minestom.server.event.player.PlayerDisconnectEvent
import net.nodisium.combat.Combat

object PlayerDisconnectListener {
    private fun onDisconnect(event: PlayerDisconnectEvent) {
        Combat.clearPlayer(event.player)
    }

    fun init() {
        Combat.eventNode.addListener(PlayerDisconnectEvent::class.java, ::onDisconnect)
    }
}
