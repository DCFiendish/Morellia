package net.morellia.combat.listeners

import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.morellia.combat.Combat
import net.morellia.combat.objects.Gun
import net.morellia.combat.objects.Item
import net.morellia.combat.objects.Melee

object MeleeListener {
    /**
     * The vanilla client sends attack-on-entity (this event) instead of the swing-only animation
     * FireListener listens for whenever a target is within melee range -- so at point-blank range a
     * gun-holding player's click never reached FireListener at all and landed a bare fist punch
     * instead of a shot. EntityAttackEvent isn't cancellable, so the fix is to fire the gun from
     * here directly rather than let the (nonexistent) fist damage go through.
     */
    private fun onAttack(event: EntityAttackEvent) {
        val attacker = event.entity as? Player ?: return
        val gun = Item.getFromItemStack(attacker.itemInMainHand) as? Gun
        if (gun != null) {
            gun.fire(attacker)
            return
        }
        val target = event.target as? LivingEntity ?: return
        val melee = Item.getFromItemStack(attacker.itemInMainHand) as? Melee ?: return

        // C4 fix: explicit server-side reach check -- the prior-art library this replaces never
        // checked attacker-to-target distance at all (docs/COMBAT_DEEP_DIVE.md C4).
        if (attacker.position.distance(target.position) > melee.maxReach) return

        val now = System.currentTimeMillis()
        val cooldownMs = (1000.0 / melee.attackSpeed).toLong()
        if (!Combat.tryStartMeleeCooldown(attacker, melee, now, cooldownMs)) return

        target.damage(Damage(DamageType.PLAYER_ATTACK, attacker, attacker, null, melee.damage.toFloat()))
    }

    /** Same fist-lockout rule as [onAttack] -- a gun's carrier material (e.g. iron_ingot) is an insta-break tool for some blocks. */
    private fun onBlockBreak(event: PlayerBlockBreakEvent) {
        if (Item.getFromItemStack(event.player.itemInMainHand) is Gun) {
            event.isCancelled = true
        }
    }

    fun init() {
        Combat.eventNode.addListener(EntityAttackEvent::class.java, ::onAttack)
        Combat.eventNode.addListener(PlayerBlockBreakEvent::class.java, ::onBlockBreak)
    }
}
