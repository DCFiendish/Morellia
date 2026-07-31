package net.aechronis.vanilla.managers

import net.aechronis.vanilla.Vanilla
import net.aechronis.vanilla.listeners.CombatListener
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object Combat {
    // player uuid -> epoch millis when the combat tag expires
    private val expiresAt = ConcurrentHashMap<UUID, Long>()
    private val bossBars = ConcurrentHashMap<UUID, BossBar>()

    fun init() {
        val timeStart = System.currentTimeMillis()
        CombatListener.init()
        MinecraftServer
            .getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.seconds(Vanilla.config.combatTickSeconds))
            .schedule()
        val timeEnd = System.currentTimeMillis()
        println("├─ Combat enabled in ${timeEnd - timeStart}ms")
    }

    fun tag(
        a: Player,
        b: Player,
    ) {
        tagOne(a)
        tagOne(b)
    }

    fun isInCombat(player: Player): Boolean = (expiresAt[player.uuid] ?: 0L) > System.currentTimeMillis()

    fun clear(player: Player) {
        expiresAt.remove(player.uuid)
        bossBars.remove(player.uuid)?.let { player.hideBossBar(it) }
    }

    private fun tagOne(player: Player) {
        val wasInCombat = isInCombat(player)
        expiresAt[player.uuid] = System.currentTimeMillis() + Vanilla.config.combatDurationSeconds * 1000

        if (!wasInCombat) {
            val bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS)
            bossBars[player.uuid] = bar
            player.showBossBar(bar)
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val durationMs = Vanilla.config.combatDurationSeconds * 1000

        for ((uuid, expiry) in expiresAt) {
            // Was an O(n) linear scan of every online player per tagged player, every tick
            // interval -- getOnlinePlayerByUuid is the O(1) direct lookup ConnectionManager
            // already provides (used elsewhere in this codebase, e.g. Resident.fromPlayer).
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: continue
            val bar = bossBars[uuid] ?: continue
            val remaining = expiry - now

            if (remaining <= 0) {
                expiresAt.remove(uuid)
                bossBars.remove(uuid)
                // Defer the actual player-facing mutation onto that player's own tick thread --
                // see EnvironmentalDamage.kt's tick() for the same fix and why.
                player.scheduler().scheduleNextTick {
                    player.hideBossBar(bar)
                    player.sendMessage(Component.text("You have left combat", NamedTextColor.GREEN))
                }
                continue
            }

            bar.progress((remaining.toFloat() / durationMs).coerceIn(0f, 1f))
            bar.name(Component.text("Combat: ${(remaining / 1000) + 1}s", NamedTextColor.RED))
        }
    }
}
