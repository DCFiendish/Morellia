package net.morellia.combat

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.timer.Task
import net.morellia.combat.listeners.AimingListener
import net.morellia.combat.listeners.FireListener
import net.morellia.combat.listeners.MeleeListener
import net.morellia.combat.listeners.PlayerDisconnectListener
import net.morellia.combat.listeners.ReloadListener
import net.morellia.combat.listeners.WeaponSwapListener
import net.morellia.combat.tasks.ActionBarManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Central per-player combat state. Every map here is a concurrent collection from the start --
 * Minestom's per-chunk-parallel tick dispatch means plain HashMap/HashSet is a real race under
 * this project's threading model (confirmed for this exact concern in
 * docs/research-todo/01-concurrency-model.md), not a theoretical one.
 */
object Combat {
    val eventNode: EventNode<net.minestom.server.event.Event> = EventNode.all("combat")

    internal val playerLastFireTimes = ConcurrentHashMap<Player, Long>()
    internal val lastFireInputTimes = ConcurrentHashMap<Player, Long>()
    internal val autoFireTasks = ConcurrentHashMap<Player, Task>()
    internal val reloadTasks = ConcurrentHashMap<Player, Task>()
    /** 0.0-1.0 fraction of the way through the in-progress reload, if any -- see ReloadListener/ActionBarManager. */
    internal val reloadProgress = ConcurrentHashMap<Player, Double>()
    internal val aimingPlayers: MutableSet<Player> = ConcurrentHashMap.newKeySet()
    internal val meleeLastAttackTimes = ConcurrentHashMap<Player, Long>()

    fun initialize() {
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)

        FireListener.init()
        ReloadListener.init()
        AimingListener.init()
        MeleeListener.init()
        WeaponSwapListener.init()
        PlayerDisconnectListener.init()
        ActionBarManager.start()
    }

    /** Drops every per-player entry -- called on disconnect so state maps don't grow unbounded. */
    internal fun clearPlayer(player: Player) {
        playerLastFireTimes.remove(player)
        lastFireInputTimes.remove(player)
        autoFireTasks.remove(player)?.cancel()
        reloadTasks.remove(player)?.cancel()
        reloadProgress.remove(player)
        aimingPlayers.remove(player)
        meleeLastAttackTimes.remove(player)
    }
}
