package DahRealPanda.plugins.colonyweb.model

import java.util.Locale

object BuiltinEntityModels {
    private val DYE = mapOf(
        "white" to 0xF9FFFE, "orange" to 0xF9801D,
        "magenta" to 0xC74EBD, "light_blue" to 0x3AB3DA,
        "yellow" to 0xFED83D, "lime" to 0x80C71F,
        "pink" to 0xF38BAA, "gray" to 0x474F52,
        "light_gray" to 0x9D9D97, "cyan" to 0x169C9C,
        "purple" to 0x8932B8, "blue" to 0x3C44AA,
        "brown" to 0x835432, "green" to 0x5E7C16,
        "red" to 0xB02E26, "black" to 0x1D1D21
    )

    @JvmStatic
    fun forItem(namespace: String, path: String?): BlockModel? {
        if (path == null) return null
        val id = path.lowercase(Locale.ROOT)
        if ("minecolonies" == namespace) {
            return if ("colony_banner" == id) banner(null) else null
        }
        if ("minecraft" != namespace) return null
        return when (id) {
            "shield" -> shield()
            "conduit" -> conduit()
            "chest" -> chest("normal")
            "trapped_chest" -> chest("trapped")
            "ender_chest" -> chest("ender")
            "shulker_box" -> shulkerBox(null)
            else -> {
                if (id.endsWith("_bed")) return bed(strip(id, "_bed"))
                if (id.endsWith("_shulker_box")) return shulkerBox(strip(id, "_shulker_box"))
                if (id.endsWith("_banner")) return banner(strip(id, "_banner"))
                null
            }
        }
    }

    private const val BED_X0 = 4.0
    private const val BED_X1 = 12.0
    private const val BED_LEG_BOTTOM = 5.75
    private const val BED_LEG_TOP = 7.25
    private const val BED_TOP = 10.25
    private const val BED_Z0 = 0.0
    private const val BED_Z_MID = 8.0
    private const val BED_Z1 = 16.0
    private const val BED_LEG = 1.5
    private const val BED_YAW = 180.0

    private fun bed(colour: String?): BlockModel {
        val model = BlockModel()
        model.textures["bed"] = "minecraft:entity/bed/$colour"
        val tex = Atlas("#bed", 64.0, 64.0)

        val head = box(BED_X0, BED_LEG_TOP, BED_Z0, BED_X1, BED_TOP, BED_Z_MID)
        head.faces["up"] = tex.face(6.0, 6.0, 22.0, 22.0)
        head.faces["down"] = tex.face(28.0, 6.0, 44.0, 22.0)
        head.faces["north"] = tex.face(6.0, 6.0, 22.0, 0.0)
        head.faces["west"] = turned(tex.face(0.0, 6.0, 6.0, 22.0), 270)
        head.faces["east"] = turned(tex.face(22.0, 6.0, 28.0, 22.0), 90)
        model.elements.add(head)

        val foot = box(BED_X0, BED_LEG_TOP, BED_Z_MID, BED_X1, BED_TOP, BED_Z1)
        foot.faces["up"] = tex.face(6.0, 28.0, 22.0, 44.0)
        foot.faces["down"] = tex.face(28.0, 28.0, 44.0, 44.0)
        foot.faces["south"] = tex.face(22.0, 28.0, 38.0, 22.0)
        foot.faces["west"] = turned(tex.face(0.0, 28.0, 6.0, 44.0), 270)
        foot.faces["east"] = turned(tex.face(22.0, 28.0, 28.0, 44.0), 90)
        model.elements.add(foot)

        model.elements.add(leg(tex, BED_X0, BED_Z0))
        model.elements.add(leg(tex, BED_X1 - BED_LEG, BED_Z0))
        model.elements.add(leg(tex, BED_X0, BED_Z1 - BED_LEG))
        model.elements.add(leg(tex, BED_X1 - BED_LEG, BED_Z1 - BED_LEG))

        turn(model, BED_YAW)
        return model
    }

