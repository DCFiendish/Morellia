package net.morellia.combat.constants

import net.minestom.server.tag.Tag
import java.util.UUID

internal object Tags {
    const val NAMESPACE = "morellia"

    /** Which registered [net.morellia.combat.objects.Item] a stack corresponds to. */
    val NAME: Tag<String> = Tag.String("combat_item_name")

    /**
     * Stamped fresh onto every stack [net.morellia.combat.objects.Item.toItemStack] produces --
     * lets reload (and anything else that needs to track a *specific physical item*, not just an
     * item type) tell two guns of the same type apart. Without this, a reload-completion check
     * that only compares item type is exploitable: start reloading an empty copy, switch to a
     * second copy of the same gun before the timer completes, and the completing task tops off
     * whichever copy is currently held -- not the one that actually consumed the ammo.
     */
    val INSTANCE_ID: Tag<UUID> = Tag.UUID("combat_instance_id")
}
