package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invoke;
import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invokeAny;
import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invokeStatic;
import static DahRealPanda.plugins.colonyweb.colony.Scan.stringOf;

/**
 * Finds colonies and reads their top-level properties. Everything that has to know how
 * MineColonies exposes its colony registry lives here, so the scanners can stay about data.
 */
public final class ColonyLookup {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String COLONY_MANAGER = "com.minecolonies.api.colony.IColonyManager";

    private final MinecraftServer server;

    public ColonyLookup(MinecraftServer server) {
        this.server = server;
    }

    /** Every colony on the server, across all dimensions. */
    @SuppressWarnings("unchecked")
    public Collection<Object> allColonies() {
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
        for (ServerLevel level : server.getAllLevels()) {
            Object colonies = invoke(manager, "getColonies",
                    new Class<?>[]{net.minecraft.world.level.Level.class}, level).orElse(null);
            if (colonies instanceof Collection<?> perLevel) {
                result.addAll((Collection<Object>) perLevel);
            }
        }
        return result;
    }

    /** The raw colony object with this id, or null. */
    public Object colonyById(int colonyId) {
        for (Object colony : allColonies()) {
            if (idOf(colony) == colonyId) {
                return colony;
            }
        }
        return null;
    }

    public int idOf(Object colony) {
        return Scan.intOf(invoke(colony, "getID").orElse(null), -1);
    }

    public String nameOf(Object colony) {
        return stringOf(invoke(colony, "getName").orElse(null), "Colony " + idOf(colony));
    }

    public String dimensionOf(Object colony) {
        Object dimension = invoke(colony, "getDimension").orElse(null);
        if (dimension == null) {
            return "minecraft:overworld";
        }
        Object location = invoke(dimension, "location").orElse(null);
        return location instanceof ResourceLocation rl ? rl.toString() : String.valueOf(dimension);
    }

    public String ownerOf(Object colony) {
        Object permissions = invoke(colony, "getPermissions").orElse(null);
        return permissions == null ? "" : stringOf(invoke(permissions, "getOwnerName").orElse(null), "");
    }

    /** The level a colony sits in, falling back to the overworld. */
    public ServerLevel levelFor(String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) {
                return level;
            }
        }
        return server.overworld();
    }

    // ------------------------------------------------------------------
    // Colony contents
    // ------------------------------------------------------------------

    /** Resolve the server-side structure/building manager for a colony. */
    private Object structureManager(Object colony) {
        // IColony#getServerBuildingManager() (server side) -> IRegisteredStructureManager.
        Object manager = invoke(colony, "getServerBuildingManager").orElse(null);
        if (manager == null) {
            // Fallbacks for older/renamed APIs.
            manager = Scan.firstNonNull(
                    invoke(colony, "getBuildingManager").orElse(null),
                    invoke(colony, "getCommonBuildingManager").orElse(null));
        }
        return manager;
    }

    public Collection<Object> buildingsOf(Object colony) {
        return valuesOf(invoke(structureManager(colony), "getBuildings").orElse(null));
    }

    public Collection<Object> workOrdersOf(Object colony) {
        Object workManager = invoke(colony, "getWorkManager").orElse(null);
        return valuesOf(invoke(workManager, "getWorkOrders").orElse(null));
    }

    /** MineColonies returns either a Map keyed by position/id or a plain Collection. */
    @SuppressWarnings("unchecked")
    private static Collection<Object> valuesOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new ArrayList<>((Collection<Object>) map.values());
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>((Collection<Object>) collection);
        }
        return List.of();
    }

    // ------------------------------------------------------------------
    // Access control
    // ------------------------------------------------------------------

    /**
     * The colonies a player is a member of — what {@code /colonyweb sync} mirrors into their
     * dashboard account.
     *
     * <p>Membership is read from each colony's permission list, so owners, officers and
     * ordinary members all qualify; the colony owner name is used as a fallback for builds
     * whose permission map is not reachable by reflection.</p>
     */
    public List<Integer> coloniesFor(UUID playerId, String playerName) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (Object colony : allColonies()) {
            try {
                if (isMember(colony, playerId, playerName)) {
                    ids.add(idOf(colony));
                }
            } catch (Throwable t) {
                LOGGER.debug("{} membership check failed for a colony", ColonyWeb.LOG, t);
            }
        }
        return new ArrayList<>(ids);
    }

    private boolean isMember(Object colony, UUID playerId, String playerName) {
        Object permissions = invoke(colony, "getPermissions").orElse(null);
        if (permissions != null) {
            Object players = invokeAny(permissions, "getPlayers").orElse(null);
            if (players instanceof Map<?, ?> map && map.containsKey(playerId)) {
                return true;
            }
            if (players instanceof Collection<?> collection) {
                for (Object entry : collection) {
                    Object id = Scan.firstNonNull(
                            invokeAny(entry, "getID").orElse(null),
                            invokeAny(entry, "getId").orElse(null));
                    if (playerId.equals(id)) {
                        return true;
                    }
                }
            }
            Object owner = invokeAny(permissions, "getOwner").orElse(null);
            if (playerId.equals(owner)) {
                return true;
            }
        }
        // Last resort: match the owner's display name.
        String owner = ownerOf(colony);
        return playerName != null && !owner.isBlank()
                && owner.toLowerCase(Locale.ROOT).equals(playerName.toLowerCase(Locale.ROOT));
    }
}
