package DahRealPanda.plugins.colonyweb.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Text}'s string helpers, which every scanner leans on to turn raw MineColonies ids and
 * translation keys into something a dashboard can show.
 */
class TextTest {

    @Nested
    @DisplayName("pathOf")
    class PathOf {

        @Test
        @DisplayName("returns the part after the namespace")
        void stripsNamespace() {
            assertEquals("blockhutbuilder", Text.pathOf("minecolonies:blockhutbuilder"));
        }

        @Test
        @DisplayName("returns an unqualified id unchanged")
        void keepsUnqualified() {
            assertEquals("oak_planks", Text.pathOf("oak_planks"));
        }

        @Test
        @DisplayName("null becomes empty rather than throwing")
        void nullIsEmpty() {
            assertEquals("", Text.pathOf(null));
        }

        @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
        @DisplayName("handles ids with no path, no namespace, or extra colons")
        @CsvSource(delimiterString = "|", value = {
                "minecolonies:|",
                ":shingle|shingle",
                "domum_ornamentum:shingle#a1b2|shingle#a1b2",
                // Only the first colon separates; the rest belong to the path.
                "a:b:c|b:c",
        })
        void edgeCases(String input, String expected) {
            assertEquals(expected == null ? "" : expected, Text.pathOf(input));
        }
    }

    @Nested
    @DisplayName("humanize")
    class Humanize {

        @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
        @CsvSource(delimiterString = "|", value = {
                "oak_planks|Oak Planks",
                "blockHutBuilder|Block Hut Builder",
                "stone-age|Stone Age",
                "supplyship.blueprint|Supplyship",
                "citizen/builder|Builder",
                "level4|Level 4",
                "4level|4 Level",
                "already Spaced|Already Spaced",
        })
        void readableNames(String input, String expected) {
            assertEquals(expected, Text.humanize(input));
        }

        @ParameterizedTest
        @DisplayName("blank input yields blank output")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        void blankStaysBlank(String input) {
            assertEquals("", Text.humanize(input));
        }

        @Test
        @DisplayName("collapses runs of separators instead of emitting empty words")
        void collapsesSeparators() {
            assertEquals("Oak Planks", Text.humanize("oak___planks"));
        }

        @Test
        @DisplayName("a trailing separator does not leave trailing whitespace")
        void noTrailingSpace() {
            assertEquals("Oak", Text.humanize("oak_"));
        }
    }

    @Nested
    @DisplayName("looksLikeKey")
    class LooksLikeKey {

        @ParameterizedTest
        @DisplayName("dotted and namespaced ids look like keys")
        @ValueSource(strings = {
                "com.minecolonies.research.technology.stone.name",
                "minecolonies:blockhutbuilder",
                "a.b",
        })
        void keys(String input) {
            assertTrue(Text.looksLikeKey(input));
        }

        @ParameterizedTest
        @DisplayName("prose and bare words do not")
        @ValueSource(strings = {
                "Stone Age",
                "Builder",
                // A leading dot or colon is not a separator between segments.
                ".leading",
                ":leading",
                // Any space at all means somebody already translated it.
                "com.minecolonies.research already translated",
        })
        void notKeys(String input) {
            assertFalse(Text.looksLikeKey(input));
        }
    }

    @Nested
    @DisplayName("stringOrEmpty")
    class StringOrEmpty {

        @Test
        void nullBecomesEmpty() {
            assertEquals("", Text.stringOrEmpty(null));
        }

        @Test
        void valueIsUnchanged() {
            assertEquals("Bob", Text.stringOrEmpty("Bob"));
        }
    }
}
