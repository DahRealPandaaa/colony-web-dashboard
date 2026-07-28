package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.colony.model.ItemCount;
import DahRealPanda.plugins.colonyweb.colony.model.ResearchInfo;
import DahRealPanda.plugins.colonyweb.util.Text;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invokeAny;
import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invokeAnyOf;
import static DahRealPanda.plugins.colonyweb.colony.Scan.intOf;

/**
 * Walks the MineColonies research tree and reports, per branch, what this colony has
 * finished, what its university is working on and what is still locked.
 *
 * <p>The tree structure comes from the global (data-driven) research registry; the per-colony
 * state comes from the colony's own local research tree.</p>
 */
public final class ResearchScanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String GLOBAL_TREE = "com.minecolonies.api.research.IGlobalResearchTree";
    private static final String RESEARCH_CONSTANTS = "com.minecolonies.api.research.util.ResearchConstants";

    /** Safety valve so a pathological data pack cannot produce an unbounded payload. */
    private static final int MAX_PER_BRANCH = 400;

    public ResearchInfo scan(Object colony) {
        ResearchInfo info = new ResearchInfo();
        try {
            Object global = MineColoniesReflect.invokeStatic(GLOBAL_TREE, "getInstance").orElse(null);
            Object manager = invokeAny(colony, "getResearchManager").orElse(null);
            Object local = invokeAny(manager, "getResearchTree").orElse(null);
            if (global == null || local == null) {
                return info;
            }
            Object branches = invokeAny(global, "getBranches").orElse(null);
            if (!(branches instanceof Collection<?> branchIds)) {
                return info;
            }
            info.available = true;
            int baseTime = intOf(MineColoniesReflect.staticFieldValue(RESEARCH_CONSTANTS, "BASE_RESEARCH_TIME")
                    .orElse(null), 0);

            for (Object branchId : branchIds) {
                ResearchInfo.Branch branch = readBranch(global, local, branchId, baseTime);
                if (branch.researches.isEmpty()) {
                    continue;
                }
                info.branches.add(branch);
                info.completed += branch.completed;
                info.inProgress += branch.inProgress;
                info.total += branch.total;
            }
            info.branches.sort((a, b) -> Text.stringOrEmpty(a.name).compareToIgnoreCase(Text.stringOrEmpty(b.name)));
        } catch (Throwable t) {
            LOGGER.debug("[ColonyWeb] research scan failed", t);
        }
        return info;
    }

    private ResearchInfo.Branch readBranch(Object global, Object local, Object branchId, int baseTime) {
        ResearchInfo.Branch branch = new ResearchInfo.Branch();
        branch.id = String.valueOf(branchId);
        Object branchData = invokeAny(global, "getBranchData", branchId).orElse(null);
        branch.name = Text.displayName(invokeAny(branchData, "getName").orElse(null),
                Text.humanize(Text.pathOf(branch.id)));

        // Breadth-first from each root research, following child links.
        Deque<Object> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        Object primary = invokeAny(global, "getPrimaryResearch", branchId).orElse(null);
        if (primary instanceof Collection<?> roots) {
            queue.addAll(roots);
        }
        while (!queue.isEmpty() && branch.researches.size() < MAX_PER_BRANCH) {
            Object id = queue.poll();
            if (id == null || !seen.add(String.valueOf(id))) {
                continue;
            }
            Object research = invokeAny(global, "getResearch", branchId, id).orElse(null);
            if (research == null) {
                continue;
            }
            ResearchInfo.Entry entry = readEntry(local, research, branchId, id, baseTime);
            branch.researches.add(entry);
            branch.total++;
            if ("COMPLETED".equals(entry.state)) {
                branch.completed++;
            } else if ("IN_PROGRESS".equals(entry.state)) {
                branch.inProgress++;
            }
            Object children = invokeAny(research, "getChildren").orElse(null);
            if (children instanceof Collection<?> kids) {
                queue.addAll(kids);
            }
        }
        branch.researches.sort((a, b) -> a.depth != b.depth
                ? Integer.compare(a.depth, b.depth)
                : Text.stringOrEmpty(a.name).compareToIgnoreCase(Text.stringOrEmpty(b.name)));
        return branch;
    }

    private ResearchInfo.Entry readEntry(Object local, Object research, Object branchId, Object id, int baseTime) {
        ResearchInfo.Entry entry = new ResearchInfo.Entry();
        entry.id = String.valueOf(id);
        entry.branch = String.valueOf(branchId);
        entry.name = Text.displayName(invokeAny(research, "getName").orElse(null),
                Text.humanize(Text.pathOf(entry.id)));
        entry.depth = intOf(invokeAny(research, "getDepth").orElse(null), 0);

        Object localResearch = invokeAny(local, "getResearch", branchId, id).orElse(null);
        entry.state = stateOf(localResearch);
        entry.progress = intOf(invokeAny(localResearch, "getProgress").orElse(null), 0);
        // MineColonies doubles the research duration with every step down a branch.
        entry.maxProgress = baseTime > 0 && entry.depth > 0
                ? (int) Math.min(Integer.MAX_VALUE, (long) baseTime * (1L << Math.min(20, entry.depth - 1)))
                : 0;
        if ("COMPLETED".equals(entry.state)) {
            entry.progress = Math.max(entry.progress, entry.maxProgress);
        }

        collectDescriptions(invokeAny(research, "getEffects").orElse(null), entry.effects);
        collectDescriptions(invokeAny(research, "getResearchRequirements").orElse(null), entry.requirements);
        collectCosts(invokeAny(research, "getCostList").orElse(null), entry.cost);
        return entry;
    }

    private static String stateOf(Object localResearch) {
        if (localResearch == null) {
            return "NOT_STARTED";
        }
        String raw = String.valueOf(invokeAny(localResearch, "getState").orElse("")).toUpperCase(Locale.ROOT);
        if (raw.contains("FINISH") || raw.contains("COMPLETE")) {
            return "COMPLETED";
        }
        if (raw.contains("PROGRESS")) {
            return "IN_PROGRESS";
        }
        return "NOT_STARTED";
    }

    /** Requirements describe themselves through {@code getDesc}; effects carry theirs as the name. */
    private static void collectDescriptions(Object source, List<String> out) {
        if (!(source instanceof Collection<?> collection)) {
            return;
        }
        for (Object item : collection) {
            Object desc = invokeAnyOf(item, "getDesc", "getName", "getId").orElse(null);
            String text = Text.displayName(desc, null);
            if (text != null && !text.isBlank() && !out.contains(text)) {
                out.add(text);
            }
        }
    }

    /**
     * Research costs changed shape between versions: 1.20.1 hands back MineColonies' own
     * {@code IResearchCost} (a list of {@code Item} plus {@code getCount()}), 1.21.1 hands
     * back a NeoForge {@code SizedIngredient} (an array of stacks plus {@code count()}).
     * Both expose the items under {@code getItems}.
     */
    private static void collectCosts(Object source, List<ItemCount> out) {
        if (!(source instanceof Collection<?> collection)) {
            return;
        }
        for (Object cost : collection) {
            ItemStack stack = firstStack(invokeAnyOf(cost, "getItems", "getItemStacks").orElse(null));
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int count = intOf(invokeAnyOf(cost, "getCount", "count").orElse(null), stack.getCount());
            out.add(Scan.itemCount(stack, count, -1));
        }
    }

    /** First usable stack out of an item or stack, or an array/collection of either. */
    private static ItemStack firstStack(Object value) {
        if (value instanceof ItemStack stack) {
            return stack.isEmpty() ? null : stack;
        }
        if (value instanceof ItemLike item) {
            ItemStack stack = new ItemStack(item);
            return stack.isEmpty() ? null : stack;
        }
        Iterable<?> items = value instanceof Object[] array ? Arrays.asList(array)
                : value instanceof Iterable<?> iterable ? iterable
                : null;
        if (items == null) {
            return null;
        }
        for (Object item : items) {
            ItemStack stack = firstStack(item);
            if (stack != null) {
                return stack;
            }
        }
        return null;
    }
}
