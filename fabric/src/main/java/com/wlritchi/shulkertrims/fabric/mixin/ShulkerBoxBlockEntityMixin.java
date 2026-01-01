package com.wlritchi.shulkertrims.fabric.mixin;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.ReadView;
import net.minecraft.util.math.BlockPos;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin to add trim storage to ShulkerBoxBlockEntity.
 *
 * Storage uses the custom_data component for cross-platform compatibility with Paper.
 * Format: custom_data contains {"shulker_trims:trim": {"pattern": "...", "material": "..."}}
 */
@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerBoxBlockEntityMixin extends BlockEntity implements TrimmedShulkerBox {

    private ShulkerBoxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private @Nullable ShulkerTrim shulkerTrims$trim;

    @Override
    public @Nullable ShulkerTrim shulkerTrims$getTrim() {
        return this.shulkerTrims$trim;
    }

    @Override
    public void shulkerTrims$setTrim(@Nullable ShulkerTrim trim) {
        this.shulkerTrims$trim = trim;
        // Also update the custom_data component for cross-platform persistence
        shulkerTrims$updateCustomDataComponent();
    }

    /**
     * Updates the custom_data component on the block entity to include our trim data.
     * This ensures the trim is serialized in the components section of the NBT,
     * which is preserved by both Fabric and Paper servers.
     */
    @Unique
    private void shulkerTrims$updateCustomDataComponent() {
        // Get existing custom_data or create new
        NbtCompound nbt = new NbtCompound();
        ComponentMap existing = this.getComponents();
        if (existing != null) {
            NbtComponent existingCustomData = existing.get(DataComponentTypes.CUSTOM_DATA);
            if (existingCustomData != null) {
                nbt = existingCustomData.copyNbt();
            }
        }

        if (this.shulkerTrims$trim != null) {
            // Add our trim data
            ShulkerTrimStorage.writeTrim(nbt, this.shulkerTrims$trim);
        } else {
            // Remove our trim data
            nbt.remove(ShulkerTrimStorage.TRIM_KEY);
        }

        // Build new component map with updated custom_data
        ComponentMap.Builder builder = ComponentMap.builder();
        if (existing != null) {
            builder.addAll(existing);
        }

        // Add or update custom_data (overwrites if already present from addAll)
        if (!nbt.isEmpty()) {
            builder.add(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }

        this.setComponents(builder.build());
        this.markDirty();
    }

    /**
     * Read trim from item's components when block is placed.
     */
    @Override
    protected void readComponents(ComponentsAccess components) {
        super.readComponents(components);

        // Try reading from custom_data component first
        NbtComponent customData = components.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            ShulkerTrim trim = ShulkerTrimStorage.readTrim(customData.copyNbt());
            if (trim != null) {
                shulkerTrims$setTrim(trim);
                return;
            }
        }

        // Try reading from minecraft:trim component (vanilla armor trim format)
        ArmorTrim armorTrim = components.get(DataComponentTypes.TRIM);
        if (armorTrim != null) {
            String pattern = armorTrim.pattern().getIdAsString();
            String material = armorTrim.material().getIdAsString();
            shulkerTrims$setTrim(new ShulkerTrim(pattern, material));
        }
    }

    /**
     * Write trim to custom_data component when block entity saves.
     */
    @Override
    protected void addComponents(ComponentMap.Builder builder) {
        super.addComponents(builder);
        if (this.shulkerTrims$trim != null) {
            // Get existing custom_data or create new
            NbtCompound nbt = new NbtCompound();

            // Try to preserve existing custom_data content
            ComponentMap existing = this.getComponents();
            if (existing != null) {
                NbtComponent existingCustomData = existing.get(DataComponentTypes.CUSTOM_DATA);
                if (existingCustomData != null) {
                    nbt = existingCustomData.copyNbt();
                }
            }

            // Add our trim data
            ShulkerTrimStorage.writeTrim(nbt, this.shulkerTrims$trim);
            builder.add(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }
    }

    /**
     * Override toInitialChunkDataNbt to include trim data in client sync.
     */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound nbt = new NbtCompound();
        if (this.shulkerTrims$trim != null) {
            ShulkerTrimStorage.writeTrim(nbt, this.shulkerTrims$trim);
        }
        return nbt;
    }

    /**
     * Read trim from disk when block entity loads.
     * Components are NOT yet loaded when readData is called, so we read directly from NBT.
     */
    @Override
    protected void readData(ReadView data) {
        super.readData(data);

        // Try reading from NBT path: components -> minecraft:trim
        Optional<ReadView> componentsOpt = data.getOptionalReadView("components");
        if (componentsOpt.isPresent()) {
            ReadView componentsView = componentsOpt.get();

            // Try minecraft:trim component (vanilla armor trim format)
            Optional<ReadView> trimOpt = componentsView.getOptionalReadView("minecraft:trim");
            if (trimOpt.isPresent()) {
                ReadView trimView = trimOpt.get();
                Optional<String> patternOpt = trimView.getOptionalString("pattern");
                Optional<String> materialOpt = trimView.getOptionalString("material");
                if (patternOpt.isPresent() && materialOpt.isPresent()) {
                    this.shulkerTrims$trim = new ShulkerTrim(patternOpt.get(), materialOpt.get());
                    return;
                }
            }

            // Try minecraft:custom_data component (Paper format)
            Optional<ReadView> customDataOpt = componentsView.getOptionalReadView("minecraft:custom_data");
            if (customDataOpt.isPresent()) {
                this.shulkerTrims$trim = ShulkerTrimStorage.readTrimFromData(customDataOpt.get());
                if (this.shulkerTrims$trim != null) {
                    return;
                }
            }
        }

        // Fallback: top-level NBT (client sync format)
        this.shulkerTrims$trim = ShulkerTrimStorage.readTrimFromData(data);
    }
}
