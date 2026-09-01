package net.aechronis.vanilla.listeners

import net.aechronis.vanilla.Vanilla
import net.aechronis.vanilla.managers.Items
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.component.EnchantmentList
import net.minestom.server.item.enchant.Enchantment

object PlayerBreakListener {
    fun onBlockBreak(event: PlayerBlockBreakEvent) {
        if (event.isCancelled) return
        val player = event.player
        // Reach-hack guard: reject breaks farther than vanilla's ~6-block interaction range
        // instead of trusting the client-reported block position.
        val blockCenter = event.blockPosition.asVec().add(0.5, 0.5, 0.5)
        if (player.position.distanceSquared(blockCenter) > 36.0) {
            event.isCancelled = true
            return
        }
        // MusicListener owns jukebox drops (block + record) when the music feature is enabled;
        // without this, both listeners spawn a duplicate jukebox item depending on listener order.
        if (Vanilla.config.musicEnabled && event.block.compare(Block.JUKEBOX)) return
        if (player.gameMode == GameMode.CREATIVE) return
        val instance = player.instance ?: return
        val material = event.block.registry()?.material() ?: return

        val config = Vanilla.config
        val heldItem = player.itemInMainHand
        val hasSilkTouch = heldItem.get(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY).has(Enchantment.SILK_TOUCH)
        val silkTouchApplies = hasSilkTouch && material in config.blocksConfig.blocksSilkTouchable

        if (material in config.blocksConfig.blocksRequiringTool) {
            val heldMaterial = heldItem.material()
            val canMine = config.blocksConfig.toolMinableBlocks[heldMaterial]?.contains(material) == true
            if (!canMine) {
                event.isCancelled = true
                return
            }
        }

        val drops =
            if (silkTouchApplies) {
                listOf(ItemStack.of(material))
            } else {
                config.blocksConfig.blockDrops[material] ?: listOf(ItemStack.of(material))
            }
        val dropPos = event.blockPosition.add(0.5, 0.5, 0.5).asPos()
        for (stack in drops) {
            if (!stack.isAir && stack.amount() > 0) Items.spawn(instance, dropPos, stack)
        }
    }

    fun init() {
        Vanilla.eventNode.addListener(PlayerBlockBreakEvent::class.java, PlayerBreakListener::onBlockBreak)
    }
}
