package net.aechronis.vanilla.managers

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object Commands {
    const val MIRRORED_SLOTS = 41
    // Plain HashMaps mutated from concurrent per-player command/save/load events (same
    // bug class already fixed in Elevator/Storage/Mannequin) -- both are also on the
    // PlayerDataSerializer/Deserializer hot path, read/written on every player save/load.
    val lastLocation = ConcurrentHashMap<UUID, Pos>()
    val viewing = ConcurrentHashMap<Inventory, Player>()
    val playerLastSender = HashMap<Player, Player>()
    val ignored = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    private val enderChests = ConcurrentHashMap<UUID, Inventory>()
    private val closingEnderChests = ConcurrentHashMap.newKeySet<UUID>()

    // getOrPut is a plain get-then-conditional-put even on a ConcurrentHashMap -- no
    // atomicity guarantee. computeIfAbsent is the atomic equivalent (same fix already
    // applied to Elevator.getOrScanColumn). The returned set is also swapped from
    // MutableSet to a concurrent set since callers mutate it in place (see BlocksListener-
    // style direct add/remove elsewhere in this codebase).
    fun getIgnored(player: Player): MutableSet<UUID> =
        ignored.computeIfAbsent(player.uuid) { ConcurrentHashMap.newKeySet() }

    fun setIgnored(
        player: Player,
        uuids: Set<UUID>,
    ) {
        ignored[player.uuid] = ConcurrentHashMap.newKeySet<UUID>().apply { addAll(uuids) }
    }

    fun isBlocked(
        a: Player,
        b: Player,
    ): Boolean =
        ignored[a.uuid]?.contains(b.uuid) == true ||
            ignored[b.uuid]?.contains(a.uuid) == true

    internal fun getLastSender(player: Player): Player? = synchronized(playerLastSender) { playerLastSender[player] }

    internal fun removeLastSenderReferences(player: Player) {
        synchronized(playerLastSender) {
            playerLastSender.entries.removeIf { (sender, receiver) -> sender === player || receiver === player }
        }
    }

    fun sendMessage(
        sender: Player,
        receiver: Player?,
        message: String,
    ) {
        if (receiver == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
            return
        }

        if (isBlocked(sender, receiver)) {
            sender.sendMessage(Component.text("You can't message this player.", NamedTextColor.RED))
            return
        }

        sender.sendMessage(
            Component.text("You Whispered to ${receiver.username}: $message").color(NamedTextColor.LIGHT_PURPLE),
        )

        receiver.sendMessage(
            Component.text("${sender.username} Whispered: $message").color(NamedTextColor.LIGHT_PURPLE),
        )

        synchronized(playerLastSender) {
            playerLastSender[receiver] = sender
            playerLastSender[sender] = receiver
        }
    }

    fun saveLastLocation(player: Player) {
        lastLocation[player.uuid] = player.position
    }

    fun getLastLocation(player: Player): Pos? = lastLocation[player.uuid]

    fun open(
        viewer: Player,
        target: Player,
    ) {
        val inv = Inventory(InventoryType.CHEST_6_ROW, Component.text("${target.username}'s inventory"))
        for (slot in 0 until MIRRORED_SLOTS) {
            inv.setItemStack(slot, target.inventory.getItemStack(slot))
        }
        viewing[inv] = target
        viewer.openInventory(inv)
    }

    fun getEnderChest(player: Player): Inventory =
        enderChests.computeIfAbsent(player.uuid) {
            Inventory(InventoryType.CHEST_3_ROW, Component.text("${player.username}'s Ender Chest"))
        }

    fun openEnderChest(
        viewer: Player,
        target: Player,
    ) {
        if (target.uuid in closingEnderChests) return
        viewer.openInventory(getEnderChest(target))
    }

    fun closeViewsOf(player: Player) {
        closingEnderChests.add(player.uuid)
        val inventories = viewing.filterValues { it === player }.keys.toList()
        for (inventory in inventories) {
            inventory.viewers.toList().forEach { it.closeInventory() }
            viewing.remove(inventory)
        }

        enderChests[player.uuid]?.viewers?.toList()?.forEach { it.closeInventory() }
    }

    fun removeEnderChest(player: Player) {
        enderChests.remove(player.uuid)
    }

    fun allowEnderChest(player: Player) {
        closingEnderChests.remove(player.uuid)
    }
}
