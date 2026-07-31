package DahRealPanda.plugins.colonyweb.util

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentContents
import net.minecraft.network.chat.contents.TranslatableContents
import java.util.regex.Pattern

object Text {
    private val CONTENTS_KEY = Pattern.compile("key='([^']+)'")

    @JvmStatic
    fun componentString(o: Any?): String? {
        if (o == null) return null
        if (o is Component) {
            val translated = fromContents(o.contents)
            if (translated != null && o.siblings.isEmpty()) {
                return translated
            }
            return o.string
        }
        if (o is ComponentContents) {
            val translated = fromContents(o)
            if (translated != null) return translated
        }
        val s = o.toString()
        val matcher = CONTENTS_KEY.matcher(s)
        if (s.startsWith("translation{") && matcher.find()) {
            val key = matcher.group(1)
            val translated = Translations.lookup(key)
            return translated ?: key
        }
        return s
    }

    private fun fromContents(contents: ComponentContents): String? {
        if (contents !is TranslatableContents) return null
        return Translations.format(contents.key, contents.args)
    }

    @JvmStatic
    fun pathOf(registryName: String?): String {
        registryName ?: return ""
        val idx = registryName.indexOf(':')
        return if (idx >= 0) registryName.substring(idx + 1) else registryName
    }

    @JvmStatic
    fun humanize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw
        val slash = maxOf(s.lastIndexOf('/'), s.lastIndexOf('\\'))
        if (slash >= 0 && slash < s.length - 1) {
            s = s.substring(slash + 1)
        }
        s = s.replace(Regex("\\.(blueprint|json)$"), "")
        s = s.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .replace(Regex("(?<=[A-Za-z])(?=[0-9])"), " ")
            .replace(Regex("(?<=[0-9])(?=[A-Za-z])"), " ")
            .replace('_', ' ')
            .replace('-', ' ')
            .replace('.', ' ')
        val parts = s.trim().split("\\s+".toRegex())
        val sb = StringBuilder()
        for (p in parts) {
            if (p.isEmpty()) continue
            sb.append(p[0].uppercaseChar())
                .append(if (p.length > 1) p.substring(1) else "")
                .append(' ')
        }
        return sb.toString().trim()
    }

    @JvmStatic
    fun displayName(value: Any?, fallback: String): String {
        val s = componentString(value)
        if (s.isNullOrBlank()) return fallback
        if (!looksLikeKey(s)) return s
        var key: String = s ?: return fallback
        for (suffix in arrayOf(".name", ".description", ".desc", ".title")) {
            if (key.endsWith(suffix)) {
                key = key.substring(0, key.length - suffix.length)
                break
            }
        }
        val dot = key.lastIndexOf('.')
        val tail = if (dot >= 0 && dot < key.length - 1) key.substring(dot + 1) else key
        val humanized = humanize(tail)
        return if (humanized.isBlank()) fallback else humanized
    }

    @JvmStatic
    fun stringOrEmpty(s: String?): String = s ?: ""

    @JvmStatic
    fun looksLikeKey(s: String): Boolean =
        !s.contains(" ") && (s.indexOf('.') > 0 || s.indexOf(':') > 0)
}
