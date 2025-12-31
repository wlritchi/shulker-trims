package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import com.wlritchi.shulkertrims.fabric.client.ItemTrimRenderContext;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to capture trim data from shulker box items during render state update.
 *
 * The vanilla item rendering pipeline doesn't pass ItemStack data to special model
 * renderers. This mixin captures trim data and stores it keyed by ItemRenderState
 * so ShulkerBoxModelRendererMixin can access it during render.
 *
 * Uses a WeakHashMap to handle deferred rendering (GUI items) where the render
 * happens later than the update call.
 */
@Mixin(ItemModelManager.class)
public class ItemModelManagerMixin {

    /**
     * Common logic for capturing trim data from a shulker box ItemStack.
     * Stores the trim keyed by the ItemRenderState for later retrieval.
     */
    @Unique
    private static void shulkerTrims$captureTrim(ItemRenderState state, ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem &&
            blockItem.getBlock() instanceof ShulkerBoxBlock) {
            ShulkerTrim trim = ShulkerTrimStorage.readTrimFromItem(stack);
            ItemTrimRenderContext.setTrim(state, trim);
        }
    }

    @Inject(method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/world/World;Lnet/minecraft/util/HeldItemContext;I)V",
            at = @At("HEAD"))
    private void shulkerTrims$captureTrimDataUpdate(ItemRenderState state, ItemStack stack,
                                                     ItemDisplayContext displayContext, World world,
                                                     HeldItemContext heldItemContext, int seed,
                                                     CallbackInfo ci) {
        shulkerTrims$captureTrim(state, stack);
    }

    @Inject(method = "clearAndUpdate(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/world/World;Lnet/minecraft/util/HeldItemContext;I)V",
            at = @At("HEAD"))
    private void shulkerTrims$captureTrimDataClearAndUpdate(ItemRenderState state, ItemStack stack,
                                                             ItemDisplayContext displayContext, World world,
                                                             HeldItemContext heldItemContext, int seed,
                                                             CallbackInfo ci) {
        shulkerTrims$captureTrim(state, stack);
    }

    @Inject(method = "updateForLivingEntity(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/entity/LivingEntity;)V",
            at = @At("HEAD"))
    private void shulkerTrims$captureTrimDataLiving(ItemRenderState state, ItemStack stack,
                                                     ItemDisplayContext displayContext,
                                                     LivingEntity entity,
                                                     CallbackInfo ci) {
        shulkerTrims$captureTrim(state, stack);
    }

    @Inject(method = "updateForNonLivingEntity(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/entity/Entity;)V",
            at = @At("HEAD"))
    private void shulkerTrims$captureTrimDataEntity(ItemRenderState state, ItemStack stack,
                                                     ItemDisplayContext displayContext,
                                                     Entity entity,
                                                     CallbackInfo ci) {
        shulkerTrims$captureTrim(state, stack);
    }
}
