package DahRealPanda.plugins.untitled1.colony.model;

/**
 * A reference to an item/block used as a texture key.
 *
 * <p>The {@code itemKey} is the registry name ({@code namespace:path}) optionally suffixed
 * with {@code #<8charHash>} when NBT is relevant (e.g. Domum Ornamentum textured blocks) so
 * that distinct variants map to distinct texture PNGs.</p>
 */
public class ItemRef {
    public String registryName;
    public String nbtHash; // optional, null when not NBT-relevant

    public ItemRef() {
    }

    public ItemRef(String registryName, String nbtHash) {
        this.registryName = registryName;
        this.nbtHash = nbtHash;
    }

    /** Full texture key: {@code namespace:path} plus {@code #hash} when present. */
    public String key() {
        if (nbtHash == null || nbtHash.isEmpty()) {
            return registryName;
        }
        return registryName + "#" + nbtHash;
    }
}
