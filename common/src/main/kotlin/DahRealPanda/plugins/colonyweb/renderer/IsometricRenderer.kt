package DahRealPanda.plugins.colonyweb.renderer

import DahRealPanda.plugins.colonyweb.model.BlockModel
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.function.Function
import javax.imageio.ImageIO

object IsometricRenderer {
    private const val SUPERSAMPLE = 2

    private const val SHADE_UP = 1.0
    private const val SHADE_DOWN = 0.5
    private const val SHADE_NS = 0.8
    private const val SHADE_EW = 0.6

    private const val DEFAULT_TINT = 0x91BD59

    private const val GUI_PITCH_DEGREES = 30.0

    /**
     * The vanilla GUI yaw, negated. Minecraft's item transform is [30, 225, 0], but the GUI
     * pass mirrors the scene (poseStack.scale(16, -16, 16)), which flips the handedness the
     * yaw is applied in. A plain +225 produces a horizontally mirrored block.
     */
    private const val GUI_YAW_DEGREES = -225.0

    private val COS_X = Math.cos(Math.toRadians(GUI_PITCH_DEGREES))
    private val SIN_X = Math.sin(Math.toRadians(GUI_PITCH_DEGREES))
    private val COS_Y = Math.cos(Math.toRadians(GUI_YAW_DEGREES))
    private val SIN_Y = Math.sin(Math.toRadians(GUI_YAW_DEGREES))

    private const val MODEL_HALF_EXTENT = 12.6

    @JvmStatic
    fun render(model: BlockModel?, textureFor: Function<String, BufferedImage?>, size: Int): BufferedImage? {
        if (model == null || model.elements.isEmpty()) return null
        val canvas = size * SUPERSAMPLE
        val argb = IntArray(canvas * canvas)
        val depth = DoubleArray(canvas * canvas)
        depth.fill(Double.NEGATIVE_INFINITY)

        val scale = (canvas * 0.5 * 0.96) / MODEL_HALF_EXTENT
        var drewAnything = false

        for (element in model.elements) {
            for ((direction, face) in element.faces) {
                val texture = textureFor.apply(face.texture ?: continue) ?: continue
                drewAnything = drewAnything or drawFace(
                    argb, depth, canvas, scale, element, direction, face, texture
                )
            }
        }
        if (!drewAnything) return null
        return downsample(argb, canvas, size)
    }

    private fun drawFace(
        argb: IntArray, depth: DoubleArray, canvas: Int, scale: Double,
        element: BlockModel.Element, direction: String,
        face: BlockModel.Face, texture: BufferedImage
    ): Boolean {
        val uv = face.uv ?: defaultUv(element, direction) ?: return false
        val shade = shadeFor(direction, face)

        val st = arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(1.0, 0.0),
            doubleArrayOf(1.0, 1.0), doubleArrayOf(0.0, 1.0))
        val pos = Array(4) { i ->
            val point = facePoint(element, direction, st[i][0], st[i][1]) ?: return false
            project(transform(point, element.rotation), canvas, scale)
        }
        val tex = Array(4) { i ->
            val rotated = rotateUv(st[i][0], st[i][1], face.rotation)
            doubleArrayOf(lerp(uv[0], uv[2], rotated[0]), lerp(uv[1], uv[3], rotated[1]))
        }

