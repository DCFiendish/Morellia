package net.aechronis.vanilla.managers

import net.aechronis.vanilla.Vanilla
import net.aechronis.vanilla.listeners.RecipesListener
import net.aechronis.vanilla.objects.Recipe
import net.aechronis.vanilla.objects.RecipesWorkspace
import net.minestom.server.inventory.AbstractInventory
import java.util.concurrent.ConcurrentHashMap

object Recipes {
    val recipes: MutableList<Recipe> = ArrayList()
    // Was a plain HashMap mutated from concurrent per-player inventory open/close/click
    // events (RecipesListener) -- same bug class already fixed in Elevator/Storage.
    val workspaces: ConcurrentHashMap<AbstractInventory, RecipesWorkspace> = ConcurrentHashMap()

    fun init() {
        // measure load time
        val timeStart = System.currentTimeMillis()
        RecipesListener.init()

        recipes.clear()
        recipes.addAll(Vanilla.config.recipesConfig.recpies)

        val timeEnd = System.currentTimeMillis()
        val timeLoad = timeEnd - timeStart
        println("├─ Recpies enabled in ${timeLoad}ms")
    }
}
