package DahRealPanda.plugins.colonyweb.api.response


data class LoginResponse(
    val authenticated: Boolean,
    val user: WebUserResponse? = null,
    val error: String? = null
)
