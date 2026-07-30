package DahRealPanda.plugins.colonyweb.model

class BlockModel {
    val textures: MutableMap<String, String> = LinkedHashMap()
    val elements: MutableList<Element> = ArrayList()

    fun resolveTextureRef(ref: String?): String? {
        var current: String? = ref
        for (i in 0 until 8) {
            if (current == null) return null
            if (!current.startsWith("#")) return current
            current = textures[current.substring(1)]
        }
        return null
    }

    fun usedTextureRefs(): List<String> {
        val used = LinkedHashSet<String>()
        for (element in elements) {
            for (face in element.faces.values) {
                if (face.texture != null) {
                    used.add(face.texture!!)
                }
            }
        }
        val ordered = LinkedHashSet<String>()
        for (name in textures.keys) {
            val ref = "#$name"
            if (used.contains(ref)) {
                ordered.add(ref)
            }
        }
        ordered.addAll(used)
        return ArrayList(ordered)
    }

    class Element {
        var from: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)
        var to: DoubleArray = doubleArrayOf(16.0, 16.0, 16.0)
        var rotation: Rotation? = null
        val faces: MutableMap<String, Face> = LinkedHashMap()
    }

    class Face {
        var texture: String? = null
        var uv: DoubleArray? = null
        var rotation: Int = 0
        var tintIndex: Int = -1
        var tint: Int = -1
    }

    class Rotation {
        var origin: DoubleArray = doubleArrayOf(8.0, 8.0, 8.0)
        var axis: String = "y"
        var angle: Double = 0.0
        var rescale: Boolean = false
    }
}
