package com.wlritchi.shulkertrims.bukkit;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import net.minecraft.core.BlockPos;
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
 * Storage uses custom_data component on items. When placed, vanilla transfers
 * custom_data to block entity components. When broken, the trim is written back.
 */
public final class ShulkerTrimStorage {
    private ShulkerTrimStorage() {}

    public static final String TRIM_KEY = "shulker_trims:trim";
    public static final String PATTERN_KEY = "pattern";
    public static final String MATERIAL_KEY = "material";

    /**
     * Read trim data from a Bukkit ItemStack's custom_data component.
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
     * Write trim data to a Bukkit ItemStack's custom_data component.
     * Returns a new ItemStack with the trim applied.
     */
    public static ItemStack writeTrimToItem(ItemStack bukkitStack, @Nullable ShulkerTrim trim) {
        if (bukkitStack == null || bukkitStack.isEmpty()) {
            return bukkitStack;
        }

        net.minecraft.world.item.ItemStack nmsStack = CraftItemStack.asNMSCopy(bukkitStack);

        if (trim == null) {
            // Remove trim from custom_data
            CustomData existingData = nmsStack.get(DataComponents.CUSTOM_DATA);
            if (existingData != null) {
                CompoundTag nbt = existingData.copyTag();
                nbt.remove(TRIM_KEY);
                if (nbt.isEmpty()) {
                    nmsStack.remove(DataComponents.CUSTOM_DATA);
                } else {
                    nmsStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                }
            }
            return CraftItemStack.asBukkitCopy(nmsStack);
        }

        // Write to custom_data component
        CustomData existingData = nmsStack.get(DataComponents.CUSTOM_DATA);
        CompoundTag nbt = existingData != null ? existingData.copyTag() : new CompoundTag();
        writeTrimToNbt(nbt, trim);
        nmsStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        return CraftItemStack.asBukkitCopy(nmsStack);
    }

    /**
     * Read trim data from a placed shulker box block.
     * Reads from the block entity's components where custom_data was transferred.
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

            // Read from block entity NBT - check components.minecraft:custom_data
            CompoundTag nbt = shulkerBE.saveWithoutMetadata(level.registryAccess());

            // Try reading from components -> minecraft:custom_data
            if (nbt.contains("components")) {
                CompoundTag components = nbt.getCompound("components").orElse(null);
                if (components != null && components.contains("minecraft:custom_data")) {
                    CompoundTag customData = components.getCompound("minecraft:custom_data").orElse(null);
                    if (customData != null) {
                        ShulkerTrim trim = readTrimFromNbt(customData);
                        if (trim != null) {
                            return trim;
                        }
                    }
                }
            }

            // Fallback: top-level NBT (legacy or sync format)
            return readTrimFromNbt(nbt);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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
