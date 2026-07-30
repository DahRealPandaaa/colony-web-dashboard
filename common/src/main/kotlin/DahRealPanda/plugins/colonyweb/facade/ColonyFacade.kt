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

    fun listColonies(token: String?): String {
        val user = resolveUser(token)
        val all = buildingService.summaries()
        val filtered = if (user != null) all.filter { auth.canAccess(user, it.id) } else all
        return JsonUtil.toJson(filtered)
    }

    fun snapshot(colonyId: Int, token: String?): String {
        val user = resolveUser(token)
        if (user != null && !auth.canAccess(user, colonyId)) return "{}"
        return buildingService.snapshot(colonyId)?.let { JsonUtil.toJson(it) } ?: "{}"
    }

    fun citizens(colonyId: Int, token: String?): String {
        val user = resolveUser(token)
        if (user != null && !auth.canAccess(user, colonyId)) return "[]"
        return citizenService.citizens(colonyId)?.let { JsonUtil.toJson(it) } ?: "[]"
    }

    fun research(colonyId: Int, token: String?): String {
        val user = resolveUser(token)
        if (user != null && !auth.canAccess(user, colonyId)) return "{}"
        return researchService.research(colonyId)?.let { JsonUtil.toJson(it) } ?: "{}"
    }

    fun combat(colonyId: Int, token: String?): String {
        val user = resolveUser(token)
        if (user != null && !auth.canAccess(user, colonyId)) return "{}"
        return combatService.combat(colonyId)?.let { JsonUtil.toJson(it) } ?: "{}"
    }

    fun mapInfo(colonyId: Int, token: String?): String {
        val user = resolveUser(token)
        if (user != null && !auth.canAccess(user, colonyId)) return "{}"
        return JsonUtil.toJson(maps.info(colonyId))
    }

    fun citizenDetail(colonyId: Int, citizenId: Int, token: String?): String {
        val user = resolveUser(token)
        if (user != null && !auth.canAccess(user, colonyId)) return "{}"
        val citizen = citizenService.citizen(colonyId, citizenId) ?: return "{}"
        val inv = citizenService.inventory(colonyId, citizenId)
        val equip = citizenService.equipment(colonyId, citizenId)
        return JsonUtil.toJson(mapOf("citizen" to citizen, "inventory" to inv, "equipment" to equip))
    }
}
