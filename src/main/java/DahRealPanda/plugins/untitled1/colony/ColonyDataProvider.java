package DahRealPanda.plugins.untitled1.colony;

import DahRealPanda.plugins.untitled1.colony.model.BuilderInfo;
import DahRealPanda.plugins.untitled1.colony.model.BuildingInfo;
import DahRealPanda.plugins.untitled1.colony.model.ColonySnapshot;
import DahRealPanda.plugins.untitled1.colony.model.ColonySummary;
import DahRealPanda.plugins.untitled1.colony.model.ResourceEntry;
import DahRealPanda.plugins.untitled1.colony.model.WorkOrderInfo;
import DahRealPanda.plugins.untitled1.texture.DomumOrnamentumResolver;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static DahRealPanda.plugins.untitled1.colony.MineColoniesReflect.fieldValue;
import static DahRealPanda.plugins.untitled1.colony.MineColoniesReflect.invoke;
import static DahRealPanda.plugins.untitled1.colony.MineColoniesReflect.invokeStatic;

/**
 * Enumerates MineColonies colonies and builds immutable snapshots via reflection.
 *
 * <p>All scanning must happen on the server thread (world/tile-entity access is not
 * thread-safe). The produced DTOs are then handed to off-thread HTTP handlers.</p>
 */
