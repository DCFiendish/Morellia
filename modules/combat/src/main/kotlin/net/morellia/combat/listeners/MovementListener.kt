package net.morellia.combat.listeners

import net.minestom.server.event.player.PlayerInputEvent
import net.morellia.combat.Combat

/**
 * Tracks whether each player is currently holding a WASD movement key, read by Gun.fire() to
 * decide whether shot spread applies. Entity.velocity can't be used for this -- it's knockback/
 * gravity only, since normal player walking is client-driven position packets, not server-side
 * physics. PlayerInputEvent (the same event AimingListener reads the shift key from) is the real
 * signal, confirmed against the pinned Minestom jar's actual PlayerInputEvent class.
 */
object MovementListener {
    private fun onInput(event: PlayerInputEvent) {
        val moving =
            event.isHoldingForwardKey() ||
                event.isHoldingBackwardKey() ||
                event.isHoldingLeftKey() ||
                event.isHoldingRightKey()
        if (moving) {
            Combat.movingPlayers.add(event.player)
        } else {
            Combat.movingPlayers.remove(event.player)
        }
    }

    fun init() {
        Combat.eventNode.addListener(PlayerInputEvent::class.java, ::onInput)
    }
}
