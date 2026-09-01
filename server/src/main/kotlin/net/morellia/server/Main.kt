package net.morellia.server

import net.aechronis.nodes.Nodes
import net.aechronis.nodes.NodesConfig
import net.aechronis.utils.createTestServer
import net.aechronis.vanilla.Vanilla
import net.aechronis.vanilla.VanillaConfig
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.morellia.combat.Combat

fun main() {
    // Real terrain test boot: WorldPainter-authored Agadir Crisis map (see AgadirWorld.kt and
    // docs/HANDOFF.md). StoneFlatTerrain.generator still covers any chunk outside the imported
    // box so the world never has unrendered holes. The imported world isn't centered on the
    // origin -- it occupies exactly block X:[0,3583] Z:[0,3711] (confirmed by scanning the
    // exported .mca region files' chunk headers directly, not just which region files exist --
    // the naive region-file-count estimate overshot Z by ~384 blocks since the last region row
    // is only partially populated) -- so spawn is the box's true center, not (0,0). High above
    // y=210 (our authored ceiling) so the player free-falls onto real terrain; not yet tuned to
    // a specific real-world landmark.
    val spawnPoint = Pos(1791.5, 250.0, 1855.5)
    val instance = createTestServer(
        generator = StoneFlatTerrain.generator,
        spawnPoint = spawnPoint,
        auth = Auth.Offline(),
        port = 25567,
    )
    AgadirWorld.attach(instance)
    instance.setChunkSupplier(::FullbrightChunk)
    Vanilla.init(VanillaConfig(path = "morellia-data/vanilla"))
    // Testing-only: cut capture time way down so siege tests don't take forever. Both test
    // territories are wilderness-bordering AND each town's home, so wasteland (2x) and home (2x)
    // multipliers stack: actual capture time = chunkAttackTime(ms) * 0.004. 7500ms -> 30s actual.
    // Revert toward defaults before anything resembling live play.
    Nodes.initialize(NodesConfig(path = "morellia-data/nodes", chunkAttackTime = 7500))
    Combat.initialize()
    TestWeapons.register()
    ResourcePack.init()
    TickMonitor.init()
    LoadTestBots.init()
    DevLoadout.init()
    MinecraftServer.getCommandManager().register(TestGunGive())
    MinecraftServer.getCommandManager().register(SourceCommand())
    TestMeleeTarget.spawn(instance, spawnPoint)
    // Testing-only: enable war at boot so bot swarms don't need a human to run
    // /nodesadmin war enable first. Remove alongside LoadTestBots once real players take over.
    Nodes.enableWar()

    println("Morellia test server ready — port 25567, offline mode")
}
