package DahRealPanda.plugins.colonyweb.model

/**
 * One Domum Ornamentum material slot of a textured block, e.g. `Supported by: Oak Planks`.
 *
 * DO blocks are built from several material components (the visible face, the support
 * block, trims, ...). Surfacing them individually lets the UI reproduce the in-game tooltip
 * instead of collapsing everything into a single "material" string.
 */
data class MaterialComponent(
    @JvmField var id: String = "",        // DO component id, e.g. "domum_ornamentum:shingle_face"
    @JvmField var label: String = "",     // "Main Material", "Supported by", ...
    @JvmField var material: String = "",  // human readable block name, e.g. "Brick Extra"
    @JvmField var itemKey: String = ""    // texture key of the material block, for an icon
)
