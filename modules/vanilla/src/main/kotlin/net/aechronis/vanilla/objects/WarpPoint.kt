package net.aechronis.vanilla.objects

import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance

data class WarpPoint(
    val name: String,
    val instance: Instance,
    val position: Pos,
)
