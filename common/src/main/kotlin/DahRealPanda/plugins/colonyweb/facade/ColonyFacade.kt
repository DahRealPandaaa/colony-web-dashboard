package DahRealPanda.plugins.colonyweb.facade

import DahRealPanda.plugins.colonyweb.auth.AuthService
import DahRealPanda.plugins.colonyweb.service.BuildingService
import DahRealPanda.plugins.colonyweb.service.CitizenService
import DahRealPanda.plugins.colonyweb.service.ColonyMapService
import DahRealPanda.plugins.colonyweb.service.CombatService
import DahRealPanda.plugins.colonyweb.service.ResearchService
import DahRealPanda.plugins.colonyweb.web.JsonUtil

class ColonyFacade(
    private val buildingService: BuildingService,
    private val citizenService: CitizenService,
    private val combatService: CombatService,
    private val researchService: ResearchService,
    private val maps: ColonyMapService,
    private val auth: AuthService
) {
    private fun resolveUser(token: String?) = if (auth.enabled()) auth.userForToken(token) else null

    /**
     * Every read goes through here rather than through a `user != null` guard. An unauthenticated
     * request resolves to a null user, and skipping the check for those would hand out exactly the
     * data auth is meant to protect. [AuthService.canAccess] already returns true for everyone while
     * auth is disabled, so this stays open when the server is configured that way.
     */
    private fun canAccess(token: String?, colonyId: Int) = auth.canAccess(resolveUser(token), colonyId)

    fun listColonies(token: String?): String {
        val user = resolveUser(token)
        val filtered = buildingService.summaries().filter { auth.canAccess(user, it.id) }
        return JsonUtil.toJson(filtered)
    }

    fun snapshot(colonyId: Int, token: String?): String {
        if (!canAccess(token, colonyId)) return "{}"
        return buildingService.snapshot(colonyId)?.let { JsonUtil.toJson(it) } ?: "{}"
    }

    fun citizens(colonyId: Int, token: String?): String {
        if (!canAccess(token, colonyId)) return "[]"
        return citizenService.citizens(colonyId)?.let { JsonUtil.toJson(it) } ?: "[]"
    }

    fun research(colonyId: Int, token: String?): String {
        if (!canAccess(token, colonyId)) return "{}"
        return researchService.research(colonyId)?.let { JsonUtil.toJson(it) } ?: "{}"
    }

    fun combat(colonyId: Int, token: String?): String {
        if (!canAccess(token, colonyId)) return "{}"
        return combatService.combat(colonyId)?.let { JsonUtil.toJson(it) } ?: "{}"
    }

    fun mapInfo(colonyId: Int, token: String?): String {
        if (!canAccess(token, colonyId)) return "{}"
        return JsonUtil.toJson(maps.info(colonyId))
    }

    fun citizenDetail(colonyId: Int, citizenId: Int, token: String?): String {
        if (!canAccess(token, colonyId)) return "{}"
        val citizen = citizenService.citizen(colonyId, citizenId) ?: return "{}"
        val inv = citizenService.inventory(colonyId, citizenId)
        val equip = citizenService.equipment(colonyId, citizenId)
        return JsonUtil.toJson(mapOf("citizen" to citizen, "inventory" to inv, "equipment" to equip))
    }
}
