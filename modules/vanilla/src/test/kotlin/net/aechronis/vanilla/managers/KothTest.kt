package net.aechronis.vanilla.managers

import net.aechronis.vanilla.ManagerTest
import net.aechronis.vanilla.VanillaTest
import net.aechronis.vanilla.objects.KothConfig
import net.aechronis.vanilla.objects.KothZone
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KothTest : ManagerTest() {
    @Test
    fun `cron schedules match values ranges lists and steps`() {
        val mondayAtEighteen = LocalDateTime.of(2026, 8, 3, 18, 0)

        assertTrue(Koth.matchesSchedule("* * * * *", mondayAtEighteen))
        assertTrue(Koth.matchesSchedule("0 18 * * *", mondayAtEighteen))
        assertTrue(Koth.matchesSchedule("0 18 * * 1-5", mondayAtEighteen))
        assertTrue(Koth.matchesSchedule("0 18 * * 0,1", mondayAtEighteen))
        assertTrue(Koth.matchesSchedule("*/15 * * * *", mondayAtEighteen))
        assertFalse(Koth.matchesSchedule("1 18 * * *", mondayAtEighteen))
        assertFalse(Koth.matchesSchedule("0 18 * * 0", mondayAtEighteen))
        assertFalse(Koth.matchesSchedule("not a cron expression", mondayAtEighteen))
    }

    @Test
    fun `zone normalizes both corners and includes all configured blocks`() {
        val zone = KothZone(BlockVec(10, 20, 30), BlockVec(8, 18, 28))

        assertTrue(zone.contains(Pos(8.0, 18.0, 28.0)))
        assertTrue(zone.contains(Pos(10.99, 20.99, 30.99)))
        assertFalse(zone.contains(Pos(7.99, 19.0, 29.0)))
        assertFalse(zone.contains(Pos(9.0, 21.0, 29.0)))
    }

    @Test
    fun `capture state can begin and be reset`() {
        val config = KothConfig("test", VanillaTest.instance, BlockVec(0, 40, 0), BlockVec(2, 42, 2), 10)
        val state = Koth.ActiveKoth(config, 100L, 10_100L)
        val player = VanillaTest.createPlayer(Pos(0.5, 40.0, 0.5))

        Koth.beginCapture(state, player.uuid, 1_000L)
        assertEquals(player.uuid, state.capturer)
        assertEquals(1_000L, state.captureStartedAt)
        Koth.resetCapture(state)
        assertNull(state.capturer)
        assertNull(state.captureStartedAt)
        VanillaTest.remove(player)
    }

    @Test
    fun `boss bar visibility stays limited to nearby players`() {
        val name = "bossbar-radius-${System.nanoTime()}"
        val config = KothConfig(name, VanillaTest.instance, BlockVec(0, 40, 0), BlockVec(2, 42, 2), 10)
        val state = Koth.ActiveKoth(config, 0L, 10_000L)
        val nearby = VanillaTest.createPlayer(Pos(0.5, 40.0, 0.5))
        val distant = VanillaTest.createPlayer(Pos(2_000.5, 40.0, 2_000.5))

        Koth.active[name] = state
        try {
            Koth.updateBossBarsFor(nearby, now = 0L)
            Koth.updateBossBarsFor(distant, now = 0L)

            assertTrue(nearby.uuid in state.visibleTo)
            assertFalse(distant.uuid in state.visibleTo)
        } finally {
            Koth.active.remove(name)
            VanillaTest.remove(nearby)
            VanillaTest.remove(distant)
        }
    }
}
