package net.aechronis.vanilla.managers

import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.instance.block.rule.BlockPlacementRule
import net.minestom.server.item.ItemStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LadderPlacementRuleTest {
    private val rule = LadderPlacementRule(Block.LADDER, fallback = null)

    private fun worldWithSolidAt(vararg solid: Triple<Int, Int, Int>): Block.Getter {
        val solidSet = solid.toSet()
        return Block.Getter { x, y, z, _ -> if (Triple(x, y, z) in solidSet) Block.STONE else Block.AIR }
    }

    private fun stateAt(
        world: Block.Getter,
        blockFace: BlockFace?,
        yaw: Float,
    ) = BlockPlacementRule.PlacementState(
        world,
        Block.LADDER,
        blockFace,
        BlockVec(0, 0, 0),
        BlockVec(0, 0, 0),
        Pos(0.0, 0.0, 0.0, yaw, 0f),
        ItemStack.AIR,
        false,
    )

    @Test
    fun `faces the clicked wall when it can support a ladder`() {
        val world = worldWithSolidAt(Triple(0, 0, 1)) // solid block south of the placement spot
        val placed = rule.blockPlace(stateAt(world, BlockFace.NORTH, yaw = 0f))
        assertEquals("north", placed?.getProperty("facing"))
    }

    @Test
    fun `falls back to whichever horizontal side actually has a wall`() {
        // clicked the top of a block (no vertical wall there) -- only a wall to the west exists
        val world = worldWithSolidAt(Triple(-1, 0, 0))
        val placed = rule.blockPlace(stateAt(world, BlockFace.TOP, yaw = 0f))
        assertEquals("east", placed?.getProperty("facing"))
    }

    @Test
    fun `refuses to place with no wall on any side`() {
        val world = worldWithSolidAt()
        val placed = rule.blockPlace(stateAt(world, BlockFace.TOP, yaw = 0f))
        assertNull(placed)
    }
}
