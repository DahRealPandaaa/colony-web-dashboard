package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.colony.model.BuildingInfo;
import DahRealPanda.plugins.colonyweb.colony.model.CitizenInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ItemCount;
import DahRealPanda.plugins.colonyweb.util.Text;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invokeAny;
import static DahRealPanda.plugins.colonyweb.colony.Scan.blockPosOf;
import static DahRealPanda.plugins.colonyweb.colony.Scan.boolOf;
import static DahRealPanda.plugins.colonyweb.colony.Scan.doubleOf;
import static DahRealPanda.plugins.colonyweb.colony.Scan.intOf;
import static DahRealPanda.plugins.colonyweb.colony.Scan.stringOf;

/**
 * Reads the colony's citizen roster: who they are, what they are good at, how they feel and
 * what they are carrying.
 *
 * <p>Inventories are returned separately from the citizen list so the list endpoint stays
 * small — the UI only pulls a citizen's items when you open them.</p>
 */
public final class CitizenScanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String SKILL_ENUM = "com.minecolonies.api.entity.citizen.Skill";

    /** Citizens plus their inventories, keyed by citizen id. */
    public static final class Result {
        public final List<CitizenInfo> citizens = new ArrayList<>();
        public final Map<Integer, List<ItemCount>> inventories = new HashMap<>();
    }

    /**
     * @param colony         the raw MineColonies colony object
     * @param buildingByPos  buildings already scanned this tick, for work/home lookups
     */
    public Result scan(Object colony, Map<BlockPos, BuildingInfo> buildingByPos) {
        Result result = new Result();
        Object manager = invokeAny(colony, "getCitizenManager").orElse(null);
        if (manager == null) {
            return result;
        }
        Object citizens = invokeAny(manager, "getCitizens").orElse(null);
        if (!(citizens instanceof Collection<?> collection)) {
            return result;
        }
        for (Object citizen : collection) {
            try {
                CitizenInfo info = readCitizen(colony, citizen, buildingByPos);
                result.citizens.add(info);
                result.inventories.put(info.id, readInventory(citizen, info));
            } catch (Throwable t) {
                LOGGER.debug("[ColonyWeb] failed to read a citizen", t);
            }
        }
        result.citizens.sort((a, b) -> {
            int byJob = jobRank(a) - jobRank(b);
            return byJob != 0 ? byJob : stringOf(a.name, "").compareTo(stringOf(b.name, ""));
        });
        return result;
    }

    /** Employed citizens first, then children, then the unemployed. */
    private static int jobRank(CitizenInfo c) {
        if (c.jobType != null) {
            return 0;
        }
        return c.child ? 1 : 2;
    }

    // ------------------------------------------------------------------
    // Per-citizen reads
    // ------------------------------------------------------------------

    private CitizenInfo readCitizen(Object colony, Object citizen, Map<BlockPos, BuildingInfo> buildingByPos) {
        CitizenInfo info = new CitizenInfo();
        info.id = intOf(invokeAny(citizen, "getId").orElse(null), -1);
        info.name = stringOf(invokeAny(citizen, "getName").orElse(null), "Citizen " + info.id);
        info.child = boolOf(invokeAny(citizen, "isChild").orElse(null), false);
        info.female = boolOf(invokeAny(citizen, "isFemale").orElse(null), false);
        info.saturation = doubleOf(invokeAny(citizen, "getSaturation").orElse(null), 0.0);

        readJob(citizen, info);
        readBuildings(citizen, info, buildingByPos);
        readEntity(citizen, info);
        readHappiness(colony, citizen, info);
        readSkills(citizen, info);
        readStatus(citizen, info);
        return info;
    }

    private void readJob(Object citizen, CitizenInfo info) {
        Object job = invokeAny(citizen, "getJob").orElse(null);
        if (job == null) {
            info.job = info.child ? "Child" : "Unemployed";
            return;
        }
        Object entry = invokeAny(job, "getJobRegistryEntry").orElse(null);
        Object key = Scan.firstNonNull(
                invokeAny(entry, "getKey").orElse(null),
                invokeAny(entry, "getRegistryName").orElse(null));
        if (key != null) {
            info.jobType = String.valueOf(key);
            info.job = Text.humanize(Text.pathOf(info.jobType));
        } else {
            // Fall back to the job's translation key, then to its class name (JobBuilder → Builder).
            String name = Text.componentString(invokeAny(job, "getName").orElse(null));
            if (name != null && !name.isBlank()) {
                info.job = Text.displayName(name, "Worker");
            } else {
                info.job = Text.humanize(job.getClass().getSimpleName().replaceFirst("^Job", ""));
            }
        }
        info.primarySkill = skillName(Scan.firstNonNull(
                invokeAny(job, "getPrimarySkill").orElse(null),
                invokeAny(invokeAny(citizen, "getWorkBuilding").orElse(null), "getPrimarySkill").orElse(null)));
        info.secondarySkill = skillName(Scan.firstNonNull(
                invokeAny(job, "getSecondarySkill").orElse(null),
                invokeAny(invokeAny(citizen, "getWorkBuilding").orElse(null), "getSecondarySkill").orElse(null)));
    }

    private static String skillName(Object skill) {
        return skill == null ? null : Text.humanize(String.valueOf(skill));
    }

    private void readBuildings(Object citizen, CitizenInfo info, Map<BlockPos, BuildingInfo> buildingByPos) {
        Object work = invokeAny(citizen, "getWorkBuilding").orElse(null);
        BlockPos workPos = blockPosOf(invokeAny(work, "getID").orElse(null));
        BuildingInfo workInfo = workPos != null ? buildingByPos.get(workPos) : null;
        if (workInfo != null) {
            info.workBuilding = workInfo.name;
            info.workBuildingId = workInfo.id;
            info.jobIcon = workInfo.blockId;
        }

        Object home = invokeAny(citizen, "getHomeBuilding").orElse(null);
        BlockPos homePos = blockPosOf(invokeAny(home, "getID").orElse(null));
        BuildingInfo homeInfo = homePos != null ? buildingByPos.get(homePos) : null;
        if (homeInfo != null) {
            info.homeBuilding = homeInfo.name;
            info.homeBuildingId = homeInfo.id;
        }
    }

    private void readEntity(Object citizen, CitizenInfo info) {
        Object entity = invokeAny(citizen, "getEntity").orElse(null);
        if (entity instanceof Optional<?> optional) {
            entity = optional.orElse(null);
        }
        if (entity instanceof LivingEntity living) {
            info.spawned = true;
            info.health = living.getHealth();
            info.maxHealth = living.getMaxHealth();
            BlockPos pos = living.blockPosition();
            info.x = pos.getX();
            info.y = pos.getY();
            info.z = pos.getZ();
            return;
        }
        // Not loaded — fall back to the last known position MineColonies persisted.
        BlockPos last = blockPosOf(Scan.firstNonNull(
                invokeAny(citizen, "getLastPosition").orElse(null),
                invokeAny(citizen, "getPosition").orElse(null)));
        if (last != null) {
            info.x = last.getX();
            info.y = last.getY();
            info.z = last.getZ();
        }
        info.maxHealth = 20.0;
    }

    private void readHappiness(Object colony, Object citizen, CitizenInfo info) {
        Object handler = invokeAny(citizen, "getCitizenHappinessHandler").orElse(null);
        if (handler == null) {
            return;
        }
        info.happiness = doubleOf(invokeAny(handler, "getHappiness", colony, citizen).orElse(null), 0.0);

        Object modifiers = invokeAny(handler, "getModifiers").orElse(null);
        if (!(modifiers instanceof Collection<?> collection)) {
            return;
        }
        for (Object modifier : collection) {
            // MineColonies exposes either modifier ids or the modifier objects themselves.
            Object id = modifier instanceof String ? modifier : invokeAny(modifier, "getId").orElse(null);
            if (id == null) {
                continue;
            }
            Object factor = modifier instanceof String
                    ? Scan.firstNonNull(
                            invokeAny(handler, "getModifierFactor", String.valueOf(id), citizen).orElse(null),
                            invokeAny(invokeAny(handler, "getModifier", String.valueOf(id)).orElse(null),
                                    "getFactor", citizen).orElse(null))
                    : invokeAny(modifier, "getFactor", citizen).orElse(null);
            info.modifiers.add(new CitizenInfo.Modifier(
                    Text.displayName(String.valueOf(id), String.valueOf(id)),
                    doubleOf(factor, 1.0)));
        }
    }

    private void readSkills(Object citizen, CitizenInfo info) {
        Object handler = invokeAny(citizen, "getCitizenSkillHandler").orElse(null);
        if (handler == null) {
            return;
        }
        Class<?> skillClass = MineColoniesReflect.resolve(SKILL_ENUM).orElse(null);
        Object[] constants = skillClass != null ? skillClass.getEnumConstants() : null;

        // The skill map (when present) is the only source of experience values.
        Object skills = invokeAny(handler, "getSkills").orElse(null);
        Map<?, ?> skillMap = skills instanceof Map<?, ?> map ? map : null;

        List<Object> keys = new ArrayList<>();
        if (constants != null) {
            keys.addAll(List.of(constants));
        } else if (skillMap != null) {
            keys.addAll(skillMap.keySet());
        }

        for (Object skill : keys) {
            int level = intOf(invokeAny(handler, "getLevel", skill).orElse(null), 0);
            double xp = 0.0;
            Object data = skillMap != null ? skillMap.get(skill) : null;
            if (data != null) {
                if (level == 0) {
                    level = intOf(Scan.firstNonNull(
                            invokeAny(data, "getLevel").orElse(null),
                            invokeAny(data, "getA").orElse(null)), 0);
                }
                xp = doubleOf(Scan.firstNonNull(
                        invokeAny(data, "getExperience").orElse(null),
                        invokeAny(data, "getB").orElse(null)), 0.0);
            }
            CitizenInfo.Skill entry = new CitizenInfo.Skill(Text.humanize(String.valueOf(skill)), level, xp);
            if (entry.name.equalsIgnoreCase(info.primarySkill)) {
                entry.role = "primary";
            } else if (entry.name.equalsIgnoreCase(info.secondarySkill)) {
                entry.role = "secondary";
            }
            info.skills.add(entry);
            info.skillTotal += level;
        }
    }

    private void readStatus(Object citizen, CitizenInfo info) {
        Object status = invokeAny(citizen, "getStatus").orElse(null);
        if (status == null) {
            return;
        }
        Object id = Scan.firstNonNull(invokeAny(status, "getId").orElse(null), status);
        info.status = Text.displayName(String.valueOf(id), null);
    }

    // ------------------------------------------------------------------
    // Inventory
    // ------------------------------------------------------------------

    /** Read the citizen's carried items and record how full their pack is. */
    private List<ItemCount> readInventory(Object citizen, CitizenInfo info) {
        List<ItemCount> out = new ArrayList<>();
        Object inventory = invokeAny(citizen, "getInventory").orElse(null);
        if (!(inventory instanceof IItemHandler handler)) {
            return out;
        }
        info.inventorySize = handler.getSlots();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            out.add(Scan.itemCount(stack, stack.getCount(), slot));
        }
        info.inventoryUsed = out.size();
        return out;
    }
}
