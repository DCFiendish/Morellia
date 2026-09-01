package net.aechronis.vanilla.objects

import net.minestom.server.coordinate.BlockVec
import net.minestom.server.instance.Instance

data class PrepZoneConfig(
    val name: String,
    val instance: Instance,
    val cornerOne: BlockVec,
    val cornerTwo: BlockVec,
) {
    val zone: PrepZone = PrepZone(cornerOne, cornerTwo)
}
