package net.aechronis.vanilla.listeners

import net.aechronis.vanilla.Vanilla
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityTeleportEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.potion.PotionEffect
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot

/**
 * Baseline movement anti-cheat: speed-hack (excess horizontal distance in one move update)
 * and fly-hack (sustained vertical ascent without touching ground) detection. Deliberately
 * simple per-move-event distance caps rather than tick-precise physics -- a cheap, high-
 * signal baseline per docs/research-todo/03-anti-cheat-and-security.md, not a full NCP-style
 * system. Enforcement is always a snap-back (cancel the offending move), never a kick/ban,
 * to keep false-positive risk low.
 *
 * Tracks its own last-accepted-position/ascent-start state per player (like
 * FallDamageListener's fallStartY) rather than relying on Player#getPosition(), since a
 * cancelled PlayerMoveEvent only prevents the client-visible position from changing --
 * this listener's own bookkeeping is what stays authoritative move-to-move either way.
 */
object MovementAntiCheatListener {
    private val lastPosition = ConcurrentHashMap<UUID, Pos>()
    private val ascentStartY = ConcurrentHashMap<UUID, Double>()

    fun onMove(event: PlayerMoveEvent) {
        if (!Vanilla.config.movementAntiCheatEnabled) return
        val player = event.player
        val to = event.newPosition

        if (isExempt(player, to)) {
            lastPosition[player.uuid] = to
            ascentStartY.remove(player.uuid)
            return
        }

        val from = lastPosition[player.uuid]
        if (from == null) {
            // First move seen for this player this session -- nothing to compare against yet.
            lastPosition[player.uuid] = to
            return
        }

        val horizontal = hypot(to.x - from.x, to.z - from.z)
        val maxHorizontal =
            if (player.isSprinting || player.hasEffect(PotionEffect.SPEED)) {
                Vanilla.config.maxHorizontalDistancePerMoveSprintOrSpeed
            } else {
                Vanilla.config.maxHorizontalDistancePerMove
            }
        if (horizontal > maxHorizontal) {
            println(
                "[AntiCheat] ${player.username} moved $horizontal blocks horizontally in one update " +
                    "(max $maxHorizontal) -- snapped back",
            )
            event.isCancelled = true
            // Leave lastPosition at `from` -- the move never actually happened.
            return
        }
        lastPosition[player.uuid] = to

        if (event.isOnGround) {
            ascentStartY.remove(player.uuid)
            return
        }

        val startY = ascentStartY.putIfAbsent(player.uuid, from.y) ?: from.y
        val maxAscent =
            if (player.hasEffect(PotionEffect.JUMP_BOOST)) {
                Vanilla.config.maxUnsupportedAscentBlocksJumpBoost
            } else {
                Vanilla.config.maxUnsupportedAscentBlocks
            }
        if (to.y - startY > maxAscent) {
            println(
                "[AntiCheat] ${player.username} climbed ${to.y - startY} blocks without touching ground " +
                    "(max $maxAscent) -- snapped back",
            )
            event.isCancelled = true
            lastPosition[player.uuid] = from
        }
    }

    // Legitimate ways to move fast/climb without hacking: creative/spectator, server-granted
    // flight (/fly, elytra), riding a vehicle (separate physics, combat's system), water
    // (buoyancy/current not modeled here), and Levitation (vanilla effect, rapid forced ascent).
    private fun isExempt(
        player: Player,
        newPosition: Pos,
    ): Boolean {
        val gm = player.gameMode
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true
        if (player.isFlying) return true
        if (player.isFlyingWithElytra) return true
        if (player.vehicle != null) return true
        if (player.hasEffect(PotionEffect.LEVITATION)) return true
        // getBlock() throws on an unloaded chunk (e.g. right after a long-distance teleport,
        // /tp, or a nodes warp landing somewhere not yet generated/loaded) instead of returning
        // AIR -- guard with isChunkLoaded first rather than let that crash the move-event pipeline.
        val instance = player.instance
        if (instance != null &&
            instance.isChunkLoaded(newPosition.blockX() shr 4, newPosition.blockZ() shr 4) &&
            instance.getBlock(newPosition, Block.Getter.Condition.TYPE) === Block.WATER
        ) {
            return true
        }
        return false
    }

    fun onDisconnect(event: PlayerDisconnectEvent) {
        lastPosition.remove(event.player.uuid)
        ascentStartY.remove(event.player.uuid)
    }

    fun onTeleport(event: EntityTeleportEvent) {
        // Without this, a teleport (/tp, /back, elevator, nodes warp) reads as an impossible
        // single-update jump against the pre-teleport lastPosition, instantly snapping the
        // player back to where they teleported FROM.
        val player = event.entity as? Player ?: return
        lastPosition.remove(player.uuid)
        ascentStartY.remove(player.uuid)
    }

    fun init() {
        Vanilla.eventNode.addListener(PlayerMoveEvent::class.java, MovementAntiCheatListener::onMove)
        Vanilla.eventNode.addListener(PlayerDisconnectEvent::class.java, MovementAntiCheatListener::onDisconnect)
        Vanilla.eventNode.addListener(EntityTeleportEvent::class.java, MovementAntiCheatListener::onTeleport)
    }
}
