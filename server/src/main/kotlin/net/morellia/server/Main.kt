package net.morellia.server

import net.aechronis.nodes.Nodes
import net.aechronis.nodes.NodesConfig
import net.aechronis.utils.createTestServer
import net.aechronis.vanilla.Vanilla
import net.aechronis.vanilla.VanillaConfig
import net.minestom.server.Auth
import net.minestom.server.coordinate.Pos
import net.morellia.combat.Combat

fun main() {
    // Plain stone superflat everywhere (StoneFlatTerrain.generator, surface at y=64). Every
    // real-world terrain source this project tried (AgadirWorld's AnvilLoader over a trimmed
    // Anvil download, EuropeTerrain's procedural NOAA/WWF-heightmap generator) has been dropped --
    // see docs/HANDOFF.md. Flat stone is the deliberate baseline while terrain gets replanned, not a
    // fallback for a broken loader.
    // Sits north of both LoadTestBots test towns' territories (chunks x:0-5, z:0-2), on the
    // flat stone surface (y=64 top, so y=65 stands directly on it).
    val spawnPoint = Pos(48.5, 65.0, -20.5)
    val instance = createTestServer(
        generator = StoneFlatTerrain.generator,
        spawnPoint = spawnPoint,
        auth = Auth.Offline(),
        port = 25567,
    )
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
    TestMeleeTarget.spawn(instance, spawnPoint)
    // Testing-only: enable war at boot so bot swarms don't need a human to run
    // /nodesadmin war enable first. Remove alongside LoadTestBots once real players take over.
    Nodes.enableWar()

    println("Morellia test server ready — port 25567, offline mode")
}
