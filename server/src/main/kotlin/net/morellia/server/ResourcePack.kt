package net.morellia.server

import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import java.net.URI
import java.util.UUID

// Local-only for now (no deploys to the production VM during the replan -- see docs/HANDOFF.md).
// Serve resourcepack.zip yourself before starting the server, e.g. from server/:
//   python -m http.server 8000
private val PACK_URL = URI.create("http://localhost:8000/resourcepack.zip")
private val PACK_ID = UUID.fromString("6d6f7265-6c6c-6961-706b-000000000001")

object ResourcePack {
    fun init() {
        val info = ResourcePackInfo.resourcePackInfo()
            .id(PACK_ID)
            .uri(PACK_URL)
            .computeHashAndBuild()
            .join()

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
