package net.aechronis.vanilla.managers

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.aechronis.vanilla.listeners.WhitelistListener
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

object Whitelist {
    data class Entry(
        val uuid: String?,
        val name: String,
    )

    var enabled: Boolean = false
        private set

    // lowercase name -> entry
    private val entries = ConcurrentHashMap<String, Entry>()
    private val gson = Gson()
    private val saveLock = Any()

    private lateinit var entriesFile: Path
    private lateinit var stateFile: Path

    fun init(path: Path) {
        entriesFile = path
        stateFile = path.resolveSibling("whitelist-enabled.txt")
        Files.createDirectories(path.parent)

        load()
        WhitelistListener.init()
    }

    fun isWhitelistedName(name: String): Boolean = entries.containsKey(name.lowercase())

    fun add(name: String) {
        entries[name.lowercase()] = Entry(resolveUuid(name), name)
        save()
    }

    fun remove(name: String) {
        entries.remove(name.lowercase())
        save()
    }

    fun toggle(): Boolean {
        enabled = !enabled
        saveState()
        return enabled
    }

    fun enforce() {
        enabled = true
        saveState()

        for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
            if (!isWhitelistedName(player.username)) {
                player.kick(Component.text("You are not whitelisted on this server"))
            }
        }
    }

    private fun resolveUuid(name: String): String? =
        MinecraftServer
            .getConnectionManager()
            .onlinePlayers
            .firstOrNull { it.username.equals(name, ignoreCase = true) }
            ?.uuid
            ?.toString()

    private fun load() {
        if (Files.exists(entriesFile)) {
            runCatching {
                Files.newBufferedReader(entriesFile).use { reader ->
                    val type = object : TypeToken<List<Entry>>() {}.type
                    val loaded: List<Entry>? = gson.fromJson(reader, type)
                    loaded?.forEach { entries[it.name.lowercase()] = it }
                }
            }.onFailure { error ->
                System.err.println("Failed to load whitelist: ${error.message}")
            }
        }

        if (Files.exists(stateFile)) {
            enabled = Files.readString(stateFile).trim().toBoolean()
        }
    }

    // Two admins (or a rapid double-invocation) calling add()/remove() concurrently used to be
    // able to interleave writes into invalid JSON -- entries is a ConcurrentHashMap (safe to
    // snapshot), but the file write itself wasn't guarded, and two writers racing on the same
    // temp-file path would corrupt each other's content before either rename could land. Both
    // the lock (serializes writers) and the temp-file+atomic-rename (no reader ever sees a
    // partial write, matches OreBlockCache/PlayerData/SaveManager's existing pattern) are needed.
    private fun save() {
        synchronized(saveLock) {
            val tmpPath = entriesFile.resolveSibling("${entriesFile.fileName}.tmp")
            Files.newBufferedWriter(tmpPath).use { writer ->
                gson.toJson(entries.values.toList(), writer)
            }
            Files.move(tmpPath, entriesFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }

    private fun saveState() {
        Files.writeString(stateFile, enabled.toString())
    }
}
