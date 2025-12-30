package com.wlritchi.shulkertrims.fabric;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import org.jetbrains.annotations.Nullable;

/**
 * Interface implemented by ShulkerBoxBlockEntity via mixin.
 * Provides access to trim data stored in the block entity.
 */
public interface TrimmedShulkerBox {
    /**
     * Get the trim applied to this shulker box.
     *
     * @return The trim, or null if not trimmed
     */
    @Nullable
    ShulkerTrim shulkerTrims$getTrim();

    /**
     * Set the trim on this shulker box.
     *
     * @param trim The trim to apply, or null to remove
     */
    void shulkerTrims$setTrim(@Nullable ShulkerTrim trim);

    /**
     * Check if this shulker box has a trim applied.
     */
    default boolean shulkerTrims$hasTrim() {
        return shulkerTrims$getTrim() != null;
    }
}
