package net.aechronis.vanilla.commands

import net.aechronis.utils.Command
import net.aechronis.vanilla.managers.Warp
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class WarpCommand : Command("warp", "vanilla.warp") {
    private val nameArg = ArgumentType.Word("name")

    init {
        setDefaultExecutor { sender: Player, _ -> sendList(sender) }
        addSyntax({ sender: Player, _ -> sendList(sender) }, ArgumentType.Literal("list"))

        addSyntax({ sender: Player, context ->
            val name = context[nameArg]
            if (Warp.isPending(sender)) {
                sender.sendMessage(Component.text("You are already warping.", NamedTextColor.RED))
                return@addSyntax
            }
            if (!Warp.start(sender, name)) {
                sender.sendMessage(Component.text("Unknown warp: $name", NamedTextColor.RED))
            }
        }, nameArg)
    }

    private fun sendList(sender: Player) {
        val names = Warp.names()
        sender.sendMessage(
            Component.text(
                if (names.isEmpty()) "No warps are configured." else "Warps: ${names.joinToString(", ")}",
                NamedTextColor.LIGHT_PURPLE,
            ),
        )
    }
}
