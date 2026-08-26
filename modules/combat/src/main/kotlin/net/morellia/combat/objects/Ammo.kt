package net.morellia.combat.objects

import net.kyori.adventure.text.Component
import net.minestom.server.item.Material

/** Broad ammo families -- extend as new weapon categories need their own reserve ammo item. */
enum class AmmoType {
    RIFLE,
    MACHINE_GUN,
    ARTILLERY,
}

class Ammo(
    name: String,
    val ammoType: AmmoType,
    itemName: Component,
    itemLore: List<Component> = emptyList(),
    itemModel: String? = null,
    material: Material = Material.IRON_NUGGET,
) : Item(name, itemName, itemLore, itemModel, material)
