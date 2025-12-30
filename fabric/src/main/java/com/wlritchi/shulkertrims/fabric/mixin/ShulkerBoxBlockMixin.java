package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin to handle trim data transfer when shulker boxes are broken.
 * Ensures trim data is copied to the dropped item's custom_data component.
 */
@Mixin(ShulkerBoxBlock.class)
public class ShulkerBoxBlockMixin {

    @Inject(method = "getDroppedStacks", at = @At("RETURN"))
    private void shulkerTrims$addTrimToDrops(BlockState state, LootContextParameterSet.Builder builder,
                                              CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> drops = cir.getReturnValue();
        if (drops.isEmpty()) {
            return;
        }

        // Get the block entity from the loot context
        // The BLOCK_ENTITY parameter contains the block entity being broken
        var blockEntity = builder.getOptional(net.minecraft.loot.context.LootContextParameters.BLOCK_ENTITY);
        if (blockEntity == null || !(blockEntity instanceof ShulkerBoxBlockEntity shulkerBE)) {
            return;
        }

        // Check if it has trim data
        if (!(shulkerBE instanceof TrimmedShulkerBox trimmed) || !trimmed.shulkerTrims$hasTrim()) {
            return;
        }

        ShulkerTrim trim = trimmed.shulkerTrims$getTrim();

        // Add trim to all shulker box drops (typically just one)
        for (ItemStack stack : drops) {
            if (stack.getItem() instanceof net.minecraft.item.BlockItem blockItem &&
                blockItem.getBlock() instanceof ShulkerBoxBlock) {
                ShulkerTrimStorage.writeTrimToItem(stack, trim);
            }
        }
    }
}
