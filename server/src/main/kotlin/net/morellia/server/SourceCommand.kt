package net.morellia.server

import net.aechronis.utils.Command
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player

/**
 * `modules/vanilla` is AGPL-3.0, which requires that anyone interacting with a modified copy of
 * it over a network be offered a way to get the corresponding source -- not merely that the
 * source happens to exist somewhere. No permission node: every connected player must be able to
 * run this.
 */
class SourceCommand : Command("source") {
    companion object {
        private const val REPOSITORY_URL = "https://github.com/DCFiendish/Nodisium"
    }

    init {
        setDefaultExecutor { player: Player, _ ->
            player.sendMessage(
                Component
                    .text("Source code: ", NamedTextColor.LIGHT_PURPLE)
                    .append(
                        Component
                            .text(REPOSITORY_URL, NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.openUrl(REPOSITORY_URL)),
                    ),
            )
        }
    }
}
