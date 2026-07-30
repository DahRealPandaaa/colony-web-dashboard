package DahRealPanda.plugins.colonyweb.model

/**
 * A reference to an item/block used as a texture key.
 *
 * The [itemKey] is the registry name (`namespace:path`) optionally suffixed
 * with `#<8charHash>` when NBT is relevant (e.g. Domum Ornamentum textured blocks) so
 * that distinct variants map to distinct texture PNGs.
 */
data class ItemRef(
    @JvmField var registryName: String = "",
    @JvmField var nbtHash: String? = null // optional, null when not NBT-relevant
) {
    /** Full texture key: `namespace:path` plus `#hash` when present. */
    fun key(): String {
        if (nbtHash.isNullOrEmpty()) {
            return registryName
        }
        return "$registryName#$nbtHash"
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
