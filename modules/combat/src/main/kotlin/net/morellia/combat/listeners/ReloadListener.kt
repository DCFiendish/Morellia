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
                        Combat.reloadProgress.remove(player)
                        return@buildTask
                    }
                    if (elapsedMs >= gun.reloadMs) {
                        if (consumeReserveAmmo(player, gun)) {
                            player.itemInMainHand = gun.setAmmo(player.itemInMainHand, gun.magazineSize)
                        }
                        Combat.reloadTasks.remove(player)?.cancel()
                        Combat.reloadProgress.remove(player)
                        gun.refreshModel(player)
                    } else {
                        Combat.reloadProgress[player] = elapsedMs.toDouble() / gun.reloadMs
                    }
                }.delay(TaskSchedule.millis(RELOAD_TICK_MS))
                .repeat(TaskSchedule.millis(RELOAD_TICK_MS))
                .schedule()

        Combat.reloadTasks[player] = task
        Combat.reloadProgress[player] = 0.0
        gun.refreshModel(player)
        player.instance?.playSound(gun.soundReload, player.position.x, player.position.y, player.position.z)
    }

    /**
     * Reloads always go from empty to a full [Gun.magazineSize] (see [tryStartReload]'s ammo==0
     * gate), so the reserve cost is one loose [Gun.ammo] item per round in the magazine, not a
     * flat one-item "fully loaded clip" cost -- a magazineSize=1 musket and a magazineSize=5 rifle
     * both spend real rounds 1:1, they just spend a different count of them per reload.
     */
    private fun totalReserveAmmo(
        player: Player,
        gun: Gun,
    ): Int = player.inventory.itemStacks.filter { isReserveAmmoStack(it, gun) }.sumOf { it.amount() }

    private fun hasReserveAmmo(
        player: Player,
        gun: Gun,
    ): Boolean = totalReserveAmmo(player, gun) >= gun.magazineSize

    /** Consumes [Gun.magazineSize] total reserve ammo, spanning multiple stacks if one isn't enough. */
    private fun consumeReserveAmmo(
        player: Player,
        gun: Gun,
    ): Boolean {
        if (totalReserveAmmo(player, gun) < gun.magazineSize) return false

        val inventory = player.inventory
        var remaining = gun.magazineSize
        for (slot in inventory.itemStacks.indices) {
            if (remaining <= 0) break
            val stack = inventory.itemStacks[slot]
            if (!isReserveAmmoStack(stack, gun)) continue
            val taken = minOf(remaining, stack.amount())
            inventory.setItemStack(slot, stack.withAmount(stack.amount() - taken))
            remaining -= taken
        }
        return true
    }

    private fun isReserveAmmoStack(
        stack: ItemStack,
        gun: Gun,
    ): Boolean = stack.amount() > 0 && Item.getFromItemStack(stack) === gun.ammo

    fun init() {
        Combat.eventNode.addListener(PlayerHandAnimationEvent::class.java, ::onHandAnimation)
    }
}
