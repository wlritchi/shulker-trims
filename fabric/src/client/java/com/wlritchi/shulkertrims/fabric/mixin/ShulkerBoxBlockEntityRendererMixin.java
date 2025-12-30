package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import com.wlritchi.shulkertrims.fabric.client.TrimmedShulkerRenderState;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.render.block.entity.ShulkerBoxBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.ShulkerBoxBlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add trim rendering to shulker box block entities.
 */
@Mixin(ShulkerBoxBlockEntityRenderer.class)
public class ShulkerBoxBlockEntityRendererMixin {

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

        // TODO: Implement actual trim overlay rendering
        // For now, this is a placeholder that will be expanded with:
        // 1. Loading trim pattern texture
        // 2. Applying material color palette
        // 3. Rendering overlay on the shulker box model
        //
        // The rendering will need to:
        // - Get the trim pattern texture (e.g., "shulker_trims:trims/entity/shulker/wild")
        // - Apply the material color (from the color palette system)
        // - Render it on top of the shulker box with proper UV mapping
    }
}
