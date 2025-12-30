package com.wlritchi.shulkertrims.fabric.mixin;

import net.minecraft.client.model.Model;
import net.minecraft.client.render.block.entity.ShulkerBoxBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to access the private model field from ShulkerBoxBlockEntityRenderer.
 */
@Mixin(ShulkerBoxBlockEntityRenderer.class)
public interface ShulkerBoxBlockEntityRendererAccessor {
    @Accessor("model")
    Model<Float> shulkerTrims$getModel();
}
