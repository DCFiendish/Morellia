package net.morellia.combat.objects

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.RelativeFlags
import net.minestom.server.entity.damage.Damage
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket
import net.morellia.combat.Combat
import net.morellia.combat.constants.Tags
import net.morellia.combat.utils.Ray
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Gun(
    name: String,
    itemName: Component,
    itemLore: List<Component> = emptyList(),
    itemModel: String? = null,
    /** Resource-location variants shown while empty/reloading/aiming -- default to [itemModel] itself when unset. */
    val itemModelEmpty: String? = itemModel,
    val itemModelReloading: String? = itemModel,
    val itemModelAiming: String? = itemModel,
    material: Material = Material.CROSSBOW,
    /** Magazine item consumed 1-per-reload regardless of [magazineSize] -- see ReloadListener. */
    val ammo: Ammo,
    val magazineSize: Int,
    val damageFalloff: DamageFalloff,
    val maxRange: Double = 128.0,
    /** Holding the trigger keeps firing at [cooldownMs] intervals; false requires a fresh click per shot. */
    val automatic: Boolean,
    val cooldownMs: Long,
    val reloadMs: Long,
    /** Every gun kicks by at least [recoilMin] -- there is no zero-recoil weapon by design. */
    val recoilMin: Float,
    val recoilMax: Float,
    val spreadMin: Float,
    val spreadMax: Float,
    /** Rays fired per trigger pull, e.g. a shotgun's pellet count -- one ammo/sound/recoil per pull regardless. */
    val pelletCount: Int = 1,
    /** Movement-speed reduction (0-1 fraction) applied while aiming this gun -- see AimingListener. */
    val adsZoomStrength: Double = 0.5,
    /** Whether aiming this gun applies the pumpkin-vignette trick -- see AimingListener. */
    val adsVignette: Boolean = true,
    val soundFire: Sound = Sound.sound(Key.key("${Tags.NAMESPACE}:$name.fire"), Sound.Source.PLAYER, 5f, 1f),
    val soundReload: Sound = Sound.sound(Key.key("${Tags.NAMESPACE}:$name.reload"), Sound.Source.PLAYER, 3f, 1f),
    /**
     * When non-empty, this gun only fires where at least one predicate returns true for the
     * shooter's current instance/position. Combat itself has no notion of territory/war state --
     * these predicates are injected by whoever defines the weapon, so e.g. server-level code that
     * already depends on modules:nodes can build one from real Territory/FlagWar data without this
     * module ever needing to depend on nodes.
     */
    val usableZones: List<(Instance, Pos) -> Boolean> = emptyList(),
) : Item(name, itemName, itemLore, itemModel, material) {
    init {
        require(magazineSize > 0) { "magazineSize must be > 0" }
        require(maxRange.isFinite() && maxRange > 0.0) { "maxRange must be a positive finite number" }
        require(cooldownMs > 0) { "cooldownMs must be > 0" }
        require(reloadMs > 0) { "reloadMs must be > 0" }
        require(recoilMin > 0f) { "recoilMin must be > 0 -- every gun has at least some recoil" }
        require(pelletCount > 0) { "pelletCount must be > 0" }
    }

    /** Reads remaining ammo from [stack]'s durability-bar encoding (see [setAmmo]). */
    fun getAmmo(stack: ItemStack): Int = magazineSize - (stack.get(DataComponents.DAMAGE) ?: 0)

    fun hasAmmo(stack: ItemStack): Boolean = getAmmo(stack) > 0

    /** HUD text for the loaded/max ammo count, e.g. "Musket [3/5]" -- see ActionBarManager. */
    fun ammoText(stack: ItemStack): Component =
        itemName
            .append(Component.text(" [${getAmmo(stack)}/$magazineSize]", NamedTextColor.GRAY))

    /**
     * Re-applies whichever of [itemModelEmpty]/[itemModelReloading]/[itemModelAiming]/[itemModel]
     * matches [player]'s current ammo/reload/aim state to their held stack, if it's still this Gun
     * (a no-op otherwise -- guards against a listener firing after the player has already switched
     * weapons). Written to the physical ItemStack's component, not sent as a one-off packet, so the
     * variant persists correctly across hotbar switches for free. Called from every state
     * transition that can change which variant applies: [fire] (ammo may hit 0), reload start/end
     * (ReloadListener), and aim press/release (AimingListener).
     */
    fun refreshModel(player: Player) {
        val stack = player.itemInMainHand
        if (Item.getFromItemStack(stack) !== this) return
        val model =
            when {
                Combat.reloadTasks.containsKey(player) -> itemModelReloading
                !hasAmmo(stack) -> itemModelEmpty
                player in Combat.aimingPlayers -> itemModelAiming
                else -> itemModel
            } ?: return
        if (stack.get(DataComponents.ITEM_MODEL) != model) {
            player.itemInMainHand = stack.with(DataComponents.ITEM_MODEL, model)
        }
    }

    /**
     * Returns [stack] with its ammo count set to [amount] (clamped to `0..magazineSize`), encoded
     * via the vanilla damage/durability-bar components so the client renders it without needing a
     * separate GUI element -- `MAX_DAMAGE` is set to this gun's own magazine size (not the base
     * Material's default range) so the bar is an exact 1:1 ammo readout, not a lossy percentage.
     */
    fun setAmmo(
        stack: ItemStack,
        amount: Int,
    ): ItemStack {
        val clamped = amount.coerceIn(0, magazineSize)
        return stack
            .with(DataComponents.MAX_DAMAGE, magazineSize)
            .with(DataComponents.DAMAGE, magazineSize - clamped)
    }

    /**
     * Fires [pelletCount] hitscan ray(s) from [player]'s eye position (one for a normal gun, many
     * for a shotgun -- one ammo/sound/recoil charge regardless of pellet count). Returns false
     * without effect if the cooldown hasn't elapsed, the gun is empty, or a reload is in progress.
     * Cooldown is enforced here unconditionally regardless of [automatic] -- no gun can be macro'd
     * past its own configured rate; see FireListener's kdoc for what [automatic] actually changes.
     *
     * Spread and recoil are independent: spread (aim inaccuracy) is fully suppressed while crouched
     * or while not holding a movement key -- see Combat.movingPlayers/aimingPlayers -- but recoil
     * (the camera kick) always applies at least [recoilMin], only reduced (never zeroed) while
     * crouched/aiming.
     */
    fun fire(player: Player): Boolean {
        val instance = player.instance ?: return false
        val now = System.currentTimeMillis()
        if (now - (Combat.playerLastFireTimes[player] ?: 0L) < cooldownMs) return false
        if (Combat.reloadTasks.containsKey(player)) return false

        val stack = player.itemInMainHand
        if (!hasAmmo(stack)) return false
        if (usableZones.isNotEmpty() && usableZones.none { it(instance, player.position) }) return false

        Combat.playerLastFireTimes[player] = now

        val crouching = player in Combat.aimingPlayers
        val moving = player in Combat.movingPlayers
        val spreadSuppressed = crouching || !moving
        val recoilMultiplier = if (crouching) AIM_RECOIL_MULTIPLIER else 1f

        val potentialTargets = instance.entities.filterIsInstance<LivingEntity>().filter { it != player }

        repeat(pelletCount) {
            val spreadAngle = if (spreadSuppressed) 0f else spreadMin + (spreadMax - spreadMin) * Random.nextFloat()
            val spreadDirectionRad = Math.toRadians((Random.nextFloat() * 360f).toDouble())
            val offsetYaw = player.position.yaw + spreadAngle * cos(spreadDirectionRad).toFloat()
            val offsetPitch = player.position.pitch + spreadAngle * sin(spreadDirectionRad).toFloat()

            val shotOrigin = player.position.withView(offsetYaw, offsetPitch).add(0.0, player.eyeHeight, 0.0)
            val ray = Ray(shotOrigin, shotOrigin.direction().mul(maxRange))

            val blockHit = ray.firstBlock(instance)
            val entityHit = ray.firstEntity(potentialTargets)

            val blockHitDistance = blockHit?.t ?: Double.POSITIVE_INFINITY
            val entityHitDistance = entityHit?.t ?: Double.POSITIVE_INFINITY

            if (entityHit != null && entityHitDistance < blockHitDistance) {
                val damage = damageFalloff.damageAt(entityHitDistance)
                entityHit.obj.damage(Damage.fromProjectile(player, null, damage))
            }
        }

        val firePosition = player.position.add(0.0, player.eyeHeight, 0.0)
        instance.playSound(soundFire, firePosition.x, firePosition.y, firePosition.z)
        recoil(player, recoilMultiplier)
        player.itemInMainHand = setAmmo(stack, getAmmo(stack) - 1)
        refreshModel(player)
        return true
    }

    private fun recoil(
        player: Player,
        multiplier: Float,
    ) {
        player.sendPacket(
            PlayerPositionAndLookPacket(
                -1,
                Pos.ZERO,
                Pos.ZERO,
                0f,
                -(Random.nextFloat() * (recoilMax - recoilMin) + recoilMin) * multiplier,
                RelativeFlags.VIEW or RelativeFlags.COORD or RelativeFlags.DELTA_COORD,
            ),
        )
    }

    companion object {
        /** Recoil while crouched/aiming is reduced, never eliminated -- pairs with the recoilMin > 0 guarantee. */
        private const val AIM_RECOIL_MULTIPLIER = 0.4f
    }
}
