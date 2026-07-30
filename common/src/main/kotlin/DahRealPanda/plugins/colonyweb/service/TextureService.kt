package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.BlockModel
import DahRealPanda.plugins.colonyweb.model.BuiltinEntityModels
import DahRealPanda.plugins.colonyweb.provider.VanillaAssetProvider
import DahRealPanda.plugins.colonyweb.renderer.IsometricRenderer
import DahRealPanda.plugins.colonyweb.repository.PngCache
import DahRealPanda.plugins.colonyweb.util.Text
import com.mojang.logging.LogUtils
import net.minecraft.resources.ResourceLocation
import org.slf4j.Logger
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO

class TextureService(dataDir: Path, private val vanilla: VanillaAssetProvider) {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()

        private const val ICON_SIZE = 64

        private val GENERIC_WORDS = setOf(
            "domum", "ornamentum", "minecraft", "material", "materials", "block", "blocks",
            "texture", "textures", "the", "all", "any", "default", "main"
        )
    }

    private val modelResolver = ModelResolver(vanilla)
    private val cache = PngCache(dataDir)

    fun getPng(itemKey: String): ByteArray {
        var png = cache.get(itemKey)
        if (png != null) return png
        png = resolve(itemKey)
        if (png == null) {
            png = placeholder()
        }
        cache.put(itemKey, png)
        return png
    }

    private fun resolve(itemKey: String): ByteArray? {
        val base: String
        val hashIdx = itemKey.indexOf('#')
        if (hashIdx >= 0) {
            base = itemKey.substring(0, hashIdx)
        } else {
            base = itemKey
        }

        if (DomumOrnamentumResolver.isDomumKey(itemKey)) {
            val rendered = renderDomum(itemKey, base)
            if (rendered != null) return rendered
        }

        val material = DomumOrnamentumResolver.materialForKey(itemKey)
        if (material != null) {
            val mat = pngForRegistryName(material)
            if (mat != null) return mat
        }

        val rendered = renderInventoryIcon(base)
        if (rendered != null) return rendered

        return pngForRegistryName(base)
    }

    private fun renderInventoryIcon(registryName: String): ByteArray? {
        return try {
            val rl = ResourceLocation.tryParse(registryName) ?: return null
            val resolved = BuiltinEntityModels.forItem(rl.namespace, rl.path)
                ?: modelResolver.resolveInventoryModel(rl.namespace, rl.path)
                ?: return null
            val model = resolved
            val icon = IsometricRenderer.render(
                model, { ref -> imageForTextureRef(model.resolveTextureRef(ref)) }, ICON_SIZE
            )
            if (icon != null) encode(icon) else null
        } catch (t: Throwable) {
            LOGGER.debug("[ColonyWeb] inventory render failed for {}", registryName, t)
            null
        }
    }

    private fun renderDomum(itemKey: String, baseRegistryName: String): ByteArray? {
        return try {
            val components = DomumOrnamentumResolver.componentsForKey(itemKey)
            if (components.isEmpty()) return null
            val rl = ResourceLocation.tryParse(baseRegistryName) ?: return null
            val model = modelResolver.resolveModel(rl.namespace, rl.path) ?: return null
            val faces = assignMaterials(model, components)
            if (faces.isEmpty()) return null
            val icon = IsometricRenderer.render(model, { faces[it] }, ICON_SIZE)
            if (icon != null) encode(icon) else null
        } catch (t: Throwable) {
            LOGGER.debug("[ColonyWeb] 3D render failed for {}", itemKey, t)
            null
        }
    }

    private fun assignMaterials(model: BlockModel, components: Map<String, String>): Map<String, BufferedImage> {
        val refs = model.usedTextureRefs()
        if (refs.isEmpty()) return emptyMap()
        val componentIds = ArrayList(components.keys)
        val componentImages = componentIds.map { imageForRegistryName(components[it]) }.toMutableList()

        val assigned = LinkedHashMap<String, BufferedImage?>()
        val taken = BooleanArray(componentIds.size)

        for (ref in refs) {
            val varName = if (ref.startsWith("#")) ref.substring(1) else Text.pathOf(ref)
            for (i in componentIds.indices) {
                if (!taken[i] && sharesWord(varName, componentIds[i])) {
                    assigned[ref] = componentImages[i]
                    taken[i] = true
                    break
                }
            }
        }

        var next = 0
        val fallback = componentImages.firstOrNull { it != null }
        for (ref in refs) {
            if (assigned.containsKey(ref)) continue
            while (next < taken.size && taken[next]) next++
            if (next < taken.size) {
                assigned[ref] = componentImages[next]
                taken[next] = true
            } else {
                assigned[ref] = fallback
            }
        }

        var any = false
        for (ref in refs) {
            var image = assigned[ref]
            if (image == null) {
                image = imageForTextureRef(model.resolveTextureRef(ref))
                assigned[ref] = image
            }
            any = any or (image != null)
        }
        @Suppress("UNCHECKED_CAST")
        return if (any) assigned.filterValues { it != null } as Map<String, BufferedImage> else emptyMap()
    }

    private fun sharesWord(a: String, b: String): Boolean {
        val wordsA = words(a)
        wordsA.retainAll(words(b))
        return wordsA.isNotEmpty()
    }

    private fun words(raw: String?): MutableSet<String> {
        val out = LinkedHashSet<String>()
        if (raw == null) return out
        for (part in raw.lowercase().split("[^a-z0-9]+".toRegex())) {
            if (part.length >= 3 && part !in GENERIC_WORDS) {
                out.add(part)
            }
        }
        return out
    }

    private val imageCache = HashMap<String, BufferedImage?>()

    @Synchronized
    private fun imageForRegistryName(registryName: String?): BufferedImage? {
        if (registryName == null) return null
        return imageCache.getOrPut("r:$registryName") {
            val png = pngForRegistryName(registryName)
            decode(png)
        }
    }

    @Synchronized
    private fun imageForTextureRef(textureRef: String?): BufferedImage? {
        if (textureRef == null) return null
        return imageCache.getOrPut("t:$textureRef") {
            val png = loadTexturePng(textureRef)
            decode(png)
        }
    }

    private fun pngForRegistryName(registryName: String?): ByteArray? {
        val rl = ResourceLocation.tryParse(registryName) ?: return null
        val texture = modelResolver.resolveTexture(rl.namespace, rl.path) ?: return null
        return loadTexturePng(texture)
    }

    private fun loadTexturePng(textureRef: String): ByteArray? {
        val ref = ModelResolver.ParsedRef.of(textureRef)
        val assetPath = "textures/${ref.path}.png"
        val bytes = if ("minecraft" == ref.namespace) {
            vanilla.readTexture(assetPath)
        } else {
            ModelResolver.classpathBytes("assets/${ref.namespace}/$assetPath")
        }
        if (bytes == null) return null
        return firstFrame(bytes)
    }

    private fun firstFrame(pngBytes: ByteArray): ByteArray {
        return try {
            val img = ImageIO.read(ByteArrayInputStream(pngBytes)) ?: return pngBytes
            val w = img.width
            val h = img.height
            if (h > w && w > 0 && h % w == 0) {
                val frame = encode(img.getSubimage(0, 0, w, w))
                frame ?: pngBytes
            } else {
                pngBytes
            }
        } catch (_: java.io.IOException) {
            pngBytes
        }
    }

    private fun decode(png: ByteArray?): BufferedImage? {
        if (png == null) return null
        return try {
            val read = ImageIO.read(ByteArrayInputStream(png)) ?: return null
            val argb = BufferedImage(read.width, read.height, BufferedImage.TYPE_INT_ARGB)
            argb.graphics.drawImage(read, 0, 0, null)
            argb
        } catch (_: java.io.IOException) {
            null
        }
    }

    private fun encode(image: BufferedImage): ByteArray? {
        return try {
            val out = ByteArrayOutputStream()
            ImageIO.write(image, "png", out)
            out.toByteArray()
        } catch (_: java.io.IOException) {
            null
        }
    }

    private fun placeholder(): ByteArray {
        val size = 16
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val magenta = 0xFFF800F8.toInt()
        val black = 0xFF000000.toInt()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val a = (x / 8 + y / 8) % 2 == 0
                img.setRGB(x, y, if (a) magenta else black)
            }
        }
        val png = encode(img)
        return png ?: ByteArray(0)
    }
}
