package net.aechronis.vanilla.listeners

import net.aechronis.vanilla.Vanilla
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityTeleportEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.potion.PotionEffect
import net.minestom.server.registry.RegistryKey
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

object FallDamageListener {
    private val FALL: RegistryKey<DamageType> = RegistryKey.unsafeOf("minecraft:fall")
    private val fallStartY = ConcurrentHashMap<UUID, Double>()

    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        val gm = player.gameMode
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            fallStartY.remove(player.uuid)
            return
        }

        if (player.vehicle != null) {
            fallStartY.remove(player.uuid)
            return
        }

        val newY = event.newPosition.y
        if (player.instance?.getBlock(event.newPosition, Block.Getter.Condition.TYPE) === Block.WATER) {
            fallStartY.remove(player.uuid)
            return
        }

        if (!event.isOnGround) {
            fallStartY.merge(player.uuid, newY, ::maxOf)
        } else {
            val startY = fallStartY.remove(player.uuid) ?: return
            // This is a from-scratch fall-damage implementation, not Minestom's built-in one,
            // so it doesn't get vanilla's Slow Falling handling for free -- without this check
            // the effect did nothing here even though it visibly capped fall speed, since damage
            // is computed purely from the Y-drop with no awareness of active potion effects.
            if (player.hasEffect(PotionEffect.SLOW_FALLING)) return
            val damage = floor(startY - newY - 3.0).toFloat()
            if (damage > 0f && player.health > 0f) {
                player.damage(FALL, damage)
            }
        }
    }

    fun onDeath(event: PlayerDeathEvent) {
        fallStartY.remove(event.player.uuid)
    }

    fun onDisconnect(event: PlayerDisconnectEvent) {
        fallStartY.remove(event.player.uuid)
    }

    fun onTeleport(event: EntityTeleportEvent) {
        // Without this, a fall tracked before a teleport (e.g. /tp, warp, elevator) keeps its old
        // startY, so landing after the teleport computes fall damage against a location the player
        // was never actually falling from.
        val player = event.entity as? Player ?: return
        fallStartY.remove(player.uuid)
    }

    fun init() {
        Vanilla.eventNode.addListener(PlayerMoveEvent::class.java, FallDamageListener::onMove)
        Vanilla.eventNode.addListener(PlayerDeathEvent::class.java, FallDamageListener::onDeath)
        Vanilla.eventNode.addListener(PlayerDisconnectEvent::class.java, FallDamageListener::onDisconnect)
        Vanilla.eventNode.addListener(EntityTeleportEvent::class.java, FallDamageListener::onTeleport)
    }
}
