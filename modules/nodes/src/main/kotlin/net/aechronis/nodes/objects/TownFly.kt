package net.aechronis.nodes.objects

import net.minestom.server.entity.Player
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect

object TownFly {
    fun isAllowed(town: Town?, territory: Territory?): Boolean {
        val territoryTown = territory?.town ?: return false
        if (territoryTown === town) return true

        val nation = town?.nation
        return nation != null && territoryTown.nation === nation
    }

    fun disable(player: Player) {
        // See NodesPlayerMoveListener's auto-disable for why both bits are needed --
        // isAllowFlying alone leaves the client still actually flying.
        player.isAllowFlying = false
        player.isFlying = false
        player.addEffect(Potion(PotionEffect.SLOW_FALLING, 0, 100))
    }
}
