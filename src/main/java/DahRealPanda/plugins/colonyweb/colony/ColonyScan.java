package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.colony.model.CitizenInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;
import DahRealPanda.plugins.colonyweb.colony.model.CombatInfo;
import DahRealPanda.plugins.colonyweb.colony.model.EquipmentInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ItemCount;
import DahRealPanda.plugins.colonyweb.colony.model.ResearchInfo;

import java.util.List;
import java.util.Map;

/**
 * Everything one scan pass produced for a colony.
 *
 * <p>The pieces are kept apart rather than merged into the snapshot because each is served
 * from its own endpoint — the browser re-fetches the snapshot on every live update, so it has
 * to stay small.</p>
 */
public final class ColonyScan {
    public ColonySnapshot snapshot;
    public List<CitizenInfo> citizens = List.of();

    /** Citizen id to the items they are carrying. */
    public Map<Integer, List<ItemCount>> inventories = Map.of();

    /** Citizen id to what they are wearing and holding. */
    public Map<Integer, List<EquipmentInfo>> equipment = Map.of();

    public CombatInfo combat = new CombatInfo();

    /** Null when research was not rescanned this pass (it changes slowly). */
    public ResearchInfo research;
}
