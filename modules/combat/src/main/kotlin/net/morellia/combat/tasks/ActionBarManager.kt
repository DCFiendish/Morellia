package net.morellia.combat.tasks

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.timer.TaskSchedule
import net.morellia.combat.Combat
import net.morellia.combat.objects.Gun
import net.morellia.combat.objects.Item

private const val UPDATE_PERIOD_TICKS = 4
private const val BAR_SEGMENTS = 10

/**
 * Drives the "above the hotbar" gun HUD via the vanilla action bar (Player.sendActionBar) -- a
 * fill-bar while reloading (Combat.reloadProgress, kept live by ReloadListener), otherwise the
 * gun's name + current/max ammo (Gun.ammoText). Both share one packet/location so the HUD doesn't
 * jump between two different screen regions depending on state.
 */
object ActionBarManager {
    fun start() {
        MinecraftServer
            .getSchedulerManager()
            .buildTask {
                for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
                    update(player)
                }
            }.repeat(TaskSchedule.tick(UPDATE_PERIOD_TICKS))
            .schedule()
    }

    private fun update(player: Player) {
        val gun = Item.getFromItemStack(player.itemInMainHand) as? Gun ?: return
        val progress = Combat.reloadProgress[player]
        val text =
            if (progress != null) {
                Component.text("Reload ", NamedTextColor.GRAY).append(progressBar(progress))
            } else {
                gun.ammoText(player.itemInMainHand)
            }
        player.sendActionBar(text)
    }

    private fun progressBar(progress: Double): Component {
        val filled = (progress.coerceIn(0.0, 1.0) * BAR_SEGMENTS).toInt()
        return Component
            .text("|".repeat(filled), NamedTextColor.YELLOW)
            .append(Component.text("|".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY))
    }
}
