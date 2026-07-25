package DahRealPanda.plugins.untitled1.texture;

import DahRealPanda.plugins.untitled1.colony.MineColoniesReflect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves Domum Ornamentum textured blocks to a stable texture key, the underlying material
 * block used for the icon, and a human-readable material name.
 *
 * <p>DO stores its material components in stack NBT at {@code BlockEntityTag → textureData},
 * a compound mapping each component id to a block registry name (e.g. {@code minecraft:...}).
 * Because two DO stacks with the same item id can look completely different, the texture key
 * includes an 8-char NBT hash so each variant caches to its own PNG.</p>
 */
public final class DomumOrnamentumResolver {
    public static final String DO_NAMESPACE = "domum_ornamentum";

    /** Maps a computed texture key (with #hash) to the underlying material block id. */
    private static final ConcurrentHashMap<String, String> VARIANT_MATERIAL = new ConcurrentHashMap<>();

    private DomumOrnamentumResolver() {
    }

    /** @return the material block id previously associated with a DO texture key, if any. */
    public static Optional<String> materialForKey(String textureKey) {
        return Optional.ofNullable(VARIANT_MATERIAL.get(textureKey));
    }

    /** @return true when the stack is a Domum Ornamentum item. */
    public static boolean isDomum(ItemStack stack) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return rl != null && DO_NAMESPACE.equals(rl.getNamespace());
    }

    /**
     * Full texture key for a stack: {@code namespace:path} plus, when NBT is relevant, a
     * {@code #<8charHash>} suffix so distinct textured variants map to distinct PNGs.
     */
    public static String textureKeyFor(ItemStack stack) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String base = rl != null ? rl.toString() : "minecraft:air";
        if (isDomum(stack) && stack.hasTag()) {
            String hash = hash8(String.valueOf(stack.getTag()));
            if (hash != null) {
                String key = base + "#" + hash;
                resolveMaterialBlock(stack).ifPresent(material -> VARIANT_MATERIAL.putIfAbsent(key, material));
                return key;
            }
        }
        return base;
    }

    /**
     * Resolve the primary material block registry name from a DO stack's NBT so the material's
     * texture can be used as the icon. Returns empty when nothing resolves.
     */
    public static Optional<String> resolveMaterialBlock(ItemStack stack) {
        List<String> materials = materialBlockIds(stack);
        return materials.isEmpty() ? Optional.empty() : Optional.of(materials.get(0));
    }

    /**
     * Human-readable material name for a DO stack, e.g. {@code "Beige Bricks"}. Combines all
     * distinct components (e.g. {@code "Brick Extra + Beige Bricks"}).
     */
    public static Optional<String> materialName(ItemStack stack) {
        List<String> ids = materialBlockIds(stack);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                continue;
            }
            Block block = ForgeRegistries.BLOCKS.getValue(rl);
            if (block != null) {
                String name = block.getName().getString();
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names.isEmpty() ? Optional.empty() : Optional.of(String.join(" + ", names));
    }

    /** Collect the material block registry names from a DO stack (primary component first). */
    private static List<String> materialBlockIds(ItemStack stack) {
        List<String> result = new ArrayList<>();
        if (!stack.hasTag()) {
            return result;
        }
        CompoundTag textureData = findTextureData(stack.getTag());
        if (textureData == null) {
            return result;
        }
        // Prefer the block's defined component order so the *primary* material is first
        // (e.g. the shingle material rather than the support material).
        for (String componentId : componentOrder(stack)) {
            if (textureData.contains(componentId, Tag.TAG_STRING)) {
                String value = textureData.getString(componentId);
                if (isRealBlock(value) && !result.contains(value)) {
                    result.add(value);
                }
            }
        }
        // Add any remaining components (deterministic order) not already included.
        TreeMap<String, String> ordered = new TreeMap<>();
        for (String key : textureData.getAllKeys()) {
            Tag v = textureData.get(key);
            if (v != null && v.getId() == Tag.TAG_STRING) {
                String value = v.getAsString();
                if (isRealBlock(value)) {
                    ordered.put(key, value);
                }
            }
        }
        for (String value : ordered.values()) {
            if (!result.contains(value)) {
                result.add(value);
            }
        }
        return result;
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
        return rl != null && ForgeRegistries.BLOCKS.containsKey(rl);
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
