package net.aechronis.vanilla.commands

import net.aechronis.utils.Command
import net.aechronis.vanilla.managers.Warp
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

/** Admin-only warp editing, separate from WarpCommand's "vanilla.warp" so ordinary players can
 * `/warp <name>` without also being able to move or delete warp points. */
class SetWarpCommand : Command("setwarp", "vanilla.setwarp") {
    private val nameArg = ArgumentType.Word("name")

    init {
        setDefaultExecutor { sender: Player, _ ->
            sender.sendMessage(Component.text("Usage: /setwarp <name> | /setwarp remove <name>", NamedTextColor.LIGHT_PURPLE))
        }

        addSyntax({ sender: Player, context ->
            val name = context[nameArg]
            Warp.setWarp(sender, name)
            sender.sendMessage(Component.text("Warp $name set to your current position.", NamedTextColor.GREEN))
        }, nameArg)

        addSyntax({ sender: Player, context ->
            val name = context[nameArg]
            if (Warp.deleteWarp(name)) {
                sender.sendMessage(Component.text("Warp $name removed.", NamedTextColor.GREEN))
            } else {
                sender.sendMessage(Component.text("Unknown warp: $name", NamedTextColor.RED))
            }
        }, ArgumentType.Literal("remove"), nameArg)
    }
}
