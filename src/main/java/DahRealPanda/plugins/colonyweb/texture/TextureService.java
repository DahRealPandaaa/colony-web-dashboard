package DahRealPanda.plugins.colonyweb.texture;

import DahRealPanda.plugins.colonyweb.util.Text;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converts an {@code itemKey} into PNG icon bytes, resolving item/block models, DO materials,
 * modded jar textures and cached vanilla assets, with an in-memory + disk cache and a
 * generated placeholder fallback.
 *
 * <p>Domum Ornamentum blocks get a real 3D icon: the block's own model geometry is rendered
 * isometrically with each material component's texture substituted in, so a "Brick Extra
 * Shingle" looks like a shingle made of brick rather than a flat brick square.</p>
 */
public final class TextureService {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Edge length of generated 3D Domum Ornamentum icons. */
    private static final int ICON_SIZE = 64;

    /** Texture-variable / component-id words that carry no matching signal. */
    private static final Set<String> GENERIC_WORDS = Set.of(
            "domum", "ornamentum", "minecraft", "material", "materials", "block", "blocks",
            "texture", "textures", "the", "all", "any", "default", "main");

    private final VanillaAssetProvider vanilla;
    private final ModelResolver modelResolver;
    private final PngCache cache;

    public TextureService(Path dataDir, VanillaAssetProvider vanilla) {
        this.vanilla = vanilla;
        this.modelResolver = new ModelResolver(vanilla);
        this.cache = new PngCache(dataDir);
    }

    /** Get PNG bytes for a texture key (never null; returns a placeholder on miss). */
    public byte[] getPng(String itemKey) {
        byte[] cached = cache.get(itemKey);
        if (cached != null) {
            return cached;
        }
        byte[] png = resolve(itemKey);
        if (png == null) {
            png = placeholder();
        }
        cache.put(itemKey, png);
        return png;
    }

    private byte[] resolve(String itemKey) {
        String base = itemKey;
        int hashIdx = itemKey.indexOf('#');
        if (hashIdx >= 0) {
            base = itemKey.substring(0, hashIdx);
        }

        // Domum Ornamentum: render the block's real geometry in its real materials.
        if (DomumOrnamentumResolver.isDomumKey(itemKey)) {
            byte[] rendered = renderDomum(itemKey, base);
            if (rendered != null) {
                return rendered;
            }
        }

        // Fall back to a flat swatch of the primary material.
        Optional<String> material = DomumOrnamentumResolver.materialForKey(itemKey);
        if (material.isPresent()) {
            byte[] mat = pngForRegistryName(material.get());
            if (mat != null) {
                return mat;
            }
        }

        return pngForRegistryName(base);
    }

    // ------------------------------------------------------------------
    // Domum Ornamentum 3D icons
    // ------------------------------------------------------------------

    /**
     * Render a DO variant isometrically. Returns null (so the caller falls back to a flat
     * texture) when the model has no parseable geometry or no material resolves.
     */
    private byte[] renderDomum(String itemKey, String baseRegistryName) {
        try {
            Map<String, String> components = DomumOrnamentumResolver.componentsForKey(itemKey);
            if (components.isEmpty()) {
                return null;
            }
            ResourceLocation rl = ResourceLocation.tryParse(baseRegistryName);
            if (rl == null) {
                return null;
            }
            Optional<BlockModel> model = modelResolver.resolveModel(rl.getNamespace(), rl.getPath());
            if (model.isEmpty()) {
                return null;
            }
            Map<String, BufferedImage> faces = assignMaterials(model.get(), components);
            if (faces.isEmpty()) {
                return null;
            }
            BufferedImage icon = IsometricRenderer.render(model.get(), faces::get, ICON_SIZE);
            return icon != null ? encode(icon) : null;
        } catch (Throwable t) {
            LOGGER.debug("[ColonyWeb] 3D render failed for {}", itemKey, t);
            return null;
        }
    }

