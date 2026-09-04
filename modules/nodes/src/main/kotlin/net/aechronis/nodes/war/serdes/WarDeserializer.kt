/**
 * Load war state from war.json format
 * See WarSerializer.kt for format
 */

package net.aechronis.nodes.war.serdes

import com.google.gson.JsonParser
import net.aechronis.nodes.objects.Coord
import net.aechronis.nodes.objects.TerritoryId
import net.aechronis.nodes.objects.Town
import net.aechronis.nodes.war.AttackMode
import net.aechronis.nodes.war.FlagWar
import net.minestom.server.coordinate.BlockVec
import java.io.FileReader
import java.nio.file.Path
import java.util.UUID

object WarDeserializer {

    // parse war.json data file
    fun fromJson(path: Path) {
        val json = JsonParser.parseReader(FileReader(path.toString()))
        val jsonObj = json.asJsonObject

        // parse war state and flags
        val warStatus = jsonObj.get("war")?.asBoolean ?: false
        if (warStatus) {
            // parse war flags
            val canAnnexTerritories = jsonObj.get("flagAnnex")?.asBoolean ?: true
            val canOnlyAttackBorders = jsonObj.get("flagBordersOnly")?.asBoolean ?: false
            val destructionEnabled = jsonObj.get("flagDestruction")?.asBoolean ?: true

            // war enabled, parse full state
            FlagWar.enable(canAnnexTerritories, canOnlyAttackBorders, destructionEnabled)
        }

        // Occupied chunks and in-progress attacks are saved regardless of
        // whether global war is enabled -- a warzone runs independently of
        // FlagWar.enabled, so its state must reload even when war is off.

        // ===============================
        // Skirmish targets
        // ===============================
        val jsonSkirmishTargets = jsonObj.get("skirmishTargets")?.asJsonObject
        jsonSkirmishTargets?.entrySet()?.forEach { (nationIdText, territoryIdJson) ->
            runCatching {
                FlagWar.loadSkirmishTarget(UUID.fromString(nationIdText), TerritoryId(territoryIdJson.asInt))
            }.onFailure { error ->
                System.err.println("[Nodes] Ignoring invalid skirmish target $nationIdText: ${error.message}")
            }
        }

        // ===============================
        // Town lives (recovery journal -- see WarSerializer's field kdoc). Runs unconditionally:
        // a life lost right before an abrupt stop must survive even if towns.json never caught up.
        // ===============================
        val jsonTownLives = jsonObj.get("townLives")?.asJsonObject
        jsonTownLives?.entrySet()?.forEach { (townIdText, livesJson) ->
            runCatching {
                val town = Town.fromUuid(UUID.fromString(townIdText)) ?: error("unknown town")
                val lifeState = livesJson.asJsonObject
                Town.restoreLives(
                    town,
                    lifeState.get("lives").asInt,
                    lifeState.get("capitalGranted")?.asBoolean ?: false,
                    lifeState.get("revision").asLong,
                )
            }.onFailure { error ->
                System.err.println("[Nodes] Ignoring invalid town lives $townIdText: ${error.message}")
            }
        }

        if (warStatus) {
            jsonObj.get("defeatedTowns")?.asJsonArray?.forEach { townIdJson ->
                runCatching { FlagWar.loadDefeatedTown(UUID.fromString(townIdJson.asString)) }
                    .onFailure { error ->
                        System.err.println("[Nodes] Ignoring invalid defeated town $townIdJson: ${error.message}")
                    }
            }
        }

        // ===============================
        // Occupied chunks
        // ===============================
        val jsonOccupiedChunks = jsonObj.get("occupied")?.asJsonObject
        if (jsonOccupiedChunks !== null) {
            for (townName in jsonOccupiedChunks.keySet()) {
                val chunkList = jsonOccupiedChunks[townName].asJsonArray
                for (i in 0 until chunkList.size() step 2) {
                    val cx = chunkList[i].asInt
                    val cz = chunkList[i + 1].asInt
                    val coord = Coord(cx, cz)

                    FlagWar.loadOccupiedChunk(townName, coord)
                }
            }
        }

        // ===============================
        // In-progress attacks
        // ===============================
        // WarSerializer has always written this array (see WarSerializer.kt), and
        // FlagWar.loadAttack() exists specifically to restore an attack from it, but nothing
        // ever called it here -- every attack in progress at shutdown was silently dropped on
        // the next restart, and the flag/beacon blocks it had placed were left orphaned in the
        // world with no Attack object tracking them anymore.
        val jsonAttacks = jsonObj.get("attacks")?.asJsonArray
        if (jsonAttacks !== null) {
            for (jsonAttack in jsonAttacks) {
                val attackObj = jsonAttack.asJsonObject
                val attacker = UUID.fromString(attackObj.get("id").asString)
                val cJson = attackObj.get("c").asJsonArray
                val coord = Coord(cJson[0].asInt, cJson[1].asInt)
                val bJson = attackObj.get("b").asJsonArray
                val flagBase = BlockVec(bJson[0].asInt, bJson[1].asInt, bJson[2].asInt)
                val completionTime = attackObj.get("t").asLong
                val mode = attackObj.get("mode")?.asString
                    ?.let { runCatching { AttackMode.valueOf(it) }.getOrNull() }
                    ?: AttackMode.WAR

                FlagWar.loadAttack(attacker, coord, flagBase, completionTime, mode)
            }
        }
    }
}
