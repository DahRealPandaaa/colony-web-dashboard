package DahRealPanda.plugins.colonyweb.texture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stand-in geometry for the items Minecraft draws with a block-entity renderer.
 *
 * <p>These models exist because the real item models are deliberately empty, so the failure
 * mode this guards against is silent: a wrong or missing entry does not crash, it just puts a
 * flat square of oak planks on the dashboard. The tests therefore assert on the shape of what
 * comes back — that a model exists, references a texture under {@code entity/}, and has UVs
 * that stay inside the texture — rather than on pixels.</p>
 */
class BuiltinEntityModelsTest {

    private static final List<String> DYES = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private static BlockModel model(String namespace, String path) {
        return BuiltinEntityModels.forItem(namespace, path)
                .orElseThrow(() -> new AssertionError("no builtin model for " + namespace + ":" + path));
    }

    @Nested
    @DisplayName("items that need stand-in geometry")
    class Recognised {

        @ParameterizedTest
        @DisplayName("every dyed bed has geometry pointing at its own texture")
        @ValueSource(strings = {"white", "red", "black", "light_gray", "magenta"})
        void beds(String dye) {
            BlockModel bed = model("minecraft", dye + "_bed");

            assertFalse(bed.elements.isEmpty());
            assertEquals("minecraft:entity/bed/" + dye, bed.textures.get("bed"));
        }

        @Test
        @DisplayName("all sixteen beds resolve")
        void allBeds() {
            for (String dye : DYES) {
                assertTrue(BuiltinEntityModels.forItem("minecraft", dye + "_bed").isPresent(), dye);
            }
        }

        @ParameterizedTest
        @DisplayName("each chest variant uses its own texture")
        @CsvSource({
                "chest,minecraft:entity/chest/normal",
                "trapped_chest,minecraft:entity/chest/trapped",
                "ender_chest,minecraft:entity/chest/ender",
        })
        void chests(String item, String texture) {
            assertEquals(texture, model("minecraft", item).textures.get("chest"));
        }

        @Test
        @DisplayName("an undyed shulker box falls back to the default texture")
        void undyedShulker() {
            assertEquals("minecraft:entity/shulker/shulker",
                    model("minecraft", "shulker_box").textures.get("shulker"));
        }

        @Test
        @DisplayName("a dyed shulker box uses its colour's texture")
        void dyedShulker() {
            assertEquals("minecraft:entity/shulker/shulker_red",
                    model("minecraft", "red_shulker_box").textures.get("shulker"));
        }

        @Test
        @DisplayName("all seventeen shulker boxes resolve")
        void allShulkers() {
            assertTrue(BuiltinEntityModels.forItem("minecraft", "shulker_box").isPresent());
            for (String dye : DYES) {
                assertTrue(BuiltinEntityModels.forItem("minecraft", dye + "_shulker_box").isPresent(), dye);
            }
        }

        @Test
        @DisplayName("every banner shares the one white base texture and is told apart by its tint")
        void banners() {
            BlockModel red = model("minecraft", "red_banner");
            BlockModel white = model("minecraft", "white_banner");

            assertEquals("minecraft:entity/banner_base", red.textures.get("banner"));
            assertEquals("minecraft:entity/banner_base", white.textures.get("banner"));
            assertNotEqualsTint(red, white);
        }

        @Test
        @DisplayName("all sixteen banners resolve")
        void allBanners() {
            for (String dye : DYES) {
                assertTrue(BuiltinEntityModels.forItem("minecraft", dye + "_banner").isPresent(), dye);
            }
        }

        @Test
        @DisplayName("the shield and conduit resolve")
        void shieldAndConduit() {
            assertEquals("minecraft:entity/shield_base_nopattern",
                    model("minecraft", "shield").textures.get("shield"));
            assertEquals("minecraft:entity/conduit/base",
                    model("minecraft", "conduit").textures.get("conduit"));
        }

        @Test
        @DisplayName("MineColonies' colony flag reuses the vanilla banner geometry, undyed")
        void colonyBanner() {
            BlockModel colony = model("minecolonies", "colony_banner");

            assertEquals("minecraft:entity/banner_base", colony.textures.get("banner"));
            assertFalse(colony.elements.isEmpty());
        }

        @Test
        @DisplayName("ids are matched case-insensitively")
        void caseInsensitive() {
            assertTrue(BuiltinEntityModels.forItem("minecraft", "RED_BED").isPresent());
            assertTrue(BuiltinEntityModels.forItem("minecraft", "Shield").isPresent());
        }
    }

    @Nested
    @DisplayName("items that must keep their own model")
    class NotRecognised {

        @ParameterizedTest
        @DisplayName("ordinary items get no stand-in")
        @ValueSource(strings = {
                "stone",
                "oak_planks",
                "diamond_sword",
                // Close to a name we do handle, but not one of them.
                "bed",
                "banner",
                "chest_minecart",
                "shulker_shell",
                "shield_blocking",
        })
        void ordinaryItems(String path) {
            assertTrue(BuiltinEntityModels.forItem("minecraft", path).isEmpty(), path);
        }

