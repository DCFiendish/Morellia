/**
 * Player town nametag
 */

package net.aechronis.nodes.objects
import net.aechronis.nodes.Nodes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minestom.server.MinecraftServer
import net.minestom.server.color.TeamColor
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.TeamsPacket
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

/**
 * Get town nametag text as VIEWED by input player
 */
fun townNametagViewedByPlayer(
    town: Town,
    viewer: Player,
    space: Boolean = true, // append space to the end of string
): String {
    // get input player relation to this.player
    val otherTown = Resident.fromPlayer(viewer)?.town
    if (otherTown !== null) {
        val townNation = town.nation
        val otherNation = otherTown.nation
        if (town === otherTown) {
            return if (space) "${town.nametagTown} " else town.nametagTown
        } else if (townNation !== null && townNation === otherNation) {
            return if (space) "${town.nametagNation} " else town.nametagNation
        } else if (townNation !== null && otherNation !== null && townNation.allies.contains(otherNation)) {
            return if (space) "${town.nametagAlly} " else town.nametagAlly
        } else if (townNation !== null && otherNation !== null && townNation.enemies.contains(otherNation)) {
            return if (space) "${town.nametagEnemy} " else town.nametagEnemy
        }
    }

    return if (space) "${town.nametagNeutral} " else town.nametagNeutral
}

object Nametag {
    private var task: Task? = null

    /**
     * Start the automatic nametag update scheduler
     */
    fun start(period: Long) {
        if (this.task !== null) {
            return
        }

        val runnable = Runnable {
            updateAllText()
        }

        this.task = MinecraftServer.getSchedulerManager()
            .buildTask(runnable)
            .delay(TaskSchedule.millis(period))
            .repeat(TaskSchedule.millis(period))
            .schedule()
    }

    /**
     * Stop the automatic nametag update scheduler
     */
    fun stop() {
        val task = this.task
        if (task === null) {
            return
        }

        task.cancel()
        this.task = null
    }

    /**
     * Update nametag text for player
     * Sends team packets directly to the player so they see customized prefixes
     */
    private fun updateTextForPlayer(player: Player, membersByTown: Map<Town, List<String>>) {
        // remove all existing town teams for this viewer
        for (town in Nodes.towns.values) {
            val teamName = "t${town.townNametagId}"
            player.sendPacket(TeamsPacket(teamName, TeamsPacket.RemoveTeamAction()))
        }

        // create teams for each town with prefix as viewed by this player
        for (town in Nodes.towns.values) {
            val teamName = "t${town.townNametagId}"
            val prefix = townNametagViewedByPlayer(town, player, space = true)
            val townMembers = membersByTown[town] ?: emptyList()

            // create team with customized prefix for this viewer
            val createAction = TeamsPacket.CreateTeamAction(
                TeamsPacket.Settings(
                    Component.text(teamName), // displayName
                    // prefix is a legacy "§a[Town]"-style string (see ChatColor) -- Component.text()
                    // treats that literally instead of parsing it, so every nametag rendered the raw
                    // "§a" as unprintable boxes in front of the actual name.
                    LegacyComponentSerializer.legacySection().deserialize(prefix), // displayName
                    Component.empty(), // teamSuffix
                    TeamsPacket.NameTagVisibility.ALWAYS, // nameTagVisibility
                    TeamsPacket.CollisionRule.ALWAYS, // collisionRule
                    TeamColor.WHITE, // teamColor
                    0, // friendlyFlags (I don't think is actually does anything visible clientside)
                ),
                townMembers, // entities (players in this town)
            )
            player.sendPacket(TeamsPacket(teamName, createAction))
        }
    }

    /**
     * Update all player nametags
     * Calls updateTextForPlayer for each online player
     */
    private fun updateAllText() {
        val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers

        // Town membership doesn't depend on the viewer, but used to get rebuilt by scanning every
        // online player for every (viewer x town) pair -- O(players^2 x towns) every single call
        // of this once-a-second task. Build it once per call instead: O(players) to bucket, then
        // O(players x towns) to send packets, no repeated inner scan.
        val membersByTown = mutableMapOf<Town, MutableList<String>>()
        for (player in onlinePlayers) {
            val town = Town.fromPlayer(player) ?: continue
            membersByTown.getOrPut(town) { mutableListOf() }.add(player.username)
        }

        for (player in onlinePlayers) {
            updateTextForPlayer(player, membersByTown)
        }
    }
}