    private fun leg(tex: Atlas, x: Double, z: Double): BlockModel.Element {
        val leg = box(x, BED_LEG_BOTTOM, z, x + BED_LEG, BED_LEG_TOP, z + BED_LEG)
        for (direction in arrayOf("up", "down", "north", "south", "east", "west")) {
            leg.faces[direction] = tex.face(53.0, 3.0, 56.0, 6.0)
        }
        return leg
    }

    private fun chest(kind: String): BlockModel {
        val model = BlockModel()
        model.textures["chest"] = "minecraft:entity/chest/$kind"
        val tex = Atlas("#chest", 64.0, 64.0)

        val bottom = box(1.0, 0.0, 1.0, 15.0, 10.0, 15.0)
        entityBox(bottom, tex, 0.0, 19.0, 14.0, 10.0, 14.0)
        flipSidesV(bottom)
        model.elements.add(bottom)

        val lid = box(1.0, 9.0, 1.0, 15.0, 14.0, 15.0)
        entityBox(lid, tex, 0.0, 0.0, 14.0, 5.0, 14.0)
        flipSidesV(lid)
        model.elements.add(lid)

        val lock = box(7.0, 7.0, 15.0, 9.0, 11.0, 16.0)
        entityBox(lock, tex, 0.0, 0.0, 2.0, 4.0, 1.0)
        flipSidesV(lock)
        model.elements.add(lock)

        turn(model, 180.0)
        return model
    }

    private fun shulkerBox(colour: String?): BlockModel {
        val model = BlockModel()
        model.textures["shulker"] = if (colour == null)
            "minecraft:entity/shulker/shulker"
        else
            "minecraft:entity/shulker/shulker_$colour"
        val tex = Atlas("#shulker", 64.0, 64.0)

        val base = box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0)
        entityBox(base, tex, 0.0, 28.0, 16.0, 8.0, 16.0)
        flipYZ(base)
        model.elements.add(base)

