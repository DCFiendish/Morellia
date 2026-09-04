package net.nodisium.combat.listeners

import net.minestom.server.event.player.PlayerInputEvent
import net.nodisium.combat.Combat

/**
 * Tracks whether each player is currently holding a WASD movement key and/or sprinting, read by
 * Gun.fire() to decide how much shot spread applies. Entity.velocity can't be used for this --
 * it's knockback/gravity only, since normal player walking is client-driven position packets, not
 * server-side physics. PlayerInputEvent (the same event AimingListener reads the shift key from)
 * is the real signal, confirmed against the pinned Minestom jar's actual PlayerInputEvent class,
 * which also exposes the sprint key directly (isHoldingSprintKey()) -- no need to infer sprint
 * from forward+double-tap ourselves.
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

        if (moving && event.isHoldingSprintKey()) {
            Combat.sprintingPlayers.add(event.player)
        } else {
            Combat.sprintingPlayers.remove(event.player)
        }
    }

    fun init() {
        Combat.eventNode.addListener(PlayerInputEvent::class.java, ::onInput)
    }
}
