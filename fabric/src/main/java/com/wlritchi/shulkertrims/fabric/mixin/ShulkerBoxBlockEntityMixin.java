package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add trim storage to ShulkerBoxBlockEntity.
 * Handles NBT persistence and provides access via TrimmedShulkerBox interface.
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

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void shulkerTrims$readTrim(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        this.shulkerTrims$trim = ShulkerTrimStorage.readTrim(nbt);
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void shulkerTrims$writeTrim(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        ShulkerTrimStorage.writeTrim(nbt, this.shulkerTrims$trim);
    }
}