        val lid = box(0.0, 4.0, 0.0, 16.0, 16.0, 16.0)
        entityBox(lid, tex, 0.0, 0.0, 16.0, 12.0, 16.0)
        flipYZ(lid)
        model.elements.add(lid)
        return model
    }

    private fun banner(colour: String?): BlockModel {
        val model = BlockModel()
        model.textures["banner"] = "minecraft:entity/banner_base"
        val tex = Atlas("#banner", 64.0, 64.0)
        val scale = 15.0 / 42.0
        val bottom = 0.5
        val top = bottom + 42 * scale

        val poleW = 2 * scale / 2
        val pole = box(8 - poleW, bottom, 9 - poleW, 8 + poleW, top, 9 + poleW)
        entityBox(pole, tex, 44.0, 0.0, 2.0, 42.0, 2.0)
        model.elements.add(pole)

        val barW = 20 * scale / 2
        val bar = box(8 - barW, top - 2 * scale, 9 - poleW, 8 + barW, top, 9 + poleW)
        entityBox(bar, tex, 0.0, 42.0, 20.0, 2.0, 2.0)
        model.elements.add(bar)

        val cloth = box(8 - barW, top - 42 * scale, 9 - poleW - scale,
            8 + barW, top - 2 * scale, 9 - poleW)
        entityBox(cloth, tex, 0.0, 0.0, 20.0, 40.0, 1.0)
        tint(cloth, dye(colour))
        model.elements.add(cloth)
        return model
    }

    private fun dye(colour: String?): Int {
        val rgb = if (colour != null) DYE[colour] else null
        return rgb ?: 0xFFFFFF
    }

    private fun shield(): BlockModel {
        val model = BlockModel()
        model.textures["shield"] = "minecraft:entity/shield_base_nopattern"
        val tex = Atlas("#shield", 64.0, 64.0)

        val scale = 15.0 / 22.0
        val plateW = 12 * scale / 2
        val plateH = 22 * scale / 2
        val plateD = 1 * scale

        val plate = box(8 - plateW, 8 - plateH, 8.0, 8 + plateW, 8 + plateH, 8 + plateD)
        entityBox(plate, tex, 0.0, 0.0, 12.0, 22.0, 1.0)
        model.elements.add(plate)

        val gripW = 2 * scale / 2
        val gripH = 6 * scale / 2
        val gripD = 6 * scale
        val handle = box(8 - gripW, 8 - gripH, 8 + plateD,
            8 + gripW, 8 + gripH, 8 + plateD + gripD)
        entityBox(handle, tex, 26.0, 0.0, 2.0, 6.0, 6.0)
        model.elements.add(handle)

        turn(model, 35.0)
        return model
    }

    private fun conduit(): BlockModel {
        val model = BlockModel()
        model.textures["conduit"] = "minecraft:entity/conduit/base"
        val tex = Atlas("#conduit", 32.0, 16.0)

        val half = 5.5
        val shell = box(8 - half, 8 - half, 8 - half, 8 + half, 8 + half, 8 + half)
        entityBox(shell, tex, 0.0, 0.0, 6.0, 6.0, 6.0)
        model.elements.add(shell)
        return model
    }

    private fun strip(id: String, suffix: String): String =
        id.substring(0, id.length - suffix.length)

    private fun box(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double): BlockModel.Element {
        val element = BlockModel.Element()
        element.from = doubleArrayOf(x0, y0, z0)
        element.to = doubleArrayOf(x1, y1, z1)
        return element
    }

    // Vanilla UV unwrap: down/up on top row (d tall), sides follow below (h tall); up is bottom-to-top.
    private fun entityBox(
        element: BlockModel.Element, tex: Atlas,
        u: Double, v: Double, w: Double, h: Double, d: Double
    ) {
        element.faces["down"] = tex.face(u + d, v, u + d + w, v + d)
        element.faces["up"] = tex.face(u + d + w, v + d, u + d + w + w, v)
        element.faces["west"] = tex.face(u, v + d, u + d, v + d + h)
        element.faces["north"] = tex.face(u + d, v + d, u + d + w, v + d + h)
        element.faces["east"] = tex.face(u + d + w, v + d, u + d + w + d, v + d + h)
        element.faces["south"] = tex.face(u + d + w + d, v + d, u + d + w + d + w, v + d + h)
    }

    // Chest layer definitions stack upward, reversing the side strip direction.
    private fun flipSidesV(element: BlockModel.Element) {
        for (direction in arrayOf("west", "north", "east", "south")) {
            val face = element.faces[direction] ?: continue
            val v0 = face.uv!![1]
            face.uv!![1] = face.uv!![3]
            face.uv!![3] = v0
        }
    }

    private fun flipYZ(element: BlockModel.Element) {
        swap(element, "up", "down")
        swap(element, "north", "south")
    }

    private fun swap(element: BlockModel.Element, a: String, b: String) {
        val fa = element.faces[a]
        val fb = element.faces[b]
        if (fa != null && fb != null) {
            element.faces[a] = fb
            element.faces[b] = fa
        }
    }

    private fun tint(element: BlockModel.Element, colour: Int) {
        for (face in element.faces.values) {
            face.tint = colour
        }
    }

    private fun turned(face: BlockModel.Face, degrees: Int): BlockModel.Face {
        face.rotation = degrees
        return face
    }

    private fun turn(model: BlockModel, degrees: Double) {
        for (element in model.elements) {
            val rotation = BlockModel.Rotation()
            rotation.axis = "y"
            rotation.angle = degrees
            element.rotation = rotation
        }
    }

    private class Atlas(val ref: String, val width: Double, val height: Double) {
        fun face(u0: Double, v0: Double, u1: Double, v1: Double): BlockModel.Face {
            val face = BlockModel.Face()
            face.texture = ref
            face.uv = doubleArrayOf(
                u0 * 16 / width, v0 * 16 / height,
                u1 * 16 / width, v1 * 16 / height
            )
            return face
        }
    }
}
