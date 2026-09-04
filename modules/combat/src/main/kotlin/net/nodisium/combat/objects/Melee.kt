package net.nodisium.combat.objects

import net.kyori.adventure.text.Component
import net.minestom.server.item.Material

class Melee(
    name: String,
    itemName: Component,
    itemLore: List<Component> = emptyList(),
    itemModel: String? = null,
    material: Material = Material.WOODEN_SWORD,
    val damage: Double,
    val attackSpeed: Double,
    /** Server-side max attacker-to-target distance for a hit to land -- see MeleeListener. */
    val maxReach: Double = 3.0,
) : Item(name, itemName, itemLore, itemModel, material) {
    init {
        require(damage >= 0.0) { "damage must be >= 0" }
        require(attackSpeed > 0.0) { "attackSpeed must be > 0" }
        require(maxReach > 0.0) { "maxReach must be > 0" }
    }
}
