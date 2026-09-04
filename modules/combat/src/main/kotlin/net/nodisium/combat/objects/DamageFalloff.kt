package net.nodisium.combat.objects

/**
 * Linear damage falloff by hit distance: full [maxDamage] up to [falloffStartRange], interpolating
 * down to [minDamage] at [falloffEndRange], then flat [minDamage] beyond that -- a hit past max
 * range still does *something*, matching how real firearms don't have a hard damage cliff.
 */
data class DamageFalloff(
    val maxDamage: Float,
    val falloffStartRange: Double,
    val falloffEndRange: Double,
    val minDamage: Float,
) {
    init {
        require(falloffStartRange >= 0.0) { "falloffStartRange must be >= 0" }
        require(falloffEndRange >= falloffStartRange) { "falloffEndRange must be >= falloffStartRange" }
        require(minDamage in 0f..maxDamage) { "minDamage must be between 0 and maxDamage" }
    }

    fun damageAt(distance: Double): Float {
        if (distance <= falloffStartRange) return maxDamage
        if (distance >= falloffEndRange) return minDamage
        val progress = (distance - falloffStartRange) / (falloffEndRange - falloffStartRange)
        return maxDamage + (minDamage - maxDamage) * progress.toFloat()
    }
}
