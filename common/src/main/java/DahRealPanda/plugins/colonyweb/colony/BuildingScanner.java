package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.colony.model.BuildingInfo;
import DahRealPanda.plugins.colonyweb.util.Text;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.fieldValue;
import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invoke;

/**
 * Turns a colony's raw buildings into {@link BuildingInfo} DTOs and indexes them by hut
 * position, folding warehouse stock in along the way.
 */
public final class BuildingScanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Every MineColonies hut block id starts with this. */
    private static final String HUT_PREFIX = "blockhut";

    /** Colony id to the building types last reported as warehouse-less, to log only on change. */
    private static final Map<Integer, List<String>> LOGGED_NO_WAREHOUSE = new ConcurrentHashMap<>();

    public void scan(Collection<Object> buildings, ScanContext ctx) {
        // Must be per scan: it remembers which racks it has already counted, so a shared
        // instance would treat every rack as already tallied from the second scan onward and
        // hand back an empty warehouse forever.
        WarehouseScanner warehouseScanner = new WarehouseScanner();

        for (Object building : buildings) {
            BlockPos pos = positionOf(building);
            if (pos == null) {
                continue;
            }
            BuildingInfo info = new BuildingInfo();
            info.id = pos.hashCode();
            info.blockId = blockIdAt(ctx.level, pos);
            info.type = typeOf(building, info.blockId);
            info.name = prettyName(info.type);
            info.level = Scan.intOf(invoke(building, "getBuildingLevel").orElse(null), 0);
            info.x = pos.getX();
            info.y = pos.getY();
            info.z = pos.getZ();

            ctx.snapshot.buildings.add(info);
            ctx.buildingByPos.put(pos, info);
            ctx.rawBuildingByPos.put(pos, building);

            if (isWarehouse(info)) {
                ctx.snapshot.warehouse.present = true;
                warehouseScanner.addWarehouse(ctx.level, building, pos, ctx.snapshot.warehouse);
            }
        }
        if (!ctx.snapshot.warehouse.present && !buildings.isEmpty()) {
            logNoWarehouse(ctx);
        }
    }

    /**
     * A colony with no warehouse yet is a normal state, not a fault — but the same is true of
     * a warehouse whose hut id we failed to recognise, and only this list tells them apart.
     * Logged at debug, and only when the colony's set of building types has changed, because
     * the scan runs every few seconds for the lifetime of the server.
     */
    private static void logNoWarehouse(ScanContext ctx) {
        List<String> types = ctx.snapshot.buildings.stream()
                .map(b -> b.type + " @" + b.blockId)
                .distinct().sorted().limit(30).toList();
        if (types.equals(LOGGED_NO_WAREHOUSE.put(ctx.snapshot.id, types))) {
            return;
        }
        LOGGER.debug("[ColonyWeb] colony {} has no warehouse; building types seen: {}",
                ctx.snapshot.id, types);
    }

    /** MineColonies keys buildings (and work-order claims) by the hut's position. */
    public static BlockPos positionOf(Object building) {
        BlockPos byId = Scan.blockPosOf(invoke(building, "getID").orElse(null));
        return byId != null ? byId : Scan.blockPosOf(invoke(building, "getPosition").orElse(null));
    }

    /**
     * Registry id of a building's type, e.g. {@code minecolonies:barracks}.
     *
     * <p>Forge dropped {@code getRegistryName()} from registry entries in 1.19, so on some
     * MineColonies builds this resolves to nothing useful and we fall back to the hut block
     * actually placed in the world — {@code minecolonies:blockhutwarehouse} tells us just as
     * much, and its id is stable across versions.</p>
     */
    public static String typeOf(Object building, String hutBlockId) {
        String type = typeOf(building);
        if (looksLikeId(type)) {
            return type;
        }
        String derived = typeFromHutBlock(hutBlockId);
        return derived != null ? derived : type;
    }

    /** Registry id straight from the building type, which may not resolve on every build. */
    public static String typeOf(Object building) {
        Object type = invoke(building, "getBuildingType").orElse(null);
        if (type == null) {
            return "unknown";
        }
        Object registryName = Scan.firstNonNull(
                invoke(type, "getRegistryName").orElse(null),
                fieldValue(type, "registryName").orElse(null),
                invoke(type, "getKey").orElse(null));
        if (registryName instanceof ResourceLocation location) {
            return location.toString();
        }
        return String.valueOf(registryName != null ? registryName : type);
    }

    /** {@code minecolonies:blockhutwarehouse} -> {@code minecolonies:warehouse}. */
    private static String typeFromHutBlock(String hutBlockId) {
        String path = Text.pathOf(hutBlockId).toLowerCase(Locale.ROOT);
        if (!path.startsWith(HUT_PREFIX) || path.length() == HUT_PREFIX.length()) {
            return null;
        }
        return "minecolonies:" + path.substring(HUT_PREFIX.length());
    }

    /** A usable {@code namespace:path}, rather than an object's {@code toString()}. */
    private static boolean looksLikeId(String type) {
        return type != null
                && type.indexOf(':') > 0
                && type.indexOf('@') < 0
                && type.indexOf(' ') < 0;
    }

    public static int countBuilders(Collection<BuildingInfo> buildings) {
        int builders = 0;
        for (BuildingInfo building : buildings) {
            if ("builder".equals(Text.pathOf(building.type))) {
                builders++;
            }
        }
        return builders;
    }

    /**
     * Builder count straight from raw buildings, for the colony list.
     *
     * <p>That endpoint deliberately does not touch the world, so it cannot fall back to the hut
     * block the way a full scan does — it is a cheap approximation for a selector label.</p>
     */
    public static int countRawBuilders(Collection<Object> buildings) {
        int builders = 0;
        for (Object building : buildings) {
            if ("builder".equals(Text.pathOf(typeOf(building)))) {
                builders++;
            }
        }
        return builders;
    }

    /**
     * Whether a building is a warehouse. Checked against both the building type and the hut
     * block, so a colony's stock still shows up when the type registry lookup comes back empty.
     */
    public static boolean isWarehouse(BuildingInfo info) {
        return "warehouse".equals(Text.pathOf(info.type))
                || Text.pathOf(info.blockId).toLowerCase(Locale.ROOT).contains("warehouse");
    }

    public static String prettyName(String registryName) {
        String path = Text.pathOf(registryName);
        return Text.humanize(path.isEmpty() ? "Building" : path);
    }

    /** Registry id of the block placed at a position — the MineColonies hut block, used as the icon. */
    private static String blockIdAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        try {
            ResourceLocation location = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            return location != null ? location.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
