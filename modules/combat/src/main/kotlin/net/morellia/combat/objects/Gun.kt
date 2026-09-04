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
import net.minestom.server.item.component.CustomModelData
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket
import net.minestom.server.particle.Particle
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
    customModelData: String? = null,
    /**
     * obj³-pipeline aiming pose (see class kdoc on [Item.customModelData]) -- default to
     * [customModelData] itself when unset, same convention as [itemModelAiming]. Only "aiming" is
     * supported here for now (no empty/reloading obj³ variant yet), unlike the item_model pipeline.
     */
    val customModelDataAiming: String? = customModelData,
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
    /** Multiplies [spreadMin]/[spreadMax] while the shooter is sprinting (see Combat.sprintingPlayers). */
    val sprintSpreadMultiplier: Float = 3f,
    /** Rays fired per trigger pull, e.g. a shotgun's pellet count -- one ammo/sound/recoil per pull regardless. */
    val pelletCount: Int = 1,
    /** Movement-speed reduction (0-1 fraction) applied while aiming this gun -- see AimingListener. */
    val adsZoomStrength: Double = 0.5,
    /**
     * Whether aiming this gun applies the pumpkin-vignette trick -- see AimingListener. Defaults
     * off: the vignette is a full-tunnel-vision "sniper scope" look, not appropriate for a gun
     * that's just aiming down iron sights (that's carried entirely by itemModelAiming's repositioned
     * first-person transform + the movement-speed FOV zoom). Reserve true for an actual scoped
     * weapon later.
     */
    val adsVignette: Boolean = false,
    val soundFire: Sound = Sound.sound(Key.key("${Tags.NAMESPACE}:$name.fire"), Sound.Source.PLAYER, 5f, 1f),
    val soundReload: Sound = Sound.sound(Key.key("${Tags.NAMESPACE}:$name.reload"), Sound.Source.PLAYER, 3f, 1f),
    /** Dry-fire click when the trigger's pulled with an empty magazine and no reserve to auto-reload from. */
    val soundEmpty: Sound = Sound.sound(Key.key("minecraft:block.lever.click"), Sound.Source.PLAYER, 1f, 0.7f),
    /**
     * When non-empty, this gun only fires where at least one predicate returns true for the
     * shooter's current instance/position. Combat itself has no notion of territory/war state --
     * these predicates are injected by whoever defines the weapon, so e.g. server-level code that
     * already depends on modules:nodes can build one from real Territory/FlagWar data without this
     * module ever needing to depend on nodes.
     */
    val usableZones: List<(Instance, Pos) -> Boolean> = emptyList(),
) : Item(name, itemName, itemLore, itemModel, material, customModelData) {
    /** Stamps a fresh [Tags.INSTANCE_ID] on top of the base stack -- see ReloadListener's kdoc. */
    override fun toItemStack(): ItemStack = super.toItemStack().withTag(Tags.INSTANCE_ID, java.util.UUID.randomUUID())

    init {
        require(magazineSize > 0) { "magazineSize must be > 0" }
        require(maxRange.isFinite() && maxRange > 0.0) { "maxRange must be a positive finite number" }
        require(cooldownMs > 0) { "cooldownMs must be > 0" }
        require(reloadMs > 0) { "reloadMs must be > 0" }
        require(recoilMin > 0f) { "recoilMin must be > 0 -- every gun has at least some recoil" }
        require(recoilMax >= recoilMin) { "recoilMax must be >= recoilMin" }
        require(spreadMin >= 0f) { "spreadMin must be >= 0" }
        require(spreadMax >= spreadMin) { "spreadMax must be >= spreadMin" }
        require(sprintSpreadMultiplier >= 1f) { "sprintSpreadMultiplier must be >= 1" }
        require(pelletCount > 0) { "pelletCount must be > 0" }
        require(damageFalloff.falloffEndRange <= maxRange) {
            "damageFalloff.falloffEndRange (${damageFalloff.falloffEndRange}) must be <= maxRange ($maxRange) -- " +
                "fire()'s hitscan ray never travels past maxRange, so a falloff tail beyond it can never apply"
        }
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
     * transition that can change which variant applies ([fire] as ammo may hit 0, reload start/end,
     * aim press/release) as an immediate best-effort update, *and* unconditionally every tick from
     * [net.morellia.combat.tasks.ModelRefreshTask] regardless of whether the target model actually
     * changed since last tick.
     *
     * The per-tick resend is the actual fix for the "aiming pose never visually changes" bug (see
     * docs/HANDOFF.md, root-caused 2026-08-27): a one-shot set on the aim-press edge alone gets
     * silently lost client-side and the held item keeps rendering its old baked model/transform.
     * Confirmed against `Aechronis/aechronis`'s own working `ModelManager.updateModel`
     * (`modules/combat/.../tasks/ModelManager.kt`), which does the identical unconditional per-tick
     * reassignment -- and whose `ak47`/`ak47-aiming` model pair also proved the swap needs no
     * geometry divergence to render correctly (their two models' `elements` arrays are
     * byte-identical, only `display` differs), ruling out the previously-suspected client-side
     * mesh-dedup-by-geometry theory.
     */
    fun refreshModel(player: Player) {
        val stack = player.itemInMainHand
        if (Item.getFromItemStack(stack) !== this) return
        val aiming = player in Combat.aimingPlayers

        var updated = stack
        val targetItemModel =
            when {
                Combat.reloadTasks.containsKey(player) -> itemModelReloading
                !hasAmmo(stack) -> itemModelEmpty
                aiming -> itemModelAiming
                else -> itemModel
            }
        // Compared against the stack's current component before calling .with() -- .with() always
        // allocates a new ItemStack even when the resulting data is identical, and this runs every
        // tick (ModelRefreshTask) for every gun-holding player, so skipping the call entirely on the
        // (overwhelmingly common) no-change tick avoids that allocation instead of just discarding it
        // afterward. Network-wise this was already harmless either way -- Minestom's own
        // AbstractInventory.setItemStack does an ItemStack.equals() check before sending anything --
        // but there's no reason to pay the allocation for a result that gets thrown away.
        if (targetItemModel != null && targetItemModel != stack.get(DataComponents.ITEM_MODEL)) {
            updated = updated.with(DataComponents.ITEM_MODEL, targetItemModel)
        }

        // obj³-pipeline guns select their baked model via custom_model_data instead of item_model
        // (see Item's customModelData kdoc) -- independent of the swap above, a gun can use either
        // path, both, or neither.
        val targetCustomModelData = if (aiming) customModelDataAiming else customModelData
        if (targetCustomModelData != null) {
            val newComponent = CustomModelData(listOf(), listOf(), listOf(targetCustomModelData), listOf())
            if (newComponent != stack.get(DataComponents.CUSTOM_MODEL_DATA)) {
                updated = updated.with(DataComponents.CUSTOM_MODEL_DATA, newComponent)
            }
        }

        if (updated !== stack) player.itemInMainHand = updated
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
     * Spread and recoil are independent. Spread (aim inaccuracy) is fully suppressed while crouched
     * or while standing still, uses [spreadMin]/[spreadMax] while walking, and that range further
     * scaled by [sprintSpreadMultiplier] while sprinting -- see Combat.aimingPlayers/movingPlayers/
     * sprintingPlayers. Recoil (the camera kick) always applies at least [recoilMin], only reduced
     * (never zeroed) while crouched/aiming, and is not affected by movement/sprint state.
     */
    fun fire(player: Player): Boolean {
        val instance = player.instance ?: return false
        val now = System.currentTimeMillis()
        if (Combat.reloadTasks.containsKey(player)) return false

        val stack = player.itemInMainHand
        if (!hasAmmo(stack)) {
            instance.playSound(soundEmpty, player.position.x, player.position.y, player.position.z)
            return false
        }
        if (usableZones.isNotEmpty() && usableZones.none { it(instance, player.position) }) return false

        if (!Combat.tryStartFireCooldown(player, this, now)) return false

        val crouching = player in Combat.aimingPlayers
        val moving = player in Combat.movingPlayers
        val sprinting = player in Combat.sprintingPlayers
        val spreadSuppressed = crouching || !moving
        val spreadMultiplier = if (sprinting) sprintSpreadMultiplier else 1f
        val recoilMultiplier = if (crouching) AIM_RECOIL_MULTIPLIER else 1f

        // Range-bounded via Minestom's chunk-indexed EntityTracker, not a full instance.entities scan
        // -- this runs on every trigger pull (up to several times/sec for an automatic gun), so
        // scanning every entity in the whole instance regardless of distance is wasted work,
        // worse the more entities/simultaneously-shooting players are around.
        val potentialTargets = instance.getNearbyEntities(player.position, maxRange).filterIsInstance<LivingEntity>().filter { it != player }

        repeat(pelletCount) {
            val spreadAngle =
                if (spreadSuppressed) {
                    0f
                } else {
                    (spreadMin + (spreadMax - spreadMin) * Random.nextFloat()) * spreadMultiplier
                }
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
        muzzleFlash(instance, firePosition, player.position.direction())
        recoil(player, recoilMultiplier)
        player.itemInMainHand = setAmmo(stack, getAmmo(stack) - 1)
        refreshModel(player)
        return true
    }

    /**
     * A handful of [Particle.SMOKE] puffs at the muzzle (eye position nudged forward along the
     * shot direction), broadcast to everyone in the instance the same way [soundFire] already is.
     * Vanilla particle with its own built-in lifetime/fade -- drifts and fades over roughly a
     * second on its own, no manual timer needed here.
     */
    private fun muzzleFlash(
        instance: Instance,
        firePosition: Pos,
        direction: net.minestom.server.coordinate.Vec,
    ) {
        val muzzle = firePosition.add(direction.mul(MUZZLE_OFFSET))
        instance.sendGroupedPacket(
            ParticlePacket(Particle.SMOKE, muzzle.x(), muzzle.y(), muzzle.z(), 0.05f, 0.05f, 0.05f, 0.02f, 6),
        )
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

        /** Blocks in front of the eye the muzzle flash/smoke spawns at -- roughly barrel-tip distance. */
        private const val MUZZLE_OFFSET = 1.2
    }
}
