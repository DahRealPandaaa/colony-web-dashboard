package DahRealPanda.plugins.colonyweb.renderer

class ColonyMap(
    val minX: Int,
    val minZ: Int,
    val width: Int,
    val height: Int
) {
    val chunkX: Int = minX shr 4
    val chunkZ: Int = minZ shr 4
    val chunkCols: Int = width shr 4
    val chunkRows: Int = height shr 4

    val rgb: IntArray = IntArray(width * height)

    val top: IntArray = IntArray(width * height)

    val chunkStamp: LongArray = LongArray(chunkCols * chunkRows)

    val order: IntArray = centreOutOrder(chunkCols, chunkRows)

    var cursor: Int = 0

    var mapped: Int = 0

    @Volatile
    var png: ByteArray? = null

    @Volatile
    var version: Int = 0

    @Volatile
    var renderedAt: Long = 0L

    fun chunkCount(): Int = chunkStamp.size

    fun covers(blockMinX: Int, blockMinZ: Int, blockMaxX: Int, blockMaxZ: Int): Boolean =
        blockMinX >= minX && blockMinZ >= minZ &&
                blockMaxX <= minX + width && blockMaxZ <= minZ + height

    fun inherit(old: ColonyMap) {
        val fromX = maxOf(minX, old.minX)
        val fromZ = maxOf(minZ, old.minZ)
        val toX = minOf(minX + width, old.minX + old.width)
        val toZ = minOf(minZ + height, old.minZ + old.height)
        for (z in fromZ until toZ) {
            val src = (z - old.minZ) * old.width + (fromX - old.minX)
            val dst = (z - minZ) * width + (fromX - minX)
            System.arraycopy(old.rgb, src, rgb, dst, toX - fromX)
            System.arraycopy(old.top, src, top, dst, toX - fromX)
        }
        for (cz in 0 until chunkRows) {
            for (cx in 0 until chunkCols) {
                val oldCx = chunkX + cx - old.chunkX
                val oldCz = chunkZ + cz - old.chunkZ
                if (oldCx < 0 || oldCz < 0 || oldCx >= old.chunkCols || oldCz >= old.chunkRows) continue
                val stamp = old.chunkStamp[oldCz * old.chunkCols + oldCx]
                chunkStamp[cz * chunkCols + cx] = stamp
                if (stamp != 0L) mapped++
            }
        }
        version = old.version
        renderedAt = old.renderedAt
    }

    private companion object {
        private fun centreOutOrder(cols: Int, rows: Int): IntArray {
            val indices = IntArray(cols * rows)
            for (i in indices.indices) indices[i] = i
            val midX = (cols - 1) / 2.0
            val midZ = (rows - 1) / 2.0
            val boxed = indices.toTypedArray()
            java.util.Arrays.sort(boxed) { a, b ->
                val da = (a % cols - midX) * (a % cols - midX) + (a / cols - midZ) * (a / cols - midZ)
                val db = (b % cols - midX) * (b % cols - midX) + (b / cols - midZ) * (b / cols - midZ)
                da.compareTo(db)
            }
            for (i in boxed.indices) indices[i] = boxed[i]
            return indices
        }
    }
}
