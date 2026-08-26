/**
 * Load war state from war.json format
 * See WarSerializer.kt for format
 */

package net.aechronis.nodes.war.serdes

import com.google.gson.JsonParser
import net.aechronis.nodes.objects.Coord
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
        if (!warStatus) {
            return
        }

        // parse war flags
        val canAnnexTerritories = jsonObj.get("flagAnnex")?.asBoolean ?: true
        val canOnlyAttackBorders = jsonObj.get("flagBordersOnly")?.asBoolean ?: false
        val destructionEnabled = jsonObj.get("flagDestruction")?.asBoolean ?: true

        // war enabled, parse full state
        FlagWar.enable(canAnnexTerritories, canOnlyAttackBorders, destructionEnabled)

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

                FlagWar.loadAttack(attacker, coord, flagBase, completionTime)
            }
        }
    }
}
