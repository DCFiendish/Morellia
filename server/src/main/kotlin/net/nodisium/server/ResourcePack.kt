package net.nodisium.server

import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import java.net.URI
import java.util.UUID

// Served from a GitHub release so prod doesn't need a dedicated file host/port.
// To update the pack: zip resourcepack/assets + resourcepack/pack.mcmeta, then
//   gh release upload resourcepack-v1 resourcepack.zip --clobber --repo DCFiendish/Nodisium
private val PACK_URL = URI.create("https://github.com/DCFiendish/Nodisium/releases/download/resourcepack-v1/resourcepack.zip")
private val PACK_ID = UUID.fromString("6d6f7265-6c6c-6961-706b-000000000001")

object ResourcePack {
    /**
     * `computeHashAndBuild().join()` fetches [PACK_URL] synchronously at boot to hash it -- if
     * nothing is serving it (no `resourcepack.zip` built yet, no local http.server running), this
     * threw uncaught straight out of `main()` and silently skipped every call after `ResourcePack.init()`
     * (`TickMonitor.init()`, `LoadTestBots.init()`, `Nodes.enableWar()`) even though the Minestom
     * tick loop itself kept running -- found while boot-checking modules/combat, previously flagged
     * as an open bug. Now just logs and skips the pack request instead of taking the rest of main()
     * down with it.
     */
    fun init() {
        val info = try {
            ResourcePackInfo.resourcePackInfo()
                .id(PACK_ID)
                .uri(PACK_URL)
                .computeHashAndBuild()
                .join()
        } catch (e: Exception) {
            System.err.println("[ResourcePack] Couldn't reach $PACK_URL -- skipping resource pack request (${e.message})")
            return
        }

        val request = ResourcePackRequest.resourcePackRequest()
            .packs(info)
            .required(true)
            .prompt(Component.text("Morellia needs this resource pack (minimap rendering, custom textures)."))
            .build()

        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.player.sendResourcePacks(request)
        }
    }
}
