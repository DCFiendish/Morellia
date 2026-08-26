package net.morellia.combat.listeners

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.timer.TaskSchedule
import net.morellia.combat.Combat
import net.morellia.combat.objects.Gun
import net.morellia.combat.objects.Item

private const val RELOAD_TICK_MS = 100L

/**
 * C2 fix (docs/COMBAT_DEEP_DIVE.md): the prior-art library this replaces tracked an in-progress
 * reload by item *type* only, so switching to a second copy of the same gun mid-reload let the
 * completing task top off whichever copy was currently held -- not the one that actually spent the
 * ammo. Here the reload task captures the exact stack's Tags.INSTANCE_ID at start and re-checks it
 * every tick; if the held stack's identity changes at all, the reload is abandoned, not completed
 * against the wrong physical item.
 */
object ReloadListener {
    private fun onHandAnimation(event: PlayerHandAnimationEvent) {
        val player = event.player
        val gun = Item.getFromItemStack(player.itemInMainHand) as? Gun ?: return
        if (!gun.hasAmmo(player.itemInMainHand)) tryStartReload(player, gun)
    }

    private fun tryStartReload(
        player: Player,
        gun: Gun,
    ) {
        val stack = player.itemInMainHand
        if (Combat.reloadTasks.containsKey(player)) return
        if (gun.getAmmo(stack) == gun.magazineSize) return
        if (!hasReserveAmmo(player, gun)) return

        val startInstanceId = Item.instanceId(stack)
        var elapsedMs = 0L

        val task =
            MinecraftServer
                .getSchedulerManager()
                .buildTask {
                    elapsedMs += RELOAD_TICK_MS
                    val currentStack = player.itemInMainHand
                    if (Item.instanceId(currentStack) != startInstanceId) {
                        Combat.reloadTasks.remove(player)?.cancel()
                        return@buildTask
                    }
                    if (elapsedMs >= gun.reloadMs) {
                        if (consumeReserveAmmo(player, gun)) {
                            player.itemInMainHand = gun.setAmmo(player.itemInMainHand, gun.magazineSize)
                        }
                        Combat.reloadTasks.remove(player)?.cancel()
                    }
                }.delay(TaskSchedule.millis(RELOAD_TICK_MS))
                .repeat(TaskSchedule.millis(RELOAD_TICK_MS))
                .schedule()

        Combat.reloadTasks[player] = task
        player.instance?.playSound(gun.soundReload, player.position.x, player.position.y, player.position.z)
    }

    private fun hasReserveAmmo(
        player: Player,
        gun: Gun,
    ): Boolean = player.inventory.itemStacks.any { isReserveAmmoStack(it, gun) }

    /** Consumes one reserve Ammo stack (one stack = one full magazine refill). Returns whether one was found. */
    private fun consumeReserveAmmo(
        player: Player,
        gun: Gun,
    ): Boolean {
        val inventory = player.inventory
        for (slot in inventory.itemStacks.indices) {
            val stack = inventory.itemStacks[slot]
            if (isReserveAmmoStack(stack, gun)) {
                inventory.setItemStack(slot, stack.withAmount(stack.amount() - 1))
                return true
            }
        }
        return false
    }

    private fun isReserveAmmoStack(
        stack: ItemStack,
        gun: Gun,
    ): Boolean = stack.amount() > 0 && Item.getFromItemStack(stack) === gun.ammo

    fun init() {
        Combat.eventNode.addListener(PlayerHandAnimationEvent::class.java, ::onHandAnimation)
    }
}
