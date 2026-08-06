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
    // Real terrain: the trimmed Agadir Crisis map (Britain -> Morocco) loads from
    // morellia-data/world via AnvilLoader. StoneFlatTerrain.generator is still passed here, but
    // now purely as the out-of-bounds fallback for chunks the loader has no data for (anything
    // outside the trimmed box), not as the primary ground everywhere -- see AgadirWorld.kt.
    val instance = createTestServer(
        generator = StoneFlatTerrain.generator,
        // Placeholder spawn: confirmed on solid ground (central France) in the trimmed Agadir
        // Crisis world -- the old coordinate here was tuned for the flat-stone-only test setup
        // and landed in open water once real terrain replaced it. Real spawn placement is a
        // separate design decision, not made yet.
        spawnPoint = Pos(-3000.5, 70.0, -1500.5),
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
    // Testing-only: enable war at boot so bot swarms don't need a human to run
    // /nodesadmin war enable first. Remove alongside LoadTestBots once real players take over.
    Nodes.enableWar()

    println("Morellia test server ready — port 25567, offline mode")
}
