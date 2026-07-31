package DahRealPanda.plugins.colonyweb.facade

import DahRealPanda.plugins.colonyweb.auth.AuthService
import DahRealPanda.plugins.colonyweb.service.ColonyMapService

class MapFacade(
    private val maps: ColonyMapService,
    private val auth: AuthService
) {
    fun getMapImage(colonyId: Int, token: String?): ByteArray? {
        if (auth.enabled()) {
            val user = auth.userForToken(token)
            if (user == null || !auth.canAccess(user, colonyId)) return null
        }
        return maps.png(colonyId)
    }
}