        @ParameterizedTest
        @DisplayName("a mod's own bed or banner is left to that mod's model")
        @CsvSource({
                "somemod,red_bed",
                "somemod,shield",
                "somemod,colony_banner",
                "minecolonies,red_bed",
                "minecolonies,shield",
        })
        void otherNamespaces(String namespace, String path) {
            assertTrue(BuiltinEntityModels.forItem(namespace, path).isEmpty(),
                    namespace + ":" + path);
        }

        @ParameterizedTest
        @DisplayName("a null path or namespace is empty rather than an exception")
        @NullSource
        void nullPath(String path) {
            assertTrue(BuiltinEntityModels.forItem("minecraft", path).isEmpty());
            assertTrue(BuiltinEntityModels.forItem(null, "red_bed").isEmpty());
        }

        @Test
        @DisplayName("an empty path is empty")
        void emptyPath() {
            assertTrue(BuiltinEntityModels.forItem("minecraft", "").isEmpty());
        }
    }

    @Nested
    @DisplayName("geometry sanity")
    class Geometry {

        @Test
        @DisplayName("every model draws something")
        void nonEmpty() {
            for (String item : all()) {
                BlockModel model = model("minecraft", item);
                assertFalse(model.elements.isEmpty(), item + " has no elements");
                for (BlockModel.Element element : model.elements) {
                    assertFalse(element.faces.isEmpty(), item + " has an element with no faces");
                }
            }
        }

        @Test
        @DisplayName("every face resolves to a texture the model declares")
        void facesResolve() {
            for (String item : all()) {
                BlockModel model = model("minecraft", item);
                for (BlockModel.Element element : model.elements) {
                    for (BlockModel.Face face : element.faces.values()) {
                        String resolved = model.resolveTextureRef(face.texture);
                        assertNotNull(resolved, item + " has a face with an unresolvable texture");
                        assertTrue(resolved.contains("entity/"),
                                item + " should draw from an entity texture, got " + resolved);
                    }
                }
            }
        }

        @Test
        @DisplayName("every UV rectangle stays inside the texture")
        void uvsInBounds() {
            for (String item : all()) {
                BlockModel model = model("minecraft", item);
                for (BlockModel.Element element : model.elements) {
                    for (var entry : element.faces.entrySet()) {
                        double[] uv = entry.getValue().uv;
                        assertNotNull(uv, item + " " + entry.getKey() + " has no explicit UV");
                        for (double value : uv) {
                            // Model UVs are a 0..16 space regardless of the texture's pixel size.
                            assertTrue(value >= 0 && value <= 16,
                                    item + " " + entry.getKey() + " UV out of range: " + value);
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("no face is degenerate, which would render as nothing")
        void noZeroAreaFaces() {
            for (String item : all()) {
                BlockModel model = model("minecraft", item);
                for (BlockModel.Element element : model.elements) {
                    for (var entry : element.faces.entrySet()) {
                        double[] uv = entry.getValue().uv;
                        assertTrue(Math.abs(uv[2] - uv[0]) > 0, item + " " + entry.getKey() + " has zero UV width");
                        assertTrue(Math.abs(uv[3] - uv[1]) > 0, item + " " + entry.getKey() + " has zero UV height");
                    }
                }
            }
        }

        @Test
        @DisplayName("every box has a positive volume")
        void boxesArePositive() {
            for (String item : all()) {
                for (BlockModel.Element element : model("minecraft", item).elements) {
                    for (int axis = 0; axis < 3; axis++) {
                        assertTrue(element.to[axis] > element.from[axis],
                                item + " has a box with no extent on axis " + axis);
                    }
                }
            }
        }

        @Test
        @DisplayName("each call returns a fresh model, so a caller cannot corrupt the next one")
        void modelsAreNotShared() {
            BlockModel first = model("minecraft", "red_bed");
            BlockModel second = model("minecraft", "red_bed");

            first.elements.clear();

            assertFalse(second.elements.isEmpty(), "models must not share mutable state");
        }
    }

    private static List<String> all() {
        List<String> items = new java.util.ArrayList<>(
                List.of("shield", "conduit", "chest", "trapped_chest", "ender_chest", "shulker_box"));
        for (String dye : DYES) {
            items.add(dye + "_bed");
            items.add(dye + "_shulker_box");
            items.add(dye + "_banner");
        }
        return items;
    }

    private static void assertNotEqualsTint(BlockModel a, BlockModel b) {
        assertFalse(tints(a).equals(tints(b)), "two differently dyed banners should not share a tint");
    }

    private static List<Integer> tints(BlockModel model) {
        List<Integer> tints = new java.util.ArrayList<>();
        for (BlockModel.Element element : model.elements) {
            for (BlockModel.Face face : element.faces.values()) {
                tints.add(face.tint);
            }
        }
        return tints;
    }
}
