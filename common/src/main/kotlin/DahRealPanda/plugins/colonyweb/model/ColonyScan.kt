package DahRealPanda.plugins.colonyweb.model

import DahRealPanda.plugins.colonyweb.model.CitizenInfo
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.model.CombatInfo
import DahRealPanda.plugins.colonyweb.model.EquipmentInfo
import DahRealPanda.plugins.colonyweb.model.ItemCount
import DahRealPanda.plugins.colonyweb.model.ResearchInfo

/**
 * Everything one scan pass produced for a colony.
 *
 * The pieces are kept apart rather than merged into the snapshot because each is served
 * from its own endpoint — the browser re-fetches the snapshot on every live update, so it has
 * to stay small.
 */
class ColonyScan {
    lateinit var snapshot: ColonySnapshot
    var citizens: List<CitizenInfo> = emptyList()

    /** Citizen id to the items they are carrying. */
    var inventories: Map<Int, List<ItemCount>> = emptyMap()

    /** Citizen id to what they are wearing and holding. */
    var equipment: Map<Int, List<EquipmentInfo>> = emptyMap()

    var combat: CombatInfo = CombatInfo()

    /** Null when research was not rescanned this pass (it changes slowly). */
    var research: ResearchInfo? = null
}
