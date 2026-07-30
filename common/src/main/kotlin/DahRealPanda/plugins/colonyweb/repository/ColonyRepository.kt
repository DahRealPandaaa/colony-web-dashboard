package DahRealPanda.plugins.colonyweb.repository

import DahRealPanda.plugins.colonyweb.ColonyWeb
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.fieldValue
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invoke
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invokeAny
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invokeAnyOf
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invokeStatic
import DahRealPanda.plugins.colonyweb.util.ScanCoercion
import com.mojang.logging.LogUtils
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import org.slf4j.Logger
import java.util.LinkedHashSet
import java.util.UUID

class ColonyRepository(private val server: MinecraftServer) {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private const val COLONY_MANAGER = "com.minecolonies.api.colony.IColonyManager"
    }

    val isAvailable: Boolean get() = MineColoniesReflect.isMineColoniesLoaded()

    fun allColonies(): Collection<Any> {
        val manager = invokeStatic(COLONY_MANAGER, "getInstance").orElse(null) ?: return emptyList()
        val all = invoke(manager, "getAllColonies").orElse(null)
        if (all is Collection<*> && all.isNotEmpty()) {
            return all.filterNotNull()
        }
        val result = mutableListOf<Any>()
        for (level in server.allLevels) {
            val colonies = invoke(manager, "getColonies",
                arrayOf(net.minecraft.world.level.Level::class.java), level).orElse(null)
            if (colonies is Collection<*>) {
                result.addAll(colonies.filterNotNull())
            }
        }
        return result
    }

    fun colonyById(colonyId: Int): Any? {
        return allColonies().firstOrNull { idOf(it) == colonyId }
    }

    fun idOf(colony: Any): Int {
        return ScanCoercion.intOf(invoke(colony, "getID").orElse(null), -1)
    }

    fun nameOf(colony: Any): String {
        return ScanCoercion.stringOf(invoke(colony, "getName").orElse(null), "Colony ${idOf(colony)}")
    }

    fun dimensionOf(colony: Any): String {
        val dimension = invoke(colony, "getDimension").orElse(null) ?: return "minecraft:overworld"
        val location = invoke(dimension, "location").orElse(null)
        return if (location is ResourceLocation) location.toString() else dimension.toString()
    }

    fun ownerOf(colony: Any): String {
        val permissions = invoke(colony, "getPermissions").orElse(null)
        return if (permissions == null) "" else ScanCoercion.stringOf(invoke(permissions, "getOwnerName").orElse(null), "")
    }

    fun centerOf(colony: Any) = ScanCoercion.blockPosOf(invoke(colony, "getCenter").orElse(null))

    fun levelFor(dimension: String): ServerLevel {
        for (level in server.allLevels) {
            if (level.dimension().location().toString() == dimension) return level
        }
        return server.overworld()
    }

    fun buildingsOf(colony: Any): Collection<Any> {
        return valuesOf(invoke(structureManager(colony), "getBuildings").orElse(null))
    }

    fun workOrdersOf(colony: Any): Collection<Any> {
        val workManager = invoke(colony, "getWorkManager").orElse(null)
        return valuesOf(invoke(workManager, "getWorkOrders").orElse(null))
    }

    fun citizenManagerOf(colony: Any): Any? {
        return invokeAny(colony, "getCitizenManager").orElse(null)
    }

    fun citizensOf(colony: Any): Collection<Any> {
        val manager = citizenManagerOf(colony) ?: return emptyList()
        val citizens = invokeAny(manager, "getCitizens").orElse(null)
        return if (citizens is Collection<*>) citizens.filterNotNull() else emptyList()
    }

    fun researchTreeOf(colony: Any): Any? {
        return invokeAny(colony, "getResearchManager").orElse(null)
            ?: invokeAny(colony, "getResearch").orElse(null)
    }

    fun coloniesFor(playerId: UUID, playerName: String): List<Int> {
        val ids = LinkedHashSet<Int>()
        for (colony in allColonies()) {
            try {
                if (isMember(colony, playerId, playerName)) {
                    ids.add(idOf(colony))
                }
            } catch (t: Throwable) {
                LOGGER.debug("{} membership check failed for a colony", ColonyWeb.LOG, t)
            }
        }
        return ids.toList()
    }

    private fun isMember(colony: Any, playerId: UUID, playerName: String): Boolean {
        val permissions = invoke(colony, "getPermissions").orElse(null)
        if (permissions != null) {
            val players = invokeAny(permissions, "getPlayers").orElse(null)
            if (players is Map<*, *> && players.containsKey(playerId)) return true
            if (players is Collection<*>) {
                for (entry in players) {
                    val id = ScanCoercion.firstNonNull(
                        invokeAny(entry, "getID").orElse(null),
                        invokeAny(entry, "getId").orElse(null))
                    if (playerId == id) return true
                }
            }
            val owner = invokeAny(permissions, "getOwner").orElse(null)
            if (playerId == owner) return true
        }
        val owner = ownerOf(colony)
        return playerName.isNotEmpty()
                && owner.isNotBlank()
                && owner.lowercase() == playerName.lowercase()
    }

    private fun structureManager(colony: Any): Any? {
        var manager = invoke(colony, "getServerBuildingManager").orElse(null)
        if (manager == null) {
            manager = ScanCoercion.firstNonNull(
                invoke(colony, "getBuildingManager").orElse(null),
                invoke(colony, "getCommonBuildingManager").orElse(null))
        }
        return manager
    }

    private fun valuesOf(value: Any?): Collection<Any> {
        if (value is Map<*, *>) return value.values.filterNotNull()
        if (value is Collection<*>) return value.filterNotNull()
        return emptyList()
    }
}
