package com.wlritchi.shulkertrims.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ShulkerTrim}.
 */
class ShulkerTrimTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("creates trim with valid pattern and material")
        void createsWithValidPatternAndMaterial() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:coast", "minecraft:gold");

            assertEquals("minecraft:coast", trim.pattern());
            assertEquals("minecraft:gold", trim.material());
        }

        @Test
        @DisplayName("throws NullPointerException when pattern is null")
        void throwsWhenPatternIsNull() {
            NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ShulkerTrim(null, "minecraft:gold")
            );
            assertEquals("pattern cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("throws NullPointerException when material is null")
        void throwsWhenMaterialIsNull() {
            NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ShulkerTrim("minecraft:coast", null)
            );
            assertEquals("material cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("NBT Constants")
    class NbtConstantsTests {

        @Test
        @DisplayName("NBT_KEY has expected value")
        void nbtKeyHasExpectedValue() {
            assertEquals("shulker_trims:trim", ShulkerTrim.NBT_KEY);
        }

        @Test
        @DisplayName("NBT_PATTERN_KEY has expected value")
        void nbtPatternKeyHasExpectedValue() {
            assertEquals("pattern", ShulkerTrim.NBT_PATTERN_KEY);
        }

        @Test
        @DisplayName("NBT_MATERIAL_KEY has expected value")
        void nbtMaterialKeyHasExpectedValue() {
            assertEquals("material", ShulkerTrim.NBT_MATERIAL_KEY);
        }
    }

    @Nested
    @DisplayName("Identifier Validation")
    class IdentifierValidationTests {

        @Test
        @DisplayName("valid pattern with minecraft namespace")
        void validPatternWithMinecraftNamespace() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:coast", "minecraft:gold");
            assertTrue(trim.hasValidPattern());
        }

        @Test
        @DisplayName("valid pattern with custom namespace")
        void validPatternWithCustomNamespace() {
            ShulkerTrim trim = new ShulkerTrim("mymod:custom_pattern", "minecraft:gold");
            assertTrue(trim.hasValidPattern());
        }

        @Test
        @DisplayName("valid pattern with path containing slashes")
        void validPatternWithSlashes() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:trim/coast", "minecraft:gold");
            assertTrue(trim.hasValidPattern());
        }

        @Test
        @DisplayName("valid pattern with underscores and numbers")
        void validPatternWithUnderscoresAndNumbers() {
            ShulkerTrim trim = new ShulkerTrim("my_mod_123:pattern_v2", "minecraft:gold");
            assertTrue(trim.hasValidPattern());
        }

        @Test
        @DisplayName("invalid pattern without colon")
        void invalidPatternWithoutColon() {
            ShulkerTrim trim = new ShulkerTrim("coast", "minecraft:gold");
            assertFalse(trim.hasValidPattern());
        }

        @Test
        @DisplayName("invalid pattern with empty namespace")
        void invalidPatternWithEmptyNamespace() {
            ShulkerTrim trim = new ShulkerTrim(":coast", "minecraft:gold");
            assertFalse(trim.hasValidPattern());
        }

        @Test
        @DisplayName("invalid pattern with empty path")
        void invalidPatternWithEmptyPath() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:", "minecraft:gold");
            assertFalse(trim.hasValidPattern());
        }

        @Test
        @DisplayName("invalid pattern with uppercase letters in namespace")
        void invalidPatternWithUppercaseNamespace() {
            ShulkerTrim trim = new ShulkerTrim("Minecraft:coast", "minecraft:gold");
            assertFalse(trim.hasValidPattern());
        }

        @Test
        @DisplayName("invalid pattern with uppercase letters in path")
        void invalidPatternWithUppercasePath() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:Coast", "minecraft:gold");
            assertFalse(trim.hasValidPattern());
        }

        @Test
        @DisplayName("invalid pattern with spaces")
        void invalidPatternWithSpaces() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:coast pattern", "minecraft:gold");
            assertFalse(trim.hasValidPattern());
        }

        @Test
        @DisplayName("invalid pattern with special characters")
        void invalidPatternWithSpecialCharacters() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:coast@pattern", "minecraft:gold");
            assertFalse(trim.hasValidPattern());
        }

        @Test
        @DisplayName("valid material with minecraft namespace")
        void validMaterialWithMinecraftNamespace() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:coast", "minecraft:gold");
            assertTrue(trim.hasValidMaterial());
        }

        @Test
        @DisplayName("invalid material without colon")
        void invalidMaterialWithoutColon() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:coast", "gold");
            assertFalse(trim.hasValidMaterial());
        }

        @Test
        @DisplayName("isValid returns true when both pattern and material are valid")
        void isValidReturnsTrueWhenBothValid() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:coast", "minecraft:gold");
            assertTrue(trim.isValid());
        }

        @Test
        @DisplayName("isValid returns false when pattern is invalid")
        void isValidReturnsFalseWhenPatternInvalid() {
            ShulkerTrim trim = new ShulkerTrim("coast", "minecraft:gold");
            assertFalse(trim.isValid());
        }

        @Test
        @DisplayName("isValid returns false when material is invalid")
        void isValidReturnsFalseWhenMaterialInvalid() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:coast", "gold");
            assertFalse(trim.isValid());
        }

        @Test
        @DisplayName("isValid returns false when both are invalid")
        void isValidReturnsFalseWhenBothInvalid() {
            ShulkerTrim trim = new ShulkerTrim("coast", "gold");
            assertFalse(trim.isValid());
        }

        @Test
        @DisplayName("valid identifier with dots in namespace")
        void validIdentifierWithDotsInNamespace() {
            ShulkerTrim trim = new ShulkerTrim("my.mod:pattern", "minecraft:gold");
            assertTrue(trim.hasValidPattern());
        }

        @Test
        @DisplayName("valid identifier with hyphens in namespace")
        void validIdentifierWithHyphensInNamespace() {
            ShulkerTrim trim = new ShulkerTrim("my-mod:pattern", "minecraft:gold");
            assertTrue(trim.hasValidPattern());
        }

        @Test
        @DisplayName("valid identifier with dots in path")
        void validIdentifierWithDotsInPath() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:pattern.v1", "minecraft:gold");
            assertTrue(trim.hasValidPattern());
        }

        @Test
        @DisplayName("empty string pattern is invalid")
        void emptyPatternIsInvalid() {
            ShulkerTrim trim = new ShulkerTrim("", "minecraft:gold");
            assertFalse(trim.hasValidPattern());
        }
    }

    @Nested
    @DisplayName("Record Equality")
    class RecordEqualityTests {

        @Test
        @DisplayName("equals returns true for identical trims")
        void equalsReturnsTrueForIdenticalTrims() {
            ShulkerTrim trim1 = new ShulkerTrim("minecraft:coast", "minecraft:gold");
            ShulkerTrim trim2 = new ShulkerTrim("minecraft:coast", "minecraft:gold");

            assertEquals(trim1, trim2);
        }

        @Test
        @DisplayName("equals returns false for different patterns")
        void equalsReturnsFalseForDifferentPatterns() {
            ShulkerTrim trim1 = new ShulkerTrim("minecraft:coast", "minecraft:gold");
            ShulkerTrim trim2 = new ShulkerTrim("minecraft:dune", "minecraft:gold");

            assertNotEquals(trim1, trim2);
        }

        @Test
        @DisplayName("equals returns false for different materials")
        void equalsReturnsFalseForDifferentMaterials() {
            ShulkerTrim trim1 = new ShulkerTrim("minecraft:coast", "minecraft:gold");
            ShulkerTrim trim2 = new ShulkerTrim("minecraft:coast", "minecraft:diamond");

            assertNotEquals(trim1, trim2);
        }

        @Test
        @DisplayName("hashCode is consistent for equal trims")
        void hashCodeIsConsistentForEqualTrims() {
            ShulkerTrim trim1 = new ShulkerTrim("minecraft:coast", "minecraft:gold");
            ShulkerTrim trim2 = new ShulkerTrim("minecraft:coast", "minecraft:gold");

            assertEquals(trim1.hashCode(), trim2.hashCode());
        }

        @Test
        @DisplayName("toString contains pattern and material")
        void toStringContainsPatternAndMaterial() {
            ShulkerTrim trim = new ShulkerTrim("minecraft:coast", "minecraft:gold");
            String str = trim.toString();

            assertTrue(str.contains("minecraft:coast"));
            assertTrue(str.contains("minecraft:gold"));
        }
    }
}
