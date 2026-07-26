package DahRealPanda.plugins.colonyweb.texture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A flattened Minecraft block/item model: the merged {@code textures} map of a model and its
 * parents, plus the cuboid {@code elements} that describe its geometry.
 *
 * <p>Only what {@link IsometricRenderer} needs is kept — this is not a general model loader.</p>
 */
public final class BlockModel {
    /** Texture variable name (without {@code #}) to a texture reference or another variable. */
    public final Map<String, String> textures = new LinkedHashMap<>();
    public final List<Element> elements = new ArrayList<>();

    /**
     * Resolve a texture reference down to a concrete {@code namespace:path}. Accepts either a
     * direct reference or a {@code #var} indirection chain.
     */
    public String resolveTextureRef(String ref) {
        String current = ref;
        for (int i = 0; i < 8; i++) {
            if (current == null) {
                return null;
            }
            if (!current.startsWith("#")) {
                return current;
            }
            current = textures.get(current.substring(1));
        }
        return null;
    }

    /**
     * Texture references used by actual geometry, in first-use order. This ordering is what
     * material components are matched against, so it must be deterministic.
     */
    public List<String> usedTextureRefs() {
        Set<String> vars = new LinkedHashSet<>();
        for (Element element : elements) {
            for (Face face : element.faces.values()) {
                if (face.texture != null) {
                    vars.add(face.texture);
                }
            }
        }
        return new ArrayList<>(vars);
    }

    /** A single cuboid of the model. */
    public static final class Element {
        public double[] from = {0, 0, 0};
        public double[] to = {16, 16, 16};
        public Rotation rotation;
        public final Map<String, Face> faces = new LinkedHashMap<>();
    }

    /** One textured face of a cuboid. */
    public static final class Face {
        /** Raw texture reference as written in the model: {@code "#var"} or {@code "ns:path"}. */
        public String texture;
        /** Explicit {@code [u1,v1,u2,v2]} in 0..16 texture space; null to derive from the box. */
        public double[] uv;
        /** Face texture rotation in degrees (0/90/180/270). */
        public int rotation;
    }

    /** Optional per-element rotation about one axis. */
    public static final class Rotation {
        public double[] origin = {8, 8, 8};
        public String axis = "y";
        public double angle;
        public boolean rescale;
    }
}
