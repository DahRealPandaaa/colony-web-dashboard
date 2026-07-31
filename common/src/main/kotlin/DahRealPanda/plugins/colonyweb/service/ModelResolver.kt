package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.BlockModel
import DahRealPanda.plugins.colonyweb.provider.VanillaAssetProvider
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStream
import java.nio.charset.StandardCharsets

class ModelResolver(private val vanilla: VanillaAssetProvider) {

    fun resolveTexture(namespace: String, path: String): String? {
        return resolveFromModel(namespace, "models/item/$path.json", 0)
            ?: resolveFromModel(namespace, "models/block/$path.json", 0)
            ?: run {
                if (hasTexture(namespace, "textures/item/$path.png"))
                    "$namespace:item/$path"
                else if (hasTexture(namespace, "textures/block/$path.png"))
                    "$namespace:block/$path"
                else if (hasTexture(namespace, "textures/blocks/$path.png"))
                    "$namespace:blocks/$path"
                else null
            }
    }

    fun resolveInventoryModel(namespace: String, path: String): BlockModel? {
        val model = BlockModel()
        if (loadInto(model, namespace, "models/item/$path.json", 0)) {
            return if (model.elements.isEmpty()) null else model
        }
        return resolveModel(namespace, path)
    }

    fun resolveModel(namespace: String, path: String): BlockModel? {
        for (candidate in listOf("models/item/$path.json", "models/block/$path.json")) {
            val model = BlockModel()
            loadInto(model, namespace, candidate, 0)
            if (model.elements.isNotEmpty()) return model
        }
        val fromState = modelFromBlockstate(namespace, path)
        if (fromState != null) {
            val ref = ParsedRef.of(fromState)
            val model = BlockModel()
            loadInto(model, ref.namespace, "models/${ref.path}.json", 0)
            if (model.elements.isNotEmpty()) return model
        }
        return null
    }

    private fun loadInto(model: BlockModel, namespace: String, modelPath: String, depth: Int): Boolean {
        if (depth > 8) return false
        val json = readAsset(namespace, modelPath) ?: return false
        try {
            val root = JsonParser.parseString(json).asJsonObject
            if (root.has("textures")) {
                for ((key, value) in root.getAsJsonObject("textures").entrySet()) {
                    if (value.isJsonPrimitive) {
                        model.textures.putIfAbsent(key, value.asString)
                    }
                }
            }
            if (model.elements.isEmpty() && root.has("elements")) {
                parseElements(model, root.getAsJsonArray("elements"))
            }
            if (root.has("parent")) {
                val parent = root.get("parent").asString
                if (!parent.startsWith("builtin/")) {
                    val ref = ParsedRef.of(parent)
                    loadInto(model, ref.namespace, "models/${ref.path}.json", depth + 1)
                }
            }
        } catch (_: Exception) {
        }
        return true
    }

