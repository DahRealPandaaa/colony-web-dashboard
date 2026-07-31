package DahRealPanda.plugins.colonyweb.renderer

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.material.MapColor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object SurfaceRenderer {
    private const val MAX_WATER_DEPTH = 16

    private const val WATER_STEP = 0.035

    private const val RELIEF_STEP = 0.055

    private const val RELIEF_MIN = 0.68
    private const val RELIEF_MAX = 1.30

    @JvmStatic
    fun drawChunk(map: ColonyMap, level: ServerLevel, chunk: LevelChunk) {
        val pos = BlockPos.MutableBlockPos()
        val baseX = chunk.pos.minBlockX
        val baseZ = chunk.pos.minBlockZ
        val floorY = level.minBuildHeight

        for (dz in 0 until 16) {
            val worldZ = baseZ + dz
            val pixelZ = worldZ - map.minZ
            if (pixelZ < 0 || pixelZ >= map.height) continue
            for (dx in 0 until 16) {
                val worldX = baseX + dx
                val pixelX = worldX - map.minX
                if (pixelX < 0 || pixelX >= map.width) continue
                drawColumn(map, level, chunk, pos, worldX, worldZ, floorY, pixelZ * map.width + pixelX)
            }
        }
    }

    private fun drawColumn(
        map: ColonyMap, level: ServerLevel, chunk: LevelChunk,
        pos: BlockPos.MutableBlockPos, worldX: Int, worldZ: Int,
        floorY: Int, index: Int
    ) {
        var y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ)
        var state: BlockState? = null
        var color = MapColor.NONE

        while (y >= floorY) {
            pos.set(worldX, y, worldZ)
            state = chunk.getBlockState(pos)
            color = state.getMapColor(level, pos)
            if (color != MapColor.NONE) break
            y--
        }
        if (state == null || color == MapColor.NONE) return

        var rgb = color.col
        val depth = waterDepth(chunk, pos, state, worldX, worldZ, y, floorY)
        if (depth > 0) {
            rgb = scale(rgb, 1.0 - minOf(depth, MAX_WATER_DEPTH) * WATER_STEP)
        }
        map.rgb[index] = (-0x1000000) or rgb
        map.top[index] = y
    }

    private fun waterDepth(
        chunk: LevelChunk, pos: BlockPos.MutableBlockPos, surface: BlockState,
        worldX: Int, worldZ: Int, surfaceY: Int, floorY: Int
    ): Int {
        if (surface.fluidState.isEmpty()) return 0
        var depth = 1
        var y = surfaceY - 1
        while (y >= floorY && depth <= MAX_WATER_DEPTH) {
            pos.set(worldX, y, worldZ)
            if (chunk.getBlockState(pos).fluidState.isEmpty()) break
            depth++
            y--
        }
        return depth
    }

    @JvmStatic
    fun encode(width: Int, height: Int, rgb: IntArray, top: IntArray): ByteArray? {
        val pixels = IntArray(rgb.size)
        for (z in 0 until height) {
            for (x in 0 until width) {
                val i = z * width + x
                val base = rgb[i]
                if (base == 0) continue
                val h = top[i]
                val north = if (z > 0 && rgb[i - width] != 0) top[i - width] else h
                val west = if (x > 0 && rgb[i - 1] != 0) top[i - 1] else h
                val relief = 1.0 + RELIEF_STEP * ((h - north) + (h - west))
                pixels[i] = (-0x1000000) or scale(base, clamp(relief))
            }
        }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, width, height, pixels, 0, width)
        return try {
            val out = ByteArrayOutputStream()
            ImageIO.write(image, "png", out)
            out.toByteArray()
        } catch (_: java.io.IOException) {
            null
        }
    }

    private fun clamp(factor: Double): Double =
        maxOf(RELIEF_MIN, minOf(RELIEF_MAX, factor))

    private fun scale(rgb: Int, factor: Double): Int {
        val r = channel(((rgb shr 16) and 0xFF) * factor)
        val g = channel(((rgb shr 8) and 0xFF) * factor)
        val b = channel((rgb and 0xFF) * factor)
        return (r shl 16) or (g shl 8) or b
    }

    private fun channel(value: Double): Int =
        maxOf(0, minOf(255, (value).toInt()))
}
