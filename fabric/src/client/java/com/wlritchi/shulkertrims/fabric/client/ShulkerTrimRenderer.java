package com.wlritchi.shulkertrims.fabric.client;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Helper class for rendering trim overlays on shulker boxes.
 */
public final class ShulkerTrimRenderer {
    private ShulkerTrimRenderer() {}

    /**
     * Get the texture identifier for a shulker trim sprite.
     * The texture is located in the armor_trims atlas with path:
     * shulker_trims:trims/entity/shulker/{pattern}_{material}
     *
     * @param trim The trim data
     * @return The texture identifier for the trim sprite
     */
    public static Identifier getTrimTextureId(ShulkerTrim trim) {
        String patternPath = getPath(trim.pattern());
        String materialPath = getPath(trim.material());
        return Identifier.of("shulker_trims", "trims/entity/shulker/" + patternPath + "_" + materialPath);
    }

    /**
     * Get a SpriteIdentifier for the trim, referencing the armor_trims atlas.
     *
     * @param trim The trim data
     * @return The sprite identifier for the trim pattern
     */
    public static SpriteIdentifier getTrimSpriteId(ShulkerTrim trim) {
        return new SpriteIdentifier(
            TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE,
            getTrimTextureId(trim)
        );
    }

    /**
     * Extract the path from a namespaced identifier string (e.g., "minecraft:wild" -> "wild")
     */
    private static String getPath(@Nullable String identifier) {
        if (identifier == null) {
            return "unknown";
        }
        int colonIndex = identifier.indexOf(':');
        if (colonIndex >= 0 && colonIndex < identifier.length() - 1) {
            return identifier.substring(colonIndex + 1);
        }
        return identifier;
    }
}
