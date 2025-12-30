package com.wlritchi.shulkertrims.fabric.client;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Helper class for rendering trim overlays on shulker boxes.
 */
public final class ShulkerTrimRenderer {
    private ShulkerTrimRenderer() {}

    /**
     * Texture atlas for shulker trims (will use armor_trims for now).
     */
    public static final Identifier ARMOR_TRIMS_ATLAS = Identifier.of("minecraft", "textures/atlas/armor_trims.png");

    /**
     * Material color mappings (RGB colors for each trim material).
     * These are approximate colors - the actual system uses palette swapping.
     */
    private static final Map<String, Integer> MATERIAL_COLORS = Map.ofEntries(
        Map.entry("minecraft:quartz", 0xE3D4C4),
        Map.entry("minecraft:iron", 0xCECACA),
        Map.entry("minecraft:netherite", 0x3D3535),
        Map.entry("minecraft:redstone", 0x8F0000),
        Map.entry("minecraft:copper", 0xB46D4F),
        Map.entry("minecraft:gold", 0xF9D849),
        Map.entry("minecraft:emerald", 0x0FA044),
        Map.entry("minecraft:diamond", 0x6DCED8),
        Map.entry("minecraft:lapis", 0x1C4A9E),
        Map.entry("minecraft:amethyst", 0x9957C6),
        Map.entry("minecraft:resin", 0xE27712)
    );

    /**
     * Get the color for a trim material.
     * @param material The material identifier (e.g., "minecraft:gold")
     * @return The RGB color, or white if unknown
     */
    public static int getMaterialColor(String material) {
        return MATERIAL_COLORS.getOrDefault(material, 0xFFFFFF);
    }

    /**
     * Get RGBA components from a color.
     */
    public static float[] getColorComponents(int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new float[] { r, g, b, 1.0f };
    }

    /**
     * Get the trim pattern texture identifier.
     * For now, returns null as we need to create the actual textures.
     *
     * @param trim The trim data
     * @return The sprite identifier for the trim pattern, or null if not available
     */
    public static SpriteIdentifier getTrimSprite(ShulkerTrim trim) {
        // TODO: Create actual shulker trim textures
        // The texture path would be something like:
        // shulker_trims:trims/entity/shulker/{pattern}_{material}
        // Using paletted permutations like armor trims
        return null;
    }
}
