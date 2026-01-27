package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import com.wlritchi.shulkertrims.fabric.client.OrthographicTrimRenderLayer;
import com.wlritchi.shulkertrims.fabric.client.ShulkerTrimRenderer;
import com.wlritchi.shulkertrims.fabric.client.TrimmedShulkerRenderState;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.block.entity.ShulkerBoxBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.ShulkerBoxBlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Mixin to add trim rendering to shulker box block entities.
 */
@Mixin(ShulkerBoxBlockEntityRenderer.class)
public abstract class ShulkerBoxBlockEntityRendererMixin {

    // Cached reflection fields for accessing private renderer internals
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
            // Find the model field - it's the only Model<?> field
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
                ShulkerTrimsMod.LOGGER.error("Failed to find required renderer fields/methods for trim rendering");
                shulkerTrims$reflectionFailed = true;
            }
        } catch (Exception e) {
            ShulkerTrimsMod.LOGGER.error("Exception during trim renderer initialization: {}", e.getMessage());
            shulkerTrims$reflectionFailed = true;
        }
    }

    /**
     * Copy trim data from block entity to render state.
     */
    @Inject(method = "updateRenderState(Lnet/minecraft/block/entity/ShulkerBoxBlockEntity;Lnet/minecraft/client/render/block/entity/state/ShulkerBoxBlockEntityRenderState;FLnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
            at = @At("TAIL"))
    private void shulkerTrims$copyTrimToRenderState(ShulkerBoxBlockEntity entity,
                                                     ShulkerBoxBlockEntityRenderState renderState,
                                                     float tickDelta, Vec3d cameraPos,
                                                     ModelCommandRenderer.CrumblingOverlayCommand crumblingCommand,
                                                     CallbackInfo ci) {
        if (entity instanceof TrimmedShulkerBox trimmed && renderState instanceof TrimmedShulkerRenderState trimmedState) {
            trimmedState.shulkerTrims$setTrim(trimmed.shulkerTrims$getTrim());
        }
    }

    /**
     * Render trim overlay after the main shulker box rendering.
     */
    @Inject(method = "render(Lnet/minecraft/client/render/block/entity/state/ShulkerBoxBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("TAIL"))
    private void shulkerTrims$renderTrimOverlay(ShulkerBoxBlockEntityRenderState renderState,
                                                 MatrixStack matrices,
                                                 OrderedRenderCommandQueue commandQueue,
                                                 CameraRenderState cameraState,
                                                 CallbackInfo ci) {
        if (!(renderState instanceof TrimmedShulkerRenderState trimmedState)) {
            return;
        }

        ShulkerTrim trim = trimmedState.shulkerTrims$getTrim();
        if (trim == null) {
            return;
        }

        // Initialize reflection on first use
        shulkerTrims$initReflection();
        if (shulkerTrims$reflectionFailed) {
            return;
        }

        try {
            // Get model and sprite
            @SuppressWarnings("unchecked")
            Model<Float> model = (Model<Float>) shulkerTrims$modelField.get(this);
            SpriteIdentifier trimSpriteId = ShulkerTrimRenderer.getTrimSpriteId(trim);
            Sprite trimSprite = MinecraftClient.getInstance().getAtlasManager().getSprite(trimSpriteId);

            if (trimSprite == null) {
                return;
            }

            // Use the armor trims render layer - the shulker model's getRenderLayer function
            // returns a layer for shulker_boxes atlas, but our sprites are in armor_trims atlas.
            // OrthographicTrimRenderLayer handles the Z-offset direction bug in orthographic mode.
            RenderLayer renderLayer = OrthographicTrimRenderLayer.getArmorTrims();

            // Apply transforms and render
            matrices.push();
            shulkerTrims$setTransformsMethod.invoke(this, matrices, renderState.facing, renderState.animationProgress);

            commandQueue.submitModel(
                model,
                renderState.animationProgress,
                matrices,
                renderLayer,
                renderState.lightmapCoordinates,
                OverlayTexture.DEFAULT_UV,
                -1, // white color (no tint)
                trimSprite,
                0,  // render order
                null // no crumbling
            );

            matrices.pop();
        } catch (Exception e) {
            ShulkerTrimsMod.LOGGER.error("Exception during trim overlay render: {}", e.getMessage());
        }
    }
}
