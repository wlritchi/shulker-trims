package com.wlritchi.shulkertrims.fabric.mixin;

import net.minecraft.client.render.block.entity.ShulkerBoxBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to access the private model field from ShulkerBoxBlockEntityRenderer.
 * Returns Object because the actual type is an inner class (ShulkerBoxBlockModel).
 */
@Mixin(ShulkerBoxBlockEntityRenderer.class)
public interface ShulkerBoxBlockEntityRendererAccessor {
    @Accessor("model")
    Object shulkerTrims$getModel();
}
