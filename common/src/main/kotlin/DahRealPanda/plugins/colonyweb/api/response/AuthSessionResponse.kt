package DahRealPanda.plugins.colonyweb.api.response


data class AuthSessionResponse(
    val authenticated: Boolean,
    val authEnabled: Boolean,
    val user: WebUserResponse? = null
)
