package net.aechronis.nodes.objects

import net.minestom.server.entity.Player
import net.minestom.server.item.Material

/**
 * A building that converts a fixed recipe of input materials into output materials.
 * Concrete factories subclass [ActiveFactory] or [PassiveFactory] -- never this class
 * directly.
 */
abstract class Factory(chunkX: Int, chunkZ: Int, tier: Int) : Building(chunkX, chunkZ, tier) {
    /** Materials and amounts one production cycle consumes. Empty if it takes no input. */
    abstract fun recipeInputs(): Map<Material, Int>

    /** Materials and amounts one production cycle yields. */
    abstract fun recipeOutputs(): Map<Material, Int>
}

/**
 * Produces on a fixed schedule with no player interaction -- the same mechanism as
 * [Building.income], collected by IncomeManager's periodic sweep. [recipeInputs]/[recipeOutputs]
 * describe what one cycle converts; override [income] (already inherited from [Building]) to
 * report what that cycle actually credits to the owning town.
 */
abstract class PassiveFactory(chunkX: Int, chunkZ: Int, tier: Int) : Factory(chunkX, chunkZ, tier)

/**
 * Produces only when a player triggers a cycle via [activate]. Wire the trigger to whatever
 * interaction a concrete factory uses (block interact, a command, a GUI button); implementations
 * should check [recipeInputs] are available before applying [recipeOutputs], and return whether
 * a cycle actually ran.
 */
abstract class ActiveFactory(chunkX: Int, chunkZ: Int, tier: Int) : Factory(chunkX, chunkZ, tier) {
    abstract fun activate(player: Player): Boolean
}
