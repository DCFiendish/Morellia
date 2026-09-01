package net.morellia.combat.listeners

import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityAttackEvent
import net.morellia.combat.Combat
import net.morellia.combat.objects.Item
import net.morellia.combat.objects.Melee

object MeleeListener {
    private fun onAttack(event: EntityAttackEvent) {
        val attacker = event.entity as? Player ?: return
        val target = event.target as? LivingEntity ?: return
        val melee = Item.getFromItemStack(attacker.itemInMainHand) as? Melee ?: return

        // C4 fix: explicit server-side reach check -- the prior-art library this replaces never
        // checked attacker-to-target distance at all (docs/COMBAT_DEEP_DIVE.md C4).
        if (attacker.position.distance(target.position) > melee.maxReach) return

        val now = System.currentTimeMillis()
        val cooldownMs = (1000.0 / melee.attackSpeed).toLong()
        val lastAttack = Combat.meleeLastAttackTimes[attacker]
        if (lastAttack != null && lastAttack.first === melee && now - lastAttack.second < cooldownMs) return
        Combat.meleeLastAttackTimes[attacker] = melee to now

        target.damage(Damage(DamageType.PLAYER_ATTACK, attacker, attacker, null, melee.damage.toFloat()))
    }

    fun init() {
        Combat.eventNode.addListener(EntityAttackEvent::class.java, ::onAttack)
    }
}
