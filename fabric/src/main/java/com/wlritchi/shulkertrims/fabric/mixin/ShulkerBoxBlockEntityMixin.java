package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add trim storage to ShulkerBoxBlockEntity.
 * Handles data persistence and provides access via TrimmedShulkerBox interface.
 */
@Mixin(ShulkerBoxBlockEntity.class)
public class ShulkerBoxBlockEntityMixin implements TrimmedShulkerBox {

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

    @Inject(method = "readData", at = @At("TAIL"))
    private void shulkerTrims$readTrim(ReadView data, CallbackInfo ci) {
        this.shulkerTrims$trim = ShulkerTrimStorage.readTrimFromData(data);
    }

    @Inject(method = "writeData", at = @At("TAIL"))
    private void shulkerTrims$writeTrim(WriteView data, CallbackInfo ci) {
        ShulkerTrimStorage.writeTrimToData(data, this.shulkerTrims$trim);
    }
}
