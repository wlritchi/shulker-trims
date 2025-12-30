package com.wlritchi.shulkertrims.fabric.client;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import org.jetbrains.annotations.Nullable;

/**
 * Duck interface to add trim data to ShulkerBoxBlockEntityRenderState.
 */
public interface TrimmedShulkerRenderState {
    @Nullable ShulkerTrim shulkerTrims$getTrim();
    void shulkerTrims$setTrim(@Nullable ShulkerTrim trim);
}
