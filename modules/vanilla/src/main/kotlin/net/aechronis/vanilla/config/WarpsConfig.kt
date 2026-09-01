package net.aechronis.vanilla.config

import net.aechronis.vanilla.objects.WarpPoint

data class WarpsConfig(
    val warps: List<WarpPoint> = emptyList(),
    val warpTimeSeconds: Long = 2L,
)
