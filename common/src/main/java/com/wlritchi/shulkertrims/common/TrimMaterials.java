package com.wlritchi.shulkertrims.common;

import java.util.Map;

/**
 * Trim material color palette, matching vanilla armor trim colors.
 */
public final class TrimMaterials {
    private TrimMaterials() {}

    /**
     * Material colors as RGB integers.
     * These match vanilla armor trim material palettes.
     */
    public static final Map<String, Integer> COLORS = Map.ofEntries(
        Map.entry("minecraft:quartz", 0xE3D4C4),
        Map.entry("minecraft:iron", 0xECECEC),
        Map.entry("minecraft:netherite", 0x625859),
        Map.entry("minecraft:redstone", 0x971607),
        Map.entry("minecraft:copper", 0xB4684D),
        Map.entry("minecraft:gold", 0xDEB12D),
        Map.entry("minecraft:emerald", 0x11A036),
        Map.entry("minecraft:diamond", 0x6EECD2),
        Map.entry("minecraft:lapis", 0x416E97),
        Map.entry("minecraft:amethyst", 0x9A5CC6)
    );

    /**
     * Get the color for a material, or a default gray if unknown.
     */
    public static int getColor(String material) {
        return COLORS.getOrDefault(material, 0x808080);
    }

    /**
     * Get color components as floats (0.0-1.0) for rendering.
     */
    public static float[] getColorComponents(String material) {
        int color = getColor(material);
        return new float[] {
            ((color >> 16) & 0xFF) / 255.0f,
            ((color >> 8) & 0xFF) / 255.0f,
            (color & 0xFF) / 255.0f
        };
    }
}
