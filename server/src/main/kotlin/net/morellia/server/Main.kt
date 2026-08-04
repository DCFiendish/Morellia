package net.morellia.server

import net.aechronis.combat.Combat
import net.aechronis.nodes.Nodes
import net.aechronis.nodes.NodesConfig
import net.aechronis.utils.createTestServer
import net.aechronis.vanilla.Vanilla
import net.aechronis.vanilla.VanillaConfig
import net.minestom.server.Auth
import net.minestom.server.coordinate.Pos

fun main() {
    // Testing-only: stone superflat instead of EuropeTerrain, so the war-flag load test's attack
    // targets sit on identical, predictable ground everywhere (see StoneFlatTerrain.kt) rather
    // than real hilly terrain. Swap back to EuropeTerrain.generator (and restore the Alps spawn
    // point below) before anything resembling live play.
    val instance = createTestServer(
        generator = StoneFlatTerrain.generator,
        spawnPoint = Pos(2386.5, (StoneFlatTerrain.SURFACE_Y + 1).toDouble(), 2088.5),
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
    // Testing-only: enable war at boot so bot swarms don't need a human to run
    // /nodesadmin war enable first. Remove alongside LoadTestBots once real players take over.
    Nodes.enableWar()

    println("Morellia test server ready — port 25567, offline mode")
}
