package net.morellia.server

import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerSpawnEvent

/**
 * Local playtesting only: hands every real (non-bot) player a full weapons loadout on every
 * spawn/respawn, so two dev clients connecting to the local server can test fire/reload/melee/ADS
 * immediately without a give-command. Re-runs on respawn too -- convenient here (full ammo after
 * every death) since this always targets the same fixed slots. Bot_<n> players are excluded; they
 * already get their war-flag fence from LoadTestBots at slot 0.
 */
object DevLoadout {
    private val botNameRegex = Regex("""^Bot_\d+$""")

    fun init() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) { event ->
            val player = event.player
            if (botNameRegex.matches(player.username)) return@addListener

            val inventory = player.inventory
            inventory.setItemStack(0, TestWeapons.musket.setAmmo(TestWeapons.musket.toItemStack(), TestWeapons.musket.magazineSize))
            inventory.setItemStack(1, TestWeapons.musketBall.toItemStack().withAmount(32))
            inventory.setItemStack(2, TestWeapons.bayonet.toItemStack())
            inventory.setItemStack(
                3,
                TestWeapons.fieldGun.setAmmo(TestWeapons.fieldGun.toItemStack(), TestWeapons.fieldGun.magazineSize),
            )
            inventory.setItemStack(4, TestWeapons.artilleryShell.toItemStack().withAmount(8))
        }
    }
}
