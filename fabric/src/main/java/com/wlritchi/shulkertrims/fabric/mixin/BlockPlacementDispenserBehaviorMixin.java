package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.dispenser.BlockPlacementDispenserBehavior;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to transfer trim data when dispensers place shulker boxes.
 */
@Mixin(BlockPlacementDispenserBehavior.class)
public class BlockPlacementDispenserBehaviorMixin {

    // ThreadLocal to store trim data between HEAD and RETURN injections
    @Unique
    private static final ThreadLocal<ShulkerTrim> shulkerTrims$pendingTrim = new ThreadLocal<>();

    @Inject(method = "dispenseSilently", at = @At("HEAD"))
    private void shulkerTrims$captureTrimBeforeDispense(BlockPointer pointer, ItemStack stack,
                                                         CallbackInfoReturnable<ItemStack> cir) {
        // Capture trim data before the item is consumed
        ShulkerTrim trim = ShulkerTrimStorage.readTrimFromItem(stack);
        shulkerTrims$pendingTrim.set(trim);
    }

    @Inject(method = "dispenseSilently", at = @At("RETURN"))
    private void shulkerTrims$transferTrimAfterDispense(BlockPointer pointer, ItemStack stack,
                                                         CallbackInfoReturnable<ItemStack> cir) {
        ShulkerTrim trim = shulkerTrims$pendingTrim.get();
        shulkerTrims$pendingTrim.remove();

        if (trim == null) {
            return;
        }

        World world = pointer.world();
        Direction facing = pointer.state().get(net.minecraft.block.DispenserBlock.FACING);
        BlockPos targetPos = pointer.pos().offset(facing);
        BlockState targetState = world.getBlockState(targetPos);

        // Check if a shulker box was placed at the target position
        if (!(targetState.getBlock() instanceof ShulkerBoxBlock)) {
            return;
        }

        // Transfer trim to the placed block entity
        BlockEntity blockEntity = world.getBlockEntity(targetPos);
        if (blockEntity instanceof ShulkerBoxBlockEntity shulkerBE &&
            shulkerBE instanceof TrimmedShulkerBox trimmed) {
            trimmed.shulkerTrims$setTrim(trim);
            blockEntity.markDirty();
        }
    }
}
