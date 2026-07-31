package DahRealPanda.plugins.colonyweb.model

/**
 * Identity of an item as the dashboard shows it: texture key, display name and (for Domum
 * Ornamentum blocks) the material breakdown rendered as tooltip lines.
 *
 * NOTE: This is an open class rather than a data class because it is extended by
 * [EquipmentInfo], [ItemCount], [ResourceEntry] and [ColonySnapshot.Stack]. Kotlin's `data`
 * modifier cannot be applied to a class that is extended by another class.
 */
open class ItemInfo(
    @JvmField var itemKey: String = "",
    @JvmField var name: String = "",
    @JvmField var material: String? = null,  // combined DO material names, null when not DO
    @JvmField var domum: Boolean = false,    // true for Domum Ornamentum textured blocks
    @JvmField var craftedIn: String? = null, // e.g. "Architects Cutter", null when unknown
    @JvmField var craftable: Boolean = false, // a colony worker knows a recipe that produces this
    @JvmField var components: MutableList<MaterialComponent> = ArrayList(),
    @JvmField var variant: String? = null     // DO shape/type, e.g. "Fancy"; null when the name already says it
)
