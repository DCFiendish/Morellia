package net.nodisium.server

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.entity.EntityDeathEvent
import net.minestom.server.instance.Instance
import net.minestom.server.timer.TaskSchedule

/**
 * A stationary, high-health zombie a few blocks from spawn for solo melee (and gun) testing.
 * EntityCreature has no AI unless AIGroups are explicitly added (nothing here does that), so it
 * just stands still and eats hits. Respawns itself a few seconds after dying instead of needing a
 * manual restart between test swings. Dev-only scaffolding, same category as LoadTestBots -- remove
 * once real players/mobs replace it.
 */
object TestMeleeTarget {
    private const val TARGET_HEALTH = 500.0
    private val SPAWN_OFFSET = Pos(3.0, 0.0, 0.0)
    private val RESPAWN_DELAY = TaskSchedule.seconds(3)

    private lateinit var instance: Instance
    private lateinit var origin: Pos
    private var current: EntityCreature? = null

    fun spawn(
        instance: Instance,
        near: Pos,
    ) {
        this.instance = instance
        this.origin = near
        spawnOne()

        MinecraftServer.getGlobalEventHandler().addListener(EntityDeathEvent::class.java) { event ->
            if (event.entity === current) {
                MinecraftServer.getSchedulerManager()
                    .buildTask(::spawnOne)
                    .delay(RESPAWN_DELAY)
                    .schedule()
            }
        }
    }

    private fun spawnOne() {
        val target = EntityCreature(EntityType.ZOMBIE)
        target.getAttribute(Attribute.MAX_HEALTH).baseValue = TARGET_HEALTH
        target.health = TARGET_HEALTH.toFloat()
        target.setInstance(instance, origin.add(SPAWN_OFFSET))
        current = target
    }
}
