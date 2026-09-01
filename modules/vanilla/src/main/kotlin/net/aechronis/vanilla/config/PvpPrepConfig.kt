package net.aechronis.vanilla.config

import net.aechronis.vanilla.objects.PrepZoneConfig

data class PvpPrepConfig(
    val zones: List<PrepZoneConfig> = emptyList(),
)
