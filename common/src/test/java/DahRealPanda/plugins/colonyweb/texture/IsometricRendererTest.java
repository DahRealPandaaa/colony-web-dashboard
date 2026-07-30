package DahRealPanda.plugins.colonyweb.texture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The software rasteriser that turns a block model into an inventory icon.
 *
 * <p>It runs on a dedicated server with no OpenGL and no client, against models supplied by
 * whatever mods are installed, so "returns null instead of drawing" is a supported outcome and
 * is what the caller falls back on. These tests pin that contract down along with the parts of
 * the output that are cheap to state exactly: size, transparency and which faces are visible.</p>
 */
class IsometricRendererTest {

    /** A flat colour, so a face's identity can be read straight out of the rendered pixels. */
    private static BufferedImage solid(int argb) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private static BlockModel cube(String textureRef) {
        BlockModel model = new BlockModel();
        model.textures.put("all", "minecraft:block/stone");
        BlockModel.Element element = new BlockModel.Element();
        element.from = new double[]{0, 0, 0};
        element.to = new double[]{16, 16, 16};
        for (String direction : new String[]{"up", "down", "north", "south", "east", "west"}) {
            BlockModel.Face face = new BlockModel.Face();
            face.texture = textureRef;
            element.faces.put(direction, face);
        }
        model.elements.add(element);
        return model;
    }

    private static Function<String, BufferedImage> always(BufferedImage image) {
        return ref -> image;
    }

