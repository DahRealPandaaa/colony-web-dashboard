package DahRealPanda.plugins.colonyweb.texture;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Geometry for the items Minecraft draws with a block-entity renderer rather than a model.
 *
 * <p>Beds, chests, shulker boxes, banners, shields and conduits all ship an item model whose
 * parent is {@code builtin/entity}: it carries a {@code display} block and a {@code particle}
 * texture, but no {@code elements} at all, because in game the geometry lives in Java
 * ({@code ChestRenderer}, {@code ShulkerModel}, ...) and the texture under
 * {@code textures/entity/}. Loading those models therefore yields nothing to draw and the icon
 * falls back to the particle texture — which is why a bed used to show a flat square of wool, a
 * chest a square of oak planks, and a shield a square of dark oak.</p>
 *
 * <p>This class re-declares that Java-side geometry as ordinary {@link BlockModel} cuboids, so
 * {@link IsometricRenderer} can draw them like any other block. Box sizes and texture offsets
 * are transcribed from the vanilla {@code LayerDefinition}s, and {@link #entityBox} applies the
 * same UV unwrap the game's {@code CubeListBuilder} does.</p>
 */
public final class BuiltinEntityModels {
    /** Vanilla's {@code DyeColor.getTextureDiffuseColors()}, used to dye a banner's cloth. */
    private static final Map<String, Integer> DYE = Map.ofEntries(
            Map.entry("white", 0xF9FFFE), Map.entry("orange", 0xF9801D),
            Map.entry("magenta", 0xC74EBD), Map.entry("light_blue", 0x3AB3DA),
            Map.entry("yellow", 0xFED83D), Map.entry("lime", 0x80C71F),
            Map.entry("pink", 0xF38BAA), Map.entry("gray", 0x474F52),
            Map.entry("light_gray", 0x9D9D97), Map.entry("cyan", 0x169C9C),
            Map.entry("purple", 0x8932B8), Map.entry("blue", 0x3C44AA),
            Map.entry("brown", 0x835432), Map.entry("green", 0x5E7C16),
            Map.entry("red", 0xB02E26), Map.entry("black", 0x1D1D21));

    private BuiltinEntityModels() {
    }

    /**
     * The stand-in model for an item whose real geometry is drawn by a block-entity renderer.
     *
     * @return the geometry to rasterize, or empty for every other item
     */
    public static Optional<BlockModel> forItem(String namespace, String path) {
        if (!"minecraft".equals(namespace) || path == null) {
            return Optional.empty();
        }
        String id = path.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "shield" -> Optional.of(shield());
            case "conduit" -> Optional.of(conduit());
            case "chest" -> Optional.of(chest("normal"));
            case "trapped_chest" -> Optional.of(chest("trapped"));
            case "ender_chest" -> Optional.of(chest("ender"));
            case "shulker_box" -> Optional.of(shulkerBox(null));
            default -> {
                if (id.endsWith("_bed")) {
                    yield Optional.of(bed(strip(id, "_bed")));
                }
                if (id.endsWith("_shulker_box")) {
                    yield Optional.of(shulkerBox(strip(id, "_shulker_box")));
                }
                if (id.endsWith("_banner")) {
                    yield Optional.of(banner(strip(id, "_banner")));
                }
                yield Optional.empty();
            }
        };
    }

    // ------------------------------------------------------------------
    // Bed — vanilla BedRenderer.createHeadLayer/createFootLayer
    // ------------------------------------------------------------------

    private static final double BED_X0 = 4.0;
    private static final double BED_X1 = 12.0;
    private static final double BED_LEG_BOTTOM = 5.75;
    private static final double BED_LEG_TOP = 7.25;
    private static final double BED_TOP = 10.25;
    private static final double BED_Z0 = 0.0;
    private static final double BED_Z_MID = 8.0;
    private static final double BED_Z1 = 16.0;
    private static final double BED_LEG = 1.5;

    /**
     * Turns the bed to put its pillow at the back right, which is where the inventory shows it.
     *
     * <p>Beds do not use the standard block angle — {@code item/template_bed} asks for a GUI yaw
     * of 160 where a block gets 225 — and the icon camera is fixed at the block angle, so the
     * bed is turned instead of the view.</p>
     */
    private static final double BED_YAW = 180;

    /**
     * A whole bed: two mattress halves and four legs, which is what the inventory shows.
     *
     * <p>Each half is one 16x16x6 box in vanilla, laid on its side so the box's 16x16 faces
     * become the bed's top and underside — hence the hand-written face mapping rather than
     * {@link #entityBox}. Drawn at half size, because a real bed is two blocks long and would
     * not fit the icon's single-block frame.</p>
     */
    private static BlockModel bed(String colour) {
        BlockModel model = new BlockModel();
        model.textures.put("bed", "minecraft:entity/bed/" + colour);
        Atlas tex = new Atlas("#bed", 64, 64);

        // Head half — vanilla texOffs(0, 0).
        BlockModel.Element head = box(BED_X0, BED_LEG_TOP, BED_Z0, BED_X1, BED_TOP, BED_Z_MID);
        head.faces.put("up", tex.face(6, 6, 22, 22));
        head.faces.put("down", tex.face(28, 6, 44, 22));
        head.faces.put("north", tex.face(6, 0, 22, 6));
        head.faces.put("west", tex.face(0, 6, 6, 22));
        head.faces.put("east", tex.face(22, 6, 28, 22));
        model.elements.add(head);

        // Foot half — vanilla texOffs(0, 22).
        BlockModel.Element foot = box(BED_X0, BED_LEG_TOP, BED_Z_MID, BED_X1, BED_TOP, BED_Z1);
        foot.faces.put("up", tex.face(6, 28, 22, 44));
        foot.faces.put("down", tex.face(28, 28, 44, 44));
        foot.faces.put("south", tex.face(6, 22, 22, 28));
        foot.faces.put("west", tex.face(0, 28, 6, 44));
        foot.faces.put("east", tex.face(22, 28, 28, 44));
        model.elements.add(foot);

        // Legs. All four take the same scrap of wood from the texture's right-hand column; at
        // icon size no one can tell one leg's grain from another's.
        model.elements.add(leg(tex, BED_X0, BED_Z0));
        model.elements.add(leg(tex, BED_X1 - BED_LEG, BED_Z0));
        model.elements.add(leg(tex, BED_X0, BED_Z1 - BED_LEG));
        model.elements.add(leg(tex, BED_X1 - BED_LEG, BED_Z1 - BED_LEG));

        turn(model, BED_YAW);
        return model;
    }

    private static BlockModel.Element leg(Atlas tex, double x, double z) {
        BlockModel.Element leg = box(x, BED_LEG_BOTTOM, z, x + BED_LEG, BED_LEG_TOP, z + BED_LEG);
        for (String direction : new String[]{"up", "down", "north", "south", "east", "west"}) {
            leg.faces.put(direction, tex.face(53, 3, 56, 6));
        }
        return leg;
    }

    // ------------------------------------------------------------------
    // Chest — vanilla ChestRenderer.createSingleBodyLayer
    // ------------------------------------------------------------------

    /**
     * A closed chest, lid and lock included.
     *
     * <p>Vanilla's boxes are already in block space with Y up, so they transcribe directly.
     * The lock sits on the box's +Z face, and the game turns a chest to its default NORTH
     * facing before drawing it, which is what brings the lock round to the visible side.</p>
     */
    private static BlockModel chest(String kind) {
        BlockModel model = new BlockModel();
        model.textures.put("chest", "minecraft:entity/chest/" + kind);
        Atlas tex = new Atlas("#chest", 64, 64);

        // bottom: box(1, 0, 1, 14, 10, 14)
        BlockModel.Element bottom = box(1, 0, 1, 15, 10, 15);
        entityBox(bottom, tex, 0, 19, 14, 10, 14);
        model.elements.add(bottom);

        // lid: box(1, 0, 0, 14, 5, 14) offset by (0, 9, 1)
        BlockModel.Element lid = box(1, 9, 1, 15, 14, 15);
        entityBox(lid, tex, 0, 0, 14, 5, 14);
        model.elements.add(lid);

        // lock: box(7, -2, 14, 2, 4, 1) offset by (0, 9, 1)
        BlockModel.Element lock = box(7, 7, 15, 9, 11, 16);
        entityBox(lock, tex, 0, 0, 2, 4, 1);
        model.elements.add(lock);

        turn(model, 180);
        return model;
    }

    // ------------------------------------------------------------------
    // Shulker box — vanilla ShulkerModel.createBodyLayer
    // ------------------------------------------------------------------

    /**
     * A closed shulker box: the base with the lid resting on it.
     *
     * <p>Vanilla builds shulkers upside down and flips them with {@code scale(1, -1, -1)}
     * before drawing, so each box's faces are re-pointed rather than the geometry rotated —
     * rotating would hand the renderer the wrong face for its directional shading and leave
     * the lid lit like an underside.</p>
     */
    private static BlockModel shulkerBox(String colour) {
        BlockModel model = new BlockModel();
        model.textures.put("shulker", colour == null
                ? "minecraft:entity/shulker/shulker"
                : "minecraft:entity/shulker/shulker_" + colour);
        Atlas tex = new Atlas("#shulker", 64, 64);

        BlockModel.Element base = box(0, 0, 0, 16, 8, 16);
        entityBox(base, tex, 0, 28, 16, 8, 16);
        flipYZ(base);
        model.elements.add(base);

        BlockModel.Element lid = box(0, 4, 0, 16, 16, 16);
        entityBox(lid, tex, 0, 0, 16, 12, 16);
        flipYZ(lid);
        model.elements.add(lid);
        return model;
    }

    // ------------------------------------------------------------------
    // Banner — vanilla BannerRenderer.createBodyLayer
    // ------------------------------------------------------------------

    /**
     * A standing banner: cloth, cross bar and pole.
     *
     * <p>All 16 banners share one white base texture and are told apart by their dye, so the
     * cloth carries an explicit tint. Patterns are not drawn: they live in the stack's NBT,
     * which an icon keyed by item alone cannot see.</p>
     *
     * <p>Vanilla's banner is 40 units of cloth on a 42-unit pole — two and a half blocks — so
     * it is scaled down to fit the icon.</p>
     */
    private static BlockModel banner(String colour) {
        BlockModel model = new BlockModel();
        model.textures.put("banner", "minecraft:entity/banner_base");
        Atlas tex = new Atlas("#banner", 64, 64);
        double scale = 15.0 / 42.0;
        double bottom = 0.5;
        double top = bottom + 42 * scale;

        // Pole: 2x42x2 at texOffs(44, 0).
        double poleW = 2 * scale / 2;
        BlockModel.Element pole = box(8 - poleW, bottom, 9 - poleW, 8 + poleW, top, 9 + poleW);
        entityBox(pole, tex, 44, 0, 2, 42, 2);
        model.elements.add(pole);

        // Cross bar: 20x2x2 at texOffs(0, 42), across the top of the pole.
        double barW = 20 * scale / 2;
        BlockModel.Element bar = box(8 - barW, top - 2 * scale, 9 - poleW, 8 + barW, top, 9 + poleW);
        entityBox(bar, tex, 0, 42, 20, 2, 2);
        model.elements.add(bar);

        // Cloth: 20x40x1 at texOffs(0, 0), hanging from the bar in front of the pole.
        BlockModel.Element cloth = box(8 - barW, top - 42 * scale, 9 - poleW - scale,
                8 + barW, top - 2 * scale, 9 - poleW);
        entityBox(cloth, tex, 0, 0, 20, 40, 1);
        tint(cloth, DYE.getOrDefault(colour, 0xFFFFFF));
        model.elements.add(cloth);
        return model;
    }

    // ------------------------------------------------------------------
    // Shield — vanilla ShieldModel.createLayer
    // ------------------------------------------------------------------

    /**
     * A shield: vanilla's 12x22x1 plate with its 2x6x6 handle on the back.
     *
     * <p>Turned to put the studded front toward the camera. The icon view is fixed to the
     * vanilla block angle and cannot honour the shield's own {@code display.gui} rotation
     * without changing the transform every other icon is rendered with, so the geometry is
     * turned instead. Square-on to the camera would be 45 degrees, which renders as a flat
     * sprite with no depth at all; stopping short of that keeps one edge visible.</p>
     */
    private static BlockModel shield() {
        BlockModel model = new BlockModel();
        model.textures.put("shield", "minecraft:entity/shield_base_nopattern");
        Atlas tex = new Atlas("#shield", 64, 64);

        double scale = 15.0 / 22.0;
        double plateW = 12 * scale / 2;
        double plateH = 22 * scale / 2;
        double plateD = 1 * scale;

        BlockModel.Element plate = box(8 - plateW, 8 - plateH, 8, 8 + plateW, 8 + plateH, 8 + plateD);
        entityBox(plate, tex, 0, 0, 12, 22, 1);
        model.elements.add(plate);

        double gripW = 2 * scale / 2;
        double gripH = 6 * scale / 2;
        double gripD = 6 * scale;
        BlockModel.Element handle = box(8 - gripW, 8 - gripH, 8 + plateD,
                8 + gripW, 8 + gripH, 8 + plateD + gripD);
        entityBox(handle, tex, 26, 0, 2, 6, 6);
        model.elements.add(handle);

        turn(model, 35);
        return model;
    }

    // ------------------------------------------------------------------
    // Conduit — vanilla ConduitRenderer.createShellLayer
    // ------------------------------------------------------------------

    /** The conduit's closed shell: one 6x6x6 box on a 32x16 texture. */
    private static BlockModel conduit() {
        BlockModel model = new BlockModel();
        model.textures.put("conduit", "minecraft:entity/conduit/base");
        Atlas tex = new Atlas("#conduit", 32, 16);

        // Vanilla's box is 6 units on a side, which would sit lost in the middle of the icon;
        // the inventory scales it up too.
        double half = 5.5;
        BlockModel.Element shell = box(8 - half, 8 - half, 8 - half, 8 + half, 8 + half, 8 + half);
        entityBox(shell, tex, 0, 0, 6, 6, 6);
        model.elements.add(shell);
        return model;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String strip(String id, String suffix) {
        return id.substring(0, id.length() - suffix.length());
    }

    private static BlockModel.Element box(double x0, double y0, double z0,
                                          double x1, double y1, double z1) {
        BlockModel.Element element = new BlockModel.Element();
        element.from = new double[]{x0, y0, z0};
        element.to = new double[]{x1, y1, z1};
        return element;
    }

    /**
     * Vanilla's UV unwrap for one entity box, as {@code CubeListBuilder} lays it out.
     *
     * <p>A box of size (w, h, d) placed at {@code texOffs(u, v)} takes a strip d+w+d+w wide:
     * {@code down} and {@code up} sit on the top row, d tall, and the four sides follow on the
     * row beneath, h tall. {@code up} is written bottom-to-top because the game flips it.</p>
     */
    private static void entityBox(BlockModel.Element element, Atlas tex,
                                  double u, double v, double w, double h, double d) {
        element.faces.put("down", tex.face(u + d, v, u + d + w, v + d));
        element.faces.put("up", tex.face(u + d + w, v + d, u + d + w + w, v));
        element.faces.put("west", tex.face(u, v + d, u + d, v + d + h));
        element.faces.put("north", tex.face(u + d, v + d, u + d + w, v + d + h));
        element.faces.put("east", tex.face(u + d + w, v + d, u + d + w + d, v + d + h));
        element.faces.put("south", tex.face(u + d + w + d, v + d, u + d + w + d + w, v + d + h));
    }

    /** Re-point a box's faces for a model the game draws flipped ({@code scale(1, -1, -1)}). */
    private static void flipYZ(BlockModel.Element element) {
        swap(element, "up", "down");
        swap(element, "north", "south");
    }

    private static void swap(BlockModel.Element element, String a, String b) {
        BlockModel.Face fa = element.faces.get(a);
        BlockModel.Face fb = element.faces.get(b);
        if (fa != null && fb != null) {
            element.faces.put(a, fb);
            element.faces.put(b, fa);
        }
    }

    private static void tint(BlockModel.Element element, int colour) {
        for (BlockModel.Face face : element.faces.values()) {
            face.tint = colour;
        }
    }

    /** Turn every element about the block's vertical centre line. */
    private static void turn(BlockModel model, double degrees) {
        for (BlockModel.Element element : model.elements) {
            BlockModel.Rotation rotation = new BlockModel.Rotation();
            rotation.axis = "y";
            rotation.angle = degrees;
            element.rotation = rotation;
        }
    }

    /** A texture and the pixel grid its UV rectangles are written in. */
    private record Atlas(String ref, double width, double height) {
        /** A face taking the given texel rectangle, converted to the model's 0..16 UV space. */
        BlockModel.Face face(double u0, double v0, double u1, double v1) {
            BlockModel.Face face = new BlockModel.Face();
            face.texture = ref;
            face.uv = new double[]{
                    u0 * 16 / width, v0 * 16 / height,
                    u1 * 16 / width, v1 * 16 / height};
            return face;
        }
    }
}
