package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.ColonyWeb
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invokeAny
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invokeStatic
import DahRealPanda.plugins.colonyweb.model.ColonyScan
import DahRealPanda.plugins.colonyweb.model.ItemInfo
import DahRealPanda.plugins.colonyweb.service.DomumOrnamentumResolver
import DahRealPanda.plugins.colonyweb.util.ScanCoercion
import com.mojang.logging.LogUtils
import net.minecraft.world.item.ItemStack
import org.slf4j.Logger

class RecipeService {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private const val COLONY_MANAGER = "com.minecolonies.api.colony.IColonyManager"

        fun markCraftable(scan: ColonyScan, craftable: Set<String>) {
            if (craftable.isEmpty()) return
            val snapshot = scan.snapshot
            mark(snapshot.warehouse.stacks, craftable)
            snapshot.buildings.forEach { building -> mark(building.required, craftable) }
            scan.inventories.values.forEach { items -> mark(items, craftable) }
            scan.research?.branches?.forEach { branch ->
                branch.researches.forEach { entry -> mark(entry.cost, craftable) }
            }
        }

        private fun mark(items: Collection<out ItemInfo>, craftable: Set<String>) {
            for (item in items) {
                if (item.itemKey != null && craftable.contains(item.itemKey)) item.craftable = true
            }
        }
    }

    fun scan(rawBuildings: Collection<Any>): Set<String> {
        val craftable = hashSetOf<String>()
        val recipeManager = recipeManager() ?: return craftable
        for (building in rawBuildings) {
            try {
                for (token in tokensOf(building)) {
                    val output = outputOf(recipeManager, token)
                    if (output != null && !output.isEmpty) {
                        craftable.add(DomumOrnamentumResolver.textureKeyFor(output))
                    }
                }
            } catch (t: Throwable) {
                LOGGER.debug("{} failed to read recipes for a building", ColonyWeb.LOG, t)
            }
        }
        return craftable
    }

    private fun recipeManager(): Any? {
        val manager = invokeStatic(COLONY_MANAGER, "getInstance").orElse(null)
        return invokeAny(manager, "getRecipeManager").orElse(null)
    }

    private fun tokensOf(building: Any): Collection<Any> {
        val direct = invokeAny(building, "getRecipes").orElse(null)
        if (direct is Collection<*>) return direct.filterNotNull()
        val tokens = hashSetOf<Any>()
        val modules = invokeAny(building, "getModules").orElse(null)
        if (modules is Collection<*>) {
            for (module in modules) {
                val fromModule = ScanCoercion.firstNonNull(
                    invokeAny(module, "getRecipes").orElse(null),
                    invokeAny(module, "getRecipeTokens").orElse(null))
                if (fromModule is Collection<*>) tokens.addAll(fromModule.filterNotNull())
            }
        }
        return tokens
    }

    private fun outputOf(recipeManager: Any?, token: Any): ItemStack? {
        var byToken = invokeAny(recipeManager, "getRecipe", token).orElse(null)
        if (byToken == null) {
            val all = invokeAny(recipeManager, "getRecipes").orElse(null)
            if (all is Map<*, *>) byToken = all[token]
        }
        return ScanCoercion.itemStackOf(ScanCoercion.firstNonNull(
            invokeAny(byToken, "getPrimaryOutput").orElse(null),
            invokeAny(byToken, "getResultItem").orElse(null)))
    }
}
