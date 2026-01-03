package com.wlritchi.shulkertrims.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TrimMaterials}.
 */
class TrimMaterialsTest {

    @Nested
    @DisplayName("COLORS Map")
    class ColorsMapTests {

        @Test
        @DisplayName("contains all expected materials")
        void containsAllExpectedMaterials() {
            assertTrue(TrimMaterials.COLORS.containsKey("minecraft:quartz"));
            assertTrue(TrimMaterials.COLORS.containsKey("minecraft:iron"));
            assertTrue(TrimMaterials.COLORS.containsKey("minecraft:netherite"));
            assertTrue(TrimMaterials.COLORS.containsKey("minecraft:redstone"));
            assertTrue(TrimMaterials.COLORS.containsKey("minecraft:copper"));
            assertTrue(TrimMaterials.COLORS.containsKey("minecraft:gold"));
            assertTrue(TrimMaterials.COLORS.containsKey("minecraft:emerald"));
            assertTrue(TrimMaterials.COLORS.containsKey("minecraft:diamond"));
            assertTrue(TrimMaterials.COLORS.containsKey("minecraft:lapis"));
            assertTrue(TrimMaterials.COLORS.containsKey("minecraft:amethyst"));
        }

        @Test
        @DisplayName("has exactly 10 materials")
        void hasExactlyTenMaterials() {
            assertEquals(10, TrimMaterials.COLORS.size());
        }

        @ParameterizedTest
        @DisplayName("returns correct color for each material")
        @CsvSource({
            "minecraft:quartz, 0xE3D4C4",
            "minecraft:iron, 0xECECEC",
            "minecraft:netherite, 0x625859",
            "minecraft:redstone, 0x971607",
            "minecraft:copper, 0xB4684D",
            "minecraft:gold, 0xDEB12D",
            "minecraft:emerald, 0x11A036",
            "minecraft:diamond, 0x6EECD2",
            "minecraft:lapis, 0x416E97",
            "minecraft:amethyst, 0x9A5CC6"
        })
        void returnsCorrectColorForMaterial(String material, String expectedColorHex) {
            int expectedColor = Integer.decode(expectedColorHex);
            assertEquals(expectedColor, TrimMaterials.COLORS.get(material));
        }

        @Test
        @DisplayName("COLORS map is immutable")
        void colorsMapIsImmutable() {
            assertThrows(UnsupportedOperationException.class, () ->
                TrimMaterials.COLORS.put("minecraft:test", 0x000000)
            );
        }
    }

    @Nested
    @DisplayName("getColor")
    class GetColorTests {

        @ParameterizedTest
        @DisplayName("returns correct color for known materials")
        @CsvSource({
            "minecraft:quartz, 14931140",    // 0xE3D4C4
            "minecraft:iron, 15527148",      // 0xECECEC
            "minecraft:netherite, 6445145",  // 0x625859
            "minecraft:redstone, 9901575",   // 0x971607
            "minecraft:copper, 11823181",    // 0xB4684D
            "minecraft:gold, 14594349",      // 0xDEB12D
            "minecraft:emerald, 1155126",    // 0x11A036
            "minecraft:diamond, 7269586",    // 0x6EECD2
            "minecraft:lapis, 4288151",      // 0x416E97
            "minecraft:amethyst, 10116294"   // 0x9A5CC6
        })
        void returnsCorrectColorForKnownMaterials(String material, int expectedColor) {
            assertEquals(expectedColor, TrimMaterials.getColor(material));
        }

        @Test
        @DisplayName("returns default gray for unknown material")
        void returnsDefaultGrayForUnknownMaterial() {
            int defaultGray = 0x808080;
            assertEquals(defaultGray, TrimMaterials.getColor("minecraft:unknown"));
        }

        @ParameterizedTest
        @DisplayName("returns default gray for various invalid materials")
        @ValueSource(strings = {
            "unknown",
            "othermod:copper",
            "",
            "minecraft:not_a_trim_material"
        })
        void returnsDefaultGrayForInvalidMaterials(String material) {
            int defaultGray = 0x808080;
            assertEquals(defaultGray, TrimMaterials.getColor(material));
        }

        @Test
        @DisplayName("throws NullPointerException for null material")
        void throwsNullPointerExceptionForNullMaterial() {
            // ImmutableCollections.MapN.getOrDefault does not accept null keys
            assertThrows(NullPointerException.class, () -> TrimMaterials.getColor(null));
        }
    }

    @Nested
    @DisplayName("getColorComponents")
    class GetColorComponentsTests {

        @Test
        @DisplayName("returns array of length 3")
        void returnsArrayOfLengthThree() {
            float[] components = TrimMaterials.getColorComponents("minecraft:gold");
            assertEquals(3, components.length);
        }

        @Test
        @DisplayName("returns correct RGB components for gold")
        void returnsCorrectComponentsForGold() {
            // Gold is 0xDEB12D = RGB(222, 177, 45)
            float[] components = TrimMaterials.getColorComponents("minecraft:gold");

            assertEquals(222f / 255f, components[0], 0.001f, "Red component");
            assertEquals(177f / 255f, components[1], 0.001f, "Green component");
            assertEquals(45f / 255f, components[2], 0.001f, "Blue component");
        }