    private static boolean anyOpaque(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nested
    @DisplayName("drawing")
    class Drawing {

        @Test
        @DisplayName("a full cube renders at the requested size")
        void rendersCube() {
            BufferedImage icon = IsometricRenderer.render(cube("#all"), always(solid(0xFFFF0000)), 64);

            assertNotNull(icon);
            assertEquals(64, icon.getWidth());
            assertEquals(64, icon.getHeight());
            assertTrue(anyOpaque(icon), "a solid cube should put pixels on the canvas");
        }

        @Test
        @DisplayName("the corners stay transparent, so an icon composites over any background")
        void cornersAreTransparent() {
            BufferedImage icon = IsometricRenderer.render(cube("#all"), always(solid(0xFFFF0000)), 64);

            assertEquals(0, icon.getRGB(0, 0) >>> 24, "top-left corner should be transparent");
            assertEquals(0, icon.getRGB(63, 0) >>> 24, "top-right corner should be transparent");
        }

        @Test
        @DisplayName("the middle of the canvas is covered")
        void centreIsDrawn() {
            BufferedImage icon = IsometricRenderer.render(cube("#all"), always(solid(0xFFFF0000)), 64);

            assertTrue((icon.getRGB(32, 32) >>> 24) > 0, "the block should cover the centre");
        }

        @Test
        @DisplayName("vanilla's face shading makes the top brighter than the sides")
        void topIsBrighterThanSides() {
            BufferedImage icon = IsometricRenderer.render(cube("#all"), always(solid(0xFFFFFFFF)), 64);

            // The vanilla GUI angle puts the top face above the centre and a side below it.
            int top = icon.getRGB(32, 18) & 0xFF;
            int side = icon.getRGB(32, 52) & 0xFF;
            assertTrue(top > side, "top " + top + " should be brighter than side " + side);
        }

        @Test
        @DisplayName("an explicit tint colours the face")
        void tintIsApplied() {
            BlockModel model = cube("#all");
            for (BlockModel.Element element : model.elements) {
                for (BlockModel.Face face : element.faces.values()) {
                    face.tint = 0xFF0000;
                }
            }

            BufferedImage icon = IsometricRenderer.render(model, always(solid(0xFFFFFFFF)), 64);

            int centre = icon.getRGB(32, 32);
            assertTrue(((centre >> 16) & 0xFF) > ((centre >> 8) & 0xFF),
                    "a red tint should leave more red than green");
        }

        @Test
        @DisplayName("a smaller icon is still square and still drawn")
        void smallIcon() {
            BufferedImage icon = IsometricRenderer.render(cube("#all"), always(solid(0xFFFF0000)), 16);

            assertNotNull(icon);
            assertEquals(16, icon.getWidth());
            assertEquals(16, icon.getHeight());
        }
    }

    @Nested
    @DisplayName("nothing to draw")
    class NothingToDraw {

        @Test
        @DisplayName("a null model renders nothing")
        void nullModel() {
            assertNull(IsometricRenderer.render(null, always(solid(0xFFFFFFFF)), 64));
        }

        @Test
        @DisplayName("a model with no elements renders nothing")
        void noElements() {
            assertNull(IsometricRenderer.render(new BlockModel(), always(solid(0xFFFFFFFF)), 64));
        }

        @Test
        @DisplayName("a model whose textures cannot be loaded renders nothing")
        void missingTextures() {
            assertNull(IsometricRenderer.render(cube("#all"), ref -> null, 64),
                    "the caller relies on null to fall back to a flat texture");
        }

        @Test
        @DisplayName("a face with no geometry renders nothing")
        void degenerateElement() {
            BlockModel model = cube("#all");
            model.elements.get(0).to = new double[]{0, 0, 0};

            assertNull(IsometricRenderer.render(model, always(solid(0xFFFFFFFF)), 64));
        }
    }

    @Nested
    @DisplayName("partial models")
    class PartialModels {

        @Test
        @DisplayName("faces whose texture is missing are skipped, the rest still draw")
        void skipsUntexturedFaces() {
            BlockModel model = cube("#all");
            model.elements.get(0).faces.get("up").texture = "#missing";
            BufferedImage stone = solid(0xFFFF0000);

            BufferedImage partial = IsometricRenderer.render(model,
                    ref -> "#all".equals(ref) ? stone : null, 64);
            BufferedImage complete = IsometricRenderer.render(cube("#all"), always(stone), 64);

            assertNotNull(partial, "the remaining faces should still produce an icon");
            // There is no back-face culling, so dropping the top reveals the inside of the
            // bottom face rather than leaving a hole — but the result must still differ.
            assertFalse(sameImage(partial, complete), "dropping a face should change the icon");
        }

        private boolean sameImage(BufferedImage a, BufferedImage b) {
            for (int y = 0; y < a.getHeight(); y++) {
                for (int x = 0; x < a.getWidth(); x++) {
                    if (a.getRGB(x, y) != b.getRGB(x, y)) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Test
        @DisplayName("a fully transparent texture draws nothing")
        void transparentTexture() {
            assertNull(IsometricRenderer.render(cube("#all"), always(solid(0x00000000)), 64),
                    "cut-out geometry must not leave an invisible icon behind");
        }

        @Test
        @DisplayName("an unresolvable texture reference is skipped rather than throwing")
        void unresolvableRef() {
            BlockModel model = cube("#nope");

            assertNull(IsometricRenderer.render(model, ref -> null, 64));
        }
    }

    @Nested
    @DisplayName("element rotation")
    class Rotation {

        @Test
        @DisplayName("a rotated element still renders")
        void rotatedElement() {
            BlockModel model = cube("#all");
            BlockModel.Rotation rotation = new BlockModel.Rotation();
            rotation.axis = "y";
            rotation.angle = 45;
            model.elements.get(0).rotation = rotation;

            assertNotNull(IsometricRenderer.render(model, always(solid(0xFFFF0000)), 64));
        }

        @Test
        @DisplayName("rotating a cube by a quarter turn leaves the same silhouette")
        void quarterTurnIsSymmetric() {
            BufferedImage plain = IsometricRenderer.render(cube("#all"), always(solid(0xFFFFFFFF)), 64);

            BlockModel turned = cube("#all");
            BlockModel.Rotation rotation = new BlockModel.Rotation();
            rotation.axis = "y";
            rotation.angle = 90;
            turned.elements.get(0).rotation = rotation;
            BufferedImage rotated = IsometricRenderer.render(turned, always(solid(0xFFFFFFFF)), 64);

            assertEquals(opaqueCount(plain), opaqueCount(rotated),
                    "a cube turned 90 degrees covers the same pixels");
        }

        private int opaqueCount(BufferedImage image) {
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) != 0) {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    @Nested
    @DisplayName("texture reference resolution")
    class TextureRefs {

        @Test
        @DisplayName("a variable resolves through the model's texture map")
        void resolvesVariable() {
            BlockModel model = new BlockModel();
            model.textures.put("all", "minecraft:block/stone");

            assertEquals("minecraft:block/stone", model.resolveTextureRef("#all"));
        }

        @Test
        @DisplayName("a chain of variables resolves to the concrete texture")
        void resolvesChain() {
            BlockModel model = new BlockModel();
            model.textures.put("particle", "#all");
            model.textures.put("all", "minecraft:block/stone");

            assertEquals("minecraft:block/stone", model.resolveTextureRef("#particle"));
        }

        @Test
        @DisplayName("a direct reference is returned unchanged")
        void directReference() {
            assertEquals("minecraft:block/stone",
                    new BlockModel().resolveTextureRef("minecraft:block/stone"));
        }

        @Test
        @DisplayName("an undefined variable resolves to nothing")
        void undefinedVariable() {
            assertNull(new BlockModel().resolveTextureRef("#missing"));
        }

        @Test
        @DisplayName("a null reference resolves to nothing")
        void nullReference() {
            assertNull(new BlockModel().resolveTextureRef(null));
        }

        @Test
        @DisplayName("a self-referencing variable terminates instead of looping forever")
        void cycleTerminates() {
            BlockModel model = new BlockModel();
            model.textures.put("a", "#b");
            model.textures.put("b", "#a");

            assertNull(model.resolveTextureRef("#a"));
        }
    }

    @Nested
    @DisplayName("used texture references")
    class UsedRefs {

        @Test
        @DisplayName("only references actual geometry uses are reported")
        void onlyUsedRefs() {
            BlockModel model = cube("#all");
            model.textures.put("unused", "minecraft:block/dirt");

            assertEquals(java.util.List.of("#all"), model.usedTextureRefs());
        }

        @Test
        @DisplayName("references are reported in the model's declaration order")
        void declarationOrder() {
            BlockModel model = new BlockModel();
            model.textures.put("first", "minecraft:block/stone");
            model.textures.put("second", "minecraft:block/dirt");
            BlockModel.Element element = new BlockModel.Element();
            // Added to the geometry in the opposite order to the declaration.
            for (Map.Entry<String, String> entry
                    : Map.of("north", "#second", "south", "#first").entrySet()) {
                BlockModel.Face face = new BlockModel.Face();
                face.texture = entry.getValue();
                element.faces.put(entry.getKey(), face);
            }
            model.elements.add(element);

            assertEquals(java.util.List.of("#first", "#second"), model.usedTextureRefs());
        }

        @Test
        @DisplayName("a model with no geometry uses no textures")
        void noGeometry() {
            assertTrue(new BlockModel().usedTextureRefs().isEmpty());
        }
    }
}
