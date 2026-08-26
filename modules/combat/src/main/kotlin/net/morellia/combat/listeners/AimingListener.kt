package net.morellia.combat.listeners

import net.minestom.server.event.player.PlayerInputEvent
import net.morellia.combat.Combat
import net.morellia.combat.objects.Gun
import net.morellia.combat.objects.Item

/**
 * Hold-sneak-to-scope: sneaking while holding a Gun reduces spread/recoil (see Gun.fire) until
 * the player stands back up. This Minestom version has no dedicated start/stop-sneaking event, so
 * the sneak (shift) key's press/release edges come from the general PlayerInputEvent instead.
 */
object AimingListener {
    private fun onInput(event: PlayerInputEvent) {
        val player = event.player
        if (Item.getFromItemStack(player.itemInMainHand) !is Gun) return

        if (event.hasPressedShiftKey()) {
            Combat.aimingPlayers.add(player)
        } else if (event.hasReleasedShiftKey()) {
            Combat.aimingPlayers.remove(player)
        }
    }

    fun init() {
        Combat.eventNode.addListener(PlayerInputEvent::class.java, ::onInput)
    }
}
