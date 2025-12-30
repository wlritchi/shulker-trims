package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import com.wlritchi.shulkertrims.fabric.client.ShulkerTrimRenderer;
import com.wlritchi.shulkertrims.fabric.client.TrimmedShulkerRenderState;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.block.entity.ShulkerBoxBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.ShulkerBoxBlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteHolder;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add trim rendering to shulker box block entities.
 */
@Mixin(ShulkerBoxBlockEntityRenderer.class)
public abstract class ShulkerBoxBlockEntityRendererMixin {

    @Shadow @Final private SpriteHolder materials;

    @Unique
    private static boolean shulkerTrims$hasLoggedTrim = false;

    /**
     * Copy trim data from block entity to render state.
     */
    @Inject(method = "updateRenderState(Lnet/minecraft/block/entity/ShulkerBoxBlockEntity;Lnet/minecraft/client/render/block/entity/state/ShulkerBoxBlockEntityRenderState;FLnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
            at = @At("TAIL"))
    private void shulkerTrims$copyTrimToRenderState(ShulkerBoxBlockEntity entity,
                                                     ShulkerBoxBlockEntityRenderState renderState,
                                                     float tickDelta, Vec3d cameraPos,
                                                     ModelCommandRenderer.CrumblingOverlayCommand crumblingCommand,
                                                     CallbackInfo ci) {
        if (entity instanceof TrimmedShulkerBox trimmed && renderState instanceof TrimmedShulkerRenderState trimmedState) {
            trimmedState.shulkerTrims$setTrim(trimmed.shulkerTrims$getTrim());
        }
    }

    /**
     * Render trim overlay after the main shulker box rendering.
     * Uses the armor trims render layer for proper depth handling.
     */
    @Inject(method = "render(Lnet/minecraft/client/render/block/entity/state/ShulkerBoxBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("TAIL"))
    private void shulkerTrims$renderTrimOverlay(ShulkerBoxBlockEntityRenderState renderState,
                                                 MatrixStack matrices,
                                                 OrderedRenderCommandQueue commandQueue,
                                                 CameraRenderState cameraState,
                                                 CallbackInfo ci) {
        if (!(renderState instanceof TrimmedShulkerRenderState trimmedState)) {
            return;
        }

        ShulkerTrim trim = trimmedState.shulkerTrims$getTrim();
        if (trim == null) {
            return;
        }

        // Log once to confirm trim data is flowing
        if (!shulkerTrims$hasLoggedTrim) {
            ShulkerTrimsMod.LOGGER.info("Rendering shulker with trim: pattern={}, material={}",
                trim.pattern(), trim.material());
            shulkerTrims$hasLoggedTrim = true;
        }

        // Get the trim sprite from the armor_trims atlas
        SpriteIdentifier spriteId = ShulkerTrimRenderer.getTrimSpriteId(trim);
        Sprite sprite = materials.getSprite(spriteId);

        // Use the armor trims decal render layer - it has depth settings for rendering on surfaces
        RenderLayer renderLayer = TexturedRenderLayers.getArmorTrims(true);

        // Get the model via the renderer's render method that we can invoke
        ShulkerBoxBlockEntityRenderer renderer = (ShulkerBoxBlockEntityRenderer)(Object)this;

        // Apply transformations and render with proper overlay render layer
        matrices.push();
        shulkerTrims$applyTransforms(matrices, renderState.facing);

        // Use invoker to get model and render with proper layer
        // We need to submit the model ourselves with the correct render layer
        shulkerTrims$renderWithModel(renderer, renderState, matrices, commandQueue, sprite, renderLayer);

        matrices.pop();
    }

    /**
     * Apply the same transformations as the base ShulkerBoxBlockEntityRenderer.
     */
    @Unique
    private void shulkerTrims$applyTransforms(MatrixStack matrices, Direction facing) {
        matrices.translate(0.5f, 0.5f, 0.5f);
        matrices.scale(0.9995f, 0.9995f, 0.9995f);
        matrices.multiply(facing.getRotationQuaternion());
        matrices.scale(1.0f, -1.0f, -1.0f);
        matrices.translate(0.0f, -1.0f, 0.0f);
    }

    /**
     * Render the trim overlay using the model from the renderer.
     * Uses the accessor mixin to get the model.
     */
    @Unique
    private void shulkerTrims$renderWithModel(ShulkerBoxBlockEntityRenderer renderer,
                                               ShulkerBoxBlockEntityRenderState renderState,
                                               MatrixStack matrices,
                                               OrderedRenderCommandQueue commandQueue,
                                               Sprite sprite,
                                               RenderLayer renderLayer) {
        // Access the model via the accessor mixin (returns Object due to inner class type)
        @SuppressWarnings("unchecked")
        Model<Float> model = (Model<Float>) ((ShulkerBoxBlockEntityRendererAccessor) renderer).shulkerTrims$getModel();

        // Set model animation state
        model.setAngles(renderState.animationProgress);

        // Submit the model with the armor trims render layer
        commandQueue.submitModel(
            model,
            renderState.animationProgress,
            matrices,
            renderLayer,
            renderState.lightmapCoordinates,
            OverlayTexture.DEFAULT_UV,
            -1, // White color (no tint)
            sprite,
            0,
            null // No crumbling overlay for trim
        );
    }
}
