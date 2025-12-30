package com.wlritchi.shulkertrims.bukkit;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.SmithingTrimRecipe;
import org.bukkit.inventory.RecipeChoice;

/**
 * Handles registration of smithing table recipes for shulker trims.
 */
public final class ShulkerTrimsRecipes {
    private ShulkerTrimsRecipes() {}

    private static final Material[] SHULKER_BOXES = {
        Material.SHULKER_BOX,
        Material.WHITE_SHULKER_BOX,
        Material.ORANGE_SHULKER_BOX,
        Material.MAGENTA_SHULKER_BOX,
        Material.LIGHT_BLUE_SHULKER_BOX,
        Material.YELLOW_SHULKER_BOX,
        Material.LIME_SHULKER_BOX,
        Material.PINK_SHULKER_BOX,
        Material.GRAY_SHULKER_BOX,
        Material.LIGHT_GRAY_SHULKER_BOX,
        Material.CYAN_SHULKER_BOX,
        Material.PURPLE_SHULKER_BOX,
        Material.BLUE_SHULKER_BOX,
        Material.BROWN_SHULKER_BOX,
        Material.GREEN_SHULKER_BOX,
        Material.RED_SHULKER_BOX,
        Material.BLACK_SHULKER_BOX
    };

    private static final Material[] TRIM_TEMPLATES = {
        Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE
    };

    private static final Material[] TRIM_MATERIALS = {
        Material.QUARTZ,
        Material.IRON_INGOT,
        Material.NETHERITE_INGOT,
        Material.REDSTONE,
        Material.COPPER_INGOT,
        Material.GOLD_INGOT,
        Material.EMERALD,
        Material.DIAMOND,
        Material.LAPIS_LAZULI,
        Material.AMETHYST_SHARD
    };

    public static void register(ShulkerTrimsPlugin plugin) {
        plugin.getLogger().info("Registering shulker trim recipes...");

        // Register a smithing trim recipe for shulker boxes
        // Note: SmithingTrimRecipe applies trim to armor - we need custom handling
        // for shulker boxes since they're not armor items.
        // We'll use PrepareSmithingEvent to intercept and handle custom output.

        // For now, register recipes that will be intercepted by our event handler
        for (int i = 0; i < SHULKER_BOXES.length; i++) {
            Material shulker = SHULKER_BOXES[i];
            NamespacedKey key = new NamespacedKey(plugin, "trim_" + shulker.name().toLowerCase());

            // Create recipe choice for all templates
            RecipeChoice.MaterialChoice templateChoice = new RecipeChoice.MaterialChoice(TRIM_TEMPLATES);
            RecipeChoice.MaterialChoice baseChoice = new RecipeChoice.MaterialChoice(shulker);
            RecipeChoice.MaterialChoice additionChoice = new RecipeChoice.MaterialChoice(TRIM_MATERIALS);

            SmithingTrimRecipe recipe = new SmithingTrimRecipe(key, templateChoice, baseChoice, additionChoice);
            plugin.getServer().addRecipe(recipe);
        }

        plugin.getLogger().info("Registered " + SHULKER_BOXES.length + " shulker trim recipes");
    }
}
