package com.wlritchi.shulkertrims.fabric.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SmithingRecipe;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.item.trim.ArmorTrimMaterial;
import net.minecraft.item.trim.ArmorTrimMaterials;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.item.trim.ArmorTrimPatterns;
import net.minecraft.world.World;

import java.util.Optional;

/**
 * Smithing recipe that applies armor trim patterns to shulker boxes.
 */
public class ShulkerTrimRecipe implements SmithingRecipe {
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;

    public ShulkerTrimRecipe(Ingredient template, Ingredient base, Ingredient addition) {
        this.template = template;
        this.base = base;
        this.addition = addition;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, World world) {
        return testTemplate(input.template()) &&
               testBase(input.base()) &&
               testAddition(input.addition());
    }

    @Override
    public ItemStack craft(SmithingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack baseStack = input.base();
        if (baseStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // Get pattern from template
        ItemStack templateStack = input.template();
        Optional<RegistryEntry.Reference<ArmorTrimPattern>> patternEntry =
            ArmorTrimPatterns.get(registries, templateStack);
        if (patternEntry.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // Get material from addition
        ItemStack additionStack = input.addition();
        Optional<RegistryEntry.Reference<ArmorTrimMaterial>> materialEntry =
            ArmorTrimMaterials.get(registries, additionStack);
        if (materialEntry.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // Create result - copy base stack and apply trim
        ItemStack result = baseStack.copyWithCount(1);
        String pattern = patternEntry.get().registryKey().getValue().toString();
        String material = materialEntry.get().registryKey().getValue().toString();
        ShulkerTrim trim = new ShulkerTrim(pattern, material);
        ShulkerTrimStorage.writeTrimToItem(result, trim);

        return result;
    }

    @Override
    public ItemStack getResult(RegistryWrapper.WrapperLookup registries) {
        // Return empty - result depends on input
        return ItemStack.EMPTY;
    }

    @Override
    public boolean testTemplate(ItemStack stack) {
        return template.test(stack);
    }

    @Override
    public boolean testBase(ItemStack stack) {
        return base.test(stack);
    }

    @Override
    public boolean testAddition(ItemStack stack) {
        return addition.test(stack);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ShulkerTrimsRecipeSerializers.SHULKER_TRIM;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    public Ingredient getTemplate() {
        return template;
    }

    public Ingredient getBase() {
        return base;
    }

    public Ingredient getAddition() {
        return addition;
    }

    /**
     * Serializer for ShulkerTrimRecipe.
     */
    public static class Serializer implements RecipeSerializer<ShulkerTrimRecipe> {
        private static final MapCodec<ShulkerTrimRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Ingredient.ALLOW_EMPTY_CODEC.fieldOf("template").forGetter(ShulkerTrimRecipe::getTemplate),
                Ingredient.ALLOW_EMPTY_CODEC.fieldOf("base").forGetter(ShulkerTrimRecipe::getBase),
                Ingredient.ALLOW_EMPTY_CODEC.fieldOf("addition").forGetter(ShulkerTrimRecipe::getAddition)
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
