/**
 * Handle saving war state
 * JSON Format:
 * {
 *   "war": true,            // flag for war enabled/disabled
 *   "occupied": {           // chunks occupied by a town
 *     "town1": [            // town occupying a chunk
 *        0, 1,              // interleaved chunk buffer [x0, y0, x1, y1, ...]
 *        2, 3 ],
 *     "town2": [
 *        4, 5,
 *        6, 7 ]
 *   },
 *   "atttacks": [           // ongoing attacks
 *     {attackJsonObject0},
 *     {attackJsonObject1},
 *     ...
 *   ]
 * }
 */

package net.aechronis.nodes.war.serdes

import com.google.gson.JsonPrimitive
import net.aechronis.nodes.Nodes
import net.aechronis.nodes.objects.TerritoryChunk
import net.aechronis.nodes.war.FlagWar
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.charset.CharsetEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

object WarSerializer {

    // pre-process war objects.
    // Was: `occupiedChunks`/`attacksJsonList` as shared mutable singleton fields, cleared and
    // rebuilt in place on every save() call. Since save(async=true) hands writeToJson() off to
    // CompletableFuture.runAsync, a busy war (frequent saves) could start rebuilding these same
    // buffers here on the main thread while a previous async write was still iterating them --
    // a genuine mutate-while-iterate race. Building fresh local buffers per call and passing them
    // straight into writeToJson() removes the shared state entirely, so concurrent saves can no
    // longer see or corrupt each other's in-flight buffers.
    fun save(async: Boolean) {
        val occupiedChunks: HashMap<String, ArrayList<Int>> = hashMapOf()

        for (coord in FlagWar.occupiedChunks) {
            val chunk = TerritoryChunk.fromCoord(coord)
            if (chunk === null) {
                continue
            }
            val town = chunk.occupier?.name
            if (town != null) {
                val coord = chunk.coord
                val cx = coord.x
                val cz = coord.z

                occupiedChunks.get(town)?.let { chunkList ->
                    chunkList.add(cx)
                    chunkList.add(cz)
                } ?: run {
                    occupiedChunks.put(town, arrayListOf(cx, cz))
                }
            }
        }

        // build json strings for each attack
        val attacksJsonList: ArrayList<StringBuilder> = arrayListOf()
        for (attack in FlagWar.chunkToAttacker.values) {
            attacksJsonList.add(attack.toJson())
        }

        if (async) {
            // write file in worker thread.
            // Was previously unguarded -- any exception thrown mid-write (e.g. a transient disk
            // I/O error) silently dropped that entire save cycle with no log trace at all, on the
            // one path (war state) where a lost save is worst: it means every attacker/defender
            // flag placed since the last successful save is gone on the next restart.
            CompletableFuture.runAsync {
                try {
                    writeToJson(Nodes.config.pathWar, occupiedChunks, attacksJsonList)
                } catch (err: Exception) {
                    System.err.println("[WAR] Failed to save war state: ${err.message}")
                    err.printStackTrace()
                }
            }
        } else {
            writeToJson(Nodes.config.pathWar, occupiedChunks, attacksJsonList)
        }
    }

    // save war json file synchronously on main thread
    fun writeToJson(path: Path, occupiedChunks: HashMap<String, ArrayList<Int>>, attacksJsonList: ArrayList<StringBuilder>) {
        // =============================================
        // calculate string builder capacity

        // war status [13]: {"war":false,
        // occupied header + close bracket + comma [14]: "occupied":{},
        // attacks header + close bracket [13]: "attacks":[]}
        // -> 40 minimum
        // will add arbitrary extra margin and up size to 60
        var bufferSize = 60

        // captured chunks format:
        // "town": [0, 1, 2, 3, ...]
        // -> get each integer size, then include brackets [] and commas ,
        for ((townName, coordList) in occupiedChunks) {
            // size of "townName":[]
            bufferSize += (5 + townName.length + coordList.size)

            // size of each integer
            for (c in coordList) {
                val intLength = 2 + c.toString().length
                bufferSize += intLength
            }
        }

        // list of attack json objects
        // add 1 to length to account for comma
        for (s in attacksJsonList) {
            bufferSize += (1 + s.length)
        }
        // =============================================

        // json string builder
        val jsonString = StringBuilder(bufferSize)

        var bytes: ByteBuffer

        // val timeBuffers = measureNanoTime {

        // ===============================
        // War status and flags
        // ===============================
        jsonString.append("{\"war\":${FlagWar.enabled},")
        jsonString.append("\"flagAnnex\":${FlagWar.canAnnexTerritories},")
        jsonString.append("\"flagBordersOnly\":${FlagWar.canOnlyAttackBorders},")
        jsonString.append("\"flagDestruction\":${FlagWar.destructionEnabled},")

        // ===============================
        // Occupied chunks
        // ===============================
        jsonString.append("\"occupied\":{")

        var index = 1
        for ((townName, coordList) in occupiedChunks) {
            jsonString.append(JsonPrimitive(townName)).append(":[")
            for ((i, c) in coordList.withIndex()) {
                jsonString.append(c)
                if (i < coordList.size - 1) {
                    jsonString.append(",")
                }
            }

            // add comma
            if (index < occupiedChunks.size) {
                jsonString.append("],")
                index += 1
            }
            // no comma for last, close with "},"
            else {
                jsonString.append("]")
            }
        }

        jsonString.append("},")

        // ===============================
        // Attacks
        // ===============================
        jsonString.append("\"attacks\":[")

        for ((i, attack) in attacksJsonList.iterator().withIndex()) {
            jsonString.append(attack)

            // add comma
            if (i < attacksJsonList.size - 1) {
                jsonString.append(",")
            }
        }

        jsonString.append("]}")

        // ===============================

        // get byte buffer
        val encoder: CharsetEncoder = StandardCharsets.UTF_8.newEncoder()
        val charBuffer: CharBuffer = CharBuffer.wrap(jsonString)
        bytes = encoder.encode(charBuffer)

        // }

        // println("[WAR] BUFFER WRITE TIME: ${timeBuffers.toString()}ns")

        // ===============================
        // WRITE FILE
        // ===============================
        // val timeWrite = measureNanoTime {

        val fileChannel: AsynchronousFileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

        val operation: Future<Int> = fileChannel.write(bytes, 0)

        operation.get()
        // }

        // println("[WAR] FILE SAVE TIME: ${timeWrite.toString()}ns")
    }
}
