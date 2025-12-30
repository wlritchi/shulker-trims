package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to transfer trim data from item to block entity when placing shulker boxes.
 */
@Mixin(BlockItem.class)
public class BlockItemMixin {

    @Inject(method = "postPlacement", at = @At("HEAD"))
    private void shulkerTrims$transferTrimToBlockEntity(BlockPos pos, World world, PlayerEntity player,
                                                         ItemStack stack, BlockState state,
                                                         CallbackInfoReturnable<Boolean> cir) {
        // Only process shulker boxes
        if (!(state.getBlock() instanceof ShulkerBoxBlock)) {
            return;
        }

        // Check if the item has trim data
        ShulkerTrim trim = ShulkerTrimStorage.readTrimFromItem(stack);
        if (trim == null) {
            return;
        }

        // Get the block entity and apply trim
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof ShulkerBoxBlockEntity shulkerBE &&
            shulkerBE instanceof TrimmedShulkerBox trimmed) {
            trimmed.shulkerTrims$setTrim(trim);
            blockEntity.markDirty();
        }
    }
}
