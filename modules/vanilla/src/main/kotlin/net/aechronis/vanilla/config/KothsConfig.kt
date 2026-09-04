package net.aechronis.vanilla.config

import net.aechronis.vanilla.objects.KothConfig

data class KothsConfig(
    val kothLengthSeconds: Long = 3600L,
    // Five-field Unix cron expressions (e.g. "0 18 * * *" for 18:00 daily, "0 */2 * * *" every 2h).
    val kothSchedules: Map<String, List<String>> = emptyMap(),
    val koths: List<KothConfig> = emptyList(),
    val kothDisplayRadiusBlocks: Double = 250.0,
)
