package net.morellia.combat

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.timer.Task
import net.morellia.combat.listeners.AimingListener
import net.morellia.combat.listeners.FireListener
import net.morellia.combat.listeners.MeleeListener
import net.morellia.combat.listeners.MovementListener
import net.morellia.combat.listeners.PlayerDisconnectListener
import net.morellia.combat.listeners.ReloadListener
import net.morellia.combat.listeners.WeaponSwapListener
import net.morellia.combat.objects.Gun
import net.morellia.combat.objects.Melee
import net.morellia.combat.tasks.ActionBarManager
import net.morellia.combat.tasks.ModelRefreshTask
import java.util.concurrent.ConcurrentHashMap

/**
 * Central per-player combat state. Every map here is a concurrent collection from the start --
 * Minestom's per-chunk-parallel tick dispatch means plain HashMap/HashSet is a real race under
 * this project's threading model (confirmed for this exact concern in
 * docs/research-todo/01-concurrency-model.md), not a theoretical one.
 */
object Combat {
    val eventNode: EventNode<net.minestom.server.event.Event> = EventNode.all("combat")

    /**
     * Keyed by player, valued by (which Gun, when) -- not just a bare timestamp. A cooldown is only
     * ever enforced against the *same* gun that set it (see Gun.fire/ActionBarManager); without the
     * gun half of this pair, switching from a slow-cooldown gun to a fast one right after firing
     * would carry the slow gun's recent timestamp over and block the fast gun from firing at all,
     * for a weapon that was never even used yet.
     */
    internal val playerLastFireTimes = ConcurrentHashMap<Player, Pair<Gun, Long>>()
    internal val lastFireInputTimes = ConcurrentHashMap<Player, Long>()
    internal val autoFireTasks = ConcurrentHashMap<Player, Task>()
    internal val reloadTasks = ConcurrentHashMap<Player, Task>()
    /** 0.0-1.0 fraction of the way through the in-progress reload, if any -- see ReloadListener/ActionBarManager. */
    internal val reloadProgress = ConcurrentHashMap<Player, Double>()
    internal val aimingPlayers: MutableSet<Player> = ConcurrentHashMap.newKeySet()
    /** Players currently holding a WASD movement key -- see MovementListener, read by Gun.fire for spread. */
    internal val movingPlayers: MutableSet<Player> = ConcurrentHashMap.newKeySet()
    /** Players currently sprinting -- see MovementListener, read by Gun.fire for the running-spread penalty. */
    internal val sprintingPlayers: MutableSet<Player> = ConcurrentHashMap.newKeySet()
    /** Same (which weapon, when) shape as [playerLastFireTimes], same reason -- see MeleeListener. */
    internal val meleeLastAttackTimes = ConcurrentHashMap<Player, Pair<Melee, Long>>()

    fun initialize() {
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)

        FireListener.init()
        ReloadListener.init()
        AimingListener.init()
        MovementListener.init()
        MeleeListener.init()
        WeaponSwapListener.init()
        PlayerDisconnectListener.init()
        ActionBarManager.start()
        ModelRefreshTask.start()
    }

    /** Drops every per-player entry -- called on disconnect so state maps don't grow unbounded. */
    internal fun clearPlayer(player: Player) {
        playerLastFireTimes.remove(player)
        lastFireInputTimes.remove(player)
        autoFireTasks.remove(player)?.cancel()
        reloadTasks.remove(player)?.cancel()
        reloadProgress.remove(player)
        aimingPlayers.remove(player)
        movingPlayers.remove(player)
        sprintingPlayers.remove(player)
        meleeLastAttackTimes.remove(player)
    }
}
