package net.nodisium.combat.listeners

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.timer.TaskSchedule
import net.nodisium.combat.Combat
import net.nodisium.combat.objects.Gun
import net.nodisium.combat.objects.Item

/**
 * Fire trigger. Uses the hand-swing-animation event, not a dig-start/cancel pair, because a
 * digging packet is only sent when a block is actually targeted within short reach -- a hitscan
 * gun aimed at open sky or a distant target (this project's guns range up to ~128 blocks) would
 * never see one. The swing animation is the only signal the vanilla client reliably sends on
 * every left-click regardless of what's being looked at.
 *
 * [Gun.automatic] fix (docs/COMBAT_DEEP_DIVE.md H2): the prior-art library this replaces read this
 * flag nowhere in its fire logic -- semi-auto and full-auto guns behaved identically, gated only
 * by cooldown. Here, a fresh swing always fires once (cooldown-gated inside Gun.fire regardless of
 * this flag, so no gun can exceed its own configured rate either way); only an *automatic* gun
 * additionally starts a server-driven repeat loop that keeps firing on its own every cooldownMs.
 * That loop self-cancels once swing events stop arriving (vanilla clients keep sending them for as
 * long as the mouse button is actually held, so a tap naturally stops the loop after at most one
 * extra shot; a semi-auto gun never gets a loop at all, so it always needs a fresh swing per shot).
 */
object FireListener {
    private const val AUTO_FIRE_INPUT_TIMEOUT_MULTIPLIER = 3

    private fun onHandAnimation(event: PlayerHandAnimationEvent) {
        val player = event.player
        val gun = Item.getFromItemStack(player.itemInMainHand) as? Gun ?: return

        Combat.lastFireInputTimes[player] = System.currentTimeMillis()
        gun.fire(player)
        if (gun.automatic) startAutoFireIfNeeded(player, gun)
    }

    /**
     * Uses computeIfAbsent, not containsKey-then-put, so two swing events arriving close together
     * for the same player can't both pass the "no task yet" check before either registers one --
     * that race would start two overlapping auto-fire loops for one trigger hold (same TOCTOU
     * class as the cooldown race fixed in Combat.tryStartCooldown; see its kdoc).
     */
    private fun startAutoFireIfNeeded(
        player: Player,
        gun: Gun,
    ) {
        Combat.autoFireTasks.computeIfAbsent(player) {
            MinecraftServer
                .getSchedulerManager()
                .buildTask {
                    val currentGun = Item.getFromItemStack(player.itemInMainHand) as? Gun
                    val lastInput = Combat.lastFireInputTimes[player] ?: 0L
                    val triggerStillHeld =
                        System.currentTimeMillis() - lastInput < gun.cooldownMs * AUTO_FIRE_INPUT_TIMEOUT_MULTIPLIER
                    if (currentGun !== gun || !triggerStillHeld) {
                        Combat.autoFireTasks.remove(player)?.cancel()
                        return@buildTask
                    }
                    gun.fire(player)
                }.repeat(TaskSchedule.millis(gun.cooldownMs))
                .schedule()
        }
    }

    fun init() {
        Combat.eventNode.addListener(PlayerHandAnimationEvent::class.java, ::onHandAnimation)
    }
}
