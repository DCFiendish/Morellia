package net.aechronis.nodes

import net.aechronis.nodes.constants.PermissionsGroup
import net.aechronis.nodes.constants.TownPermissions
import net.aechronis.nodes.listeners.NodesBlockPlacementCooldownListener
import net.aechronis.nodes.objects.MinimapViewerSnapshot
import net.aechronis.nodes.objects.Nation
import net.aechronis.nodes.objects.OreDeposit
import net.aechronis.nodes.objects.OreSampler
import net.aechronis.nodes.objects.Plot
import net.aechronis.nodes.objects.Resident
import net.aechronis.nodes.objects.Territory
import net.aechronis.nodes.objects.TerritoryId
import net.aechronis.nodes.objects.Town
import net.aechronis.nodes.objects.WaypointSharing
import net.aechronis.nodes.war.FlagWar
import net.aechronis.vanilla.listeners.BlockPlacementCooldownListener
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.server.ServerTickMonitorEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.play.SetCooldownPacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator
import java.util.UUID
import kotlin.math.floor
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NodesTest {
    private lateinit var tmpDir: Path
    private var serverInitialized = false

    @BeforeAll
    fun setup() {
        // start server
        val server = MinecraftServer.init()
        serverInitialized = true
        server.start("0.0.0.0", 25565)

        // create instance
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator(TestGenerator())

        val eventNode = EventNode.all("test-node").setPriority(0)

        MinecraftServer.getGlobalEventHandler().addChild(eventNode)

        val bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS)

        eventNode.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            val player = event.player
            event.spawningInstance = instance
            player.respawnPoint = Pos(27000.0, 60.0, 5700.0)
            player.gameMode = GameMode.CREATIVE
        }

        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.showBossBar(bossBar)
        }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (!event.isCancelled) {
                Message.print(event.player, "you would have just interacted")
            } else {
                Message.error(event.player, "interact event cancelled")
            }
        }

        eventNode.addListener(ServerTickMonitorEvent::class.java) { e ->
            val tickTime = floor(e.tickMonitor.tickTime * 100.0) / 100.0
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val maxMemory = runtime.maxMemory() / 1024 / 1024

            bossBar.name(
                Component.text()
                    .append(Component.text("MSPT: $tickTime | Mem: ${usedMemory}MB/${maxMemory}MB")),
            )
            bossBar.progress(min(tickTime / MinecraftServer.TICK_MS, 1.0).toFloat())

            if (tickTime > MinecraftServer.TICK_MS) {
                bossBar.color(BossBar.Color.RED)
            } else {
                bossBar.color(BossBar.Color.GREEN)
            }
        }

        val dir = Paths.get(javaClass.getResource("/nodes/world.json")!!.toURI()).parent
        tmpDir = Files.createTempDirectory("nodes-test")
        Files.walk(dir).use { resources ->
            resources.forEach { src ->
                val dest = tmpDir.resolve(dir.relativize(src))
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest)
                } else {
                    Files.copy(src, dest)
                }
            }
        }

        // create test config
        val config = NodesConfig(
            path = tmpDir.toString(),
            defaultTownPermissions = enumValues<TownPermissions>().associateWith { setOf(PermissionsGroup.OUTSIDER) },
        )

        // initialize nodes with test config
        Nodes.initialize(config)

        // Only the one vanilla listener this test actually exercises -- not a full Vanilla.init(),
        // which would also spin up playerdata/storage/whitelist file I/O rooted at a relative
        // "vanilla" path this test suite has no business touching.
        BlockPlacementCooldownListener.init()
    }

    @Test
    fun `territories are loaded`() {
        assertTrue(Territory.count() > 0, "Should have loaded territories")
    }

    @Test
    fun `towns are loaded`() {
        assertTrue(Town.count() > 0, "Should have loaded towns")
    }

    @Test
    fun `can get town by name`() {
        assertNotNull(Town.fromName("London"), "Town from test data should not be null")
    }

    @Test
    fun `can create a new town`() {
        // territory without a town
        val territory = Territory.fromId(TerritoryId(18248))
        assertNotNull(territory, "Territory should exist")

        val result = Town.create("Birmingham", territory, null)
        assertTrue(result.isSuccess, "Town should have created")

        val town = Town.fromName("Birmingham")
        assertNotNull(town)
        assertEquals("Birmingham", town.name)
        for (permission in enumValues<TownPermissions>()) {
            assertEquals(setOf(PermissionsGroup.TOWN), town.permissions[permission])
        }
    }

    @Test
    fun `empty town permissions use configured defaults`() {
        val territory = Nodes.territories.values.first { it.town == null }
        val town = Town.load(
            UUID.randomUUID(),
            "EmptyDefaults",
            null,
            territory.id.toInt(),
            null,
            null,
            arrayListOf(),
            arrayListOf(),
            arrayListOf(territory.id.toInt()),
            arrayListOf(),
            arrayListOf(),
            mutableMapOf(),
            permissions = mutableMapOf(),
            protectedBlocks = hashSetOf(),
            plots = arrayListOf(),
        )

        assertNotNull(town)
        for (permission in enumValues<TownPermissions>()) {
            assertEquals(setOf(PermissionsGroup.OUTSIDER), town.permissions[permission])
        }
    }

    @Test
    fun `all permission updates apply to town and plots`() {
        val territory = Nodes.territories.values.first { it.town == null }
        val town = Town.create("BulkPermissions", territory, null).getOrThrow()
        val allPermissions = enumValues<TownPermissions>().toList()

        Town.setPermissions(town, allPermissions, PermissionsGroup.OUTSIDER, true)
        for (permission in allPermissions) {
            assertTrue(town.permissions[permission].contains(PermissionsGroup.OUTSIDER))
        }

        val core = territory.core
        val plot = Plot.create(
            town,
            "all",
            Plot.BlockVec3(core.x * 16, 0, core.z * 16),
            Plot.BlockVec3(core.x * 16, 0, core.z * 16),
        ).getOrThrow()
        Plot.setGroupPermissions(town, plot, PermissionsGroup.OUTSIDER, allPermissions, false)
        for (permission in allPermissions) {
            assertEquals(false, plot.groupPermission(PermissionsGroup.OUTSIDER, permission))
        }

        val resident = Resident(UUID.randomUUID(), "plot-player")
        Plot.setPlayerPermissions(town, plot, resident, allPermissions, true)
        for (permission in allPermissions) {
            assertEquals(true, plot.playerPermission(resident.uuid, permission))
        }
    }

    @Test
    fun `can enable war`() {
        FlagWar.enable(canAnnexTerritories = true, canOnlyAttackBorders = false, destructionEnabled = true)
        assertTrue(Nodes.war.enabled, "War should be enabled")
    }

    // ---- OreSampler ----

    @Test
    fun `ore sampler returns nothing at a y-level with no configured deposits`() {
        val sampler = OreSampler(arrayListOf(OreDeposit(Material.DIAMOND, 1.0, 1, 1, ymin = 0, ymax = 10)))
        repeat(50) {
            assertTrue(sampler.sample(200).isEmpty(), "Should never drop outside the deposit's y-range")
        }
    }

    @Test
    fun `ore sampler returns nothing outside the world height bounds`() {
        val sampler = OreSampler(arrayListOf(OreDeposit(Material.DIAMOND, 1.0, 1, 1)))
        assertTrue(sampler.sample(-1).isEmpty(), "y below world min should return no drops")
        assertTrue(sampler.sample(256).isEmpty(), "y above world max should return no drops")
    }

    @Test
    fun `ore sampler drops at the topmost world height, y=255`() {
        // Regression test for a fixed off-by-one: the height-interval builder used to stop one
        // short of Y_WORLD_MAX (255), leaving that single top level with no distribution at all
        // no matter how the deposits were configured.
        val sampler = OreSampler(arrayListOf(OreDeposit(Material.DIAMOND, 1.0, 1, 1, ymin = 250, ymax = 255)))
        repeat(50) {
            val drop = sampler.sample(255)
            assertTrue(drop.isNotEmpty(), "y=255 should still be sampled")
            assertEquals(Material.DIAMOND, drop[0].material())
        }
    }

    @Test
    fun `ore sampler amount stays within the deposit's configured range`() {
        val sampler = OreSampler(arrayListOf(OreDeposit(Material.GOLD_INGOT, 1.0, 2, 5, ymin = 0, ymax = 10)))
        repeat(200) {
            val drop = sampler.sample(5)
            assertTrue(drop.isNotEmpty())
            val amount = drop[0].amount()
            assertTrue(amount in 2..5, "amount $amount should be within [2, 5]")
        }
    }

    @Test
    fun `ore sampler favors the higher drop-chance material`() {
        val sampler = OreSampler(
            arrayListOf(
                OreDeposit(Material.DIAMOND, 0.9, 1, 1, ymin = 0, ymax = 10),
                OreDeposit(Material.COAL, 0.1, 1, 1, ymin = 0, ymax = 10),
            ),
        )
        var diamonds = 0
        var coal = 0
        repeat(2000) {
            val drop = sampler.sample(5)
            when (drop.getOrNull(0)?.material()) {
                Material.DIAMOND -> diamonds++
                Material.COAL -> coal++
                else -> {}
            }
        }
        assertTrue(diamonds > coal, "the 0.9-weighted material ($diamonds) should drop far more than the 0.1-weighted one ($coal)")
    }

    // ---- FlagWar.calculateAttackTimeTicks ----

    @Test
    fun `attack time is chunk attack time converted from ms to ticks with no modifiers`() {
        val ticks = FlagWar.calculateAttackTimeTicks(
            chunkAttackTimeMs = 5000L,
            bordersWilderness = false,
            wastelandMultiplier = 2.0,
            hasOwningTown = false,
            isHomeTerritory = false,
            homeMultiplier = 2.0,
            isDefendingSide = false,
            defenderTimeMultiplier = 1.0,
            attackerTimeMultiplier = 1.0,
        )
        assertEquals(100L, ticks) // 5000ms * 20 / 1000
    }

    @Test
    fun `attack time applies the wasteland multiplier when bordering wilderness`() {
        val ticks = FlagWar.calculateAttackTimeTicks(
            chunkAttackTimeMs = 5000L,
            bordersWilderness = true,
            wastelandMultiplier = 2.0,
            hasOwningTown = false,
            isHomeTerritory = false,
            homeMultiplier = 2.0,
            isDefendingSide = false,
            defenderTimeMultiplier = 1.0,
            attackerTimeMultiplier = 1.0,
        )
        assertEquals(200L, ticks)
    }

    @Test
    fun `attack time modifiers never apply without an owning town, even if the flags are set`() {
        val ticks = FlagWar.calculateAttackTimeTicks(
            chunkAttackTimeMs = 5000L,
            bordersWilderness = false,
            wastelandMultiplier = 2.0,
            hasOwningTown = false,
            isHomeTerritory = true,
            homeMultiplier = 5.0,
            isDefendingSide = true,
            defenderTimeMultiplier = 3.0,
            attackerTimeMultiplier = 1.0,
        )
        assertEquals(100L, ticks, "home/defender/attacker multipliers must be gated on hasOwningTown")
    }

    @Test
    fun `attack time applies the home multiplier only for the town's home territory`() {
        val ticks = FlagWar.calculateAttackTimeTicks(
            chunkAttackTimeMs = 5000L,
            bordersWilderness = false,
            wastelandMultiplier = 2.0,
            hasOwningTown = true,
            isHomeTerritory = true,
            homeMultiplier = 3.0,
            isDefendingSide = false,
            defenderTimeMultiplier = 1.0,
            attackerTimeMultiplier = 1.0,
        )
        assertEquals(300L, ticks)
    }

    @Test
    fun `attack time uses the defender multiplier when defending, attacker multiplier otherwise`() {
        val defending = FlagWar.calculateAttackTimeTicks(
            chunkAttackTimeMs = 5000L,
            bordersWilderness = false,
            wastelandMultiplier = 1.0,
            hasOwningTown = true,
            isHomeTerritory = false,
            homeMultiplier = 1.0,
            isDefendingSide = true,
            defenderTimeMultiplier = 4.0,
            attackerTimeMultiplier = 0.5,
        )
        val attacking = FlagWar.calculateAttackTimeTicks(
            chunkAttackTimeMs = 5000L,
            bordersWilderness = false,
            wastelandMultiplier = 1.0,
            hasOwningTown = true,
            isHomeTerritory = false,
            homeMultiplier = 1.0,
            isDefendingSide = false,
            defenderTimeMultiplier = 4.0,
            attackerTimeMultiplier = 0.5,
        )
        assertEquals(400L, defending)
        assertEquals(50L, attacking)
    }

    @Test
    fun `attack time compounds all applicable modifiers together`() {
        val ticks = FlagWar.calculateAttackTimeTicks(
            chunkAttackTimeMs = 1000L,
            bordersWilderness = true,
            wastelandMultiplier = 2.0,
            hasOwningTown = true,
            isHomeTerritory = true,
            homeMultiplier = 3.0,
            isDefendingSide = true,
            defenderTimeMultiplier = 2.0,
            attackerTimeMultiplier = 1.0,
        )
        // 1000ms -> 20 ticks, * wasteland(2) * home(3) * defender(2) = 240
        assertEquals(240L, ticks)
    }

    // ---- Nodes.rateToAmount ----

    @Test
    fun `rateToAmount grants nothing for a zero or negative rate`() {
        assertEquals(0, Nodes.rateToAmount(0.0))
        assertEquals(0, Nodes.rateToAmount(-3.5))
    }

    @Test
    fun `rateToAmount grants exactly the rate when it's a whole number`() {
        repeat(20) {
            assertEquals(5, Nodes.rateToAmount(5.0))
        }
    }

    @Test
    fun `rateToAmount stays within floor and floor plus one for a fractional rate`() {
        repeat(500) {
            val amount = Nodes.rateToAmount(3.4)
            assertTrue(amount == 3 || amount == 4, "expected 3 or 4, got $amount")
        }
    }

    @Test
    fun `rateToAmount's extra unit roughly tracks the fractional probability`() {
        var extraGranted = 0
        val trials = 5000
        repeat(trials) {
            if (Nodes.rateToAmount(2.5) == 3) extraGranted++
        }
        val observedRate = extraGranted.toDouble() / trials
        assertTrue(observedRate in 0.35..0.65, "expected roughly 50% of trials to grant the extra unit, got $observedRate")
    }

    // ---- Waypoint client-mod integration ----

    @Test
    fun `town-shared waypoint is visible to town members but not outsiders`() {
        val territory = Nodes.territories.values.first { it.town == null }
        val leader = Resident(UUID.randomUUID(), "waypoint-leader")
        Nodes.residents[leader.uuid] = leader
        val town = Town.create("Waypointville", territory, leader).getOrThrow()

        val member = Resident(UUID.randomUUID(), "waypoint-member")
        Nodes.residents[member.uuid] = member
        assertTrue(Town.addResident(town, member), "Member should join the town")

        val outsider = Resident(UUID.randomUUID(), "waypoint-outsider")
        Nodes.residents[outsider.uuid] = outsider

        val waypoint = leader.createPermanentWaypoint("Docks", 100, 64, 200, WaypointSharing.TOWN).getOrThrow()

        assertTrue(
            member.availablePermanentWaypoints().any { visible -> visible.waypoint == waypoint },
            "Town member should see the shared waypoint",
        )
        assertTrue(
            outsider.availablePermanentWaypoints().none { visible -> visible.waypoint == waypoint },
            "Non-member should not see the town-shared waypoint",
        )
    }

    @Test
    fun `suppressing native waypoint display empties the minimap snapshot`() {
        val resident = Resident(UUID.randomUUID(), "suppress-tester")
        Nodes.residents[resident.uuid] = resident
        resident.createPermanentWaypoint("Home", 10, 70, 20, WaypointSharing.PRIVATE).getOrThrow()

        val shown = MinimapViewerSnapshot.capture(resident)
        assertTrue(shown.permanentWaypoints.isNotEmpty(), "Waypoint should render natively by default")

        resident.suppressNativeWaypointDisplays = true
        val suppressed = MinimapViewerSnapshot.capture(resident)
        assertTrue(suppressed.permanentWaypoints.isEmpty(), "Native minimap markers should be suppressed once opted out")
    }

    private class CapturingConnection : PlayerConnection() {
        val packets = mutableListOf<SendablePacket>()

        override fun sendPacket(packet: SendablePacket) {
            packets.add(packet)
        }

        override fun getRemoteAddress(): SocketAddress = InetSocketAddress(0)
    }

    @Test
    fun `foreign claim placement cools block materials but exempts friendly claims`() {
        val territories = Nodes.territories.values.filter { it.town == null }.take(4)
        assertEquals(4, territories.size)
        val suffix = UUID.randomUUID().toString().take(8)
        val target = Town.create("CooldownTarget$suffix", territories[0], null).getOrThrow()
        val nationTown = Town.create("CooldownNation$suffix", territories[1], null).getOrThrow()
        val allyTown = Town.create("CooldownAlly$suffix", territories[2], null).getOrThrow()
        val targetNation = Nation.create("CooldownTargetNation$suffix", target).getOrThrow()
        Nation.addTown(targetNation, nationTown).getOrThrow()
        val allyNation = Nation.create("CooldownAllyNation$suffix", allyTown).getOrThrow()
        Nation.addAlly(targetNation, allyNation).getOrThrow()

        val connection = CapturingConnection()
        val player = Player(connection, GameProfile(UUID.randomUUID(), "cooldown-test"))
        val resident = Resident(player.uuid, player.username)
        val position = BlockVec(territories[0].core.x * 16, 64, territories[0].core.z * 16)
        Nodes.residents[resident.uuid] = resident
        player.inventory.setItemStack(0, ItemStack.of(Material.DIRT))
        player.inventory.setItemStack(1, ItemStack.of(Material.STONE))
        player.inventory.setItemStack(2, ItemStack.of(Material.DIRT))
        player.inventory.setItemStack(3, ItemStack.of(Material.DIAMOND))

        try {
            NodesBlockPlacementCooldownListener.apply(player, position.blockX, position.blockZ)
            val cooldowns = connection.packets.filterIsInstance<SetCooldownPacket>()
            assertEquals(setOf(Material.DIRT.key().asString(), Material.STONE.key().asString()), cooldowns.map { it.cooldownGroup() }.toSet())
            assertTrue(cooldowns.all { it.cooldownTicks() == 4 })

            fun assertNoCooldown(town: Town?, blockPosition: BlockVec = position) {
                connection.packets.clear()
                resident.town = town
                NodesBlockPlacementCooldownListener.apply(player, blockPosition.blockX, blockPosition.blockZ)
                assertTrue(connection.packets.filterIsInstance<SetCooldownPacket>().isEmpty())
            }

            assertNoCooldown(target)
            assertNoCooldown(nationTown)
            assertNoCooldown(allyTown)
            assertNoCooldown(null, BlockVec(territories[3].core.x * 16, 64, territories[3].core.z * 16))
        } finally {
            resident.town = null
            Nodes.residents.remove(resident.uuid)
            Nation.destroy(targetNation)
            Nation.destroy(allyNation)
            Town.destroy(target)
            Town.destroy(nationTown)
            Town.destroy(allyTown)
            player.remove()
        }
    }

    @Test
    fun `legacy nametag color codes don't survive as literal text`() {
        // townNametagViewedByPlayer() (see Nametag.kt) returns raw "§a[Town]"-style legacy strings --
        // wrapping one straight in Component.text() (the bug this guards) renders the "§" and color
        // letter as literal unprintable glyphs above the player's head instead of applying the color.
        val legacy = "${net.aechronis.nodes.utils.ChatColor.GREEN}[Testville]"
        val component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(legacy)
        val plainText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component)
        assertEquals("[Testville]", plainText)
    }

    @AfterAll
    fun tearDown() {
        // if -DkeepRunning=true is set keep server running for manual testing
        if (System.getProperty("keepRunning") == "true") {
            Thread.currentThread().join()
        }
        if (serverInitialized) MinecraftServer.stopCleanly()
        if (::tmpDir.isInitialized) {
            Files.walk(tmpDir).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
