package com.wlritchi.shulkertrims.fabric.recipe;

import com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registry for Shulker Trims recipe serializers.
 */
public final class ShulkerTrimsRecipeSerializers {
    public static final RecipeSerializer<ShulkerTrimRecipe> SHULKER_TRIM =
        new ShulkerTrimRecipe.Serializer();

    private ShulkerTrimsRecipeSerializers() {}

    public static void register() {
        Registry.register(
            Registries.RECIPE_SERIALIZER,
            Identifier.of(ShulkerTrimsMod.MOD_ID, "shulker_trim"),
            SHULKER_TRIM
        );
        ShulkerTrimsMod.LOGGER.info("Registered shulker trim recipe serializer");
    }
}
