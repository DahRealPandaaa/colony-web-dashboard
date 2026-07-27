package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import DahRealPanda.plugins.colonyweb.colony.model.BuilderInfo;
import DahRealPanda.plugins.colonyweb.colony.model.BuildingInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;
import DahRealPanda.plugins.colonyweb.colony.model.ResourceEntry;
import DahRealPanda.plugins.colonyweb.colony.model.WorkOrderInfo;
import DahRealPanda.plugins.colonyweb.util.Text;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invoke;
import static DahRealPanda.plugins.colonyweb.colony.Scan.firstNonNull;
import static DahRealPanda.plugins.colonyweb.colony.Scan.intOf;

/**
 * Reads the colony's work orders: what is being built, who claimed it, how far along it is,
 * and which resources the job still needs.
 *
 * <p>Required resources are read from the assigned <em>builder's</em> building rather than the
 * target, which is what MineColonies' in-game resource scroll shows.</p>
 */
public final class WorkOrderScanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    public void scan(Collection<Object> workOrders, ScanContext ctx) {
        for (Object workOrder : workOrders) {
            try {
                ctx.snapshot.workOrders.add(read(workOrder, ctx));
            } catch (Throwable t) {
                LOGGER.debug("{} failed to read a work order", ColonyWeb.LOG, t);
            }
        }
    }

    private WorkOrderInfo read(Object workOrder, ScanContext ctx) {
        WorkOrderInfo info = new WorkOrderInfo();
        info.id = intOf(invoke(workOrder, "getID").orElse(null), -1);

        BlockPos target = Scan.blockPosOf(firstNonNull(
                invoke(workOrder, "getLocation").orElse(null),
                invoke(workOrder, "getBuildingLocation").orElse(null)));
        if (target != null) {
            info.x = target.getX();
            info.y = target.getY();
            info.z = target.getZ();
        }
        info.currentLevel = intOf(invoke(workOrder, "getCurrentLevel").orElse(null), 0);
        info.targetLevel = intOf(invoke(workOrder, "getTargetLevel").orElse(null), 0);
        info.action = actionOf(workOrder, info.currentLevel, info.targetLevel);

        String structureName = nameOf(workOrder);
        BuildingInfo targetBuilding = target != null ? ctx.buildingByPos.get(target) : null;
        boolean decoration = false;
        if (targetBuilding == null && target != null) {
            targetBuilding = addDecoration(ctx, target, structureName, info.currentLevel);
            decoration = true;
        }
        if (targetBuilding != null) {
            info.buildingType = targetBuilding.type;
            info.buildingName = decoration && structureName != null ? structureName : targetBuilding.name;
            targetBuilding.beingBuilt = true;
            targetBuilding.workOrderId = info.id;
        } else if (structureName != null) {
            info.buildingName = structureName;
        }

        linkBuilder(workOrder, info, targetBuilding, ctx);
        return info;
    }

    /** A work order whose target is not a registered building is a decoration. */
    private BuildingInfo addDecoration(ScanContext ctx, BlockPos pos, String name, int level) {
        BuildingInfo decoration = new BuildingInfo();
        decoration.id = pos.hashCode();
        decoration.kind = "decoration";
        decoration.type = "decoration";
        decoration.name = name != null ? name : "Decoration";
        decoration.level = level;
        decoration.x = pos.getX();
        decoration.y = pos.getY();
        decoration.z = pos.getZ();
        ctx.snapshot.buildings.add(decoration);
        ctx.buildingByPos.put(pos, decoration);
        return decoration;
    }

    /** Attach the claiming builder, their progress, and the resources the job still needs. */
    private void linkBuilder(Object workOrder, WorkOrderInfo info, BuildingInfo targetBuilding, ScanContext ctx) {
        BlockPos claimedBy = Scan.blockPosOf(firstNonNull(
                invoke(workOrder, "getClaimedBy").orElse(null),
                invoke(workOrder, "getClaimedByBuilding").orElse(null)));
        // An order still in the queue is not claimed by anyone. MineColonies signals that with
        // the origin rather than null — its own AbstractWorkOrder.isClaimed() is exactly
        // "claimedBy != BlockPos.ZERO" — so the origin has to be filtered out here too, or every
        // queued order ends up attributed to a builder that does not exist.
        if (claimedBy == null || BlockPos.ZERO.equals(claimedBy)) {
            return;
        }
        Object builderBuilding = ctx.rawBuildingByPos.get(claimedBy);
        BuilderInfo builder = ensureBuilder(ctx.snapshot, claimedBy, builderBuilding);
        builder.assignedWorkOrderId = info.id;
        info.builderId = builder.id;
        info.builderName = builder.name;

        if (builderBuilding == null) {
            return;
        }
        info.progress = progressOf(workOrder, builderBuilding);
        if (targetBuilding != null) {
            targetBuilding.required.addAll(neededResources(builderBuilding, ctx.snapshot));
        }
    }

    private BuilderInfo ensureBuilder(ColonySnapshot snapshot, BlockPos hutPos, Object rawBuilding) {
        for (BuilderInfo existing : snapshot.builders) {
            if (existing.hutX == hutPos.getX() && existing.hutY == hutPos.getY() && existing.hutZ == hutPos.getZ()) {
                return existing;
            }
        }
        BuilderInfo builder = new BuilderInfo();
        builder.id = hutPos.hashCode();
        builder.hutX = hutPos.getX();
        builder.hutY = hutPos.getY();
        builder.hutZ = hutPos.getZ();
        builder.name = builderName(rawBuilding);
        snapshot.builders.add(builder);
        return builder;
    }

    private String builderName(Object rawBuilding) {
        Object citizens = invoke(rawBuilding, "getAllAssignedCitizen").orElse(null);
        if (citizens instanceof Collection<?> assigned && !assigned.isEmpty()) {
            String name = Scan.stringOf(invoke(assigned.iterator().next(), "getName").orElse(null), null);
            if (name != null) {
                return name;
            }
        }
        return "Builder";
    }

    /**
     * Build progress in [0,1], matching MineColonies' own calculation:
     * {@code 1 - (remaining needed resources / total build resources)}.
     */
    @SuppressWarnings("unchecked")
    private double progressOf(Object workOrder, Object builderBuilding) {
        int total = intOf(invoke(workOrder, "getAmountOfResources").orElse(null), 0);
        Object needed = invoke(builderBuilding, "getNeededResources").orElse(null);
        if (total <= 0 || !(needed instanceof Map<?, ?> map)) {
            return 0.0;
        }
        int remaining = 0;
        for (Object resource : ((Map<Object, Object>) map).values()) {
            remaining += intOf(invoke(resource, "getAmount").orElse(null), 0);
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - ((double) remaining / (double) total)));
    }

    @SuppressWarnings("unchecked")
    private List<ResourceEntry> neededResources(Object builderBuilding, ColonySnapshot snapshot) {
        List<ResourceEntry> resources = new ArrayList<>();
        Object needed = firstNonNull(
                invoke(builderBuilding, "getNeededResources").orElse(null),
                invoke(builderBuilding, "getRequiredResources").orElse(null));
        if (!(needed instanceof Map<?, ?> map)) {
            return resources;
        }
        for (Object raw : ((Map<Object, Object>) map).values()) {
            try {
                ItemStack stack = Scan.itemStackOf(invoke(raw, "getItemStack").orElse(null));
                int amount = intOf(firstNonNull(
                        invoke(raw, "getAmount").orElse(null),
                        invoke(raw, "getNeededAmount").orElse(null)), 0);
                if (stack == null || stack.isEmpty() || amount <= 0) {
                    continue;
                }
                ResourceEntry entry = Scan.fillItem(new ResourceEntry(), stack);
                entry.needed = amount;
                entry.maxStackSize = Math.max(1, stack.getMaxStackSize());
                entry.inHut = intOf(invoke(raw, "getAvailable").orElse(null), 0);
                entry.inWarehouse = WarehouseScanner.countIn(snapshot, entry.itemKey);
                int shortfall = Math.max(0, amount - entry.inHut);
                entry.deliverable = shortfall > 0 && entry.inWarehouse >= shortfall;
                resources.add(entry);
            } catch (Throwable t) {
                LOGGER.debug("{} failed to read a needed resource", ColonyWeb.LOG, t);
            }
        }
        return resources;
    }

    private String actionOf(Object workOrder, int current, int target) {
        Object type = invoke(workOrder, "getWorkOrderType").orElse(null);
        if (type != null) {
            String name = String.valueOf(type).toUpperCase(Locale.ROOT);
            if (name.contains("BUILD")) {
                return current <= 0 ? "BUILD" : "UPGRADE";
            }
            if (name.contains("UPGRADE")) {
                return "UPGRADE";
            }
            if (name.contains("REPAIR")) {
                return "REPAIR";
            }
            if (name.contains("REMOVE")) {
                return "REMOVE";
            }
        }
        return target > current && current > 0 ? "UPGRADE" : "BUILD";
    }

    /** Best-effort readable name for a work order (used for decorations without a building). */
    private String nameOf(Object workOrder) {
        // Prefer the structure path — it yields a clean id we can humanize.
        String path = Text.componentString(firstNonNull(
                invoke(workOrder, "getStructureName").orElse(null),
                invoke(workOrder, "getStructurePath").orElse(null)));
        if (path != null && !path.isBlank()) {
            String humanized = Text.humanize(path);
            if (!humanized.isBlank()) {
                return humanized;
            }
        }
        // Then an explicit custom/display name (unwrap Components, never toString() them).
        String display = Text.componentString(firstNonNull(
                invoke(workOrder, "getCustomName").orElse(null),
                invoke(workOrder, "getDisplayName").orElse(null),
                invoke(workOrder, "getName").orElse(null)));
        if (display != null && !display.isBlank()) {
            // A single token is still an id (e.g. "sawmill1"); anything with spaces is prose.
            return display.contains(" ") ? display : Text.humanize(display);
        }
        String key = Text.componentString(invoke(workOrder, "getTranslationKey").orElse(null));
        return key != null && !key.isBlank() ? Text.humanize(key) : "Decoration";
    }
}
