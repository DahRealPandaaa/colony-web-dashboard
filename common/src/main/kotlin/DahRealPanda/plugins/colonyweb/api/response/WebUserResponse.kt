package DahRealPanda.plugins.colonyweb.api.response


data class WebUserResponse(
    val uuid: String,
    val name: String,
    val colonies: List<Int>,
    val granted: List<Int>,
    val admin: Boolean,
    val syncedAt: Long
)
