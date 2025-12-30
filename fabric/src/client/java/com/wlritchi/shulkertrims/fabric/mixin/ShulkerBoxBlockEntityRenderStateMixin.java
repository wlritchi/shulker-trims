package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.client.TrimmedShulkerRenderState;
import net.minecraft.client.render.block.entity.state.ShulkerBoxBlockEntityRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin to add trim data storage to ShulkerBoxBlockEntityRenderState.
 */
@Mixin(ShulkerBoxBlockEntityRenderState.class)
public class ShulkerBoxBlockEntityRenderStateMixin implements TrimmedShulkerRenderState {

    @Unique
    private @Nullable ShulkerTrim shulkerTrims$trim;

    @Override
    public @Nullable ShulkerTrim shulkerTrims$getTrim() {
        return this.shulkerTrims$trim;
    }

    @Override
    public void shulkerTrims$setTrim(@Nullable ShulkerTrim trim) {
        this.shulkerTrims$trim = trim;
    }
}
