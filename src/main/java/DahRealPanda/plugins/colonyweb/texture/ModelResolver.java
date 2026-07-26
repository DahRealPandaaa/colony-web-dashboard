package DahRealPanda.plugins.colonyweb.texture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
                .or(() -> resolveFromModel(namespace, "models/block/" + path + ".json", 0))
                .or(() -> {
                    // Fallback: assume a direct texture with the same path. MineColonies uses
                    // the plural "blocks/" folder, vanilla uses singular "block/".
                    if (hasTexture(namespace, "textures/item/" + path + ".png")) {
                        return Optional.of(namespace + ":item/" + path);
                    }
                    if (hasTexture(namespace, "textures/block/" + path + ".png")) {
                        return Optional.of(namespace + ":block/" + path);
                    }
                    if (hasTexture(namespace, "textures/blocks/" + path + ".png")) {
                        return Optional.of(namespace + ":blocks/" + path);
                    }
                    return Optional.empty();
                });
    }

    /**
     * Resolve the geometry Minecraft draws in an inventory slot, when there is any.
     *
     * <p>The <em>item</em> model is authoritative here, exactly as it is in game: it decides
     * whether a slot shows a flat sprite or a 3D block. Wheat's model inherits
     * {@code item/generated} and carries no {@code elements}, so it stays a sprite; oak stairs'
     * model inherits {@code block/oak_stairs} and brings the cuboids along, so it is drawn as a
     * stair. Only when a namespace ships no item model at all — some mods build theirs at
     * runtime — do we fall back to {@link #resolveModel}'s block/blockstate search.</p>
     *
     * @return the model to rasterize, or empty when the item should keep its flat texture
     */
    public Optional<BlockModel> resolveInventoryModel(String namespace, String path) {
        BlockModel model = new BlockModel();
        if (loadInto(model, namespace, "models/item/" + path + ".json", 0)) {
            return model.elements.isEmpty() ? Optional.empty() : Optional.of(model);
        }
        return resolveModel(namespace, path);
    }

    /**
     * Resolve the full geometry of a block/item so it can be rendered in 3D.
     *
     * <p>Tries the item model, then the block model, then whatever the blockstate points at,
     * and returns the first that actually carries {@code elements}. Flat "generated" item
     * models produce no geometry and are skipped.</p>
     */
    public Optional<BlockModel> resolveModel(String namespace, String path) {
        for (String candidate : List.of("models/item/" + path + ".json", "models/block/" + path + ".json")) {
            BlockModel model = new BlockModel();
            loadInto(model, namespace, candidate, 0);
            if (!model.elements.isEmpty()) {
                return Optional.of(model);
            }
        }
        String fromState = modelFromBlockstate(namespace, path);
        if (fromState != null) {
            ParsedRef ref = ParsedRef.of(fromState);
            BlockModel model = new BlockModel();
            loadInto(model, ref.namespace(), "models/" + ref.path() + ".json", 0);
            if (!model.elements.isEmpty()) {
                return Optional.of(model);
            }
        }
        return Optional.empty();
    }

    /**
     * Merge a model and its ancestors into {@code model}. Called child-first, so
     * {@code putIfAbsent} gives the child precedence and the closest ancestor that defines
     * {@code elements} supplies the geometry.
     *
     * @return whether {@code modelPath} itself existed — an empty result then means "this model
     *         is deliberately flat", not "no such item", which are different answers
     */
    private boolean loadInto(BlockModel model, String namespace, String modelPath, int depth) {
        if (depth > 8) {
            return false;
        }
        String json = readAsset(namespace, modelPath);
        if (json == null) {
            return false;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("textures")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("textures").entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        model.textures.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
            if (model.elements.isEmpty() && root.has("elements")) {
                parseElements(model, root.getAsJsonArray("elements"));
            }
            if (root.has("parent")) {
                String parent = root.get("parent").getAsString();
                if (!parent.startsWith("builtin/")) {
                    ParsedRef ref = ParsedRef.of(parent);
                    loadInto(model, ref.namespace(), "models/" + ref.path() + ".json", depth + 1);
                }
            }
        } catch (Exception ignored) {
            // Malformed or loader-driven model — the caller falls back to a flat texture.
        }
        return true;
    }

    private void parseElements(BlockModel model, JsonArray elements) {
        for (JsonElement el : elements) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            BlockModel.Element element = new BlockModel.Element();
            double[] from = readVec3(obj, "from");
            double[] to = readVec3(obj, "to");
            if (from != null) {
                element.from = from;
            }
            if (to != null) {
                element.to = to;
            }
            if (obj.has("rotation") && obj.get("rotation").isJsonObject()) {
                JsonObject rot = obj.getAsJsonObject("rotation");
                BlockModel.Rotation rotation = new BlockModel.Rotation();
                double[] origin = readVec3(rot, "origin");
                if (origin != null) {
                    rotation.origin = origin;
                }
                if (rot.has("axis")) {
                    rotation.axis = rot.get("axis").getAsString();
                }
                if (rot.has("angle")) {
                    rotation.angle = rot.get("angle").getAsDouble();
                }
                rotation.rescale = rot.has("rescale") && rot.get("rescale").getAsBoolean();
                element.rotation = rotation;
            }
            if (obj.has("faces") && obj.get("faces").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("faces").entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject faceObj = entry.getValue().getAsJsonObject();
                    BlockModel.Face face = new BlockModel.Face();
                    if (faceObj.has("texture")) {
                        face.texture = faceObj.get("texture").getAsString();
                    }
                    if (faceObj.has("uv") && faceObj.get("uv").isJsonArray()) {
                        JsonArray uv = faceObj.getAsJsonArray("uv");
                        if (uv.size() == 4) {
                            face.uv = new double[]{uv.get(0).getAsDouble(), uv.get(1).getAsDouble(),
                                    uv.get(2).getAsDouble(), uv.get(3).getAsDouble()};
                        }
                    }
                    if (faceObj.has("rotation")) {
                        face.rotation = faceObj.get("rotation").getAsInt();
                    }
                    if (faceObj.has("tintindex")) {
                        face.tintIndex = faceObj.get("tintindex").getAsInt();
                    }
                    if (face.texture != null) {
                        element.faces.put(entry.getKey(), face);
                    }
                }
            }
            if (!element.faces.isEmpty()) {
                model.elements.add(element);
            }
        }
    }

    private static double[] readVec3(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) {
            return null;
        }
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr.size() != 3) {
            return null;
        }
        return new double[]{arr.get(0).getAsDouble(), arr.get(1).getAsDouble(), arr.get(2).getAsDouble()};
    }

    /** First model referenced by a blockstate file, for blocks whose model name differs. */
    private String modelFromBlockstate(String namespace, String path) {
        String json = readAsset(namespace, "blockstates/" + path + ".json");
        if (json == null) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("variants")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("variants").entrySet()) {
                    String model = modelOf(entry.getValue());
                    if (model != null) {
                        return model;
                    }
                }
            }
            if (root.has("multipart")) {
                for (JsonElement part : root.getAsJsonArray("multipart")) {
                    if (part.isJsonObject() && part.getAsJsonObject().has("apply")) {
                        String model = modelOf(part.getAsJsonObject().get("apply"));
                        if (model != null) {
                            return model;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private static String modelOf(JsonElement value) {
        if (value.isJsonArray() && !value.getAsJsonArray().isEmpty()) {
            return modelOf(value.getAsJsonArray().get(0));
        }
        if (value.isJsonObject() && value.getAsJsonObject().has("model")) {
            return value.getAsJsonObject().get("model").getAsString();
        }
        return null;
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
