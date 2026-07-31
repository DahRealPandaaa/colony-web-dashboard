package DahRealPanda.plugins.colonyweb.facade

import DahRealPanda.plugins.colonyweb.auth.AuthService
import DahRealPanda.plugins.colonyweb.auth.WebUser
import DahRealPanda.plugins.colonyweb.api.response.AuthSessionResponse
import DahRealPanda.plugins.colonyweb.api.response.LoginResponse
import DahRealPanda.plugins.colonyweb.api.response.WebUserResponse

class AuthFacade(private val auth: AuthService) {
    data class LoginResult(val token: String?, val response: LoginResponse)

    fun isEnabled(): Boolean = auth.enabled()

    fun checkSession(token: String?): AuthSessionResponse {
        if (!auth.enabled()) {
            return AuthSessionResponse(authenticated = false, authEnabled = false)
        }
        val user = auth.userForToken(token)
        return AuthSessionResponse(
            authenticated = user != null,
            authEnabled = true,
            user = user?.let { toResponse(it) }
        )
    }

    fun login(code: String): LoginResult {
        if (!auth.enabled()) {
            return LoginResult(null, LoginResponse(authenticated = false, error = "Auth disabled"))
        }
        val token = auth.redeemCode(code)
        if (token != null) {
            val user = auth.userForToken(token)
            return LoginResult(token, LoginResponse(
                authenticated = true,
                user = user?.let { toResponse(it) }
            ))
        }
        return LoginResult(null, LoginResponse(authenticated = false, error = "That code was not accepted."))
    }

    fun logout(token: String?) {
        if (token != null) auth.revokeToken(token)
    }

    fun userForToken(token: String?): WebUser? {
        if (!auth.enabled()) return null
        return auth.userForToken(token)
    }

    private fun toResponse(user: WebUser): WebUserResponse {
        return WebUserResponse(
            uuid = user.uuid,
            name = user.name,
            colonies = user.accessibleColonies().toList(),
            granted = user.granted.toList(),
            admin = user.admin,
            syncedAt = user.syncedAt
        )
    }
}
