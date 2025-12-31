package com.wlritchi.shulkertrims.bukkit;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Utility for reading/writing ShulkerTrim data using NMS.
 *
 * Storage format matches Fabric implementation exactly:
 * - Items: minecraft:custom_data component containing {"shulker_trims:trim": {"pattern": "...", "material": "..."}}
 * - Block entities: Data stored in block entity's component map (vanilla transfers this automatically)
 *
 * In 1.21+, when an item with custom_data is placed, vanilla preserves the component
 * on the block entity. We read from there.
 */
public final class ShulkerTrimStorage {
    private ShulkerTrimStorage() {}

    public static final String TRIM_KEY = "shulker_trims:trim";
    public static final String PATTERN_KEY = "pattern";
    public static final String MATERIAL_KEY = "material";

    /**
     * Read trim data from a Bukkit ItemStack.
     */
    @Nullable
    public static ShulkerTrim readTrimFromItem(ItemStack bukkitStack) {
        if (bukkitStack == null || bukkitStack.isEmpty()) {
            return null;
        }

        net.minecraft.world.item.ItemStack nmsStack = CraftItemStack.asNMSCopy(bukkitStack);
        CustomData customData = nmsStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }

        return readTrimFromNbt(customData.copyTag());
    }

    /**
     * Write trim data to a Bukkit ItemStack.
     * Returns a new ItemStack with the trim applied.
     */
    public static ItemStack writeTrimToItem(ItemStack bukkitStack, @Nullable ShulkerTrim trim) {
        if (bukkitStack == null || bukkitStack.isEmpty()) {
            return bukkitStack;
        }

        net.minecraft.world.item.ItemStack nmsStack = CraftItemStack.asNMSCopy(bukkitStack);

        // Set custom_data - vanilla will preserve this on the block entity when placed
        CustomData existingCustomData = nmsStack.get(DataComponents.CUSTOM_DATA);
        CompoundTag customNbt = existingCustomData != null ? existingCustomData.copyTag() : new CompoundTag();
        writeTrimToNbt(customNbt, trim);
        if (customNbt.isEmpty()) {
            nmsStack.remove(DataComponents.CUSTOM_DATA);
        } else {
            nmsStack.set(DataComponents.CUSTOM_DATA, CustomData.of(customNbt));
        }

        return CraftItemStack.asBukkitCopy(nmsStack);
    }

    /**
     * Read trim data from a placed shulker box block.
     * Checks both the block entity's component map and its NBT data.
     */
    @Nullable
    public static ShulkerTrim readTrimFromBlock(ShulkerBox shulkerBox) {
        Block block = shulkerBox.getBlock();
        if (!(block.getWorld() instanceof CraftWorld craftWorld)) {
            return null;
        }

        try {
            ServerLevel level = craftWorld.getHandle();
            BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
            BlockEntity blockEntity = level.getBlockEntity(pos);

            if (!(blockEntity instanceof ShulkerBoxBlockEntity shulkerBE)) {
                return null;
            }

            // First, try to read from the block entity's component map
            // Vanilla stores custom_data from items here when blocks are placed
            DataComponentMap components = shulkerBE.components();
            if (components != null) {
                CustomData customData = components.get(DataComponents.CUSTOM_DATA);
                if (customData != null) {
                    ShulkerTrim trim = readTrimFromNbt(customData.copyTag());
                    if (trim != null) {
                        return trim;
                    }
                }
            }

            // Fallback: check the raw NBT (for Fabric compatibility)
            CompoundTag nbt = shulkerBE.saveWithoutMetadata(level.registryAccess());
            return readTrimFromNbt(nbt);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Write trim data to a placed shulker box block.
     * Sets the custom_data component on the block entity.
     */
    public static void writeTrimToBlock(ShulkerBox shulkerBox, @Nullable ShulkerTrim trim) {
        Block block = shulkerBox.getBlock();
        if (!(block.getWorld() instanceof CraftWorld craftWorld)) {
            return;
        }

        try {
            ServerLevel level = craftWorld.getHandle();
            BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
            BlockEntity blockEntity = level.getBlockEntity(pos);

            if (!(blockEntity instanceof ShulkerBoxBlockEntity shulkerBE)) {
                return;
            }

            // Get current custom_data from components, modify it, and set it back
            DataComponentMap components = shulkerBE.components();
            CustomData existingData = components != null ? components.get(DataComponents.CUSTOM_DATA) : null;
            CompoundTag nbt = existingData != null ? existingData.copyTag() : new CompoundTag();

            writeTrimToNbt(nbt, trim);

            // Set the component on the block entity
            if (nbt.isEmpty()) {
                shulkerBE.setComponents(shulkerBE.components().filter(type -> type != DataComponents.CUSTOM_DATA));
            } else {
                // We need to create a new component map with our custom_data
                shulkerBE.setComponents(
                    DataComponentMap.builder()
                        .addAll(shulkerBE.components())
                        .set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
                        .build()
                );
            }

            shulkerBE.setChanged();

            // Force sync to clients
            level.sendBlockUpdated(pos, shulkerBE.getBlockState(), shulkerBE.getBlockState(), 3);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Read trim from NBT compound.
     */
    @Nullable
    private static ShulkerTrim readTrimFromNbt(CompoundTag nbt) {
        if (!nbt.contains(TRIM_KEY) || nbt.get(TRIM_KEY).getId() != Tag.TAG_COMPOUND) {
            return null;
        }

        CompoundTag trimNbt = nbt.getCompound(TRIM_KEY).orElse(null);
        if (trimNbt == null) {
            return null;
        }

        if (!trimNbt.contains(PATTERN_KEY) || !trimNbt.contains(MATERIAL_KEY)) {
            return null;
        }

        Tag patternTag = trimNbt.get(PATTERN_KEY);
        Tag materialTag = trimNbt.get(MATERIAL_KEY);
        if (patternTag == null || patternTag.getId() != Tag.TAG_STRING ||
            materialTag == null || materialTag.getId() != Tag.TAG_STRING) {
            return null;
        }

        String pattern = trimNbt.getString(PATTERN_KEY).orElse("");
        String material = trimNbt.getString(MATERIAL_KEY).orElse("");

        if (pattern.isEmpty() || material.isEmpty()) {
            return null;
        }

        ShulkerTrim trim = new ShulkerTrim(pattern, material);
        return trim.isValid() ? trim : null;
    }

    /**
     * Write trim to NBT compound.
     */
    private static void writeTrimToNbt(CompoundTag nbt, @Nullable ShulkerTrim trim) {
        if (trim == null) {
            nbt.remove(TRIM_KEY);
            return;
        }

        CompoundTag trimNbt = new CompoundTag();
        trimNbt.putString(PATTERN_KEY, trim.pattern());
        trimNbt.putString(MATERIAL_KEY, trim.material());
        nbt.put(TRIM_KEY, trimNbt);
    }
}
