package com.wlritchi.shulkertrims.fabric.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.IngredientPlacement;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SmithingRecipe;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimMaterials;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Smithing recipe that applies armor trim patterns to shulker boxes.
 */
public class ShulkerTrimRecipe implements SmithingRecipe {
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private IngredientPlacement ingredientPlacement;

    public ShulkerTrimRecipe(Ingredient template, Ingredient base, Ingredient addition) {
        this.template = template;
        this.base = base;
        this.addition = addition;
    }

    @Override
    public ItemStack craft(SmithingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack baseStack = input.base();
        if (baseStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // Get pattern from template item ID
        // Template items follow pattern: {pattern}_armor_trim_smithing_template
        ItemStack templateStack = input.template();
        Identifier itemId = Registries.ITEM.getId(templateStack.getItem());
        String patternName = extractPatternFromTemplateId(itemId);
        if (patternName == null) {
            return ItemStack.EMPTY;
        }
        String pattern = itemId.getNamespace() + ":" + patternName;

        // Get material from addition
        ItemStack additionStack = input.addition();
        Optional<RegistryEntry<ArmorTrimMaterial>> materialEntry =
            ArmorTrimMaterials.get(registries, additionStack);
        if (materialEntry.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // Create result - copy base stack and apply trim
        ItemStack result = baseStack.copyWithCount(1);
        String material = materialEntry.get().getIdAsString();
        ShulkerTrim trim = new ShulkerTrim(pattern, material);
        ShulkerTrimStorage.writeTrimToItem(result, trim);

        return result;
    }

    /**
     * Extract pattern name from template item ID.
     * Example: "wild_armor_trim_smithing_template" -> "wild"
     */
    private static String extractPatternFromTemplateId(Identifier itemId) {
        String path = itemId.getPath();
        String suffix = "_armor_trim_smithing_template";
        if (path.endsWith(suffix)) {
            return path.substring(0, path.length() - suffix.length());
        }
        return null;
    }

    @Override
    public Optional<Ingredient> template() {
        return Optional.of(template);
    }

    @Override
    public Ingredient base() {
        return base;
    }

    @Override
    public Optional<Ingredient> addition() {
        return Optional.of(addition);
    }

    @Override
    public RecipeSerializer<ShulkerTrimRecipe> getSerializer() {
        return ShulkerTrimsRecipeSerializers.SHULKER_TRIM;
    }

    @Override
    public IngredientPlacement getIngredientPlacement() {
        if (this.ingredientPlacement == null) {
            this.ingredientPlacement = IngredientPlacement.forShapeless(List.of(template, base, addition));
        }
        return this.ingredientPlacement;
    }

    /**
     * Serializer for ShulkerTrimRecipe.
     */
    public static class Serializer implements RecipeSerializer<ShulkerTrimRecipe> {
        private static final MapCodec<ShulkerTrimRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Ingredient.CODEC.fieldOf("template").forGetter(r -> r.template),
                Ingredient.CODEC.fieldOf("base").forGetter(r -> r.base),
                Ingredient.CODEC.fieldOf("addition").forGetter(r -> r.addition)
            ).apply(instance, ShulkerTrimRecipe::new)
        );

        private static final PacketCodec<RegistryByteBuf, ShulkerTrimRecipe> PACKET_CODEC =
            PacketCodec.ofStatic(Serializer::write, Serializer::read);

        @Override
        public MapCodec<ShulkerTrimRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, ShulkerTrimRecipe> packetCodec() {
            return PACKET_CODEC;
        }

        private static ShulkerTrimRecipe read(RegistryByteBuf buf) {
            Ingredient template = Ingredient.PACKET_CODEC.decode(buf);
            Ingredient base = Ingredient.PACKET_CODEC.decode(buf);
            Ingredient addition = Ingredient.PACKET_CODEC.decode(buf);
            return new ShulkerTrimRecipe(template, base, addition);
        }

        private static void write(RegistryByteBuf buf, ShulkerTrimRecipe recipe) {
            Ingredient.PACKET_CODEC.encode(buf, recipe.template);
            Ingredient.PACKET_CODEC.encode(buf, recipe.base);
            Ingredient.PACKET_CODEC.encode(buf, recipe.addition);
        }
    }
}
