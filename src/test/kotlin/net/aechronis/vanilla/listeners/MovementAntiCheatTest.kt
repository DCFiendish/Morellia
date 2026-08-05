package net.aechronis.vanilla.listeners

import net.aechronis.vanilla.ManagerTest
import net.aechronis.vanilla.VanillaTest
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.event.entity.EntityTeleportEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovementAntiCheatTest : ManagerTest() {
    @Test
    fun `ordinary walking is never flagged`() {
        val player = VanillaTest.createPlayer(Pos(0.5, 40.0, 0.5))
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(0.5, 40.0, 0.5), true))

        var event = PlayerMoveEvent(player, Pos(0.7, 40.0, 0.5), true)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)

        event = PlayerMoveEvent(player, Pos(0.9, 40.0, 0.5), true)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)
        VanillaTest.remove(player)
    }

    @Test
    fun `a huge horizontal jump in one update is cancelled`() {
        val player = VanillaTest.createPlayer(Pos(10.5, 40.0, 10.5))
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(10.5, 40.0, 10.5), true))

        val event = PlayerMoveEvent(player, Pos(30.5, 40.0, 10.5), true)
        MovementAntiCheatListener.onMove(event)
        assertTrue(event.isCancelled)
        VanillaTest.remove(player)
    }

    @Test
    fun `sprinting allows a higher horizontal threshold than walking`() {
        val player = VanillaTest.createPlayer(Pos(50.5, 40.0, 10.5))
        player.isSprinting = true
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(50.5, 40.0, 10.5), true))

        // Within the sprint/speed threshold (2.2) but above the plain-walk one (1.5).
        val event = PlayerMoveEvent(player, Pos(52.3, 40.0, 10.5), true)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)
        VanillaTest.remove(player)
    }

    @Test
    fun `the same distance is flagged without sprint or speed`() {
        val player = VanillaTest.createPlayer(Pos(60.5, 40.0, 10.5))
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(60.5, 40.0, 10.5), true))

        val event = PlayerMoveEvent(player, Pos(62.3, 40.0, 10.5), true)
        MovementAntiCheatListener.onMove(event)
        assertTrue(event.isCancelled)
        VanillaTest.remove(player)
    }

    @Test
    fun `speed potion effect allows the higher threshold too`() {
        val player = VanillaTest.createPlayer(Pos(70.5, 40.0, 10.5))
        (player as LivingEntity).addEffect(Potion(PotionEffect.SPEED, 0, 200))
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(70.5, 40.0, 10.5), true))

        val event = PlayerMoveEvent(player, Pos(72.3, 40.0, 10.5), true)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)
        VanillaTest.remove(player)
    }

    @Test
    fun `sustained ascent without touching ground is cancelled`() {
        val player = VanillaTest.createPlayer(Pos(80.5, 40.0, 10.5))
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(80.5, 40.0, 10.5), true))

        // Climb in small legal-looking horizontal-wise steps, never touching ground.
        var event = PlayerMoveEvent(player, Pos(80.5, 41.5, 10.5), false)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)

        event = PlayerMoveEvent(player, Pos(80.5, 43.0, 10.5), false)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)

        // Total ascent from the 40.0 baseline is now beyond maxUnsupportedAscentBlocks (4.0).
        event = PlayerMoveEvent(player, Pos(80.5, 45.0, 10.5), false)
        MovementAntiCheatListener.onMove(event)
        assertTrue(event.isCancelled)
        VanillaTest.remove(player)
    }

    @Test
    fun `touching ground resets the ascent baseline`() {
        val player = VanillaTest.createPlayer(Pos(90.5, 40.0, 10.5))
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(90.5, 40.0, 10.5), true))

        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(90.5, 42.0, 10.5), false))
        // Lands (on ground) -- ascent tracking should reset here.
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(90.5, 42.0, 10.5), true))

        // A fresh climb from this new baseline, still under the cap, must not be flagged just
        // because it's a similar total height to the previous (reset) climb.
        val event = PlayerMoveEvent(player, Pos(90.5, 44.0, 10.5), false)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)
        VanillaTest.remove(player)
    }

    @Test
    fun `jump boost allows a higher ascent threshold`() {
        val player = VanillaTest.createPlayer(Pos(100.5, 40.0, 10.5))
        (player as LivingEntity).addEffect(Potion(PotionEffect.JUMP_BOOST, 0, 200))
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(100.5, 40.0, 10.5), true))

        // Beyond the plain cap (4.0) but within the jump-boost cap (6.0).
        val event = PlayerMoveEvent(player, Pos(100.5, 45.0, 10.5), false)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)
        VanillaTest.remove(player)
    }

    @Test
    fun `creative mode is exempt from both checks`() {
        val player = VanillaTest.createPlayer(Pos(110.5, 40.0, 10.5))
        player.setGameMode(GameMode.CREATIVE)
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(110.5, 40.0, 10.5), true))

        val event = PlayerMoveEvent(player, Pos(140.5, 80.0, 10.5), false)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)
        VanillaTest.remove(player)
    }

    @Test
    fun `flying is exempt from both checks`() {
        val player = VanillaTest.createPlayer(Pos(120.5, 40.0, 10.5))
        player.isAllowFlying = true
        player.isFlying = true
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(120.5, 40.0, 10.5), true))

        val event = PlayerMoveEvent(player, Pos(150.5, 80.0, 10.5), false)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)
        VanillaTest.remove(player)
    }

    @Test
    fun `riding a vehicle is exempt from both checks`() {
        val player = VanillaTest.createPlayer(Pos(130.5, 40.0, 10.5))
        val vehicle = Entity(EntityType.PIG)
        vehicle.setInstance(VanillaTest.instance, Pos(130.5, 40.0, 10.5)).join()
        vehicle.addPassenger(player)
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(130.5, 40.0, 10.5), true))

        val event = PlayerMoveEvent(player, Pos(160.5, 80.0, 10.5), false)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)
        vehicle.remove()
        VanillaTest.remove(player)
    }

    @Test
    fun `standing in water is exempt from both checks`() {
        val player = VanillaTest.createPlayer(Pos(140.5, 40.0, 10.5))
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(140.5, 40.0, 10.5), true))
        VanillaTest.instance.setBlock(140, 45, 10, Block.WATER)

        val event = PlayerMoveEvent(player, Pos(140.5, 45.0, 10.5), false)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)
        VanillaTest.instance.setBlock(140, 45, 10, Block.AIR)
        VanillaTest.remove(player)
    }

    @Test
    fun `levitation effect is exempt from the ascent check`() {
        val player = VanillaTest.createPlayer(Pos(150.5, 40.0, 10.5))
        (player as LivingEntity).addEffect(Potion(PotionEffect.LEVITATION, 0, 200))
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(150.5, 40.0, 10.5), true))

        val event = PlayerMoveEvent(player, Pos(150.5, 55.0, 10.5), false)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)
        VanillaTest.remove(player)
    }

    @Test
    fun `a teleport resets tracking so the next move is not flagged`() {
        val player = VanillaTest.createPlayer(Pos(160.5, 40.0, 10.5))
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(160.5, 40.0, 10.5), true))

        // Teleport far away (e.g. /tp, /back, a nodes warp).
        MovementAntiCheatListener.onTeleport(EntityTeleportEvent(player, Pos(500.5, 70.0, 500.5), 0))

        // The first move after a teleport has no prior baseline to compare against, so it must
        // never be treated as an impossible single-update jump from the pre-teleport position.
        val event = PlayerMoveEvent(player, Pos(500.5, 70.0, 500.5), true)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)
        VanillaTest.remove(player)
    }

    @Test
    fun `disconnect clears tracked state`() {
        val player = VanillaTest.createPlayer(Pos(170.5, 40.0, 10.5))
        MovementAntiCheatListener.onMove(PlayerMoveEvent(player, Pos(170.5, 40.0, 10.5), true))
        MovementAntiCheatListener.onDisconnect(PlayerDisconnectEvent(player))

        // No baseline left after "disconnect" -- next move for a reused UUID (unrealistic, but
        // proves the map entry was actually removed) must not be compared against stale state.
        val event = PlayerMoveEvent(player, Pos(500.5, 40.0, 10.5), true)
        MovementAntiCheatListener.onMove(event)
        assertFalse(event.isCancelled)
        VanillaTest.remove(player)
    }
}
