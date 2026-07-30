package DahRealPanda.plugins.colonyweb.web

import com.google.gson.Gson
import com.google.gson.GsonBuilder

object JsonUtil {
    val gson: Gson = GsonBuilder().create()

    fun toJson(obj: Any): String = gson.toJson(obj)
}
