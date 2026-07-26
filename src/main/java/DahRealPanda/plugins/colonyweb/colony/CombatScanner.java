package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.colony.model.BuildingInfo;
import DahRealPanda.plugins.colonyweb.colony.model.CitizenInfo;
import DahRealPanda.plugins.colonyweb.colony.model.CombatInfo;
import DahRealPanda.plugins.colonyweb.util.Text;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invokeAny;
import static DahRealPanda.plugins.colonyweb.colony.Scan.blockPosOf;
import static DahRealPanda.plugins.colonyweb.colony.Scan.boolOf;
import static DahRealPanda.plugins.colonyweb.colony.Scan.intOf;

/**
 * Colony defence: raid pressure from the raider manager, the guard roster taken from the
 * already-scanned citizens, and how well each guard post is staffed.
 */
public final class CombatScanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Job registry paths that count as a fighting job, matched <em>exactly</em>.
     *
     * <p>A substring test looks tempting and is wrong: "researcher" contains "archer", so every
     * university researcher was being filed as a guard.</p>
     */
    private static final Set<String> GUARD_JOBS = Set.of(
            "knight", "ranger", "druid", "archer", "guard", "samurai", "combat",
            "archertraining", "combattraining", "knighttraining");

    /** Whole-word "guard" in a citizen's display job title. */
    private static final Pattern GUARD_NAME = Pattern.compile("(?i)\\bguards?\\b");

    /** Building type paths that house or train guards. */
    private static final Set<String> GUARD_BUILDINGS = Set.of(
            "guardtower", "barracks", "barrackstower", "archery", "combatacademy");

    public CombatInfo scan(Object colony, List<CitizenInfo> citizens,
                           Map<BlockPos, BuildingInfo> buildingByPos,
                           Map<BlockPos, Object> rawBuildingByPos) {
        CombatInfo info = new CombatInfo();
        try {
            readRaiders(colony, info);
            readGraves(colony, info);
            readEvents(colony, info);
            readPosts(buildingByPos, rawBuildingByPos, info);
            readGuards(citizens, info);
        } catch (Throwable t) {
            LOGGER.debug("[ColonyWeb] combat scan failed", t);
        }
        return info;
    }

    private void readRaiders(Object colony, CombatInfo info) {
        Object raiders = invokeAny(colony, "getRaiderManager").orElse(null);
        if (raiders == null) {
            return;
        }
        info.underAttack = boolOf(invokeAny(raiders, "isRaided").orElse(null), false);
        info.raidsPossible = boolOf(invokeAny(raiders, "canHaveRaiderEvents").orElse(null), false);
        info.spiesEnabled = boolOf(invokeAny(raiders, "areSpiesEnabled").orElse(null), false);
        info.nightsSinceRaid = intOf(invokeAny(raiders, "getNightsSinceLastRaid").orElse(null), 0);
        info.raidLevel = intOf(Scan.firstNonNull(
                invokeAny(raiders, "getColonyRaidLevel").orElse(null),
                invokeAny(raiders, "getColonyRaidLevelHelper").orElse(null)), 0);
    }

    private void readGraves(Object colony, CombatInfo info) {
        Object graveManager = Scan.firstNonNull(
                invokeAny(colony, "getGraveManager").orElse(null),
                invokeAny(colony, "getGraveyardManager").orElse(null));
        Object graves = invokeAny(graveManager, "getGraves").orElse(null);
        if (graves instanceof Map<?, ?> map) {
            info.graves = map.size();
        } else if (graves instanceof Collection<?> collection) {
            info.graves = collection.size();
        }
    }

    private void readEvents(Object colony, CombatInfo info) {
        Object manager = invokeAny(colony, "getEventManager").orElse(null);
        Object events = invokeAny(manager, "getEvents").orElse(null);
        Collection<?> values;
        if (events instanceof Map<?, ?> map) {
            values = map.values();
        } else if (events instanceof Collection<?> collection) {
            values = collection;
        } else {
            return;
        }
        for (Object raw : values) {
            CombatInfo.Event event = new CombatInfo.Event();
            event.id = intOf(invokeAny(raw, "getID").orElse(null), -1);
            event.name = Text.displayName(Scan.firstNonNull(
                    invokeAny(raw, "getEventTypeID").orElse(null),
                    invokeAny(raw, "getName").orElse(null)), "Colony event");
            event.status = Text.humanize(String.valueOf(invokeAny(raw, "getStatus").orElse("")));
            BlockPos pos = blockPosOf(invokeAny(raw, "getPosition").orElse(null));
            if (pos != null) {
                event.x = pos.getX();
                event.y = pos.getY();
                event.z = pos.getZ();
            }
            info.events.add(event);
        }
    }

    private void readPosts(Map<BlockPos, BuildingInfo> buildingByPos,
                           Map<BlockPos, Object> rawBuildingByPos, CombatInfo info) {
        for (Map.Entry<BlockPos, BuildingInfo> entry : buildingByPos.entrySet()) {
            BuildingInfo building = entry.getValue();
            if (!isGuardBuilding(building.type)) {
                continue;
            }
            Object raw = rawBuildingByPos.get(entry.getKey());
            CombatInfo.Post post = new CombatInfo.Post();
            post.id = building.id;
            post.name = building.name;
            post.type = building.type;
            post.blockId = building.blockId;
            post.level = building.level;
            post.x = building.x;
            post.y = building.y;
            post.z = building.z;

            Object assigned = invokeAny(raw, "getAllAssignedCitizen").orElse(null);
            post.assigned = assigned instanceof Collection<?> c ? c.size() : 0;
            post.capacity = intOf(Scan.firstNonNull(
                    invokeAny(raw, "getMaxInhabitants").orElse(null),
                    invokeAny(raw, "getGuardSlots").orElse(null)), post.assigned);

            info.posts.add(post);
            info.guardCapacity += post.capacity;
        }
        info.posts.sort((a, b) -> Text.stringOrEmpty(a.name).compareToIgnoreCase(Text.stringOrEmpty(b.name)));
    }

    private void readGuards(List<CitizenInfo> citizens, CombatInfo info) {
        double levelSum = 0;
        double healthSum = 0;
        for (CitizenInfo citizen : citizens) {
            if (!isGuardJob(citizen.jobType, citizen.job)) {
                continue;
            }
            CombatInfo.Guard guard = new CombatInfo.Guard();
            guard.id = citizen.id;
            guard.name = citizen.name;
            guard.job = citizen.job;
            guard.jobType = citizen.jobType;
            guard.level = bestCombatLevel(citizen);
            guard.health = citizen.health;
            guard.maxHealth = citizen.maxHealth;
            guard.spawned = citizen.spawned;
            guard.building = citizen.workBuilding;
            guard.x = citizen.x;
            guard.y = citizen.y;
            guard.z = citizen.z;
            info.guards.add(guard);

            levelSum += guard.level;
            healthSum += guard.maxHealth > 0 ? guard.health / guard.maxHealth : 0;
        }
        info.guardCount = info.guards.size();
        if (info.guardCount > 0) {
            info.averageGuardLevel = levelSum / info.guardCount;
            info.averageHealthPct = healthSum / info.guardCount * 100.0;
        }
        info.guards.sort((a, b) -> b.level - a.level);
    }

    /** The guard's own primary skill level, falling back to their best combat-ish skill. */
    private static int bestCombatLevel(CitizenInfo citizen) {
        int best = 0;
        for (CitizenInfo.Skill skill : citizen.skills) {
            if ("primary".equals(skill.role)) {
                return skill.level;
            }
            best = Math.max(best, skill.level);
        }
        return best;
    }

    private static boolean isGuardJob(String jobType, String jobName) {
        String path = Text.pathOf(jobType).toLowerCase(Locale.ROOT).replace("_", "");
        if (GUARD_JOBS.contains(path)) {
            return true;
        }
        // Job names are display strings ("Guard", "Guard in Training"), so a whole-word match is
        // safe where a substring test on the registry path is not.
        return jobName != null && GUARD_NAME.matcher(jobName).find();
    }

    private static boolean isGuardBuilding(String type) {
        String path = Text.pathOf(type).toLowerCase(Locale.ROOT).replace("_", "");
        return GUARD_BUILDINGS.contains(path);
    }
}
