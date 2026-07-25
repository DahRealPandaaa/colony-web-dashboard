package DahRealPanda.plugins.untitled1.texture;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves Domum Ornamentum textured blocks to a stable texture key and, where possible, to
 * the underlying material block whose texture should be shown as the icon.
 *
 * <p>Domum blocks store their material components in stack NBT. Because two DO stacks with the
 * same item id can look completely different, the texture key includes an 8-char NBT hash so
 * each variant caches to its own PNG.</p>
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

    /**
     * Full texture key for a stack: {@code namespace:path} plus, when NBT is relevant, a
     * {@code #<8charHash>} suffix so distinct textured variants map to distinct PNGs.
     */
    public static String textureKeyFor(ItemStack stack) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String base = rl != null ? rl.toString() : "minecraft:air";
        if (rl != null && DO_NAMESPACE.equals(rl.getNamespace()) && stack.hasTag()) {
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
     * Try to resolve the primary material block registry name from a DO stack's NBT so the
     * material's texture can be used as the icon. Returns empty when nothing resolves.
     */
    public static Optional<String> resolveMaterialBlock(ItemStack stack) {
        if (!stack.hasTag()) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        return findBlockId(tag);
    }

    private static Optional<String> findBlockId(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            // Common DO layout: a "textureData" compound mapping component -> block id string.
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (child == null) {
                    continue;
                }
                if (child.getId() == Tag.TAG_STRING) {
                    String value = child.getAsString();
                    if (looksLikeBlockId(value)) {
                        return Optional.of(value);
                    }
                }
                Optional<String> nested = findBlockId(child);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean looksLikeBlockId(String value) {
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
