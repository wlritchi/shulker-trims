package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod;
import com.wlritchi.shulkertrims.fabric.client.ItemTrimRenderContext;
import com.wlritchi.shulkertrims.fabric.client.ShulkerTrimRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.block.entity.ShulkerBoxBlockEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.ShulkerBoxModelRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Mixin to render trim overlay on shulker box items.
 *
 * Works in conjunction with ItemModelManagerMixin which captures trim data
 * from the ItemStack and stores it in ItemTrimRenderContext.
 */
@Mixin(ShulkerBoxModelRenderer.class)
public class ShulkerBoxModelRendererMixin {

    @Shadow
    @Final
    private ShulkerBoxBlockEntityRenderer blockEntityRenderer;

    @Shadow
    @Final
    private float openness;

    @Shadow
    @Final
    private Direction facing;

    // Cached reflection for accessing model from block entity renderer
    @Unique
    private static Field shulkerTrims$modelField = null;
    @Unique
    private static Method shulkerTrims$setTransformsMethod = null;
    @Unique
    private static boolean shulkerTrims$reflectionInitialized = false;
    @Unique
    private static boolean shulkerTrims$reflectionFailed = false;

    @Unique
    private static void shulkerTrims$initReflection() {
        if (shulkerTrims$reflectionInitialized) return;
        shulkerTrims$reflectionInitialized = true;

        try {
            // Find the model field in ShulkerBoxBlockEntityRenderer
            for (Field field : ShulkerBoxBlockEntityRenderer.class.getDeclaredFields()) {
                if (Model.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    shulkerTrims$modelField = field;
                    break;
                }
            }

            // Find setTransforms method
            for (Method method : ShulkerBoxBlockEntityRenderer.class.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 3 &&
                    params[0] == MatrixStack.class &&
                    params[1] == Direction.class &&
                    params[2] == float.class) {
                    method.setAccessible(true);
                    shulkerTrims$setTransformsMethod = method;
                    break;
                }
            }

            if (shulkerTrims$modelField == null || shulkerTrims$setTransformsMethod == null) {
                ShulkerTrimsMod.LOGGER.error("Failed to find required renderer fields/methods for item trim rendering");
                shulkerTrims$reflectionFailed = true;
            }
        } catch (Exception e) {
            ShulkerTrimsMod.LOGGER.error("Exception during item trim renderer initialization: {}", e.getMessage());
            shulkerTrims$reflectionFailed = true;
        }
    }

    /**
     * Render trim overlay after the main shulker box rendering.
     * Uses direct model submission with armor_trims render layer for correct palette swapping.
     */
    @Inject(method = "render(Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IIZI)V",
            at = @At("TAIL"))
    private void shulkerTrims$renderTrimOverlay(ItemDisplayContext displayContext,
                                                 MatrixStack matrices,
                                                 OrderedRenderCommandQueue commandQueue,
                                                 int light, int overlay,
                                                 boolean useItemLight, int color,
                                                 CallbackInfo ci) {
        // Get trim data for the current ItemRenderState
        ShulkerTrim trim = ItemTrimRenderContext.getCurrentTrim();
        if (trim == null) {
            return;
        }

        // Initialize reflection on first use
        shulkerTrims$initReflection();
        if (shulkerTrims$reflectionFailed) {
            return;
        }

        try {
            // Get model from the block entity renderer
            @SuppressWarnings("unchecked")
            Model<Float> model = (Model<Float>) shulkerTrims$modelField.get(this.blockEntityRenderer);

            // Get the trim sprite from the armor_trims atlas
            SpriteIdentifier trimSpriteId = ShulkerTrimRenderer.getTrimSpriteId(trim);
            Sprite trimSprite = MinecraftClient.getInstance().getAtlasManager().getSprite(trimSpriteId);

            if (trimSprite == null) {
                return;
            }

            // Use armor trims render layer for correct palette swapping
            RenderLayer renderLayer = TexturedRenderLayers.getArmorTrims(false);

            // The vanilla render method already applied transforms and popped the matrix.
            // We need to apply our own transforms from scratch.
            matrices.push();
            shulkerTrims$setTransformsMethod.invoke(this.blockEntityRenderer, matrices, this.facing, this.openness);

            // Submit model directly with correct render layer
            commandQueue.submitModel(
                model,
                this.openness,
                matrices,
                renderLayer,
                light,
                OverlayTexture.DEFAULT_UV,
                -1, // white color (no tint)
                trimSprite,
                0,  // render order
                null // no crumbling
            );

            matrices.pop();
        } catch (Exception e) {
            ShulkerTrimsMod.LOGGER.error("Exception during item trim overlay render: {}", e.getMessage());
        }
    }
}
