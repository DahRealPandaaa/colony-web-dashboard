package DahRealPanda.plugins.colonyweb.colony.model;

/**
 * Everything the browser needs to place the colony map: where the rendered surface image sits
 * in world coordinates, and how much of it has been drawn so far.
 *
 * <p>The image itself is served separately from {@code /map/{colonyId}.png} — this document is
 * small enough to poll while the map fills in.</p>
 */
public class MapInfo {
    /** False when the colony cannot be mapped at all (map disabled, or no colony data yet). */
    public boolean available;

    /** True once a PNG has been encoded, so /map/{colonyId}.png is expected to exist. */
    public boolean ready;

    /** Why {@link #available} is false, for the empty state. Null when it is true. */
    public String unavailableReason;

    public String dimension;

    /** The colony centre — where the town hall stands. */
    public int centerX;
    public int centerY;
    public int centerZ;

    /** World coordinates of the image's top-left pixel. One pixel is one block. */
    public int minX;
    public int minZ;
    public int width;
    public int height;

    /** Bumped every time the image changes, so the browser can cache-bust it. */
    public int version;

    /** Epoch millis of the last redraw, or 0 when nothing has been drawn. */
    public long renderedAt;

    /** Chunks drawn at least once, out of the chunks the image covers. */
    public int chunksMapped;
    public int chunksTotal;
}
