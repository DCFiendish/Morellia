package net.nodisium.combat.objects

import net.kyori.adventure.text.Component
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData
import net.nodisium.combat.constants.Tags
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Base class for every registered combat item (guns, melee weapons, ammo). */
open class Item(
    val name: String,
    val itemName: Component,
    val itemLore: List<Component> = emptyList(),
    val itemModel: String? = null,
    val material: Material = Material.STICK,
    /**
     * obj³-baked mesh weapons select their model via a `custom_model_data` string on a carrier
     * material (e.g. iron_ingot), not the `item_model` path [itemModel] uses -- see
     * TestGunGive.kt/DevLoadout.kt's obj³ items and docs/HANDOFF.md's obj³ playbook. The two are
     * independent: a gun can set either, both, or neither.
     */
    val customModelData: String? = null,
) {
    /**
     * Builds a fresh physical stack. Does *not* stamp [Tags.INSTANCE_ID] -- only [Gun] needs that
     * (see ReloadListener), and every other item here is fungible and must stay vanilla-stackable:
     * a per-call random tag would make every stack's data components unique, so the client could
     * never merge two of them past count 1 (e.g. Ammo, which players expect to stack to 64).
     */
    open fun toItemStack(): ItemStack {
        var stack =
            ItemStack.of(material)
                .with(DataComponents.ITEM_NAME, itemName)
                .with(DataComponents.LORE, itemLore)
                .withTag(Tags.NAME, name)
        if (itemModel != null) stack = stack.with(DataComponents.ITEM_MODEL, itemModel)
        if (customModelData != null) {
            stack = stack.with(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(listOf(), listOf(), listOf(customModelData), listOf()))
        }
        return stack
    }

    companion object {
        private val registry = ConcurrentHashMap<String, Item>()

        fun registerItems(vararg items: Item) {
            items.forEach { registry[it.name] = it }
        }

        /** Looks up the registered [Item] a stack was built from, or null if it isn't one. */
        fun getFromItemStack(stack: ItemStack): Item? {
            val name = stack.getTag(Tags.NAME) ?: return null
            return registry[name]
        }

        internal fun instanceId(stack: ItemStack): UUID? = stack.getTag(Tags.INSTANCE_ID)
    }
}
