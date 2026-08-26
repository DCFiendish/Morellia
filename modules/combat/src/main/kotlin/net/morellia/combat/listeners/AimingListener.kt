package net.morellia.combat.listeners

import net.kyori.adventure.key.Key
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.attribute.AttributeModifier
import net.minestom.server.entity.attribute.AttributeOperation
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent
import net.minestom.server.event.player.PlayerInputEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket
import net.morellia.combat.Combat
import net.morellia.combat.constants.Tags
import net.morellia.combat.objects.Gun
import net.morellia.combat.objects.Item

/**
 * Hold-sneak-to-scope: sneaking while holding a loaded Gun reduces spread/recoil (see Gun.fire)
 * until the player stands back up. This Minestom version has no dedicated start/stop-sneaking
 * event, so the sneak (shift) key's press/release edges come from the general PlayerInputEvent
 * instead.
 *
 * Two independent visual tricks combine into "looking down the barrel":
 * - **Zoom**: vanilla has no server-settable FOV packet, but the client *does* narrow FOV in
 *   proportion to the player's current MOVEMENT_SPEED attribute value even while standing still --
 *   the same mechanism behind the well-known Speed/Slowness potion FOV wobble. A reversible
 *   ADD_MULTIPLIED_TOTAL modifier on that attribute while aiming gets a real vanilla-client zoom
 *   "for free", no resource pack or client mod involved, and doubles as a realistic ADS movement
 *   penalty rather than being a purely cosmetic hack.
 * - **Vignette**: wearing a carved pumpkin as a helmet is hardcoded vanilla-client behaviour that
 *   blocks peripheral vision, exactly the tunnel-vision look of peering through a sight. Spoofing
 *   just the helmet slot's *equipment packet* to this one player (not a real inventory change --
 *   no fire-resistance, no zombie-pigman-aggro side effect, and nothing broadcast to anyone else)
 *   gets that effect without touching the player's actual armor.
 */
object AimingListener {
    private val AIM_SPEED_MODIFIER_ID = Key.key("${Tags.NAMESPACE}:combat.aiming_speed")
    private val SCOPE_VIGNETTE_HELMET = ItemStack.of(Material.CARVED_PUMPKIN)

    private fun onInput(event: PlayerInputEvent) {
        val player = event.player
        val gun = Item.getFromItemStack(player.itemInMainHand) as? Gun ?: return

        if (event.hasPressedShiftKey()) {
            startAiming(player, gun)
        } else if (event.hasReleasedShiftKey()) {
            stopAiming(player)
        }
    }

    /** Clears aiming state (speed modifier + vignette) if the player hotbar-switches off a Gun mid-aim. */
    private fun onHeldSlotChange(event: PlayerChangeHeldSlotEvent) {
        val player = event.player
        if (player !in Combat.aimingPlayers) return
        if (Item.getFromItemStack(event.getItemInNewSlot()) !is Gun) stopAiming(player)
    }

    private fun startAiming(
        player: Player,
        gun: Gun,
    ) {
        if (!Combat.aimingPlayers.add(player)) return
        val speedModifier = AttributeModifier(AIM_SPEED_MODIFIER_ID, -gun.adsZoomStrength, AttributeOperation.ADD_MULTIPLIED_TOTAL)
        player.getAttribute(Attribute.MOVEMENT_SPEED).addModifier(speedModifier)
        if (gun.adsVignette && gun.hasAmmo(player.itemInMainHand)) {
            player.sendPacket(EntityEquipmentPacket(player.entityId, mapOf(EquipmentSlot.HELMET to SCOPE_VIGNETTE_HELMET)))
        }
        gun.refreshModel(player)
    }

    private fun stopAiming(player: Player) {
        if (!Combat.aimingPlayers.remove(player)) return
        player.getAttribute(Attribute.MOVEMENT_SPEED).removeModifier(AIM_SPEED_MODIFIER_ID)
        player.sendPacket(EntityEquipmentPacket(player.entityId, mapOf(EquipmentSlot.HELMET to player.helmet)))
        (Item.getFromItemStack(player.itemInMainHand) as? Gun)?.refreshModel(player)
    }

    fun init() {
        Combat.eventNode.addListener(PlayerInputEvent::class.java, ::onInput)
        Combat.eventNode.addListener(PlayerChangeHeldSlotEvent::class.java, ::onHeldSlotChange)
    }
}
