package net.morellia.server

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData
import net.minestom.server.timer.TaskSchedule

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
            inventory.setItemStack(2, TestWeapons.bayonet.toItemStack())
            inventory.setItemStack(
                3,
                TestWeapons.fieldGun.setAmmo(TestWeapons.fieldGun.toItemStack(), TestWeapons.fieldGun.magazineSize),
            )
            inventory.setItemStack(4, TestWeapons.artilleryShell.toItemStack().withAmount(8))
            inventory.setItemStack(
                5,
                TestWeapons.shotgun.setAmmo(TestWeapons.shotgun.toItemStack(), TestWeapons.shotgun.magazineSize),
            )
            inventory.setItemStack(6, TestWeapons.shotgunShell.toItemStack().withAmount(8))

            // New from the team's Mural weapon-planning board -- see TestWeapons.kt. Placed past the
            // hotbar (slots 9+) since the hotbar's already full; open the inventory to grab these.
            inventory.setItemStack(7, TestWeapons.usTrenchKnife.toItemStack())
            inventory.setItemStack(8, TestWeapons.nahkampfmesser.toItemStack())
            inventory.setItemStack(9, TestWeapons.couteauPoignard.toItemStack())
            inventory.setItemStack(10, TestWeapons.m1911.setAmmo(TestWeapons.m1911.toItemStack(), TestWeapons.m1911.magazineSize))
            inventory.setItemStack(11, TestWeapons.m1911Round.toItemStack().withAmount(32))
            inventory.setItemStack(
                12,
                TestWeapons.mauserC96.setAmmo(TestWeapons.mauserC96.toItemStack(), TestWeapons.mauserC96.magazineSize),
            )
            inventory.setItemStack(13, TestWeapons.mauserC96Round.toItemStack().withAmount(32))
            inventory.setItemStack(14, TestWeapons.mp18.setAmmo(TestWeapons.mp18.toItemStack(), TestWeapons.mp18.magazineSize))
            inventory.setItemStack(15, TestWeapons.mp18Magazine.toItemStack().withAmount(4))
            inventory.setItemStack(
                16,
                TestWeapons.tommyGun.setAmmo(TestWeapons.tommyGun.toItemStack(), TestWeapons.tommyGun.magazineSize),
            )
            inventory.setItemStack(17, TestWeapons.tommyGunMagazine.toItemStack().withAmount(4))

            // TEMP: raw obj³ export visual check for the Kar98k import (see docs/HANDOFF.md's
            // 2026-08-30 status update) -- not a real Gun yet, just an iron_ingot carrying the
            // custom_model_data string the scratch export's item override matches on. Remove once
            // the model is confirmed and wired into the real TestWeapons.kt pipeline.
            try {
                val testStack =
                    ItemStack.of(Material.IRON_INGOT)
                        .with(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(listOf(), listOf(), listOf("kar98k_lowpoly"), listOf()))
                        .with(DataComponents.ITEM_NAME, Component.text("KAR98K TEST", NamedTextColor.RED))
                inventory.setItemStack(22, testStack)
                println("[DevLoadout] kar98k test item set for ${player.username}: $testStack")
            } catch (e: Exception) {
                println("[DevLoadout] FAILED to set kar98k test item for ${player.username}: ${e.stackTraceToString()}")
            }

            // TEMP: raw obj³ export visual check for the Lebel M1886 import (first of the newly
            // downloaded TastyTony models, see docs/HANDOFF.md). Not a real Gun yet, just an
            // iron_ingot carrying the custom_model_data string the export's item override matches
            // on. Remove once the model/pose is confirmed and wired into the real pipeline.
            try {
                val testStack =
                    ItemStack.of(Material.IRON_INGOT)
                        .with(
                            DataComponents.CUSTOM_MODEL_DATA,
                            CustomModelData(listOf(), listOf(), listOf("lebel_m1886_import"), listOf()),
                        )
                        .with(DataComponents.ITEM_NAME, Component.text("LEBEL M1886 TEST", NamedTextColor.RED))
                inventory.setItemStack(23, testStack)
                println("[DevLoadout] lebel_m1886_import test item set for ${player.username}: $testStack")
            } catch (e: Exception) {
                println("[DevLoadout] FAILED to set lebel_m1886_import test item for ${player.username}: ${e.stackTraceToString()}")
            }
        }
    }
}