        var drew = triangle(argb, depth, canvas, pos[0], pos[1], pos[2], tex[0], tex[1], tex[2], texture, shade)
        drew = drew or triangle(argb, depth, canvas, pos[0], pos[2], pos[3], tex[0], tex[2], tex[3], texture, shade)
        return drew
    }

    private fun facePoint(e: BlockModel.Element, direction: String, s: Double, t: Double): DoubleArray? {
        val x0 = e.from[0]; val y0 = e.from[1]; val z0 = e.from[2]
        val x1 = e.to[0]; val y1 = e.to[1]; val z1 = e.to[2]
        return when (direction) {
            "up" -> doubleArrayOf(lerp(x0, x1, s), y1, lerp(z0, z1, t))
            "down" -> doubleArrayOf(lerp(x0, x1, s), y0, lerp(z1, z0, t))
            "north" -> doubleArrayOf(lerp(x1, x0, s), lerp(y1, y0, t), z0)
            "south" -> doubleArrayOf(lerp(x0, x1, s), lerp(y1, y0, t), z1)
            "west" -> doubleArrayOf(x0, lerp(y1, y0, t), lerp(z0, z1, s))
            "east" -> doubleArrayOf(x1, lerp(y1, y0, t), lerp(z1, z0, s))
            else -> null
        }
    }

    private fun defaultUv(e: BlockModel.Element, direction: String): DoubleArray? {
        val x0 = e.from[0]; val y0 = e.from[1]; val z0 = e.from[2]
        val x1 = e.to[0]; val y1 = e.to[1]; val z1 = e.to[2]
        return when (direction) {
            "up" -> doubleArrayOf(x0, z0, x1, z1)
            "down" -> doubleArrayOf(x0, 16 - z1, x1, 16 - z0)
            "north" -> doubleArrayOf(16 - x1, 16 - y1, 16 - x0, 16 - y0)
            "south" -> doubleArrayOf(x0, 16 - y1, x1, 16 - y0)
            "west" -> doubleArrayOf(z0, 16 - y1, z1, 16 - y0)
            "east" -> doubleArrayOf(16 - z1, 16 - y1, 16 - z0, 16 - y0)
            else -> null
        }
    }

    private fun shadeFor(direction: String, face: BlockModel.Face): DoubleArray {
        val shade = when (direction) {
            "up" -> SHADE_UP
            "down" -> SHADE_DOWN
            "north", "south" -> SHADE_NS
            else -> SHADE_EW
        }
        val tint = if (face.tint >= 0) face.tint else if (face.tintIndex >= 0) DEFAULT_TINT else -1
        if (tint < 0) return doubleArrayOf(shade, shade, shade)
        return doubleArrayOf(
            shade * ((tint shr 16) and 0xFF) / 255.0,
            shade * ((tint shr 8) and 0xFF) / 255.0,
            shade * (tint and 0xFF) / 255.0
        )
    }

    private fun rotateUv(s: Double, t: Double, rotation: Int): DoubleArray {
        val steps = ((rotation / 90) % 4 + 4) % 4
        return when (steps) {
            1 -> doubleArrayOf(t, 1 - s)
            2 -> doubleArrayOf(1 - s, 1 - t)
            3 -> doubleArrayOf(1 - t, s)
            else -> doubleArrayOf(s, t)
        }
    }

    private fun transform(p: DoubleArray, rotation: BlockModel.Rotation?): DoubleArray {
        var x = p[0]
        var y = p[1]
        var z = p[2]

        if (rotation != null && rotation.angle != 0.0) {
            val angle = Math.toRadians(rotation.angle)
            val cos = Math.cos(angle)
            val sin = Math.sin(angle)
            val ox = rotation.origin[0]
            val oy = rotation.origin[1]
            val oz = rotation.origin[2]
            var dx = x - ox
            var dy = y - oy
            var dz = z - oz
            val factor = if (rotation.rescale) 1.0 / Math.cos(angle) else 1.0
            when (rotation.axis) {
                "x" -> {
                    val ny = dy * cos - dz * sin
                    val nz = dy * sin + dz * cos
                    dy = ny * factor; dz = nz * factor
                }
                "z" -> {
                    val nx = dx * cos - dy * sin
                    val ny = dx * sin + dy * cos
                    dx = nx * factor; dy = ny * factor
                }
                else -> {
                    val nx = dx * cos + dz * sin
                    val nz = -dx * sin + dz * cos
                    dx = nx * factor; dz = nz * factor
                }
            }
            x = ox + dx; y = oy + dy; z = oz + dz
        }

        x -= 8; y -= 8; z -= 8
        val rx = x * COS_Y + z * SIN_Y
        val rz = -x * SIN_Y + z * COS_Y
        val ry = y * COS_X - rz * SIN_X
        val rzz = y * SIN_X + rz * COS_X
        return doubleArrayOf(rx, ry, rzz)
    }

    private fun project(v: DoubleArray, canvas: Int, scale: Double): DoubleArray {
        val half = canvas * 0.5
        return doubleArrayOf(half + v[0] * scale, half - v[1] * scale, v[2])
    }

    private fun triangle(
        argb: IntArray, depth: DoubleArray, canvas: Int,
        a: DoubleArray, b: DoubleArray, c: DoubleArray,
        uvA: DoubleArray, uvB: DoubleArray, uvC: DoubleArray,
        texture: BufferedImage, shade: DoubleArray
    ): Boolean {
        val area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        if (Math.abs(area) < 1e-9) return false
        val minX = maxOf(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0]))).toInt())
        val maxX = minOf(canvas - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))).toInt())
        val minY = maxOf(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1]))).toInt())
        val maxY = minOf(canvas - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))).toInt())

        val texW = texture.width
        val texH = texture.height
        var drew = false

        for (py in minY..maxY) {
            for (px in minX..maxX) {
                val sx = px + 0.5
                val sy = py + 0.5
                val w0 = ((b[0] - a[0]) * (sy - a[1]) - (b[1] - a[1]) * (sx - a[0])) / area
                val w1 = ((sx - a[0]) * (c[1] - a[1]) - (sy - a[1]) * (c[0] - a[0])) / area
                val w2 = 1.0 - w0 - w1
                if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue
                val z = w2 * a[2] + w1 * b[2] + w0 * c[2]
                val idx = py * canvas + px
                if (z <= depth[idx]) continue
                val u = w2 * uvA[0] + w1 * uvB[0] + w0 * uvC[0]
                val v = w2 * uvA[1] + w1 * uvB[1] + w0 * uvC[1]
                val tx = Math.floorMod((u / 16.0 * texW).toInt(), texW)
                val ty = Math.floorMod((v / 16.0 * texH).toInt(), texH)
                val texel = texture.getRGB(tx, ty)
                val alpha = (texel ushr 24) and 0xFF
                if (alpha < 8) continue
                depth[idx] = z
                argb[idx] = shadeColor(texel, shade)
                drew = true
            }
        }
        return drew
    }

    private fun shadeColor(argb: Int, shade: DoubleArray): Int {
        val a = (argb ushr 24) and 0xFF
        val r = ((argb shr 16) and 0xFF) * shade[0]
        val g = ((argb shr 8) and 0xFF) * shade[1]
        val b = (argb and 0xFF) * shade[2]
        return (a shl 24) or (minOf(255, r.toInt()) shl 16) or
                (minOf(255, g.toInt()) shl 8) or minOf(255, b.toInt())
    }

    private fun downsample(argb: IntArray, canvas: Int, size: Int): BufferedImage {
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val factor = canvas / size
        for (y in 0 until size) {
            for (x in 0 until size) {
                var a = 0L; var r = 0L; var g = 0L; var b = 0L
                for (dy in 0 until factor) {
                    for (dx in 0 until factor) {
                        val p = argb[(y * factor + dy) * canvas + (x * factor + dx)]
                        val pa = (p ushr 24) and 0xFF
                        a += pa
                        r += ((p shr 16) and 0xFF) * pa
                        g += ((p shr 8) and 0xFF) * pa
                        b += (p and 0xFF) * pa
                    }
                }
                val samples = factor * factor
                val outA = (a / samples).toInt()
                if (a == 0L) {
                    out.setRGB(x, y, 0)
                    continue
                }
                val outR = (r / a).toInt()
                val outG = (g / a).toInt()
                val outB = (b / a).toInt()
                out.setRGB(x, y, (outA shl 24) or (outR shl 16) or (outG shl 8) or outB)
            }
        }
        return out
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}
