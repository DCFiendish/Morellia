package net.morellia.server

import me.lucko.luckperms.minestom.CommandRegistry
import me.lucko.luckperms.minestom.LuckPermsMinestom
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.Node
import net.luckperms.api.node.types.InheritanceNode
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.UUID

/**
 * Real LuckPerms (ConceptMC's Minestom port, same lib+convention as Aechronis/aechronis's own
 * Server.kt), replacing the no-provider-registered state every `net.aechronis.utils.hasPermission`
 * check has been silently failing closed against (see server/build.gradle.kts's prior comment on
 * why `com.conceptmc:luckperms-minestom` was already a dependency) -- every permission-gated
 * command (`/warp`, `/ec`, `/nda`, `/testgun`, `//` worldedit, ...) currently denies everyone,
 * DCFiendish included, since nothing was ever enabled to answer those checks.
 *
 * Seeds three groups on first boot only (skipped once `staff` already exists, so retuning via
 * `/lp` in-game afterward sticks across restarts): `default` (base gameplay -- every player lands
 * here automatically, LuckPerms' own reserved name), `mod` (inherits default, adds moderation
 * tools), `staff` (inherits mod, gets `*` -- everything, including nodes.admin/worldedit/spark).
 * DCFiendish is seeded into `staff` directly so admin access isn't lost the moment this goes live.
 */
object Permissions {
    private val defaultNodes = setOf(
        "vanilla.warp", "vanilla.ec", "vanilla.craft", "vanilla.koth", "vanilla.kit",
        "vanilla.list", "vanilla.ignore", "vanilla.music", "vanilla.back",
    )
    private val modNodes = setOf(
        "vanilla.invsee", "vanilla.kill", "vanilla.teleport", "vanilla.gamemode",
        "vanilla.clear", "vanilla.fly", "vanilla.broadcast", "vanilla.whitelist", "vanilla.give",
    )

    fun init() {
        LuckPermsMinestom
            .builder(Path.of("morellia-data/luckperms"))
            .permissionSuggestions(defaultNodes + modNodes + setOf("nodes.admin", "morellia.testgun", "vanilla.setwarp", "vanilla.convert", "*"))
            .commandRegistry(CommandRegistry.minestom())
            .enable()

        val luckPerms = LuckPermsProvider.get()
        val groupManager = luckPerms.groupManager
        if (groupManager.getGroup("staff") != null) return

        val default = groupManager.createAndLoadGroup("default").join()
        val mod = groupManager.createAndLoadGroup("mod").join()
        val staff = groupManager.createAndLoadGroup("staff").join()

        defaultNodes.forEach { default.data().add(Node.builder(it).build()) }
        mod.data().add(InheritanceNode.builder("default").build())
        modNodes.forEach { mod.data().add(Node.builder(it).build()) }
        staff.data().add(InheritanceNode.builder("mod").build())
        staff.data().add(Node.builder("*").build())

        groupManager.saveGroup(default).join()
        groupManager.saveGroup(mod).join()
        groupManager.saveGroup(staff).join()

        // Same offline-UUID derivation as Auth.Offline() (Main.kt) uses for real player UUIDs --
        // without this, DCFiendish would be locked out of every admin command by their own group.
        val dcUuid = UUID.nameUUIDFromBytes("OfflinePlayer:DCFiendish".toByteArray(StandardCharsets.UTF_8))
        val dcUser = luckPerms.userManager.loadUser(dcUuid, "DCFiendish").join()
        dcUser.data().add(InheritanceNode.builder("staff").build())
        luckPerms.userManager.saveUser(dcUser).join()
    }
}
