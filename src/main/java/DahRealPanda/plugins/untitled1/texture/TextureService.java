package DahRealPanda.plugins.untitled1.texture;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Converts an {@code itemKey} into PNG icon bytes, resolving item/block models, DO materials,
 * modded jar textures and cached vanilla assets, with an in-memory + disk cache and a
 * generated placeholder fallback.
 */
public final class TextureService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final VanillaAssetProvider vanilla;
    private final ModelResolver modelResolver;
    private final PngCache cache;

    public TextureService(String minecraftVersion, Path baseDir, VanillaAssetProvider vanilla) {
        this.vanilla = vanilla;
        this.modelResolver = new ModelResolver(vanilla);
        this.cache = new PngCache(baseDir);
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

        // Domum Ornamentum: prefer the resolved material block's texture.
        Optional<String> material = DomumOrnamentumResolver.materialForKey(itemKey);
        if (material.isPresent()) {
            byte[] mat = pngForRegistryName(material.get());
            if (mat != null) {
                return mat;
            }
        }

        return pngForRegistryName(base);
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
            BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(pngBytes));
            if (img == null) {
                return pngBytes;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (h > w && w > 0 && h % w == 0) {
                BufferedImage frame = img.getSubimage(0, 0, w, w);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(frame, "png", out);
                return out.toByteArray();
            }
            return pngBytes;
        } catch (IOException e) {
            return pngBytes;
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
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