        @Test
        @DisplayName("returns correct RGB components for diamond")
        void returnsCorrectComponentsForDiamond() {
            // Diamond is 0x6EECD2 = RGB(110, 236, 210)
            float[] components = TrimMaterials.getColorComponents("minecraft:diamond");

            assertEquals(110f / 255f, components[0], 0.001f, "Red component");
            assertEquals(236f / 255f, components[1], 0.001f, "Green component");
            assertEquals(210f / 255f, components[2], 0.001f, "Blue component");
        }

        @Test
        @DisplayName("returns correct RGB components for netherite")
        void returnsCorrectComponentsForNetherite() {
            // Netherite is 0x625859 = RGB(98, 88, 89)
            float[] components = TrimMaterials.getColorComponents("minecraft:netherite");

            assertEquals(98f / 255f, components[0], 0.001f, "Red component");
            assertEquals(88f / 255f, components[1], 0.001f, "Green component");
            assertEquals(89f / 255f, components[2], 0.001f, "Blue component");
        }

        @Test
        @DisplayName("returns components in range 0.0 to 1.0")
        void returnsComponentsInValidRange() {
            for (String material : TrimMaterials.COLORS.keySet()) {
                float[] components = TrimMaterials.getColorComponents(material);

                for (int i = 0; i < 3; i++) {
                    assertTrue(components[i] >= 0.0f && components[i] <= 1.0f,
                        "Component " + i + " for " + material + " should be between 0.0 and 1.0");
                }
            }
        }

        @Test
        @DisplayName("returns default gray components for unknown material")
        void returnsDefaultGrayComponentsForUnknownMaterial() {
            // Default gray is 0x808080 = RGB(128, 128, 128)
            float[] components = TrimMaterials.getColorComponents("minecraft:unknown");

            float expectedGray = 128f / 255f;
            assertEquals(expectedGray, components[0], 0.001f, "Red component");
            assertEquals(expectedGray, components[1], 0.001f, "Green component");
            assertEquals(expectedGray, components[2], 0.001f, "Blue component");
        }

        @Test
        @DisplayName("returns new array each call")
        void returnsNewArrayEachCall() {
            float[] components1 = TrimMaterials.getColorComponents("minecraft:gold");
            float[] components2 = TrimMaterials.getColorComponents("minecraft:gold");

            assertNotSame(components1, components2, "Should return new array each call");
            assertArrayEquals(components1, components2, 0.001f, "Arrays should have same values");
        }

        @Test
        @DisplayName("modifying returned array does not affect future calls")
        void modifyingArrayDoesNotAffectFutureCalls() {
            float[] components1 = TrimMaterials.getColorComponents("minecraft:gold");
            float originalRed = components1[0];

            components1[0] = 0.0f;  // Modify the array

            float[] components2 = TrimMaterials.getColorComponents("minecraft:gold");
            assertEquals(originalRed, components2[0], 0.001f, "Modification should not affect future calls");
        }
    }

    @Nested
    @DisplayName("Color values verification")
    class ColorValuesVerificationTests {

        @Test
        @DisplayName("quartz has warm white color")
        void quartzHasWarmWhiteColor() {
            int color = TrimMaterials.getColor("minecraft:quartz");
            // Quartz should be a warm white/beige color (high RGB values with warm tint)
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;

            assertTrue(red > 200, "Quartz red should be high");
            assertTrue(green > 180, "Quartz green should be moderately high");
            assertTrue(blue > 160, "Quartz blue should be moderately high");
        }

        @Test
        @DisplayName("iron has grayish color")
        void ironHasGrayishColor() {
            int color = TrimMaterials.getColor("minecraft:iron");
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;

            // Iron should be a near-neutral gray (similar RGB values)
            assertTrue(Math.abs(red - green) < 10, "Iron R and G should be similar");
            assertTrue(Math.abs(green - blue) < 10, "Iron G and B should be similar");
        }

        @Test
        @DisplayName("redstone has strong red color")
        void redstoneHasStrongRedColor() {
            int color = TrimMaterials.getColor("minecraft:redstone");
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;

            assertTrue(red > green * 2, "Redstone red should dominate over green");
            assertTrue(red > blue * 2, "Redstone red should dominate over blue");
        }

        @Test
        @DisplayName("emerald has strong green color")
        void emeraldHasStrongGreenColor() {
            int color = TrimMaterials.getColor("minecraft:emerald");
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;

            assertTrue(green > red, "Emerald green should dominate over red");
            assertTrue(green > blue, "Emerald green should dominate over blue");
        }

        @Test
        @DisplayName("lapis has strong blue color")
        void lapisHasStrongBlueColor() {
            int color = TrimMaterials.getColor("minecraft:lapis");
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;

            assertTrue(blue > red, "Lapis blue should dominate over red");
            assertTrue(blue > green, "Lapis blue should dominate over green");
        }
    }
}
