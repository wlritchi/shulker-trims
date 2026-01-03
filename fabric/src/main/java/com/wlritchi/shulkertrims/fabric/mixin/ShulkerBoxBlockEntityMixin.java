package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.ReadView;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin to add trim storage to ShulkerBoxBlockEntity.
 *
 * Strategy: Let vanilla handle all component persistence (custom_data transfers
 * automatically). We just read the trim from BE components when needed, and
 * cache it for rendering performance. For client sync, we include the trim
 * in the initial chunk data.
 */
@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerBoxBlockEntityMixin extends BlockEntity implements TrimmedShulkerBox {

    private ShulkerBoxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private @Nullable ShulkerTrim shulkerTrims$cachedTrim;

    @Unique
    private boolean shulkerTrims$trimLoaded = false;

    @Override
    public @Nullable ShulkerTrim shulkerTrims$getTrim() {
        // Lazy-load trim from BE components
        if (!this.shulkerTrims$trimLoaded) {
            this.shulkerTrims$trimLoaded = true;
            NbtComponent customData = this.getComponents().get(DataComponentTypes.CUSTOM_DATA);
            if (customData != null) {
                this.shulkerTrims$cachedTrim = ShulkerTrimStorage.readTrim(customData.copyNbt());
            }
        }
        return this.shulkerTrims$cachedTrim;
    }

    @Override
    public void shulkerTrims$setTrim(@Nullable ShulkerTrim trim) {
        this.shulkerTrims$cachedTrim = trim;
        this.shulkerTrims$trimLoaded = true;
        this.markDirty();
    }

    /**
     * Include trim data in client sync packet.
     * Client doesn't have access to BE components, so we send trim in NBT.
     */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound nbt = super.toInitialChunkDataNbt(registryLookup);
        ShulkerTrim trim = this.shulkerTrims$getTrim();
        if (trim != null) {
            ShulkerTrimStorage.writeTrim(nbt, trim);
        }
        return nbt;
    }

    /**
     * Return update packet for runtime block entity syncs (e.g., dispenser placement).
     */
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    /**
     * Read trim from sync NBT on client, or from components on disk load.
     */
    @Override
    public void readData(ReadView data) {
        super.readData(data);

        // Reset cache - will be lazy-loaded from components
        this.shulkerTrims$trimLoaded = false;
        this.shulkerTrims$cachedTrim = null;

        // Check for sync data (top-level NBT from toInitialChunkDataNbt)
        ShulkerTrim syncTrim = ShulkerTrimStorage.readTrimFromData(data);
        if (syncTrim != null) {
            this.shulkerTrims$cachedTrim = syncTrim;
            this.shulkerTrims$trimLoaded = true;
        }
    }
}
