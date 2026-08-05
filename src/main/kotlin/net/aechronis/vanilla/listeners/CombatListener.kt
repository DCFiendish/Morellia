package net.aechronis.vanilla.listeners

import net.aechronis.vanilla.Vanilla
import net.aechronis.vanilla.managers.Combat
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerDisconnectEvent

object CombatListener {
    fun onDamage(event: EntityDamageEvent) {
        // nodes' friendly-fire check (NodesPlayerDamageListener, registered on the higher-priority
        // Nodes.highPriorityEventNode) cancels ally/nation damage before this listener's subtree
        // runs. Without this check, allowed friendly-fire sparring still combat-tagged both players
        // even though the "attack" was cancelled/non-hostile -- and combat-tag has real teeth
        // (disconnecting while tagged is an instant-kill).
        if (event.isCancelled) return
        val victim = event.entity as? Player ?: return
        val attacker = event.damage.attacker as? Player ?: return
        if (attacker.uuid == victim.uuid) return

        Combat.tag(attacker, victim)
    }

    fun onDisconnect(event: PlayerDisconnectEvent) {
        val player = event.player
        val wasInCombat = Combat.isInCombat(player)

        Combat.clear(player)
        if (wasInCombat) player.kill()
    }

    fun init() {
        val combatEventNode = EventNode.all("vanilla-combat").setPriority(1000)
        Vanilla.eventNode.addChild(combatEventNode)
        combatEventNode.addListener(EntityDamageEvent::class.java, CombatListener::onDamage)

        Vanilla.eventNode.addListener(PlayerDisconnectEvent::class.java, CombatListener::onDisconnect)
    }
}
