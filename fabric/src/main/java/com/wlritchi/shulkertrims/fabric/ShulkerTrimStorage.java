package com.wlritchi.shulkertrims.fabric;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Utility for reading/writing ShulkerTrim data to NBT and ItemStacks.
 *
 * Storage strategy:
 * - Block entities: Store trim in block entity NBT (for mod-to-mod compatibility)
 * - Items: Store in minecraft:custom_data component (vanilla servers preserve this)
 *
 * When a shulker is placed, we read from the item's custom_data.
 * When a shulker is broken, we write to the dropped item's custom_data.
 */
public final class ShulkerTrimStorage {
    private ShulkerTrimStorage() {}

    /**
     * NBT key for the trim compound tag.
     * Namespaced to avoid conflicts with vanilla or other mods.
     */
    public static final String TRIM_KEY = "shulker_trims:trim";
    public static final String PATTERN_KEY = "pattern";
    public static final String MATERIAL_KEY = "material";

    /**
     * Write trim data to an NBT compound.
     *
     * @param nbt The compound to write to (typically from writeNbt)
     * @param trim The trim to write, or null to remove existing trim data
     */
    public static void writeTrim(NbtCompound nbt, @Nullable ShulkerTrim trim) {
        if (trim == null) {
            nbt.remove(TRIM_KEY);
            return;
        }

        NbtCompound trimNbt = new NbtCompound();
        trimNbt.putString(PATTERN_KEY, trim.pattern());
        trimNbt.putString(MATERIAL_KEY, trim.material());
        nbt.put(TRIM_KEY, trimNbt);
    }

    /**
     * Read trim data from an NBT compound.
     *
     * @param nbt The compound to read from (typically from readNbt)
     * @return The trim, or null if no valid trim data found
     */
    @Nullable
    public static ShulkerTrim readTrim(NbtCompound nbt) {
        Optional<NbtCompound> trimNbtOpt = nbt.getCompound(TRIM_KEY);
        if (trimNbtOpt.isEmpty()) {
            return null;
        }

        NbtCompound trimNbt = trimNbtOpt.get();
        Optional<String> patternOpt = trimNbt.getString(PATTERN_KEY);
        Optional<String> materialOpt = trimNbt.getString(MATERIAL_KEY);

        if (patternOpt.isEmpty() || materialOpt.isEmpty()) {
            ShulkerTrimsMod.LOGGER.warn("Invalid trim NBT structure: missing pattern or material");
            return null;
        }

        ShulkerTrim trim = new ShulkerTrim(patternOpt.get(), materialOpt.get());
        if (!trim.isValid()) {
            ShulkerTrimsMod.LOGGER.warn("Invalid trim identifiers: pattern={}, material={}", patternOpt.get(), materialOpt.get());
            return null;
        }

        return trim;
    }

    /**
     * Read trim data from an ItemStack's custom_data component.
     *
     * @param stack The item stack to read from
     * @return The trim, or null if no valid trim data found
     */
    @Nullable
    public static ShulkerTrim readTrimFromItem(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        return readTrim(customData.copyNbt());
    }

    /**
     * Write trim data to an ItemStack's custom_data component.
     *
     * @param stack The item stack to write to
     * @param trim The trim to write, or null to remove
     */
    public static void writeTrimToItem(ItemStack stack, @Nullable ShulkerTrim trim) {
        if (trim == null) {
            // Remove trim from custom_data if present
            NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (customData != null) {
                NbtCompound nbt = customData.copyNbt();
                nbt.remove(TRIM_KEY);
                if (nbt.isEmpty()) {
                    stack.remove(DataComponentTypes.CUSTOM_DATA);
                } else {
                    stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
                }
            }
            return;
        }

        // Get or create custom_data
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = customData != null ? customData.copyNbt() : new NbtCompound();
        writeTrim(nbt, trim);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    /**
     * Read trim data from a ReadView (1.21.10+ block entity data format).
     *
     * @param data The ReadView to read from
     * @return The trim, or null if no valid trim data found
     */
    @Nullable
    public static ShulkerTrim readTrimFromData(ReadView data) {
        Optional<ReadView> trimData = data.getOptionalReadView(TRIM_KEY);
        if (trimData.isEmpty()) {
            return null;
        }

        ReadView trimView = trimData.get();
        Optional<String> pattern = trimView.getOptionalString(PATTERN_KEY);
        Optional<String> material = trimView.getOptionalString(MATERIAL_KEY);

        if (pattern.isEmpty() || material.isEmpty()) {
            ShulkerTrimsMod.LOGGER.warn("Invalid trim data structure: missing pattern or material");
            return null;
        }

        ShulkerTrim trim = new ShulkerTrim(pattern.get(), material.get());
        if (!trim.isValid()) {
            ShulkerTrimsMod.LOGGER.warn("Invalid trim identifiers: pattern={}, material={}", pattern.get(), material.get());
            return null;
        }

        return trim;
    }

    /**
     * Write trim data to a WriteView (1.21.10+ block entity data format).
     *
     * @param data The WriteView to write to
     * @param trim The trim to write, or null to remove existing trim data
     */
    public static void writeTrimToData(WriteView data, @Nullable ShulkerTrim trim) {
        if (trim == null) {
            data.remove(TRIM_KEY);
            return;
        }

        WriteView trimView = data.get(TRIM_KEY);
        trimView.putString(PATTERN_KEY, trim.pattern());
        trimView.putString(MATERIAL_KEY, trim.material());
    }

    /**
     * Read trim data from a block entity's components.
     * This is used as a fallback when receiving data from non-Fabric servers
     * that store trim in the custom_data component.
     *
     * @param blockEntity The block entity to read from
     * @return The trim, or null if no valid trim data found
     */
    @Nullable
    public static ShulkerTrim readTrimFromBlockEntityComponents(BlockEntity blockEntity) {
        try {
            ComponentMap components = blockEntity.getComponents();
            if (components == null) {
                return null;
            }

            // Check for custom_data component
            NbtComponent customData = components.get(DataComponentTypes.CUSTOM_DATA);
            if (customData != null) {
                ShulkerTrim trim = readTrim(customData.copyNbt());
                if (trim != null) {
                    return trim;
                }
            }

            return null;
        } catch (Exception e) {
            ShulkerTrimsMod.LOGGER.debug("Error reading trim from block entity components: {}", e.getMessage());
            return null;
        }
    }
}
