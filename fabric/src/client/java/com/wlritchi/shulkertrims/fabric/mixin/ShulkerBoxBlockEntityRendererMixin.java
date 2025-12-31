package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
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
import net.minecraft.util.Identifier;
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

    @Unique
    private static int shulkerTrims$logCount = 0;

    @Unique
    private static boolean shulkerTrims$hasLoggedTrim = false;

    @Unique
    private static boolean shulkerTrims$hasLoggedSpriteInfo = false;

    // Cached reflection fields
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
                    ShulkerTrimsMod.LOGGER.info("[REFLECTION] Found model field: {}", field.getName());
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
                    ShulkerTrimsMod.LOGGER.info("[REFLECTION] Found setTransforms method: {}", method.getName());
                    break;
                }
            }

            if (shulkerTrims$modelField == null || shulkerTrims$setTransformsMethod == null) {
                ShulkerTrimsMod.LOGGER.error("[REFLECTION] Failed to find required fields/methods");
                shulkerTrims$reflectionFailed = true;
            }
        } catch (Exception e) {
            ShulkerTrimsMod.LOGGER.error("[REFLECTION] Exception during initialization: {}", e.getMessage());
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
        boolean entityIsTrimmed = entity instanceof TrimmedShulkerBox;
        boolean stateIsTrimmed = renderState instanceof TrimmedShulkerRenderState;

        // Log first 3 calls to help debug
        if (shulkerTrims$logCount < 3) {
            shulkerTrims$logCount++;
            ShulkerTrimsMod.LOGGER.info("[DEBUG] updateRenderState #{}: entity instanceof TrimmedShulkerBox = {}", shulkerTrims$logCount, entityIsTrimmed);
            ShulkerTrimsMod.LOGGER.info("[DEBUG] updateRenderState #{}: renderState instanceof TrimmedShulkerRenderState = {}", shulkerTrims$logCount, stateIsTrimmed);
            if (entityIsTrimmed) {
                ShulkerTrim trim = ((TrimmedShulkerBox) entity).shulkerTrims$getTrim();
                ShulkerTrimsMod.LOGGER.info("[DEBUG] updateRenderState #{}: entity trim = {}", shulkerTrims$logCount, trim);
            }
        }

        if (entityIsTrimmed && stateIsTrimmed) {
            TrimmedShulkerBox trimmed = (TrimmedShulkerBox) entity;
            TrimmedShulkerRenderState trimmedState = (TrimmedShulkerRenderState) renderState;
            trimmedState.shulkerTrims$setTrim(trimmed.shulkerTrims$getTrim());
        }
    }

    /**
     * Render trim overlay AFTER the main shulker box rendering.
     * Testing TAIL to see if vanilla works with this timing.
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

        // Get the trim sprite identifier from the armor_trims atlas
        SpriteIdentifier trimSpriteId = ShulkerTrimRenderer.getTrimSpriteId(trim);

        // Detailed sprite diagnostics - log once per session
        if (!shulkerTrims$hasLoggedSpriteInfo) {
            shulkerTrims$hasLoggedSpriteInfo = true;
            ShulkerTrimsMod.LOGGER.info("[SPRITE DEBUG] Trim: pattern={}, material={}", trim.pattern(), trim.material());
            ShulkerTrimsMod.LOGGER.info("[SPRITE DEBUG] SpriteIdentifier atlas={}, texture={}",
                trimSpriteId.getAtlasId(), trimSpriteId.getTextureId());

            // Try to get the actual sprite from the atlas
            try {
                Sprite sprite = MinecraftClient.getInstance()
                    .getAtlasManager()
                    .getSprite(trimSpriteId);
                if (sprite != null) {
                    Identifier spriteId = sprite.getContents().getId();
                    ShulkerTrimsMod.LOGGER.info("[SPRITE DEBUG] Sprite found! contents.id={}", spriteId);
                    // Check if it's the missing texture
                    if (spriteId.getPath().contains("missingno") || spriteId.toString().contains("missing")) {
                        ShulkerTrimsMod.LOGGER.warn("[SPRITE DEBUG] WARNING: Sprite is MISSING TEXTURE!");
                    }
                    ShulkerTrimsMod.LOGGER.info("[SPRITE DEBUG] Sprite UV: u0={}, u1={}, v0={}, v1={}",
                        sprite.getMinU(), sprite.getMaxU(), sprite.getMinV(), sprite.getMaxV());
                } else {
                    ShulkerTrimsMod.LOGGER.warn("[SPRITE DEBUG] WARNING: getSprite() returned null!");
                }
            } catch (Exception e) {
                ShulkerTrimsMod.LOGGER.error("[SPRITE DEBUG] Exception while checking sprite: {}", e.getMessage());
            }
        }

        // Initialize reflection on first use
        shulkerTrims$initReflection();
        if (shulkerTrims$reflectionFailed) {
            return;
        }

        try {
            // Get model and sprite via reflection
            @SuppressWarnings("unchecked")
            Model<Float> model = (Model<Float>) shulkerTrims$modelField.get(this);
            Sprite trimSprite = MinecraftClient.getInstance().getAtlasManager().getSprite(trimSpriteId);

            if (trimSprite == null) {
                if (!shulkerTrims$hasLoggedTrim) {
                    ShulkerTrimsMod.LOGGER.warn("[RENDER DEBUG] trimSprite is null!");
                }
                return;
            }

            // Use the armor trims render layer - this is the key fix!
            // The shulker model's getRenderLayer function returns a layer for shulker_boxes atlas,
            // but we need a layer for the armor_trims atlas
            RenderLayer renderLayer = TexturedRenderLayers.getArmorTrims(false);

            // Log render call details once
            if (!shulkerTrims$hasLoggedTrim) {
                shulkerTrims$hasLoggedTrim = true;
                ShulkerTrimsMod.LOGGER.info("[RENDER DEBUG] Rendering trim with direct submitModel");
                ShulkerTrimsMod.LOGGER.info("[RENDER DEBUG] trimSprite={}", trimSprite.getContents().getId());
                ShulkerTrimsMod.LOGGER.info("[RENDER DEBUG] renderLayer={}", renderLayer);
            }

            // Push matrix and apply transforms
            matrices.push();
            shulkerTrims$setTransformsMethod.invoke(this, matrices, renderState.facing, renderState.animationProgress);

            // Submit model directly with the correct render layer for armor_trims atlas
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
            if (!shulkerTrims$hasLoggedTrim) {
                shulkerTrims$hasLoggedTrim = true;
                ShulkerTrimsMod.LOGGER.error("[RENDER DEBUG] Exception during render: {}", e.getMessage(), e);
            }
        }
    }
}
