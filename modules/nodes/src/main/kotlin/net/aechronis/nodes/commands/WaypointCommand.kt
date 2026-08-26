package net.aechronis.nodes.commands

import net.aechronis.nodes.Message
import net.aechronis.nodes.objects.NodesCommand
import net.aechronis.nodes.objects.WaypointMenu
import net.minestom.server.command.builder.arguments.ArgumentBoolean

/** Per-player permanent and death waypoint menus. */
class WaypointCommand : NodesCommand("waypoint", null, "wp") {
    init {
        setDefaultExecutor { player, resident, _ ->
            WaypointMenu.openBrowse(player, resident)
        }

        addSubcommand(WaypointCreateCommand())
        addSubcommand(WaypointListCommand())
        addSubcommand(WaypointNativeDisplayCommand())
    }
}

private class WaypointCreateCommand : NodesCommand("create") {
    init {
        setDefaultExecutor { player, resident, _ ->
            WaypointMenu.openCreate(player, resident)
        }
    }
}

/**
 * Machine-readable dump of every waypoint available to the sender, one per chat line.
 * Meant for a client mod to parse on join and seed its own markers (e.g. real Xaero
 * waypoints) from — the creation-time chat announcements alone only cover waypoints made
 * *after* the mod starts watching chat, so a full listing is needed to pick up anything
 * that already existed.
 */
private class WaypointListCommand : NodesCommand("list") {
    init {
        setDefaultExecutor { player, resident, _ ->
            val waypoints = resident.availablePermanentWaypoints()
            if (waypoints.isEmpty()) {
                Message.print(player, "[Nodes] No waypoints found.")
                return@setDefaultExecutor
            }
            waypoints.forEach { visible ->
                val waypoint = visible.waypoint
                val ownerSuffix = if (visible.owner === resident) "" else ", shared by ${visible.owner.name}"
                Message.print(
                    player,
                    "[Nodes] Waypoint: \"${waypoint.name}\" @ ${waypoint.x}, ${waypoint.y}, ${waypoint.z} " +
                        "(${waypoint.sharing.id}$ownerSuffix)",
                )
            }
        }
    }
}

/**
 * Toggles whether the server renders this resident's own native waypoint markers
 * (in-world floating labels and minimap icons). For players whose client already renders
 * waypoints itself, so the two displays don't stack on top of each other.
 */
private class WaypointNativeDisplayCommand : NodesCommand("nativedisplay") {
    init {
        setDefaultExecutor { player, resident, _ ->
            Message.print(player, "Usage: /waypoint nativedisplay <true|false>")
        }

        val enabledArg = ArgumentBoolean("enabled")
        addSyntax({ player, resident, context ->
            val enabled = context[enabledArg]
            resident.suppressNativeWaypointDisplays = !enabled
            resident.minimap?.refresh()
            Message.print(player, "Native waypoint markers are now ${if (enabled) "on" else "off"}")
        }, enabledArg)
    }
}
