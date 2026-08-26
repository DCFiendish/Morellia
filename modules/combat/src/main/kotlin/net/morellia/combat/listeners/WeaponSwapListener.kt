package net.morellia.combat.listeners

import net.minestom.server.event.player.PlayerSwapItemEvent
import net.morellia.combat.Combat
import net.morellia.combat.objects.Item

/**
 * H5 fix (docs/COMBAT_DEEP_DIVE.md): the prior-art library this replaces only reset attack
 * cooldown on hotbar-slot changes, never on the F-key main/off-hand swap -- a maxed-out cooldown
 * on an idle item could be F-swapped in for an instant full-power first hit. Cancelling the swap
 * outright while holding any combat item closes the vector entirely: there's no swap to exploit if
 * it never happens.
 */
object WeaponSwapListener {
    private fun onSwap(event: PlayerSwapItemEvent) {
        if (Item.getFromItemStack(event.player.itemInMainHand) != null) {
            event.isCancelled = true
        }
    }

    fun init() {
        Combat.eventNode.addListener(PlayerSwapItemEvent::class.java, ::onSwap)
    }
}
