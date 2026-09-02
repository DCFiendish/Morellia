package net.morellia.server

import me.lucko.spark.minestom.SparkMinestom
import net.aechronis.nodes.Nodes
import net.aechronis.nodes.NodesConfig
import net.aechronis.utils.createTestServer
import net.aechronis.utils.hasPermission
import net.aechronis.vanilla.Vanilla
import net.aechronis.vanilla.VanillaConfig
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.command.ConsoleSender
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.anvil.AnvilLoader
import net.morellia.combat.Combat
import java.nio.file.Path

fun main() {
    // Pvp playtest boot: the Nodisium Playtest Map (a purpose-built arena, not the Agadir Crisis
    // terrain -- see AgadirWorld.kt for that one, still available, just not attached below).
    // StoneFlatTerrain.generator still covers any chunk outside the imported box so the world
    // never has unrendered holes. Spawn point per the map author.
    val spawnPoint = Pos(150.0, 105.0, 150.0)
    val instance = createTestServer(
        generator = StoneFlatTerrain.generator,
        spawnPoint = spawnPoint,
        auth = Auth.Offline(),
        port = 25567,
    )
    // The map was exported with the datapack-style dimension layout (dimensions/minecraft/overworld/region/...)
    // rather than the standard single-player format (region/ at the root), so AnvilLoader needs the
    // overworld subfolder directly -- pointing it at the map root finds no region/ and silently falls
    // back to StoneFlatTerrain.generator for every chunk.
    instance.setChunkLoader(AnvilLoader(Path.of("morellia-data/nodisium-playtest-map/dimensions/minecraft/overworld")))
    instance.setChunkSupplier(::FullbrightChunk)
    // TODO(pvp playtest): warpsConfig.warps and pvpPrepConfig.zones are empty by default (same as
    // kothsConfig above them) -- add real WarpPoint/PrepZoneConfig entries here, each referencing
    // `instance`, once the warp landing spots and their no-damage/no-break box corners are picked.
    Vanilla.init(VanillaConfig(path = "morellia-data/vanilla"))
    // Testing-only: cut capture time way down so siege tests don't take forever. Both test
    // territories are wilderness-bordering AND each town's home, so wasteland (2x) and home (2x)
    // multipliers stack: actual capture time = chunkAttackTime(ms) * 0.004. 7500ms -> 30s actual.
    // Revert toward defaults before anything resembling live play.
    // defaultRespawnPoint governs where townless players land on death (PlayerRespawnEvent),
    // separately from the instance's own spawnPoint above which only applies on first join --
    // without this it defaults to (0,64,0), off the edge of the Nodisium map.
    Nodes.initialize(NodesConfig(path = "morellia-data/nodes", chunkAttackTime = 7500, defaultRespawnPoint = spawnPoint, canInteractInEmpty = false, canInteractInUnclaimed = false, adminUsernames = setOf("DCFiendish")))
    Combat.initialize()
    TestWeapons.register()
    ResourcePack.init()
    TickMonitor.init()
    // LoadTestBots.init() -- disabled for the pvp playtest: real players don't need the
    // TownA/TownB/NationA/NationB bot fixture, and it recreates those towns on every boot
    // (createTownIfMissing) even after they're wiped from the save data.
    PvpKit.init()
    // Perf profiler -- /spark ..., self-registers its own commands (`.commands(true)`). Same
    // morellia.<node> permission convention as every other admin command here (see TestGunGive's
    // "morellia.testgun", backed by the same net.aechronis.utils.hasPermission); console is always
    // allowed, matching every other CommandSender that isn't a Player.
    SparkMinestom.builder(Path.of("morellia-data/spark"))
        .commands(true)
        .permissionHandler { sender, permission -> sender is ConsoleSender || (sender is Player && sender.hasPermission(permission)) }
        .enable()
    MinecraftServer.getCommandManager().register(TestGunGive())
    MinecraftServer.getCommandManager().register(SourceCommand())
    MinecraftServer.getCommandManager().register(KitCommand())
    TestMeleeTarget.spawn(instance, spawnPoint)
    // Testing-only: enable war at boot so bot swarms don't need a human to run
    // /nodesadmin war enable first. Remove alongside LoadTestBots once real players take over.
    Nodes.enableWar()

    println("Morellia test server ready — port 25567, offline mode")
}
