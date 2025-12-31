package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.fabric.client.ItemTrimRenderContext;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to track the current ItemRenderState during layer rendering.
 *
 * This sets a thread-local with the parent ItemRenderState before the
 * SpecialModelRenderer.render() call, so ShulkerBoxModelRendererMixin
 * can look up trim data for the correct ItemRenderState.
 */
@Mixin(targets = "net.minecraft.client.render.item.ItemRenderState$LayerRenderState")
public class LayerRenderStateMixin {

    @Shadow
    @Final
    ItemRenderState field_55345;  // Parent ItemRenderState

    /**
     * Set the current render state before the render method processes special models.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void shulkerTrims$setCurrentRenderState(MatrixStack matrices, OrderedRenderCommandQueue commandQueue,
                                                     int light, int overlay, int color, CallbackInfo ci) {
        ItemTrimRenderContext.setCurrentRenderState(this.field_55345);
    }

    /**
     * Clear the current render state after render completes.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void shulkerTrims$clearCurrentRenderState(MatrixStack matrices, OrderedRenderCommandQueue commandQueue,
                                                       int light, int overlay, int color, CallbackInfo ci) {
        ItemTrimRenderContext.clearCurrentRenderState();
    }
}
