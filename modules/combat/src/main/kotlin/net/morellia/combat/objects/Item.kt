package net.morellia.combat.objects

import net.kyori.adventure.text.Component
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.morellia.combat.constants.Tags
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Base class for every registered combat item (guns, melee weapons, ammo). */
open class Item(
    val name: String,
    val itemName: Component,
    val itemLore: List<Component> = emptyList(),
    val itemModel: String? = null,
    val material: Material = Material.STICK,
) {
    /** Builds a fresh physical stack. Every call stamps a new [Tags.INSTANCE_ID] -- see its kdoc. */
    open fun toItemStack(): ItemStack {
        var stack =
            ItemStack.of(material)
                .with(DataComponents.ITEM_NAME, itemName)
                .with(DataComponents.LORE, itemLore)
                .withTag(Tags.NAME, name)
                .withTag(Tags.INSTANCE_ID, UUID.randomUUID())
        if (itemModel != null) stack = stack.with(DataComponents.ITEM_MODEL, itemModel)
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
