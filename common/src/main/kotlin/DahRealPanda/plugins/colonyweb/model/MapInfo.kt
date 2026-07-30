package DahRealPanda.plugins.colonyweb.model

/**
 * Everything the browser needs to place the colony map: where the rendered surface image sits
 * in world coordinates, and how much of it has been drawn so far.
 *
 * The image itself is served separately from `/map/{colonyId}.png` — this document is
 * small enough to poll while the map fills in.
 */
data class MapInfo(
    /** False when the colony cannot be mapped at all (map disabled, or no colony data yet). */
    @JvmField var available: Boolean = false,
    /** True once a PNG has been encoded, so /map/{colonyId}.png is expected to exist. */
    @JvmField var ready: Boolean = false,
    /** Why [available] is false, for the empty state. Null when it is true. */
    @JvmField var unavailableReason: String? = null,
    @JvmField var dimension: String = "",
    /** The colony centre — where the town hall stands. */
    @JvmField var centerX: Int = 0,
    @JvmField var centerY: Int = 0,
    @JvmField var centerZ: Int = 0,
    /** World coordinates of the image's top-left pixel. One pixel is one block. */
    @JvmField var minX: Int = 0,
    @JvmField var minZ: Int = 0,
    @JvmField var width: Int = 0,
    @JvmField var height: Int = 0,
    /** Bumped every time the image changes, so the browser can cache-bust it. */
    @JvmField var version: Int = 0,
    /** Epoch millis of the last redraw, or 0 when nothing has been drawn. */
    @JvmField var renderedAt: Long = 0L,
    /** Chunks drawn at least once, out of the chunks the image covers. */
    @JvmField var chunksMapped: Int = 0,
    @JvmField var chunksTotal: Int = 0
)
