package net.nodisium.server

import net.minestom.server.MinecraftServer
import net.minestom.server.event.server.ServerTickMonitorEvent
import net.minestom.server.timer.TaskSchedule

/**
 * Rolling tick-time/TPS logger, for reading server load during load tests.
 * Minestom exposes per-tick timing via ServerTickMonitorEvent -- this aggregates it into a
 * periodic summary instead of logging every single tick (20x/sec).
 */
object TickMonitor {
    private const val REPORT_INTERVAL_SECONDS = 5L

    private var sampleCount = 0
    private var tickTimeSum = 0.0
    private var tickTimeMax = 0.0

    fun init() {
        MinecraftServer.getGlobalEventHandler().addListener(ServerTickMonitorEvent::class.java) { event ->
            val tickTime = event.tickMonitor.tickTime
            sampleCount++
            tickTimeSum += tickTime
            if (tickTime > tickTimeMax) tickTimeMax = tickTime
        }

        MinecraftServer.getSchedulerManager()
            .buildTask(::report)
            .repeat(TaskSchedule.seconds(REPORT_INTERVAL_SECONDS))
            .schedule()
    }

    private fun report() {
        if (sampleCount == 0) return
        val avgTickTime = tickTimeSum / sampleCount
        // Server can't tick faster than its 50ms/tick target, so this is capped at 20.
        val avgTps = (1000.0 / avgTickTime).coerceAtMost(20.0)
        println(
            "[TickMonitor] avg tick: %.2fms (~%.1f tps), max tick: %.2fms, over %d ticks (last %ds)"
                .format(avgTickTime, avgTps, tickTimeMax, sampleCount, REPORT_INTERVAL_SECONDS),
        )
        sampleCount = 0
        tickTimeSum = 0.0
        tickTimeMax = 0.0
    }
}
