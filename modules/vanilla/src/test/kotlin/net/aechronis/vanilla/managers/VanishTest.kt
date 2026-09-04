package net.aechronis.vanilla.managers

import net.aechronis.vanilla.ManagerTest
import net.aechronis.vanilla.VanillaTest
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Permission checks resolve to allowed here via the test task's DEBUG=true env var (see
// build.gradle.kts) -- no LuckPerms provider is registered in this test process.
class VanishTest : ManagerTest() {
    @Test
    fun `unvanishing restores the default view rule`() {
        val viewer = VanillaTest.createPlayer(Pos(0.5, 40.0, 0.5))
        val target = VanillaTest.createPlayer(Pos(2.5, 40.0, 0.5))

        try {
            assertTrue(viewer in target.viewers)

            Vanish.toggle(target)
            assertFalse(viewer in target.viewers)

            Vanish.toggle(target)
            assertTrue(viewer in target.viewers)
        } finally {
            if (Vanish.isVanished(target)) Vanish.toggle(target)
            VanillaTest.remove(target)
            VanillaTest.remove(viewer)
        }
    }

    @Test
    fun `spectators are hidden until they leave spectator mode`() {
        val viewer = VanillaTest.createPlayer(Pos(4.5, 40.0, 0.5))
        val target = VanillaTest.createPlayer(Pos(6.5, 40.0, 0.5))

        try {
            assertTrue(viewer in target.viewers)

            target.gameMode = GameMode.SPECTATOR
            assertFalse(viewer in target.viewers)

            target.gameMode = GameMode.SURVIVAL
            assertTrue(viewer in target.viewers)
        } finally {
            VanillaTest.remove(target)
            VanillaTest.remove(viewer)
        }
    }

    @Test
    fun `vanished spectators remain hidden until unvanished`() {
        val viewer = VanillaTest.createPlayer(Pos(8.5, 40.0, 0.5))
        val target = VanillaTest.createPlayer(Pos(10.5, 40.0, 0.5))

        try {
            Vanish.toggle(target)
            assertFalse(viewer in target.viewers)

            target.gameMode = GameMode.SPECTATOR
            target.gameMode = GameMode.SURVIVAL
            assertFalse(viewer in target.viewers)

            Vanish.toggle(target)
            assertTrue(viewer in target.viewers)
        } finally {
            if (Vanish.isVanished(target)) Vanish.toggle(target)
            VanillaTest.remove(target)
            VanillaTest.remove(viewer)
        }
    }
}
