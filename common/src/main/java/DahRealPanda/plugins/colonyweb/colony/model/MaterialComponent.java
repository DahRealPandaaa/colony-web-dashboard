package DahRealPanda.plugins.colonyweb.colony.model;

/**
 * One Domum Ornamentum material slot of a textured block, e.g. {@code Supported by: Oak Planks}.
 *
 * <p>DO blocks are built from several material components (the visible face, the support
 * block, trims, …). Surfacing them individually lets the UI reproduce the in-game tooltip
 * instead of collapsing everything into a single "material" string.</p>
 */
public class MaterialComponent {
    public String id;        // DO component id, e.g. "domum_ornamentum:shingle_face"
    public String label;     // "Main Material", "Supported by", …
    public String material;  // human readable block name, e.g. "Brick Extra"
    public String itemKey;   // texture key of the material block, for an icon

    public MaterialComponent() {
    }

    public MaterialComponent(String id, String label, String material, String itemKey) {
        this.id = id;
        this.label = label;
        this.material = material;
        this.itemKey = itemKey;
    }
}
