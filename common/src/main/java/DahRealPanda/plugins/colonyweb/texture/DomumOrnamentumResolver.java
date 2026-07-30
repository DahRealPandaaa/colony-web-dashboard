package DahRealPanda.plugins.colonyweb.texture;

import DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect;
import DahRealPanda.plugins.colonyweb.colony.model.MaterialComponent;
import DahRealPanda.plugins.colonyweb.platform.Platform;
import DahRealPanda.plugins.colonyweb.util.Text;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Resolves Domum Ornamentum textured blocks to a stable texture key and to the individual
 * material components that make them up.
 *
 * <p>DO stores its material components in the stack's block-entity data at {@code BlockEntityTag →
 * textureData} (1.20.5 and later: the {@code block_entity_data} component), a compound mapping
 * each component id to a block registry name (e.g. {@code minecraft:...}). Because two DO stacks
 * with the same item id can look completely different, the texture key includes an 8-char hash of
 * that data so each variant caches to its own PNG. Where the data lives is the platform's problem
 * — see {@link DahRealPanda.plugins.colonyweb.platform.Platform#blockEntityData}.</p>
 *
 * <p>The per-variant component map recorded here is what lets {@link IsometricRenderer} draw
 * the block's real geometry with its real materials, instead of falling back to a flat swatch
 * of a single material.</p>
 */
public final class DomumOrnamentumResolver {
    public static final String DO_NAMESPACE = "domum_ornamentum";

    /** Java package every Domum Ornamentum class lives under, including its shape enums. */
    private static final String DO_PACKAGE = "com.ldtteam.domumornamentum";

    /** Block class to the getter handing back its shape enum, resolved once per class. */
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> VARIANT_GETTER = new ConcurrentHashMap<>();

    /** Maps a computed texture key (with #hash) to the underlying material block id. */
    private static final ConcurrentHashMap<String, String> VARIANT_MATERIAL = new ConcurrentHashMap<>();

    /** Maps a texture key to its ordered {@code componentId -> materialBlockId} map. */
    private static final ConcurrentHashMap<String, Map<String, String>> VARIANT_COMPONENTS = new ConcurrentHashMap<>();

    private DomumOrnamentumResolver() {
    }

    /** @return the material block id previously associated with a DO texture key, if any. */
    public static Optional<String> materialForKey(String textureKey) {
        return Optional.ofNullable(VARIANT_MATERIAL.get(textureKey));
    }

    /**
     * @return the ordered component-id → material-block-id map recorded for a DO texture key.
     *         Empty when the key was never seen or is not a DO variant.
     */
    public static Map<String, String> componentsForKey(String textureKey) {
        Map<String, String> map = VARIANT_COMPONENTS.get(textureKey);
        return map == null ? Map.of() : map;
    }

    /** @return true when the stack is a Domum Ornamentum item. */
    public static boolean isDomum(ItemStack stack) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return rl != null && DO_NAMESPACE.equals(rl.getNamespace());
    }

    /** @return true when a texture key belongs to the Domum Ornamentum namespace. */
    public static boolean isDomumKey(String textureKey) {
        return textureKey != null && textureKey.startsWith(DO_NAMESPACE + ":");
    }

    /**
     * Full texture key for a stack: {@code namespace:path} plus, when the stack carries material
     * data, a {@code #<8charHash>} suffix so distinct textured variants map to distinct PNGs.
     */
    public static String textureKeyFor(ItemStack stack) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String base = rl != null ? rl.toString() : "minecraft:air";
        if (!isDomum(stack)) {
            return base;
        }
        Map<String, String> components = componentMaterials(stack);
        String fingerprint = components.isEmpty()
                ? Platform.get().dataFingerprint(stack)
                : canonicalFingerprint(components);
        if (fingerprint == null) {
            return base;
        }
        String hash = hash8(fingerprint);
        if (hash == null) {
            return base;
        }
        String key = base + "#" + hash;
        if (!components.isEmpty()) {
            VARIANT_COMPONENTS.putIfAbsent(key, components);
            primaryMaterial(components).ifPresent(m -> VARIANT_MATERIAL.putIfAbsent(key, m));
        }
        return key;
    }

    /**
     * The materials of a DO stack, and nothing else, as a stable string.
     *
     * <p>Hashing the raw stack data instead — which is what this used to do — makes the key depend
     * on everything the stack happens to carry. Two stacks describing the same block with the same
     * materials then hash differently over an unrelated tag or an empty {@code textureData}
     * compound (which is exactly what MineColonies writes onto a cutter recipe's output), so the
     * same block cached twice and a taught recipe never matched the resource a builder asked for.
     * Sorting the pairs also makes the key identical across loaders, where the same data arrives as
     * NBT on 1.20.1 and as a data component on 1.21.</p>
     */
    private static String canonicalFingerprint(Map<String, String> components) {
        return new TreeMap<>(components).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(";"));
    }

    /**
     * The cut shape of a DO block, e.g. {@code "Fancy"} for a fancy framed light.
     *
     * <p>A whole family of DO blocks shares one item name — every framed light is "Framed
     * &lt;centre material&gt;" — and only the shape tells them apart, which is why DO gives it a
     * tooltip line of its own. Families are modelled one of two ways: a block per shape holding
     * its own enum, or one block with a {@code type} property that travels on the stack.</p>
     *
     * @param displayName the name already shown for the stack; a shape it spells out ("Beige
     *                    Bricks") is dropped rather than repeated back as "Type: Beige Bricks".
     */
    public static Optional<String> variantName(ItemStack stack, String displayName) {
        if (!isDomum(stack) || !(stack.getItem() instanceof BlockItem blockItem)) {
            return Optional.empty();
        }
        String variant = variantFromBlock(blockItem.getBlock())
                // 1.20.1 stores the property value as the enum constant ("PLAIN"), 1.21 as its
                // serialized name ("plain"); lower-casing first makes both humanize the same.
                .or(() -> Platform.get().blockStateProperty(stack, "type")
                        .map(value -> Text.humanize(value.toLowerCase(Locale.ROOT))))
                .orElse(null);
        if (variant == null || variant.isBlank()) {
            return Optional.empty();
        }
        if (displayName != null && displayName.toLowerCase(Locale.ROOT).contains(variant.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        return Optional.of(variant);
    }

    /** The shape enum a block instance carries, read through its own getter. */
    private static Optional<String> variantFromBlock(Block block) {
        Method getter = VARIANT_GETTER.computeIfAbsent(block.getClass(),
                DomumOrnamentumResolver::findVariantGetter).orElse(null);
        if (getter == null) {
            return Optional.empty();
        }
        try {
            if (!(getter.invoke(block) instanceof Enum<?> value)) {
                return Optional.empty();
            }
            // The shape enums carry the wording DO's own tooltip uses; the rest name themselves.
            Object langName = MineColoniesReflect.invoke(value, "getLangName").orElse(null);
            return Optional.of(langName instanceof String s && !s.isBlank() ? s : Text.humanize(value.name()));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /**
     * The getter is matched on its return type rather than its name: every family names it
     * differently ({@code getFramedLightType}, {@code getTimberFrameType}, {@code getType}), but
     * they all hand back an enum declared by Domum Ornamentum.
     */
    private static Optional<Method> findVariantGetter(Class<?> blockClass) {
        Method found = null;
        for (Method method : blockClass.getMethods()) {
            if (method.getParameterCount() != 0
                    || Modifier.isStatic(method.getModifiers())
                    || !method.getReturnType().isEnum()
                    || !method.getReturnType().getName().startsWith(DO_PACKAGE)) {
                continue;
            }
            // getMethods() has no defined order, so a block exposing more than one would otherwise
            // report a different shape from run to run. Name order is arbitrary but stable.
            if (found == null || method.getName().compareTo(found.getName()) < 0) {
                found = method;
            }
        }
        return Optional.ofNullable(found);
    }

    /**
     * Resolve the material block whose texture best represents a DO stack. The support
     * component is skipped when another component exists — a shingle should read as its
     * shingle material, not as the planks holding it up.
     */
    public static Optional<String> resolveMaterialBlock(ItemStack stack) {
        return primaryMaterial(componentMaterials(stack));
    }

    /**
     * Human-readable material name for a DO stack, e.g. {@code "Beige Bricks"}. Combines all
     * distinct components (e.g. {@code "Brick Extra + Beige Bricks"}).
     */
    public static Optional<String> materialName(ItemStack stack) {
        List<String> names = new ArrayList<>();
        for (String id : componentMaterials(stack).values()) {
            String name = blockName(id);
            if (name != null && !names.contains(name)) {
                names.add(name);
            }
        }
        return names.isEmpty() ? Optional.empty() : Optional.of(String.join(" + ", names));
    }

    /** Role labels for a block's material slots, in the block's own component order. */
    private static final String[] MATERIAL_LABELS = {
            "Main Material", "Secondary Material", "Tertiary Material", "Quaternary Material"};

    /**
     * The material components of a DO stack as tooltip-ready lines, in the block's own
     * component order (e.g. {@code Supported by: Oak Planks}, {@code Main Material: Brick Extra}).
     *
     * <p>Labels are positional rather than derived from the component id. A component id is not
     * a role name — framed blocks key their slots by the <em>default</em> block, so a slot filled
     * with Roan Bricks was being labelled "Dark Oak Planks", which reads as a second material
     * rather than as the slot it is.</p>
     */
    public static List<MaterialComponent> componentsOf(ItemStack stack) {
        List<MaterialComponent> out = new ArrayList<>();
        int slot = 0;
        for (Map.Entry<String, String> e : componentMaterials(stack).entrySet()) {
            String name = blockName(e.getValue());
            if (name == null) {
                continue;
            }
            String label = isSupport(e.getKey()) ? "Supported by" : materialLabel(slot++);
            out.add(new MaterialComponent(e.getKey(), label, name, e.getValue()));
        }
        return out;
    }

    /** "Main Material", "Secondary Material", … then a plain numbered fallback. */
    private static String materialLabel(int slot) {
        return slot < MATERIAL_LABELS.length ? MATERIAL_LABELS[slot] : "Material " + (slot + 1);
    }

    /**
     * Collect {@code componentId -> materialBlockId} for a DO stack, keeping the block's own
     * component order (the primary component first) and appending anything else found in NBT.
     */
    public static Map<String, String> componentMaterials(ItemStack stack) {
        Map<String, String> result = new LinkedHashMap<>();
        CompoundTag root = Platform.get().blockEntityData(stack).orElse(null);
        if (root == null) {
            return result;
        }
        CompoundTag textureData = findTextureData(root);
        if (textureData == null) {
            return result;
        }
        // Prefer the block's defined component order so the *primary* material is first
        // (e.g. the shingle material rather than the support material).
        for (String componentId : componentOrder(stack)) {
            if (textureData.contains(componentId, Tag.TAG_STRING)) {
                String value = textureData.getString(componentId);
                if (isRealBlock(value)) {
                    result.putIfAbsent(componentId, value);
                }
            }
        }
        // Add any remaining components (deterministic order) not already included.
        TreeMap<String, String> ordered = new TreeMap<>();
        for (String key : textureData.getAllKeys()) {
            Tag v = textureData.get(key);
            if (v != null && v.getId() == Tag.TAG_STRING && isRealBlock(v.getAsString())) {
                ordered.put(key, v.getAsString());
            }
        }
        ordered.forEach(result::putIfAbsent);
        return result;
    }

    /** The first non-support material, else the first material of any kind. */
    private static Optional<String> primaryMaterial(Map<String, String> components) {
        for (Map.Entry<String, String> e : components.entrySet()) {
            if (!isSupport(e.getKey())) {
                return Optional.of(e.getValue());
            }
        }
        return components.values().stream().findFirst();
    }

    private static boolean isSupport(String componentId) {
        return componentId != null && componentId.toLowerCase().contains("support");
    }

    private static String blockName(String blockId) {
        ResourceLocation rl = ResourceLocation.tryParse(blockId);
        if (rl == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(rl);
        return block != null ? block.getName().getString() : null;
    }

    /**
     * The DO block's material component ids in their defined order (via reflection into
     * {@code IMateriallyTexturedBlock#getComponents()}). The first is the primary component.
     */
    private static List<String> componentOrder(ItemStack stack) {
        List<String> ids = new ArrayList<>();
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return ids;
        }
        Block block = blockItem.getBlock();
        Object components = MineColoniesReflect.invoke(block, "getComponents").orElse(null);
        if (components instanceof Collection<?> collection) {
            for (Object component : collection) {
                Object id = MineColoniesReflect.invoke(component, "getId").orElse(null);
                if (id != null) {
                    ids.add(String.valueOf(id));
                }
            }
        }
        return ids;
    }

    /** Navigate to the {@code textureData} compound (via BlockEntityTag), else search for it. */
    private static CompoundTag findTextureData(CompoundTag root) {
        if (root == null) {
            return null;
        }
        // Canonical DO/vanilla layout: BlockEntityTag -> textureData.
        if (root.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            CompoundTag be = root.getCompound("BlockEntityTag");
            if (be.contains("textureData", Tag.TAG_COMPOUND)) {
                return be.getCompound("textureData");
            }
        }
        if (root.contains("textureData", Tag.TAG_COMPOUND)) {
            return root.getCompound("textureData");
        }
        // Fallback: recursively find a compound whose values look like block ids.
        for (String key : root.getAllKeys()) {
            Tag child = root.get(key);
            if (child instanceof CompoundTag childCompound) {
                if (looksLikeTextureData(childCompound)) {
                    return childCompound;
                }
                CompoundTag nested = findTextureData(childCompound);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static boolean looksLikeTextureData(CompoundTag tag) {
        if (tag.getAllKeys().isEmpty()) {
            return false;
        }
        for (String key : tag.getAllKeys()) {
            Tag v = tag.get(key);
            if (v == null || v.getId() != Tag.TAG_STRING || !isRealBlock(v.getAsString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRealBlock(String value) {
        int idx = value.indexOf(':');
        if (idx <= 0 || idx == value.length() - 1) {
            return false;
        }
        ResourceLocation rl = ResourceLocation.tryParse(value);
        return rl != null && BuiltInRegistries.BLOCK.containsKey(rl);
    }

    private static String hash8(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
