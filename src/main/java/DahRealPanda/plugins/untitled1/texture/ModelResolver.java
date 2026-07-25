package DahRealPanda.plugins.untitled1.texture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Reads item/block model JSON from the classpath (modded jars) or the cached vanilla client
 * jar, and resolves a representative texture path for an item.
 */
public final class ModelResolver {
    private final VanillaAssetProvider vanilla;

    public ModelResolver(VanillaAssetProvider vanilla) {
        this.vanilla = vanilla;
    }

    /**
     * Resolve a texture reference (e.g. {@code minecraft:block/oak_planks}) for an item
     * registry name, following the model {@code parent} chain a few levels deep.
     */
    public Optional<String> resolveTexture(String namespace, String path) {
        return resolveFromModel(namespace, "models/item/" + path + ".json", 0)
                .or(() -> {
                    // Fallback: assume a direct item/block texture with the same path.
                    if (hasTexture(namespace, "textures/item/" + path + ".png")) {
                        return Optional.of(namespace + ":item/" + path);
                    }
                    if (hasTexture(namespace, "textures/block/" + path + ".png")) {
                        return Optional.of(namespace + ":block/" + path);
                    }
                    return Optional.empty();
                });
    }

    private Optional<String> resolveFromModel(String namespace, String modelPath, int depth) {
        if (depth > 6) {
            return Optional.empty();
        }
        String json = readAsset(namespace, modelPath);
        if (json == null) {
            return Optional.empty();
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("textures")) {
                JsonObject textures = root.getAsJsonObject("textures");
                for (String key : new String[]{"layer0", "all", "up", "side", "north", "texture", "0"}) {
                    if (textures.has(key)) {
                        String tex = textures.get(key).getAsString();
                        if (!tex.startsWith("#")) {
                            return Optional.of(tex);
                        }
                    }
                }
                // Otherwise take the first concrete (non-reference) texture value.
                for (var entry : textures.entrySet()) {
                    String tex = entry.getValue().getAsString();
                    if (!tex.startsWith("#")) {
                        return Optional.of(tex);
                    }
                }
            }
            if (root.has("parent")) {
                String parent = root.get("parent").getAsString();
                if (!parent.startsWith("builtin/")) {
                    ParsedRef ref = ParsedRef.of(parent);
                    return resolveFromModel(ref.namespace, "models/" + ref.path + ".json", depth + 1);
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Optional.empty();
    }

    private boolean hasTexture(String namespace, String assetPath) {
        if ("minecraft".equals(namespace)) {
            return vanilla.isReady() && vanilla.readTexture(assetPath) != null;
        }
        return classpathBytes("assets/" + namespace + "/" + assetPath) != null;
    }

    private String readAsset(String namespace, String assetPath) {
        if ("minecraft".equals(namespace)) {
            return vanilla.readAsset(assetPath);
        }
        byte[] bytes = classpathBytes("assets/" + namespace + "/" + assetPath);
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
    }

    static byte[] classpathBytes(String resource) {
        ClassLoader cl = ModelResolver.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            return in != null ? in.readAllBytes() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** A namespaced reference like {@code minecraft:block/oak_planks}. */
    record ParsedRef(String namespace, String path) {
        static ParsedRef of(String raw) {
            int idx = raw.indexOf(':');
            if (idx >= 0) {
                return new ParsedRef(raw.substring(0, idx), raw.substring(idx + 1));
            }
            return new ParsedRef("minecraft", raw);
        }
    }
}
