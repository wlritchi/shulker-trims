package com.wlritchi.shulkertrims.fabric.client;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4fStack;

/**
 * Provides orthographic-compatible render layers for trim overlays.
 *
 * <p>Vanilla Minecraft's armor trim render layer uses {@code VIEW_OFFSET_Z_LAYERING} to push
 * overlays slightly in front of the base model. This works correctly in perspective projection
 * (where scaling brings things closer), but in orthographic projection the Z translation pushes
 * overlays in the <em>wrong direction</em> (away from camera), causing Z-fighting.
 *
 * <p>Additionally, the vanilla offset magnitude (1/512 ≈ 0.002 units) is too small to reliably
 * prevent Z-fighting with the large depth ranges typically used in orthographic projection.
 *
 * <p>This class provides render layers that work correctly in both projection modes:
 * <ul>
 *   <li>In perspective mode: uses vanilla's {@code getArmorTrims(false)} layer</li>
 *   <li>In orthographic mode: uses a custom layer with larger forward Z offset</li>
 * </ul>
 *
 * @see <a href="https://bugs.mojang.com/browse/MC-XXXXXX">MC-XXXXXX (if reported)</a>
 */
public final class OrthographicTrimRenderLayer extends RenderPhase {

    // Private constructor - this class only provides static utilities
    private OrthographicTrimRenderLayer() {
        super("shulker_trims_orthographic", () -> {}, () -> {});
    }

    /**
     * Offset distance towards camera for orthographic overlay rendering.
     * Uses the same magnitude as vanilla's VIEW_OFFSET_Z_LAYERING (1/512 ≈ 0.00195 units),
     * but translated towards the camera instead of along a fixed axis.
     */
    private static final float ORTHO_CAMERA_OFFSET = 1.0f / 512.0f;

    /**
     * Custom layering phase that translates towards the camera in world space.
     * Unlike vanilla's VIEW_OFFSET_Z_LAYERING which translates along a fixed axis,
     * this computes the camera's look direction and translates towards it.
     */
    private static final RenderPhase.Layering ORTHO_OVERLAY_LAYERING = new RenderPhase.Layering(
            "shulker_trims_ortho_layering",
            () -> {
                Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
                matrix4fStack.pushMatrix();

                // Get camera look direction
                Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
                Vec3d lookVec = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());

                // Translate towards camera (opposite of look direction)
                // This moves the overlay closer to the camera so it wins the depth test
                matrix4fStack.translate(
                        (float) (-lookVec.x * ORTHO_CAMERA_OFFSET),
                        (float) (-lookVec.y * ORTHO_CAMERA_OFFSET),
                        (float) (-lookVec.z * ORTHO_CAMERA_OFFSET)
                );
            },
            () -> {
                Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
                matrix4fStack.popMatrix();
            }
    );

    /**
     * Custom render layer for armor trims with larger forward Z offset.
     * This pushes overlays reliably towards the camera in orthographic mode.
     */
    private static final RenderLayer ARMOR_TRIMS_ORTHO = RenderLayer.of(
            "armor_cutout_no_cull_ortho",
            1536,
            true,
            false,
            RenderPipelines.ARMOR_CUTOUT_NO_CULL,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(new RenderPhase.Texture(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE, false))
                    .lightmap(ENABLE_LIGHTMAP)
                    .overlay(ENABLE_OVERLAY_COLOR)
                    .layering(ORTHO_OVERLAY_LAYERING)
                    .build(true)
    );

    /**
     * Returns the appropriate render layer for trim overlays based on current projection type.
     *
     * <p>This method is only intended for <strong>block entity rendering</strong> (placed shulker
     * boxes in the world). For <strong>item rendering</strong> (GUI, armor stand previews, held
     * items), use {@link TexturedRenderLayers#getArmorTrims(boolean)} directly, as vanilla's
     * layer works correctly for all item rendering contexts.
     *
     * @return render layer that works correctly for the current projection mode
     */
    public static RenderLayer getArmorTrims() {
        if (RenderSystem.getProjectionType() == ProjectionType.ORTHOGRAPHIC) {
            return ARMOR_TRIMS_ORTHO;
        }
        return TexturedRenderLayers.getArmorTrims(false);
    }
}
