package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.BuildingInfo
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.model.ColonySummary
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.fieldValue
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invoke
import DahRealPanda.plugins.colonyweb.util.ScanCoercion
import DahRealPanda.plugins.colonyweb.util.Text
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import java.util.concurrent.ConcurrentHashMap

class BuildingResult(
    val buildings: MutableList<BuildingInfo>,
    val buildingByPos: MutableMap<BlockPos, BuildingInfo>,
    val rawBuildingByPos: MutableMap<BlockPos, Any>
)

class BuildingService {
    private val HUT_PREFIX = "blockhut"

    @Volatile
    private var summaries: List<ColonySummary> = emptyList()
    private val snapshots = ConcurrentHashMap<Int, ColonySnapshot>()

    fun summaries(): List<ColonySummary> = summaries

    fun setSummaries(list: List<ColonySummary>) { summaries = list }

    fun snapshot(colonyId: Int): ColonySnapshot? = snapshots[colonyId]

    fun storeSnapshot(colonyId: Int, snapshot: ColonySnapshot) { snapshots[colonyId] = snapshot }

    fun retainOnly(current: List<ColonySummary>) {
        snapshots.keys.removeIf { id -> current.none { s -> s.id == id } }
    }

    fun scan(rawBuildings: Collection<Any>, level: ServerLevel?): BuildingResult {
        val buildings = mutableListOf<BuildingInfo>()
        val buildingByPos = mutableMapOf<BlockPos, BuildingInfo>()
        val rawBuildingByPos = mutableMapOf<BlockPos, Any>()

        for (building in rawBuildings) {
            val pos = positionOf(building) ?: continue
            val info = BuildingInfo()
            info.id = pos.hashCode()
            info.blockId = blockIdAt(level, pos)
            info.type = typeOf(building, info.blockId ?: "")
            info.name = prettyName(info.type)
            info.level = ScanCoercion.intOf(invoke(building, "getBuildingLevel").orElse(null), 0)
            info.x = pos.x; info.y = pos.y; info.z = pos.z
            buildings.add(info)
            buildingByPos[pos] = info
            rawBuildingByPos[pos] = building
        }
        return BuildingResult(buildings, buildingByPos, rawBuildingByPos)
    }

    fun positionOf(building: Any): BlockPos? {
        val byId = ScanCoercion.blockPosOf(invoke(building, "getID").orElse(null))
        return byId ?: ScanCoercion.blockPosOf(invoke(building, "getPosition").orElse(null))
    }

    fun typeOf(building: Any, hutBlockId: String): String {
        val type = typeOf(building)
        if (looksLikeId(type)) return type
        return typeFromHutBlock(hutBlockId) ?: type
    }

    fun typeOf(building: Any): String {
        val type = invoke(building, "getBuildingType").orElse(null) ?: return "unknown"
        val key = ScanCoercion.firstNonNull(
            invoke(type, "getRegistryName").orElse(null),
            fieldValue(type, "registryName").orElse(null),
            invoke(type, "getKey").orElse(null))
        if (key is ResourceLocation) return key.toString()
        return key?.toString() ?: type.toString()
    }

    fun countBuilders(buildings: Collection<BuildingInfo>): Int =
        buildings.count { "builder" == Text.pathOf(it.type) }

    fun countRawBuilders(buildings: Collection<Any>): Int =
        buildings.count { "builder" == Text.pathOf(typeOf(it)) }

    fun isWarehouse(info: BuildingInfo): Boolean =
        "warehouse" == Text.pathOf(info.type) || Text.pathOf(info.blockId).lowercase().contains("warehouse")

    fun prettyName(registryName: String): String {
        val path = Text.pathOf(registryName)
        return Text.humanize(path.ifEmpty { "Building" })
    }

    private fun typeFromHutBlock(hutBlockId: String): String? {
        val path = Text.pathOf(hutBlockId).lowercase()
        if (!path.startsWith(HUT_PREFIX) || path.length == HUT_PREFIX.length) return null
        return "minecolonies:" + path.substring(HUT_PREFIX.length)
    }

    private fun looksLikeId(type: String): Boolean =
        type.indexOf(':') > 0 && type.indexOf('@') < 0 && type.indexOf(' ') < 0

    private fun blockIdAt(level: ServerLevel?, pos: BlockPos?): String? {
        if (level == null || pos == null) return null
        return try { BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).block)?.toString() }
        catch (_: Throwable) { null }
    }
}
