package net.aechronis.vanilla.objects

import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos

/** Box containment check for a no-damage/no-break pvp prep area -- see [PrepZoneConfig]. */
class PrepZone(
    cornerOne: BlockVec,
    cornerTwo: BlockVec,
) {
    private val minX = minOf(cornerOne.blockX(), cornerTwo.blockX())
    private val minY = minOf(cornerOne.blockY(), cornerTwo.blockY())
    private val minZ = minOf(cornerOne.blockZ(), cornerTwo.blockZ())
    private val maxX = maxOf(cornerOne.blockX(), cornerTwo.blockX())
    private val maxY = maxOf(cornerOne.blockY(), cornerTwo.blockY())
    private val maxZ = maxOf(cornerOne.blockZ(), cornerTwo.blockZ())

    fun contains(position: Pos): Boolean =
        position.x() >= minX &&
            position.x() < maxX + 1 &&
            position.y() >= minY &&
            position.y() < maxY + 1 &&
            position.z() >= minZ &&
            position.z() < maxZ + 1
}