    /**
     * Decide which material texture each of the model's texture variables should use.
     *
     * <p>DO does the same substitution at render time, but its component-to-variable wiring
     * is not described in the model JSON. Variables are therefore matched to components by
     * name first, then positionally in the block's declared component order — so the shape is
     * always right, and multi-material blocks land their materials in a stable order.</p>
     */
    private Map<String, BufferedImage> assignMaterials(BlockModel model, Map<String, String> components) {
        List<String> refs = model.usedTextureRefs();
        if (refs.isEmpty()) {
            return Map.of();
        }
        List<String> componentIds = new ArrayList<>(components.keySet());
        List<BufferedImage> componentImages = new ArrayList<>();
        for (String id : componentIds) {
            componentImages.add(imageForRegistryName(components.get(id)));
        }

        Map<String, BufferedImage> assigned = new LinkedHashMap<>();
        boolean[] taken = new boolean[componentIds.size()];

        // 1. Match by name — a "#shingle" variable should take the shingle component.
        for (String ref : refs) {
            String varName = ref.startsWith("#") ? ref.substring(1) : Text.pathOf(ref);
            for (int i = 0; i < componentIds.size(); i++) {
                if (!taken[i] && sharesWord(varName, componentIds.get(i))) {
                    assigned.put(ref, componentImages.get(i));
                    taken[i] = true;
                    break;
                }
            }
        }

        // 2. Fill the rest positionally, reusing the first material once components run out.
        int next = 0;
        BufferedImage fallback = componentImages.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        for (String ref : refs) {
            if (assigned.containsKey(ref)) {
                continue;
            }
            while (next < taken.length && taken[next]) {
                next++;
            }
            if (next < taken.length) {
                assigned.put(ref, componentImages.get(next));
                taken[next] = true;
            } else {
                assigned.put(ref, fallback);
            }
        }

        // 3. Anything still without pixels keeps the model's own declared texture.
        boolean any = false;
        for (String ref : refs) {
            BufferedImage image = assigned.get(ref);
            if (image == null) {
                image = imageForTextureRef(model.resolveTextureRef(ref));
                assigned.put(ref, image);
            }
            any |= image != null;
        }
        return any ? assigned : Map.of();
    }

    /** True when two identifiers share a meaningful word (case/separator insensitive). */
    private static boolean sharesWord(String a, String b) {
        Set<String> wordsA = words(a);
        wordsA.retainAll(words(b));
        return !wordsA.isEmpty();
    }

    private static Set<String> words(String raw) {
        Set<String> out = new HashSet<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (part.length() >= 3 && !GENERIC_WORDS.contains(part)) {
                out.add(part);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Texture loading
    // ------------------------------------------------------------------

    private final Map<String, Optional<BufferedImage>> imageCache = new HashMap<>();

    /** Decoded texture for a block/item registry name, memoized per service instance. */
    private synchronized BufferedImage imageForRegistryName(String registryName) {
        return imageCache.computeIfAbsent("r:" + registryName, key -> {
            byte[] png = pngForRegistryName(registryName);
            return Optional.ofNullable(decode(png));
        }).orElse(null);
    }

    /** Decoded texture for a direct {@code namespace:path} texture reference. */
    private synchronized BufferedImage imageForTextureRef(String textureRef) {
        if (textureRef == null) {
            return null;
        }
        return imageCache.computeIfAbsent("t:" + textureRef, key -> {
            byte[] png = loadTexturePng(textureRef);
            return Optional.ofNullable(decode(png));
        }).orElse(null);
    }

    private byte[] pngForRegistryName(String registryName) {
        ResourceLocation rl = ResourceLocation.tryParse(registryName);
        if (rl == null) {
            return null;
        }
        Optional<String> texture = modelResolver.resolveTexture(rl.getNamespace(), rl.getPath());
        if (texture.isEmpty()) {
            return null;
        }
        return loadTexturePng(texture.get());
    }

    private byte[] loadTexturePng(String textureRef) {
        ModelResolver.ParsedRef ref = ModelResolver.ParsedRef.of(textureRef);
        String assetPath = "textures/" + ref.path() + ".png";
        byte[] bytes;
        if ("minecraft".equals(ref.namespace())) {
            bytes = vanilla.readTexture(assetPath);
        } else {
            bytes = ModelResolver.classpathBytes("assets/" + ref.namespace() + "/" + assetPath);
        }
        if (bytes == null) {
            return null;
        }
        return firstFrame(bytes);
    }

    /** Some textures are animated (tall strips); crop to the top square frame. */
    private byte[] firstFrame(byte[] pngBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (img == null) {
                return pngBytes;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (h > w && w > 0 && h % w == 0) {
                byte[] frame = encode(img.getSubimage(0, 0, w, w));
                return frame != null ? frame : pngBytes;
            }
            return pngBytes;
        } catch (IOException e) {
            return pngBytes;
        }
    }

    private static BufferedImage decode(byte[] png) {
        if (png == null) {
            return null;
        }
        try {
            BufferedImage read = ImageIO.read(new ByteArrayInputStream(png));
            if (read == null) {
                return null;
            }
            // Normalize to ARGB so alpha handling in the renderer is uniform.
            BufferedImage argb = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB);
            argb.getGraphics().drawImage(read, 0, 0, null);
            return argb;
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] encode(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /** Magenta/black checker placeholder so the UI still lays out. */
    private byte[] placeholder() {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int magenta = 0xFFF800F8;
        int black = 0xFF000000;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean a = (x / 8 + y / 8) % 2 == 0;
                img.setRGB(x, y, a ? magenta : black);
            }
        }
        byte[] png = encode(img);
        return png != null ? png : new byte[0];
    }
}
