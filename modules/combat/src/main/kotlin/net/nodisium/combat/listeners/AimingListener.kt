package net.nodisium.combat.listeners

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
import net.nodisium.combat.Combat
import net.nodisium.combat.constants.Tags
import net.nodisium.combat.objects.Gun
import net.nodisium.combat.objects.Item

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
 * - **Vignette** (opt-in via Gun.adsVignette, off by default): wearing a carved pumpkin as a helmet
 *   is hardcoded vanilla-client behaviour that blocks peripheral vision, exactly the tunnel-vision
 *   look of a telescopic sniper scope -- not appropriate for a gun aiming down iron sights, which
 *   gets its "peering down the barrel" look from itemModelAiming's repositioned first-person
 *   transform instead (see Gun.refreshModel). Spoofing just the helmet slot's *equipment packet* to
 *   this one player (not a real inventory change -- no fire-resistance, no zombie-pigman-aggro side
 *   effect, and nothing broadcast to anyone else) gets that effect without touching the player's
 *   actual armor, reserved for an actual scoped weapon.
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

    /**
     * Clears aiming state (speed modifier + vignette) if the player hotbar-switches off a Gun
     * mid-aim -- or, if they switch to a *different* Gun while still aiming (no shift press/release
     * edge fires on a hotbar swap, so [startAiming] never re-runs on its own), re-applies the aim
     * effects using the new gun's own adsZoomStrength/adsVignette instead of leaving the old gun's
     * values stuck on.
     *
     * Passes [PlayerChangeHeldSlotEvent.getItemInNewSlot] down to [applyAimEffects] instead of
     * letting it read player.itemInMainHand itself -- confirmed against Minestom's own
     * PlayerHeldListener (the packet handler that fires this event): it constructs the event from
     * the *old* held slot and only calls Player.setHeldItemSlot afterward, once the event returns
     * uncancelled. So at the time this listener runs, player.itemInMainHand is still the outgoing
     * gun's stack, not the new one -- reading it here for e.g. the ammo-gated vignette check would
     * silently show/hide the scope based on the wrong weapon.
     */
    private fun onHeldSlotChange(event: PlayerChangeHeldSlotEvent) {
        val player = event.player
        if (player !in Combat.aimingPlayers) return
        val newStack = event.getItemInNewSlot()
        val newGun = Item.getFromItemStack(newStack) as? Gun
        if (newGun == null) stopAiming(player) else applyAimEffects(player, newGun, newStack)
    }

    private fun startAiming(
        player: Player,
        gun: Gun,
    ) {
        if (!Combat.aimingPlayers.add(player)) return
        applyAimEffects(player, gun, player.itemInMainHand)
    }

    /** (Re-)applies [gun]'s zoom/vignette to [player] using [stack] (the gun's own item), replacing whichever gun's values were active before. */
    private fun applyAimEffects(
        player: Player,
        gun: Gun,
        stack: ItemStack,
    ) {
        val speedAttribute = player.getAttribute(Attribute.MOVEMENT_SPEED)
        speedAttribute.removeModifier(AIM_SPEED_MODIFIER_ID)
        speedAttribute.addModifier(AttributeModifier(AIM_SPEED_MODIFIER_ID, -gun.adsZoomStrength, AttributeOperation.ADD_MULTIPLIED_TOTAL))
        val helmet = if (gun.adsVignette && gun.hasAmmo(stack)) SCOPE_VIGNETTE_HELMET else player.helmet
        player.sendPacket(EntityEquipmentPacket(player.entityId, mapOf(EquipmentSlot.HELMET to helmet)))
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
