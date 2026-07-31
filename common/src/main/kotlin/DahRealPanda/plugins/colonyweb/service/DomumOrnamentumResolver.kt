package DahRealPanda.plugins.colonyweb.service
import java.util.Optional

import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect
import DahRealPanda.plugins.colonyweb.util.Text
import DahRealPanda.plugins.colonyweb.model.MaterialComponent
import DahRealPanda.plugins.colonyweb.platform.Platform
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

object DomumOrnamentumResolver {
    const val DO_NAMESPACE = "domum_ornamentum"

    private const val DO_PACKAGE = "com.ldtteam.domumornamentum"

    private val VARIANT_GETTER = ConcurrentHashMap<Class<*>, Optional<Method>>()

    private val VARIANT_MATERIAL = ConcurrentHashMap<String, String>()

    private val VARIANT_COMPONENTS = ConcurrentHashMap<String, Map<String, String>>()

    @JvmStatic
    fun materialForKey(textureKey: String): String? = VARIANT_MATERIAL[textureKey]

    @JvmStatic
    fun componentsForKey(textureKey: String): Map<String, String> =
        VARIANT_COMPONENTS[textureKey] ?: emptyMap()

    @JvmStatic
    fun isDomum(stack: ItemStack): Boolean {
        val rl = BuiltInRegistries.ITEM.getKey(stack.item)
        return rl != null && DO_NAMESPACE == rl.namespace
    }

    @JvmStatic
    fun isDomumKey(textureKey: String?): Boolean =
        textureKey != null && textureKey.startsWith("$DO_NAMESPACE:")

    @JvmStatic
    fun textureKeyFor(stack: ItemStack): String {
        val rl = BuiltInRegistries.ITEM.getKey(stack.item)
        val base = if (rl != null) rl.toString() else "minecraft:air"
        if (!isDomum(stack)) return base
        val components = componentMaterials(stack)
        val fingerprint = (if (components.isEmpty()) Platform.get().dataFingerprint(stack)
                           else canonicalFingerprint(components)) ?: return base
        val hash = hash8(fingerprint) ?: return base
        val key = "$base#$hash"
        if (components.isNotEmpty()) {
            VARIANT_COMPONENTS.putIfAbsent(key, components)
            primaryMaterial(components)?.let { VARIANT_MATERIAL.putIfAbsent(key, it) }
        }
        return key
    }

    /**
     * The materials of a DO stack and nothing else, so an unrelated tag or the empty textureData
     * compound MineColonies writes onto a cutter recipe's output cannot split one block into two
     * keys. Sorting keeps the key identical on both loaders.
     */
    internal fun canonicalFingerprint(components: Map<String, String>): String =
        TreeMap(components).entries.joinToString(";") { "${it.key}=${it.value}" }

    @JvmStatic
    fun variantName(stack: ItemStack, displayName: String?): String? {
        val item = stack.item
        if (!isDomum(stack) || item !is BlockItem) return null
        val variant = variantFromBlock(item.block)
            ?: Platform.get().blockStateProperty(stack, "type")
                ?.let { Text.humanize(it.lowercase(Locale.ROOT)) }
        if (variant.isNullOrBlank()) return null
        if (displayName != null && displayName.lowercase(Locale.ROOT).contains(variant.lowercase(Locale.ROOT))) {
            return null
        }
        return variant
    }

