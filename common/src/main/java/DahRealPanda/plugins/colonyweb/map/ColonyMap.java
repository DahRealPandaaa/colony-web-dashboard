package DahRealPanda.plugins.colonyweb.map;

/**
 * One colony's surface raster: a block-per-pixel top-down view of the area around it.
 *
 * <p>The raster is filled in a chunk at a time by {@link ColonyMapService} on the server thread,
 * and keeps whatever it already drew for chunks that are no longer loaded — so the map behaves
 * like a mapping mod's: it remembers everywhere the server has actually had loaded, rather than
 * going blank the moment players walk away.</p>
 *
 * <p>{@code rgb} and {@code top} are written on the server thread only. The PNG encoder works
 * from clones of them, so the fields it publishes ({@link #png}, {@link #version},
 * {@link #renderedAt}) are the only ones read off-thread, and are volatile for that reason.</p>
 */
final class ColonyMap {
    /** World coordinates of pixel (0, 0), always chunk-aligned. */
    final int minX;
    final int minZ;
    final int width;
    final int height;

    /** Chunk coordinates of the top-left chunk, and the grid size in chunks. */
    final int chunkX;
    final int chunkZ;
    final int chunkCols;
    final int chunkRows;

    /** Surface colour per pixel, {@code 0} where nothing has been drawn yet. */
    final int[] rgb;

    /** Surface height per pixel, used for hill shading when the image is encoded. */
    final int[] top;

    /** When each chunk was last drawn, {@code 0} for never. Indexed like {@link #order}. */
    final long[] chunkStamp;

    /** Chunk indices ordered by distance from the centre, so the colony itself draws first. */
    final int[] order;

    /** Round-robin position into {@link #order} for the next incremental pass. */
    int cursor;

    /** How many chunks have been drawn at least once. */
    int mapped;

    volatile byte[] png;
    volatile int version;
    volatile long renderedAt;

    ColonyMap(int minX, int minZ, int width, int height) {
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.height = height;
        this.chunkX = minX >> 4;
        this.chunkZ = minZ >> 4;
        this.chunkCols = width >> 4;
        this.chunkRows = height >> 4;
        this.rgb = new int[width * height];
        this.top = new int[width * height];
        this.chunkStamp = new long[chunkCols * chunkRows];
        this.order = centreOutOrder(chunkCols, chunkRows);
    }

    int chunkCount() {
        return chunkStamp.length;
    }

    boolean covers(int blockMinX, int blockMinZ, int blockMaxX, int blockMaxZ) {
        return blockMinX >= minX && blockMinZ >= minZ
                && blockMaxX <= minX + width && blockMaxZ <= minZ + height;
    }

    /**
     * Copy everything an older map already drew into this one, for the area they share.
     *
     * <p>Called when a colony grows past its map's edges: re-drawing from scratch would blank
     * out every chunk that is no longer loaded, and those are exactly the ones worth keeping.</p>
     */
    void inherit(ColonyMap old) {
        int fromX = Math.max(minX, old.minX);
        int fromZ = Math.max(minZ, old.minZ);
        int toX = Math.min(minX + width, old.minX + old.width);
        int toZ = Math.min(minZ + height, old.minZ + old.height);
        for (int z = fromZ; z < toZ; z++) {
            int src = (z - old.minZ) * old.width + (fromX - old.minX);
            int dst = (z - minZ) * width + (fromX - minX);
            System.arraycopy(old.rgb, src, rgb, dst, toX - fromX);
            System.arraycopy(old.top, src, top, dst, toX - fromX);
        }
        for (int cz = 0; cz < chunkRows; cz++) {
            for (int cx = 0; cx < chunkCols; cx++) {
                int oldCx = chunkX + cx - old.chunkX;
                int oldCz = chunkZ + cz - old.chunkZ;
                if (oldCx < 0 || oldCz < 0 || oldCx >= old.chunkCols || oldCz >= old.chunkRows) {
                    continue;
                }
                long stamp = old.chunkStamp[oldCz * old.chunkCols + oldCx];
                chunkStamp[cz * chunkCols + cx] = stamp;
                if (stamp != 0) {
                    mapped++;
                }
            }
        }
        // The old PNG is the wrong size for these bounds, so it is deliberately not carried
        // over — the caller re-encodes immediately. The version keeps climbing so the browser
        // still sees a new URL rather than a cached image of the old area.
        version = old.version;
        renderedAt = old.renderedAt;
    }

    /** Chunk indices sorted by how far they sit from the middle of the grid. */
    private static int[] centreOutOrder(int cols, int rows) {
        int[] indices = new int[cols * rows];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        double midX = (cols - 1) / 2.0;
        double midZ = (rows - 1) / 2.0;
        Integer[] boxed = new Integer[indices.length];
        for (int i = 0; i < indices.length; i++) {
            boxed[i] = i;
        }
        java.util.Arrays.sort(boxed, java.util.Comparator.comparingDouble(index -> {
            double dx = (index % cols) - midX;
            double dz = (index / cols) - midZ;
            return dx * dx + dz * dz;
        }));
        for (int i = 0; i < boxed.length; i++) {
            indices[i] = boxed[i];
        }
        return indices;
    }
}
