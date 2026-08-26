package net.morellia.server

import net.aechronis.nodes.objects.Territory
import net.aechronis.nodes.objects.TerritoryChunk
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.morellia.combat.objects.Ammo
import net.morellia.combat.objects.AmmoType
import net.morellia.combat.objects.DamageFalloff
import net.morellia.combat.objects.Gun
import net.morellia.combat.objects.Item
import net.morellia.combat.objects.Melee

/**
 * combat itself has no dependency on nodes (see modules/combat/build.gradle.kts) -- this predicate
 * lives here, in server, which already depends on both, and gets injected into Gun.usableZones.
 * Rule: field guns are only usable in wilderness (unclaimed land) or in a chunk currently under
 * active siege -- not inside a town's peacetime territory.
 */
private val wildernessOrWarzoneOnly: (Instance, Pos) -> Boolean = { _, pos ->
    val territory = Territory.fromBlock(pos.blockX(), pos.blockZ())
    val chunk = TerritoryChunk.fromBlock(pos.blockX(), pos.blockZ())
    territory?.town == null || chunk?.attacker != null
}

// Placeholder musket-era test weapons for combat testing, backed by the from-scratch
// modules/combat (replacing the old net.aechronis.combat-based stub -- see docs/HANDOFF.md). No
// real resource-pack models exist yet (item models fall back to the base Material), and stats are
// unbalanced guesses -- just enough to exercise fire/reload/melee/ADS end-to-end locally. See
// docs/research-todo/10-asset-sourcing-and-licensing.md for the asset-sourcing plan.
object TestWeapons {
    val musketBall =
        Ammo(
            name = "musket_ball",
            ammoType = AmmoType.RIFLE,
            itemName = Component.text("Musket Ball"),
        )

    val musket =
        Gun(
            name = "musket",
            itemName = Component.text("Musket"),
            // Mosin Nagant model from the MIT-licensed "WWI & WWII rifles" pack -- see
            // resourcepack/CREDITS.md. Only a bare model right now (no separate empty/reloading/
            // aiming variants -- the pack has fire/bayonet/sniper variants we could map to those
            // later, e.g. mosinchb.json for a fixed-bayonet look).
            // NB: item_model resolves to an *item definition* (assets/<ns>/items/<id>.json), which
            // in turn references the raw model -- it does not point at the raw model directly. This
            // value is the definition's id ("musket"), not the model's path ("item/musket").
            itemModel = "morellia:musket",
            ammo = musketBall,
            magazineSize = 1,
            damageFalloff =
                DamageFalloff(
                    maxDamage = 12f,
                    falloffStartRange = 20.0,
                    falloffEndRange = 80.0,
                    minDamage = 4f,
                ),
            automatic = false,
            cooldownMs = 2500,
            reloadMs = 3000,
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

    val artilleryShell =
        Ammo(
            name = "artillery_shell",
            ammoType = AmmoType.ARTILLERY,
            itemName = Component.text("Artillery Shell"),
        )

    // Demonstrates zone-restricted weapons (docs/HANDOFF.md's guns/vehicles plan §6) -- a horse-drawn
    // field gun, only usable in wilderness or an actively-sieged chunk, wired against real nodes
    // territory data via wildernessOrWarzoneOnly above.
    val fieldGun =
        Gun(
            name = "field_gun",
            itemName = Component.text("Field Gun"),
            ammo = artilleryShell,
            magazineSize = 1,
            damageFalloff =
                DamageFalloff(
                    maxDamage = 40f,
                    falloffStartRange = 10.0,
                    falloffEndRange = 60.0,
                    minDamage = 15f,
                ),
            automatic = false,
            cooldownMs = 6000,
            reloadMs = 8000,
            recoilMin = 6f,
            recoilMax = 10f,
            spreadMin = 1f,
            spreadMax = 3f,
            usableZones = listOf(wildernessOrWarzoneOnly),
        )

    fun register() {
        Item.registerItems(musketBall, musket, bayonet, artilleryShell, fieldGun)
    }
}
