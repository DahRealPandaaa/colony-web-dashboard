package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invokeAny
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invokeAnyOf
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invokeStatic
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.staticFieldValue
import DahRealPanda.plugins.colonyweb.model.ItemCount
import DahRealPanda.plugins.colonyweb.model.ResearchInfo
import DahRealPanda.plugins.colonyweb.util.ScanCoercion
import DahRealPanda.plugins.colonyweb.util.Text
import com.mojang.logging.LogUtils
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

class ResearchService {
    private val data = ConcurrentHashMap<Int, ResearchInfo>()

    fun research(colonyId: Int): ResearchInfo? = data[colonyId]
    fun store(colonyId: Int, info: ResearchInfo) { data[colonyId] = info }
    fun retainOnly(current: List<Int>) { data.keys.removeIf { it !in current } }
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private const val GLOBAL_TREE = "com.minecolonies.api.research.IGlobalResearchTree"
        private const val RESEARCH_CONSTANTS = "com.minecolonies.api.research.util.ResearchConstants"
        private const val MAX_PER_BRANCH = 400
    }

    fun scan(colony: Any): ResearchInfo {
        val info = ResearchInfo()
        try {
            val global = invokeStatic(GLOBAL_TREE, "getInstance").orElse(null)
            val manager = invokeAny(colony, "getResearchManager").orElse(null)
            val local = invokeAny(manager, "getResearchTree").orElse(null)
            if (global == null || local == null) return info
            val branches = invokeAny(global, "getBranches").orElse(null)
            if (branches !is Collection<*>) return info
            info.available = true
            val baseTime = ScanCoercion.intOf(staticFieldValue(RESEARCH_CONSTANTS, "BASE_RESEARCH_TIME").orElse(null), 0)

            for (branchId in branches) {
                val branch = readBranch(global, local, branchId!!, baseTime)
                if (branch.researches.isEmpty()) continue
                info.branches.add(branch)
                info.completed += branch.completed
                info.inProgress += branch.inProgress
                info.total += branch.total
            }
            info.branches.sortWith { a, b ->
                Text.stringOrEmpty(a.name).compareTo(Text.stringOrEmpty(b.name), ignoreCase = true)
            }
        } catch (t: Throwable) {
            LOGGER.debug("[ColonyWeb] research scan failed", t)
        }
        return info
    }

    private fun readBranch(global: Any, local: Any, branchId: Any, baseTime: Int): ResearchInfo.Branch {
        val branch = ResearchInfo.Branch()
        branch.id = branchId.toString()
        val branchData = invokeAny(global, "getBranchData", branchId).orElse(null)
        branch.name = Text.displayName(invokeAny(branchData, "getName").orElse(null),
            Text.humanize(Text.pathOf(branch.id)))

        val queue = ArrayDeque<Any>()
        val seen = hashSetOf<String>()
        val primary = invokeAny(global, "getPrimaryResearch", branchId).orElse(null)
        if (primary is Collection<*>) queue.addAll(primary as Collection<Any>)
        while (queue.isNotEmpty() && branch.researches.size < MAX_PER_BRANCH) {
            val id = queue.removeFirstOrNull() ?: continue
            if (!seen.add(id.toString())) continue
            val research = invokeAny(global, "getResearch", branchId, id).orElse(null) ?: continue
            val entry = readEntry(local, research, branchId, id, baseTime)
            branch.researches.add(entry)
            branch.total++
            when (entry.state) {
                "COMPLETED" -> branch.completed++
                "IN_PROGRESS" -> branch.inProgress++
            }
            val children = invokeAny(research, "getChildren").orElse(null)
            if (children is Collection<*>) queue.addAll(children as Collection<Any>)
        }
        branch.researches.sortWith { a, b ->
            if (a.depth != b.depth) a.depth.compareTo(b.depth)
            else Text.stringOrEmpty(a.name).compareTo(Text.stringOrEmpty(b.name), ignoreCase = true)
        }
        return branch
    }

    private fun readEntry(local: Any, research: Any, branchId: Any, id: Any, baseTime: Int): ResearchInfo.Entry {
        val entry = ResearchInfo.Entry()
        entry.id = id.toString()
        entry.branch = branchId.toString()
        entry.name = Text.displayName(invokeAny(research, "getName").orElse(null),
            Text.humanize(Text.pathOf(entry.id)))
        entry.depth = ScanCoercion.intOf(invokeAny(research, "getDepth").orElse(null), 0)

        val localResearch = invokeAny(local, "getResearch", branchId, id).orElse(null)
        entry.state = stateOf(localResearch)
        entry.progress = ScanCoercion.intOf(invokeAny(localResearch, "getProgress").orElse(null), 0)
        entry.maxProgress = if (baseTime > 0 && entry.depth > 0)
            minOf(Int.MAX_VALUE.toLong(), baseTime.toLong() * (1L shl minOf(20, entry.depth - 1))).toInt()
        else 0
        if ("COMPLETED" == entry.state) entry.progress = maxOf(entry.progress, entry.maxProgress)

        collectDescriptions(invokeAny(research, "getEffects").orElse(null), entry.effects)
        collectDescriptions(invokeAny(research, "getResearchRequirements").orElse(null), entry.requirements)
        collectCosts(invokeAny(research, "getCostList").orElse(null), entry.cost)
        return entry
    }

    private fun stateOf(localResearch: Any?): String {
        if (localResearch == null) return "NOT_STARTED"
        val raw = invokeAny(localResearch, "getState").orElse("").toString().uppercase()
        return when {
            raw.contains("FINISH") || raw.contains("COMPLETE") -> "COMPLETED"
            raw.contains("PROGRESS") -> "IN_PROGRESS"
            else -> "NOT_STARTED"
        }
    }

    private fun collectDescriptions(source: Any?, out: MutableList<String>) {
        if (source !is Collection<*>) return
        for (item in source) {
            val desc = invokeAnyOf(item, "getDesc", "getName", "getId").orElse(null)
            val text = Text.displayName(desc, "Unknown")
            if (text != null && text.isNotBlank() && !out.contains(text)) out.add(text)
        }
    }

    private fun collectCosts(source: Any?, out: MutableList<ItemCount>) {
        if (source !is Collection<*>) return
        for (cost in source) {
            val stack = firstStack(invokeAnyOf(cost, "getItems", "getItemStacks").orElse(null))
            if (stack == null || stack.isEmpty) continue
            val count = ScanCoercion.intOf(invokeAnyOf(cost, "getCount", "count").orElse(null), stack.count)
            out.add(ScanCoercion.itemCount(stack, count, -1))
        }
    }

    private fun firstStack(value: Any?): ItemStack? {
        if (value is ItemStack) return if (value.isEmpty) null else value
        if (value is ItemLike) {
            val stack = ItemStack(value)
            return if (stack.isEmpty) null else stack
        }
        val items: Iterable<*>? = when (value) {
            is Array<*> -> value.toList()
            is Iterable<*> -> value
            else -> null
        }
        if (items == null) return null
        for (item in items) {
            val stack = firstStack(item)
            if (stack != null) return stack
        }
        return null
    }
}