    private fun parseElements(model: BlockModel, elements: JsonArray) {
        for (el in elements) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val element = BlockModel.Element()
            val from = readVec3(obj, "from")
            val to = readVec3(obj, "to")
            if (from != null) element.from = from
            if (to != null) element.to = to
            if (obj.has("rotation") && obj.get("rotation").isJsonObject) {
                val rot = obj.getAsJsonObject("rotation")
                val rotation = BlockModel.Rotation()
                val origin = readVec3(rot, "origin")
                if (origin != null) rotation.origin = origin
                if (rot.has("axis")) rotation.axis = rot.get("axis").asString
                if (rot.has("angle")) rotation.angle = rot.get("angle").asDouble
                rotation.rescale = rot.has("rescale") && rot.get("rescale").asBoolean
                element.rotation = rotation
            }
            if (obj.has("faces") && obj.get("faces").isJsonObject) {
                for ((key, value) in obj.getAsJsonObject("faces").entrySet()) {
                    if (!value.isJsonObject) continue
                    val faceObj = value.asJsonObject
                    val face = BlockModel.Face()
                    if (faceObj.has("texture")) {
                        face.texture = faceObj.get("texture").asString
                    }
                    if (faceObj.has("uv") && faceObj.get("uv").isJsonArray) {
                        val uv = faceObj.getAsJsonArray("uv")
                        if (uv.size() == 4) {
                            face.uv = doubleArrayOf(
                                uv[0].asDouble, uv[1].asDouble,
                                uv[2].asDouble, uv[3].asDouble
                            )
                        }
                    }
                    if (faceObj.has("rotation")) {
                        face.rotation = faceObj.get("rotation").asInt
                    }
                    if (faceObj.has("tintindex")) {
                        face.tintIndex = faceObj.get("tintindex").asInt
                    }
                    if (face.texture != null) {
                        element.faces[key] = face
                    }
                }
            }
            if (element.faces.isNotEmpty()) {
                model.elements.add(element)
            }
        }
    }

    private fun readVec3(obj: JsonObject, key: String): DoubleArray? {
        if (!obj.has(key) || !obj.get(key).isJsonArray) return null
        val arr = obj.getAsJsonArray(key)
        if (arr.size() != 3) return null
        return doubleArrayOf(arr[0].asDouble, arr[1].asDouble, arr[2].asDouble)
    }

    private fun modelFromBlockstate(namespace: String, path: String): String? {
        val json = readAsset(namespace, "blockstates/$path.json") ?: return null
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            if (root.has("variants")) {
                for ((_, value) in root.getAsJsonObject("variants").entrySet()) {
                    val model = modelOf(value) ?: continue
                    return model
                }
            }
            if (root.has("multipart")) {
                for (part in root.getAsJsonArray("multipart")) {
                    if (part.isJsonObject && part.asJsonObject.has("apply")) {
                        val model = modelOf(part.asJsonObject.get("apply")) ?: continue
                        return model
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun modelOf(value: JsonElement): String? {
        if (value.isJsonArray && value.asJsonArray.size() > 0) {
            return modelOf(value.asJsonArray.get(0))
        }
        if (value.isJsonObject && value.asJsonObject.has("model")) {
            return value.asJsonObject.get("model").asString
        }
        return null
    }

    private fun resolveFromModel(namespace: String, modelPath: String, depth: Int): String? {
        if (depth > 6) return null
        val json = readAsset(namespace, modelPath) ?: return null
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            if (root.has("textures")) {
                val textures = root.getAsJsonObject("textures")
                for (key in arrayOf("layer0", "all", "up", "side", "north", "texture", "0")) {
                    if (textures.has(key)) {
                        val tex = textures[key].asString
                        if (!tex.startsWith("#")) return tex
                    }
                }
                for ((_, value) in textures.entrySet()) {
                    val tex = value.asString
                    if (!tex.startsWith("#")) return tex
                }
            }
            if (root.has("parent")) {
                val parent = root.get("parent").asString
                if (!parent.startsWith("builtin/")) {
                    val ref = ParsedRef.of(parent)
                    return resolveFromModel(ref.namespace, "models/${ref.path}.json", depth + 1)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun hasTexture(namespace: String, assetPath: String): Boolean {
        if ("minecraft" == namespace) {
            return vanilla.isReady && vanilla.readTexture(assetPath) != null
        }
        return classpathBytes("assets/$namespace/$assetPath") != null
    }

    private fun readAsset(namespace: String, assetPath: String): String? {
        if ("minecraft" == namespace) {
            return vanilla.readAsset(assetPath)
        }
        val bytes = classpathBytes("assets/$namespace/$assetPath")
        return if (bytes != null) String(bytes, StandardCharsets.UTF_8) else null
    }

    companion object {
        @JvmStatic
        fun classpathBytes(resource: String): ByteArray? {
            val cl = ModelResolver::class.java.classLoader
            return try {
                cl.getResourceAsStream(resource)?.use { it.readAllBytes() }
            } catch (_: Exception) {
                null
            }
        }
    }

    data class ParsedRef(val namespace: String, val path: String) {
        companion object {
            fun of(raw: String): ParsedRef {
                val idx = raw.indexOf(':')
                return if (idx >= 0) {
                    ParsedRef(raw.substring(0, idx), raw.substring(idx + 1))
                } else {
                    ParsedRef("minecraft", raw)
                }
            }
        }
    }
}
