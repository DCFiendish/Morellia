package net.nodisium.combat.utils

import net.minestom.server.collision.Shape
import net.minestom.server.collision.ShapeImpl
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.block.BlockIterator
import kotlin.math.max
import kotlin.math.min

/** Returns the entry point as a fraction of [vector], or null when the segment misses the box. */
internal fun segmentBoxIntersection(
    origin: Point,
    vector: Vec,
    boxStart: Point,
    boxEnd: Point,
): Double? {
    var entry = 0.0
    var exit = 1.0

    fun intersectsAxis(
        start: Double,
        delta: Double,
        minimum: Double,
        maximum: Double,
    ): Boolean {
        if (delta == 0.0) return start in minimum..maximum
        val first = (minimum - start) / delta
        val second = (maximum - start) / delta
        entry = max(entry, min(first, second))
        exit = min(exit, max(first, second))
        return exit >= entry
    }

    if (!intersectsAxis(origin.x(), vector.x(), boxStart.x(), boxEnd.x())) return null
    if (!intersectsAxis(origin.y(), vector.y(), boxStart.y(), boxEnd.y())) return null
    if (!intersectsAxis(origin.z(), vector.z(), boxStart.z(), boxEnd.z())) return null
    return entry
}

/** A ray cast from [origin] along [vector], checking for collisions up to [vector]'s length. */
class Ray(
    internal val origin: Point,
    internal val vector: Vec,
) {
    val distance: Double = vector.length()
    val direction: Vec = if (distance == 0.0) Vec.ZERO else vector.div(distance)

    data class Hit<T>(
        val t: Double,
        val point: Point,
        val obj: T,
    )

    private fun <S : Shape> cast(
        shape: S,
        offset: Point,
    ): Hit<S>? = hitBox(shape.relativeStart().add(offset), shape.relativeEnd().add(offset), shape)

    fun <T> hitBox(
        boxStart: Point,
        boxEnd: Point,
        obj: T,
    ): Hit<T>? {
        val fraction = segmentBoxIntersection(origin, vector, boxStart, boxEnd) ?: return null
        return Hit(distance * fraction, origin.add(vector.mul(fraction)), obj)
    }

    fun <E : Entity> hitEntity(entity: E): Hit<E>? = cast(entity, entity.position)

    fun <E : Entity> firstEntity(entities: Collection<E>): Hit<E>? {
        var best: Hit<E>? = null
        for (entity in entities) {
            val hit = hitEntity(entity) ?: continue
            if (best == null || hit.t < best.t) best = hit
        }
        return best
    }

    /**
     * Closest block-collision hit, or a synthetic full-block obstruction for the first unloaded
     * chunk or unrecognized collision shape the ray crosses. Fails *closed*: an obstruction we
     * can't resolve stops the ray instead of silently letting it pass through -- the opposite
     * choice (treating both cases as "no hit, keep going") is a confirmed shoot-through-walls
     * vector in prior art, see docs/COMBAT_DEEP_DIVE.md C3. [Hit.obj] is null for the synthetic
     * case since there's no real block to report.
     */
    fun firstBlock(instance: Instance): Hit<Block?>? {
        if (distance == 0.0) {
            return firstBlockAt(instance, Vec(origin.blockX().toDouble(), origin.blockY().toDouble(), origin.blockZ().toDouble()))
        }

        val iterator = BlockIterator(origin.asVec(), direction, 0.0, distance)
        while (iterator.hasNext()) {
            val pos = iterator.next()
            firstBlockAt(instance, pos)?.let { return it }
        }
        return null
    }

    private fun firstBlockAt(
        instance: Instance,
        pos: Point,
    ): Hit<Block?>? {
        val blockOrigin = Vec(pos.blockX().toDouble(), pos.blockY().toDouble(), pos.blockZ().toDouble())
        val blockEnd = blockOrigin.add(1.0, 1.0, 1.0)

        if (!instance.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
            return hitBox(blockOrigin, blockEnd, null)
        }

        val block = instance.getBlock(pos)
        if (block.isAir) return null

        val hitboxes = (block.registry()?.collisionShape() as? ShapeImpl)?.boundingBoxes()
        if (hitboxes == null) {
            return hitBox(blockOrigin, blockEnd, block)
        }

        var best: Hit<Block?>? = null
        for (hitbox in hitboxes) {
            val hit = cast(hitbox, pos.asVec()) ?: continue
            if (best == null || hit.t < best.t) best = Hit(hit.t, hit.point, block)
        }
        return best
    }
}
