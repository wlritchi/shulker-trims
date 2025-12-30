package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
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
public abstract class ShulkerBoxBlockEntityMixin extends BlockEntity implements TrimmedShulkerBox {

    private ShulkerBoxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

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
        com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod.LOGGER.info("[DEBUG] readData called on ShulkerBoxBlockEntity");
        this.shulkerTrims$trim = ShulkerTrimStorage.readTrimFromData(data);
        if (this.shulkerTrims$trim != null) {
            com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod.LOGGER.info(
                "Loaded trim from block entity: pattern={}, material={}",
                this.shulkerTrims$trim.pattern(), this.shulkerTrims$trim.material());
        } else {
            com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod.LOGGER.info("[DEBUG] No trim data found in readData");
        }
    }

    @Inject(method = "writeData", at = @At("TAIL"))
    private void shulkerTrims$writeTrim(WriteView data, CallbackInfo ci) {
        ShulkerTrimStorage.writeTrimToData(data, this.shulkerTrims$trim);
    }

    /**
     * Override toInitialChunkDataNbt to include trim data in client sync.
     * The default implementation returns empty NBT, so our trim never reaches the client.
     */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        // Create NBT with our trim data for client sync
        NbtCompound nbt = new NbtCompound();
        if (this.shulkerTrims$trim != null) {
            ShulkerTrimStorage.writeTrim(nbt, this.shulkerTrims$trim);
            com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod.LOGGER.info(
                "[DEBUG] toInitialChunkDataNbt: including trim pattern={}, material={}",
                this.shulkerTrims$trim.pattern(), this.shulkerTrims$trim.material());
        }
        return nbt;
    }
}
