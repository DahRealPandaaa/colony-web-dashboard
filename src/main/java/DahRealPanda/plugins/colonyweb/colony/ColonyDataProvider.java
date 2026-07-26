package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySummary;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds immutable snapshots of MineColonies colonies, delegating each part of the work to a
 * focused scanner.
 *
 * <p>All scanning must happen on the server thread (world and tile-entity access is not
 * thread-safe). The resulting DTOs are then handed to off-thread HTTP handlers.</p>
 */
public final class ColonyDataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ColonyLookup lookup;
    private final BuildingScanner buildingScanner = new BuildingScanner();
    private final WorkOrderScanner workOrderScanner = new WorkOrderScanner();
    private final CitizenScanner citizenScanner = new CitizenScanner();
    private final CombatScanner combatScanner = new CombatScanner();
    private final ResearchScanner researchScanner = new ResearchScanner();
    private final RecipeScanner recipeScanner = new RecipeScanner();
    private final StatsBuilder statsBuilder = new StatsBuilder();

    public ColonyDataProvider(MinecraftServer server) {
        this.lookup = new ColonyLookup(server);
    }

    /** @return true when MineColonies data is available. */
    public boolean available() {
        return MineColoniesReflect.isMineColoniesLoaded();
    }

    /** The level a colony lives in, for callers that only have its dimension id. */
    public ServerLevel levelFor(String dimension) {
        return lookup.levelFor(dimension);
    }

    /** Colonies a player is a member of — used by {@code /colonyweb sync}. */
    public List<Integer> coloniesFor(UUID playerId, String playerName) {
        return available() ? lookup.coloniesFor(playerId, playerName) : List.of();
    }

    /** Enumerate every colony as a lightweight summary. */
    public List<ColonySummary> listColonies() {
        List<ColonySummary> summaries = new ArrayList<>();
        if (!available()) {
            return summaries;
        }
        for (Object colony : lookup.allColonies()) {
            try {
                summaries.add(summarize(colony));
            } catch (Throwable t) {
                LOGGER.debug("{} failed to summarize a colony", ColonyWeb.LOG, t);
            }
        }
        return summaries;
    }

    private ColonySummary summarize(Object colony) {
        ColonySummary summary = new ColonySummary();
        summary.id = lookup.idOf(colony);
        summary.name = lookup.nameOf(colony);
        summary.dimension = lookup.dimensionOf(colony);
        summary.owner = lookup.ownerOf(colony);

        BlockPos center = Scan.blockPosOf(MineColoniesReflect.invoke(colony, "getCenter").orElse(null));
        if (center != null) {
            summary.x = center.getX();
            summary.y = center.getY();
            summary.z = center.getZ();
        }
        Collection<Object> buildings = lookup.buildingsOf(colony);
        summary.buildingCount = buildings.size();
        summary.activeWorkOrders = lookup.workOrdersOf(colony).size();
        summary.builderCount = BuildingScanner.countRawBuilders(buildings);
        return summary;
    }

    /**
     * Scan one colony in full.
     *
     * @param includeResearch also walk the research tree — comparatively expensive and slow to
     *                        change, so callers run it on a slower cadence
     */
    public Optional<ColonyScan> scan(int colonyId, boolean includeResearch) {
        if (!available()) {
            return Optional.empty();
        }
        Object colony = lookup.colonyById(colonyId);
        if (colony == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(buildScan(colony, includeResearch));
        } catch (Throwable t) {
            LOGGER.debug("{} failed to scan colony {}", ColonyWeb.LOG, colonyId, t);
            return Optional.empty();
        }
    }

    private ColonyScan buildScan(Object colony, boolean includeResearch) {
        ColonySnapshot snapshot = new ColonySnapshot();
        snapshot.id = lookup.idOf(colony);
        snapshot.name = lookup.nameOf(colony);
        snapshot.dimension = lookup.dimensionOf(colony);
        snapshot.owner = lookup.ownerOf(colony);

        ServerLevel level = lookup.levelFor(snapshot.dimension);
        ScanContext ctx = new ScanContext(level, snapshot);

        Collection<Object> buildings = lookup.buildingsOf(colony);
        buildingScanner.scan(buildings, ctx);
        workOrderScanner.scan(lookup.workOrdersOf(colony), ctx);

        ColonyScan scan = new ColonyScan();
        scan.snapshot = snapshot;

        CitizenScanner.Result citizens = citizenScanner.scan(colony, ctx.buildingByPos);
        scan.citizens = citizens.citizens;
        scan.inventories = citizens.inventories;
        scan.equipment = citizens.equipment;
        scan.combat = combatScanner.scan(colony, scan.citizens, citizens.equipment,
                ctx.buildingByPos, ctx.rawBuildingByPos);
        if (includeResearch) {
            scan.research = researchScanner.scan(colony);
        }
        statsBuilder.fill(colony, snapshot, scan.citizens, scan.combat);

        // Colony-wide fact, so it can only be applied once every payload above exists.
        RecipeScanner.markCraftable(scan, recipeScanner.scan(buildings));

        LOGGER.debug("{} colony {} ('{}'): buildings={} workOrders={} citizens={} guards={} warehouse={} ({} stacks)",
                ColonyWeb.LOG, snapshot.id, snapshot.name, snapshot.buildings.size(),
                snapshot.workOrders.size(), scan.citizens.size(), scan.combat.guardCount,
                snapshot.warehouse.present, snapshot.warehouse.stacks.size());
        return scan;
    }
}
