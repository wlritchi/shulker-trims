package com.wlritchi.shulkertrims.bukkit;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.bukkit.block.ShulkerBox;
import org.bukkit.craftbukkit.block.CraftShulkerBox;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Utility for reading/writing ShulkerTrim data using NMS.
 *
 * Storage format matches Fabric implementation exactly:
 * - Items: minecraft:custom_data component containing {"shulker_trims:trim": {"pattern": "...", "material": "..."}}
 * - Block entities: Same structure in block entity NBT
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

        // Get or create custom_data
        CustomData existingData = nmsStack.get(DataComponents.CUSTOM_DATA);
        CompoundTag nbt = existingData != null ? existingData.copyTag() : new CompoundTag();

        writeTrimToNbt(nbt, trim);

        if (nbt.isEmpty()) {
            nmsStack.remove(DataComponents.CUSTOM_DATA);
        } else {
            nmsStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        }

        return CraftItemStack.asBukkitCopy(nmsStack);
    }

    /**
     * Read trim data from a placed shulker box block.
     */
    @Nullable
    public static ShulkerTrim readTrimFromBlock(ShulkerBox shulkerBox) {
        if (!(shulkerBox instanceof CraftShulkerBox craftBox)) {
            return null;
        }

        try {
            ShulkerBoxBlockEntity blockEntity = getBlockEntity(craftBox);
            if (blockEntity == null || blockEntity.getLevel() == null) {
                return null;
            }
            CompoundTag nbt = blockEntity.saveWithoutMetadata(blockEntity.getLevel().registryAccess());
            return readTrimFromNbt(nbt);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Write trim data to a placed shulker box block.
     * Note: Due to API changes in 1.21.10, this relies on the snapshot update pattern.
     */
    public static void writeTrimToBlock(ShulkerBox shulkerBox, @Nullable ShulkerTrim trim) {
        if (!(shulkerBox instanceof CraftShulkerBox craftBox)) {
            return;
        }

        try {
            ShulkerBoxBlockEntity blockEntity = getBlockEntity(craftBox);
            if (blockEntity == null || blockEntity.getLevel() == null) {
                return;
            }

            // Read current NBT, modify it, and write back using reflection
            CompoundTag nbt = blockEntity.saveWithoutMetadata(blockEntity.getLevel().registryAccess());
            writeTrimToNbt(nbt, trim);

            // Try to load the modified NBT back
            try {
                // Try the older loadAdditional signature if available
                Method loadMethod = ShulkerBoxBlockEntity.class.getMethod("loadAdditional",
                    CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
                loadMethod.invoke(blockEntity, nbt, blockEntity.getLevel().registryAccess());
            } catch (NoSuchMethodException e) {
                // Fallback: mark as changed and hope the NBT is preserved
            }

            blockEntity.setChanged();
        } catch (Exception e) {
            // Silently fail - trim won't be saved to block
        }
    }

    /**
     * Get the actual block entity from a CraftShulkerBox using reflection.
     */
    @Nullable
    private static ShulkerBoxBlockEntity getBlockEntity(CraftShulkerBox craftBox) {
        try {
            // Try getTileEntity first (older API)
            try {
                Method method = craftBox.getClass().getMethod("getTileEntity");
                return (ShulkerBoxBlockEntity) method.invoke(craftBox);
            } catch (NoSuchMethodException ignored) {}

            // Try to access the snapshot field from CraftBlockEntityState
            Field snapshotField = findField(craftBox.getClass(), "snapshot");
            if (snapshotField != null) {
                snapshotField.setAccessible(true);
                return (ShulkerBoxBlockEntity) snapshotField.get(craftBox);
            }

            // Try getSnapshot method
            try {
                Method method = craftBox.getClass().getMethod("getSnapshot");
                return (ShulkerBoxBlockEntity) method.invoke(craftBox);
            } catch (NoSuchMethodException ignored) {}

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Find a field in class hierarchy.
     */
    @Nullable
    private static Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
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