public final class ColonyDataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String COLONY_MANAGER = "com.minecolonies.api.colony.IColonyManager";

    private final MinecraftServer server;

    public ColonyDataProvider(MinecraftServer server) {
        this.server = server;
    }

    /** @return true when MineColonies data is available. */
    public boolean available() {
        return MineColoniesReflect.isMineColoniesLoaded();
    }

    /** Enumerate every colony as a lightweight summary. */
    public List<ColonySummary> listColonies() {
        List<ColonySummary> out = new ArrayList<>();
        if (!available()) {
            return out;
        }
        for (Object colony : allColonies()) {
            try {
                ColonySummary s = new ColonySummary();
                s.id = intOf(invoke(colony, "getID").orElse(null), -1);
                s.name = stringOf(invoke(colony, "getName").orElse(null), "Colony " + s.id);
                s.dimension = dimensionOf(colony);
                s.owner = ownerOf(colony);
                BlockPos center = blockPosOf(invoke(colony, "getCenter").orElse(null));
                if (center != null) {
                    s.x = center.getX();
                    s.y = center.getY();
                    s.z = center.getZ();
                }
                Collection<Object> buildings = buildingsOf(colony);
                s.buildingCount = buildings.size();
                Collection<Object> workOrders = workOrdersOf(colony);
                s.activeWorkOrders = workOrders.size();
                s.builderCount = countBuilders(buildings);
                out.add(s);
            } catch (Throwable t) {
                LOGGER.debug("[ColonyWeb] failed to summarize a colony", t);
            }
        }
        return out;
    }

    /** Build a full snapshot for a single colony id. */
    public Optional<ColonySnapshot> snapshot(int colonyId) {
        if (!available()) {
            return Optional.empty();
        }
        for (Object colony : allColonies()) {
            int id = intOf(invoke(colony, "getID").orElse(null), -1);
            if (id != colonyId) {
                continue;
            }
            try {
                return Optional.of(buildSnapshot(colony));
            } catch (Throwable t) {
                LOGGER.debug("[ColonyWeb] failed to snapshot colony {}", colonyId, t);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Snapshot assembly
    // ------------------------------------------------------------------

    private ColonySnapshot buildSnapshot(Object colony) {
        ColonySnapshot snap = new ColonySnapshot();
        snap.id = intOf(invoke(colony, "getID").orElse(null), -1);
        snap.name = stringOf(invoke(colony, "getName").orElse(null), "Colony " + snap.id);
        snap.dimension = dimensionOf(colony);
        snap.owner = ownerOf(colony);

        ServerLevel level = levelForDimension(snap.dimension);

        Collection<Object> buildings = buildingsOf(colony);
        Collection<Object> workOrders = workOrdersOf(colony);

        // Map builder hut position -> BuilderInfo, and index buildings by position.
        Map<BlockPos, BuildingInfo> buildingByPos = new HashMap<>();
        Map<BlockPos, Object> rawBuildingByPos = new HashMap<>();

        for (Object building : buildings) {
            BlockPos pos = buildingPosition(building);
            if (pos == null) {
                continue;
            }
            BuildingInfo info = new BuildingInfo();
            info.id = pos.hashCode();
            info.type = buildingType(building);
            info.name = prettyName(info.type);
            info.level = intOf(invoke(building, "getBuildingLevel").orElse(null), 0);
            info.x = pos.getX();
            info.y = pos.getY();
            info.z = pos.getZ();
            snap.buildings.add(info);
            buildingByPos.put(pos, info);
            rawBuildingByPos.put(pos, building);

            if (isWarehouse(info.type)) {
                snap.warehouse.present = true;
                aggregateWarehouse(level, building, pos, snap.warehouse);
            }
        }

        // Work orders + builder linkage.
        for (Object wo : workOrders) {
            WorkOrderInfo woi = new WorkOrderInfo();
            woi.id = intOf(invoke(wo, "getID").orElse(null), -1);
            BlockPos target = blockPosOf(firstNonNull(
                    invoke(wo, "getLocation").orElse(null),
                    invoke(wo, "getBuildingLocation").orElse(null)));
            if (target != null) {
                woi.x = target.getX();
                woi.y = target.getY();
                woi.z = target.getZ();
            }
            woi.currentLevel = intOf(invoke(wo, "getCurrentLevel").orElse(null), 0);
            woi.targetLevel = intOf(invoke(wo, "getTargetLevel").orElse(null), 0);
            woi.action = workOrderAction(wo, woi.currentLevel, woi.targetLevel);
            woi.progress = progressOf(wo);

            String woName = workOrderName(wo);

            BlockPos claimedBy = blockPosOf(firstNonNull(
                    invoke(wo, "getClaimedBy").orElse(null),
                    invoke(wo, "getClaimedByBuilding").orElse(null)));

            BuildingInfo targetBuilding = target != null ? buildingByPos.get(target) : null;
            boolean decoration = false;

            // A work order whose target is not a registered building is a decoration.
            if (targetBuilding == null && target != null) {
                targetBuilding = new BuildingInfo();
                targetBuilding.id = target.hashCode();
                targetBuilding.kind = "decoration";
                targetBuilding.type = "decoration";
                targetBuilding.name = woName != null ? woName : "Decoration";
                targetBuilding.level = woi.currentLevel;
                targetBuilding.x = target.getX();
                targetBuilding.y = target.getY();
                targetBuilding.z = target.getZ();
                snap.buildings.add(targetBuilding);
                buildingByPos.put(target, targetBuilding);
                decoration = true;
            }

            if (targetBuilding != null) {
                woi.buildingType = targetBuilding.type;
                woi.buildingName = decoration && woName != null ? woName : targetBuilding.name;
                targetBuilding.beingBuilt = true;
                targetBuilding.workOrderId = woi.id;
            } else if (woName != null) {
                woi.buildingName = woName;
            }

            if (claimedBy != null) {
                BuilderInfo builder = ensureBuilder(snap, claimedBy, rawBuildingByPos.get(claimedBy));
                builder.assignedWorkOrderId = woi.id;
                woi.builderId = builder.id;
                woi.builderName = builder.name;

                // Required resources are read from the builder's building (resource scroll parity).
                Object builderBuilding = rawBuildingByPos.get(claimedBy);
                if (builderBuilding != null && targetBuilding != null) {
                    targetBuilding.required.addAll(neededResources(level, builderBuilding, snap));
                }
            }

            snap.workOrders.add(woi);
        }

        LOGGER.info("[ColonyWeb] colony {} ('{}'): buildings={} workOrders={} warehouse={} ({} stacks)",
                snap.id, snap.name, snap.buildings.size(), snap.workOrders.size(),
                snap.warehouse.present, snap.warehouse.stacks.size());

        return snap;
    }

    private BuilderInfo ensureBuilder(ColonySnapshot snap, BlockPos hutPos, Object rawBuilding) {
        for (BuilderInfo b : snap.builders) {
            if (b.hutX == hutPos.getX() && b.hutY == hutPos.getY() && b.hutZ == hutPos.getZ()) {
                return b;
            }
        }
        BuilderInfo b = new BuilderInfo();
        b.id = hutPos.hashCode();
        b.hutX = hutPos.getX();
        b.hutY = hutPos.getY();
        b.hutZ = hutPos.getZ();
        b.name = builderName(rawBuilding);
        snap.builders.add(b);
        return b;
    }

    // ------------------------------------------------------------------
    // MineColonies reflection helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Collection<Object> allColonies() {
        // IColonyManager.getInstance()
        Object manager = invokeStatic(COLONY_MANAGER, "getInstance").orElse(null);
        if (manager == null) {
            return List.of();
        }
        // Try a no-arg getAllColonies() first.
        Object all = invoke(manager, "getAllColonies").orElse(null);
        if (all instanceof Collection<?> c && !c.isEmpty()) {
            return new ArrayList<>((Collection<Object>) c);
        }
        // Fall back to per-level enumeration.
        List<Object> result = new ArrayList<>();
        for (ServerLevel lvl : server.getAllLevels()) {
            Object colonies = invoke(manager, "getColonies",
                    new Class<?>[]{net.minecraft.world.level.Level.class}, lvl).orElse(null);
            if (colonies instanceof Collection<?> cc) {
                result.addAll((Collection<Object>) cc);
            }
        }
        return result;
    }

    /** Resolve the server-side structure/building manager for a colony. */
    private Object structureManager(Object colony) {
        // IColony#getServerBuildingManager() (server side) -> IRegisteredStructureManager.
        Object bm = invoke(colony, "getServerBuildingManager").orElse(null);
        if (bm == null) {
            // Fallbacks for older/renamed APIs.
            bm = firstNonNull(
                    invoke(colony, "getBuildingManager").orElse(null),
                    invoke(colony, "getCommonBuildingManager").orElse(null));
        }
        return bm;
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> buildingsOf(Object colony) {
        Object bm = structureManager(colony);
        if (bm == null) {
            return List.of();
        }
        Object buildings = invoke(bm, "getBuildings").orElse(null);
        if (buildings instanceof Map<?, ?> m) {
            return new ArrayList<>((Collection<Object>) m.values());
        }
        if (buildings instanceof Collection<?> c) {
            return new ArrayList<>((Collection<Object>) c);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> workOrdersOf(Object colony) {
        Object wm = invoke(colony, "getWorkManager").orElse(null);
        if (wm == null) {
            return List.of();
        }
        Object orders = invoke(wm, "getWorkOrders").orElse(null);
        if (orders instanceof Map<?, ?> m) {
            return new ArrayList<>((Collection<Object>) m.values());
        }
        if (orders instanceof Collection<?> c) {
            return new ArrayList<>((Collection<Object>) c);
        }
        return List.of();
    }

    private int countBuilders(Collection<Object> buildings) {
        int n = 0;
        for (Object b : buildings) {
            if ("builder".equals(pathOf(buildingType(b)))) {
                n++;
            }
        }
        return n;
    }

    private BlockPos buildingPosition(Object building) {
        // MineColonies keys its building map (and work-order claimedBy/location) by getID().
        BlockPos p = blockPosOf(invoke(building, "getID").orElse(null));
        if (p != null) {
            return p;
        }
        return blockPosOf(invoke(building, "getPosition").orElse(null));
    }

    private String buildingType(Object building) {
        Object type = invoke(building, "getBuildingType").orElse(null);
        if (type == null) {
            return "unknown";
        }
        Object rl = firstNonNull(
                invoke(type, "getRegistryName").orElse(null),
                fieldValue(type, "registryName").orElse(null));
        if (rl instanceof ResourceLocation loc) {
            return loc.toString();
        }
        return String.valueOf(rl != null ? rl : type);
    }

    private String builderName(Object rawBuilding) {
        if (rawBuilding == null) {
            return "Builder";
        }
        // Try the assigned citizen's name.
        Object citizens = invoke(rawBuilding, "getAllAssignedCitizen").orElse(null);
        if (citizens instanceof Collection<?> c && !c.isEmpty()) {
            Object first = c.iterator().next();
            String name = stringOf(invoke(first, "getName").orElse(null), null);
            if (name != null) {
                return name;
            }
        }
        return "Builder";
    }

    private double progressOf(Object wo) {
        // Not all versions expose progress; default to 0 when unknown.
        Object done = invoke(wo, "getProgress").orElse(null);
        if (done instanceof Number n) {
            double d = n.doubleValue();
            if (d > 1.0) {
                d = d / 100.0;
            }
            return Math.max(0.0, Math.min(1.0, d));
        }
        return 0.0;
    }

    private String workOrderAction(Object wo, int current, int target) {
        Object t = invoke(wo, "getWorkOrderType").orElse(null);
        if (t != null) {
            String s = String.valueOf(t).toUpperCase();
            if (s.contains("BUILD")) return current <= 0 ? "BUILD" : "UPGRADE";
            if (s.contains("UPGRADE")) return "UPGRADE";
            if (s.contains("REPAIR")) return "REPAIR";
            if (s.contains("REMOVE")) return "REMOVE";
        }
        if (target > current && current > 0) return "UPGRADE";
        if (target > 0 && current <= 0) return "BUILD";
        return "BUILD";
    }

    /** Best-effort readable name for a work order (used for decorations without a building). */
    private String workOrderName(Object wo) {
        // Prefer an explicit display/custom name.
        Object display = firstNonNull(
                invoke(wo, "getCustomName").orElse(null),
                invoke(wo, "getName").orElse(null),
                invoke(wo, "getDisplayName").orElse(null));
        String name = display != null ? String.valueOf(display) : null;
        if (name != null && !name.isBlank() && !name.contains(":")) {
            return prettyName(name);
        }
        // Fall back to the structure path, e.g. ".../decorations/roads/paved" -> "Paved".
        Object path = firstNonNull(
                invoke(wo, "getStructureName").orElse(null),
                invoke(wo, "getStructurePath").orElse(null),
                invoke(wo, "getTranslationKey").orElse(null));
        if (path != null) {
            String s = String.valueOf(path);
            int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('.'));
            if (slash >= 0 && slash < s.length() - 1) {
                s = s.substring(slash + 1);
            }
            if (!s.isBlank()) {
                return prettyName(s);
            }
        }
        return name;
    }

    // ------------------------------------------------------------------
    // Resource requirements (builder building) + inventories
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<ResourceEntry> neededResources(ServerLevel level, Object builderBuilding, ColonySnapshot snap) {
        List<ResourceEntry> out = new ArrayList<>();
        Object needed = firstNonNull(
                invoke(builderBuilding, "getNeededResources").orElse(null),
                invoke(builderBuilding, "getRequiredResources").orElse(null));
        if (!(needed instanceof Map<?, ?> map)) {
            return out;
        }
        for (Object res : ((Map<Object, Object>) map).values()) {
            try {
                ItemStack stack = itemStackOf(invoke(res, "getItemStack").orElse(null));
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                int amount = intOf(firstNonNull(
                        invoke(res, "getAmount").orElse(null),
                        invoke(res, "getNeededAmount").orElse(null)), 0);
                int available = intOf(invoke(res, "getAvailable").orElse(null), 0);
                if (amount <= 0) {
                    continue;
                }
                ResourceEntry e = new ResourceEntry();
                e.itemKey = DomumOrnamentumResolver.textureKeyFor(stack);
                e.name = stack.getHoverName().getString();
                e.material = DomumOrnamentumResolver.isDomum(stack)
                        ? DomumOrnamentumResolver.materialName(stack).orElse(null) : null;
                e.needed = amount;
                e.inHut = available;
                e.inWarehouse = warehouseCount(snap, e.itemKey);
                int shortfall = Math.max(0, amount - available);
                e.deliverable = shortfall > 0 && e.inWarehouse >= shortfall;
                out.add(e);
            } catch (Throwable t) {
                LOGGER.debug("[ColonyWeb] failed to read a needed resource", t);
            }
        }
        return out;
    }

    private int warehouseCount(ColonySnapshot snap, String itemKey) {
        int total = 0;
        for (ColonySnapshot.Stack s : snap.warehouse.stacks) {
            if (s.itemKey.equals(itemKey)) {
                total += s.count;
            }
        }
        return total;
    }

    private void aggregateWarehouse(ServerLevel level, Object building, BlockPos pos, ColonySnapshot.Warehouse warehouse) {
        Map<String, ColonySnapshot.Stack> byKey = new LinkedHashMap<>();
        for (ColonySnapshot.Stack existing : warehouse.stacks) {
            byKey.put(existing.itemKey, existing);
        }
        for (IItemHandler handler : inventoriesFor(level, building, pos)) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                String key = DomumOrnamentumResolver.textureKeyFor(stack);
                ColonySnapshot.Stack agg = byKey.get(key);
                if (agg == null) {
                    agg = new ColonySnapshot.Stack(key, stack.getHoverName().getString(), 0);
                    agg.material = DomumOrnamentumResolver.isDomum(stack)
                            ? DomumOrnamentumResolver.materialName(stack).orElse(null) : null;
                    byKey.put(key, agg);
                    warehouse.stacks.add(agg);
                }
                agg.count += stack.getCount();
            }
        }
    }

    /**
     * Collect item handlers backing a building. Warehouses have multiple racks, so we scan
     * the building's registered container positions when available, else the hut block entity.
     */
    private List<IItemHandler> inventoriesFor(ServerLevel level, Object building, BlockPos hutPos) {
        List<IItemHandler> handlers = new ArrayList<>();
        if (level == null) {
            return handlers;
        }
        List<BlockPos> positions = new ArrayList<>();
        positions.add(hutPos);
        Object containers = invoke(building, "getContainers").orElse(null);
        if (containers instanceof Collection<?> c) {
            for (Object o : c) {
                BlockPos p = blockPosOf(o);
                if (p != null) {
                    positions.add(p);
                }
            }
        }
        for (BlockPos p : positions) {
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) {
                continue;
            }
            be.getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .ifPresent(handlers::add);
        }
        return handlers;
    }

    // ------------------------------------------------------------------
    // Dimension / level helpers
    // ------------------------------------------------------------------

    private String dimensionOf(Object colony) {
        Object dim = invoke(colony, "getDimension").orElse(null);
        if (dim == null) {
            return "minecraft:overworld";
        }
        Object loc = invoke(dim, "location").orElse(null);
        if (loc instanceof ResourceLocation rl) {
            return rl.toString();
        }
        return String.valueOf(dim);
    }

    private String ownerOf(Object colony) {
        Object perms = invoke(colony, "getPermissions").orElse(null);
        if (perms != null) {
            String owner = stringOf(invoke(perms, "getOwnerName").orElse(null), null);
            if (owner != null) {
                return owner;
            }
        }
        return "";
    }

    private ServerLevel levelForDimension(String dimension) {
        for (ServerLevel lvl : server.getAllLevels()) {
            if (lvl.dimension().location().toString().equals(dimension)) {
                return lvl;
            }
        }
        return server.overworld();
    }

    // ------------------------------------------------------------------
    // Primitive coercion helpers
    // ------------------------------------------------------------------

    private static boolean isWarehouse(String type) {
        return "warehouse".equals(pathOf(type));
    }

    private static String pathOf(String registryName) {
        if (registryName == null) {
            return "";
        }
        int idx = registryName.indexOf(':');
        return idx >= 0 ? registryName.substring(idx + 1) : registryName;
    }

    private static String prettyName(String registryName) {
        String path = pathOf(registryName);
        if (path.isEmpty()) {
            return "Building";
        }
        String[] parts = path.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private static Object firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static int intOf(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return def;
    }

    private static String stringOf(Object o, String def) {
        if (o == null) {
            return def;
        }
        String s = String.valueOf(o);
        return s.isEmpty() ? def : s;
    }

    private static BlockPos blockPosOf(Object o) {
        return o instanceof BlockPos ? (BlockPos) o : null;
    }

    private static ItemStack itemStackOf(Object o) {
        return o instanceof ItemStack ? (ItemStack) o : null;
    }

    /** Look up an item's registry key string. */
    public static String registryKey(ItemStack stack) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return rl != null ? rl.toString() : "minecraft:air";
    }
}
