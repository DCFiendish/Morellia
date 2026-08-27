package net.morellia.combat.objects

import net.kyori.adventure.text.Component
import net.minestom.server.item.Material

/** Broad ammo families -- extend as new weapon categories need their own reserve ammo item. */
enum class AmmoType {
    PISTOL,
    RIFLE,
    SHOTGUN,
    MACHINE_GUN,
    ARTILLERY,
}

/**
 * A magazine item, not a loose round: one stack unit is consumed per reload regardless of the
 * gun's [Gun.magazineSize] -- see ReloadListener. Guns of different types generally get their own
 * [Ammo] instance (e.g. musket ball vs. shotgun shell), though nothing stops two guns sharing one
 * if they're meant to share a magazine type.
 */
class Ammo(
    name: String,
    val ammoType: AmmoType,
    itemName: Component,
    itemLore: List<Component> = emptyList(),
    itemModel: String? = null,
    material: Material = Material.IRON_NUGGET,
) : Item(name, itemName, itemLore, itemModel, material)
