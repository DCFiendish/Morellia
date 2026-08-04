package net.morellia.server

import net.aechronis.combat.objects.Ammo
import net.aechronis.combat.objects.AmmoTypes
import net.aechronis.combat.objects.Gun
import net.aechronis.combat.objects.Item
import net.aechronis.combat.objects.Melee
import net.kyori.adventure.text.Component

// Placeholder musket-era test weapons for solo combat testing. No real resource-pack
// models exist for these yet (item models will fall back to the base Material), and
// stats are unbalanced guesses — just enough to exercise fire/reload/melee mechanics.
object TestWeapons {
    val musketBall =
        Ammo(
            name = "musket_ball",
            ammoType = AmmoTypes.NORMAL,
            itemName = Component.text("Musket Ball"),
        )

    val musket =
        Gun(
            name = "musket",
            itemName = Component.text("Musket"),
            ammo = musketBall,
            maxAmmo = 1,
            damage = 12f,
            automatic = false,
            sniper = false,
            cooldown = 2500,
            reloadTime = 3000,
            recoilMin = 2f,
            recoilMax = 4f,
            spreadMin = 0.5f,
            spreadMax = 2f,
        )

    val bayonet =
        Melee(
            name = "bayonet",
            itemName = Component.text("Bayonet"),
            damage = 6.0,
            attackSpeed = 2.0,
        )

    fun register() {
        Item.registerItems(musketBall, musket, bayonet)
    }
}