    private fun variantFromBlock(block: Block): String? {
        val getter = VARIANT_GETTER
            .computeIfAbsent(block.javaClass) { findVariantGetter(it) }
            .orElse(null) ?: return null
        return try {
            val value = getter.invoke(block) as? Enum<*> ?: return null
            val langName = MineColoniesReflect.invoke(value, "getLangName").orElse(null)
            if (langName is String && langName.isNotBlank()) langName else Text.humanize(value.name)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Matched on return type, not name: every family names the getter differently but they all
     * hand back a DO enum. Name order breaks ties so a block exposing two stays stable.
     */
    private fun findVariantGetter(blockClass: Class<*>): Optional<Method> {
        var found: Method? = null
        for (method in blockClass.methods) {
            if (method.parameterCount != 0
                || Modifier.isStatic(method.modifiers)
                || !method.returnType.isEnum
                || !method.returnType.name.startsWith(DO_PACKAGE)) {
                continue
            }
            if (found == null || method.name < found.name) {
                found = method
            }
        }
        return Optional.ofNullable(found)
    }

    @JvmStatic
    fun resolveMaterialBlock(stack: ItemStack): String? =
        primaryMaterial(componentMaterials(stack))

    @JvmStatic
    fun materialName(stack: ItemStack): String? {
        val names = mutableListOf<String>()
        for (id in componentMaterials(stack).values) {
            val name = blockName(id)
            if (name != null && name !in names) {
                names.add(name)
            }
        }
        return if (names.isEmpty()) null else names.joinToString(" + ")
    }

    private val MATERIAL_LABELS = arrayOf(
        "Main Material", "Secondary Material", "Tertiary Material", "Quaternary Material"
    )

    @JvmStatic
    fun componentsOf(stack: ItemStack): List<MaterialComponent> {
        val out = mutableListOf<MaterialComponent>()
        var slot = 0
        for ((key, value) in componentMaterials(stack)) {
            val name = blockName(value) ?: continue
            val label = if (isSupport(key)) "Supported by" else materialLabel(slot++)
            out.add(MaterialComponent(key, label, name, value))
        }
        return out
    }

    private fun materialLabel(slot: Int): String =
        if (slot < MATERIAL_LABELS.size) MATERIAL_LABELS[slot] else "Material ${slot + 1}"

    @JvmStatic
    fun componentMaterials(stack: ItemStack): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val root = Platform.get().blockEntityData(stack) ?: return result
        val textureData = findTextureData(root) ?: return result
        for (componentId in componentOrder(stack)) {
            if (textureData.contains(componentId, Tag.TAG_STRING.toInt())) {
                val value = textureData.getString(componentId)
                if (isRealBlock(value)) {
                    result.putIfAbsent(componentId, value)
                }
            }
        }
        val ordered = TreeMap<String, String>()
        for (key in textureData.allKeys) {
            val v = textureData[key]
            if (v != null && v.id == Tag.TAG_STRING && isRealBlock(v.asString)) {
                ordered[key] = v.asString
            }
        }
        ordered.forEach { (k, v) -> result.putIfAbsent(k, v) }
        return result
    }

    private fun primaryMaterial(components: Map<String, String>): String? {
        for ((key, value) in components) {
            if (!isSupport(key)) return value
        }
        return components.values.firstOrNull()
    }

    private fun isSupport(componentId: String?): Boolean =
        componentId != null && componentId.lowercase().contains("support")

    private fun blockName(blockId: String): String? {
        val rl = ResourceLocation.tryParse(blockId) ?: return null
        val block = BuiltInRegistries.BLOCK.get(rl)
        return block?.name?.string
    }

    private fun componentOrder(stack: ItemStack): List<String> {
        val ids = mutableListOf<String>()
        val item = stack.item
        if (item !is BlockItem) return ids
        val block = item.block
        val components = MineColoniesReflect.invoke(block, "getComponents").orElse(null)
        if (components is Collection<*>) {
            for (component in components) {
                val id = MineColoniesReflect.invoke(component, "getId").orElse(null)
                if (id != null) ids.add(id.toString())
            }
        }
        return ids
    }

    private fun findTextureData(root: CompoundTag): CompoundTag? {
        if (root.contains("BlockEntityTag", Tag.TAG_COMPOUND.toInt())) {
            val be = root.getCompound("BlockEntityTag")
            if (be.contains("textureData", Tag.TAG_COMPOUND.toInt())) {
                return be.getCompound("textureData")
            }
        }
        if (root.contains("textureData", Tag.TAG_COMPOUND.toInt())) {
            return root.getCompound("textureData")
        }
        for (key in root.allKeys) {
            val child = root[key]
            if (child is CompoundTag) {
                if (looksLikeTextureData(child)) return child
                val nested = findTextureData(child)
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun looksLikeTextureData(tag: CompoundTag): Boolean {
        val keys = tag.allKeys
        if (keys.isEmpty()) return false
        for (key in keys) {
            val v = tag[key]
            if (v == null || v.id != Tag.TAG_STRING || !isRealBlock(v.asString)) return false
        }
        return true
    }

    private fun isRealBlock(value: String): Boolean {
        val idx = value.indexOf(':')
        if (idx <= 0 || idx == value.length - 1) return false
        val rl = ResourceLocation.tryParse(value) ?: return false
        return BuiltInRegistries.BLOCK.containsKey(rl)
    }

    private fun hash8(input: String): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
            val sb = StringBuilder()
            for (i in 0 until 4) {
                sb.append(String.format("%02x", digest[i]))
            }
            sb.toString()
        } catch (_: Exception) {
            null
        }
    }
}
