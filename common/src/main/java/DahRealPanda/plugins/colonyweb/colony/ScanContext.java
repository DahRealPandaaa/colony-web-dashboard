package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.colony.model.BuildingInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * The mutable working state shared by the scanners while one colony is being scanned.
 *
 * <p>Buildings are indexed by position because MineColonies keys work orders, builder claims
 * and citizen workplaces by the hut's {@code BlockPos} — every later stage needs to look
 * buildings up that way.</p>
 */
public final class ScanContext {
    /** The level the colony lives in; null when it could not be resolved. */
    public final ServerLevel level;

    /** The snapshot being filled in. */
    public final ColonySnapshot snapshot;

    /** Hut position to the DTO the UI will see. */
    public final Map<BlockPos, BuildingInfo> buildingByPos = new HashMap<>();

    /** Hut position to the raw MineColonies building object, for further reflection. */
    public final Map<BlockPos, Object> rawBuildingByPos = new HashMap<>();

    public ScanContext(ServerLevel level, ColonySnapshot snapshot) {
        this.level = level;
        this.snapshot = snapshot;
    }
}
