package com.wlritchi.shulkertrims.bukkit;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.ShulkerBox;

/**
 * Handles smithing table events to apply trim data to shulker boxes.
 */
public class ShulkerTrimsListener implements Listener {
    private final ShulkerTrimsPlugin plugin;

    public ShulkerTrimsListener(ShulkerTrimsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();

        ItemStack template = inv.getItem(0);  // Template slot
        ItemStack base = inv.getItem(1);      // Base item slot
        ItemStack addition = inv.getItem(2);  // Addition slot

        if (template == null || base == null || addition == null) {
            return;
        }

        // Check if base is a shulker box
        if (!isShulkerBox(base.getType())) {
            return;
        }

        // Check if template is a trim template
        String pattern = getTrimPattern(template.getType());
        if (pattern == null) {
            return;
        }

        // Check if addition is a trim material
        String material = getTrimMaterial(addition.getType());
        if (material == null) {
            return;
        }

        // Create result: copy of base shulker with trim NBT applied
        ItemStack result = base.clone();
        applyTrim(result, new ShulkerTrim(pattern, material));

        event.setResult(result);
    }

    private boolean isShulkerBox(Material mat) {
        return mat.name().contains("SHULKER_BOX");
    }

    private String getTrimPattern(Material template) {
        String name = template.name();
        if (!name.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")) {
            return null;
        }
        // Extract pattern name: SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE -> minecraft:sentry
        String pattern = name.replace("_ARMOR_TRIM_SMITHING_TEMPLATE", "").toLowerCase();
        return "minecraft:" + pattern;
    }

    private String getTrimMaterial(Material mat) {
        return switch (mat) {
            case QUARTZ -> "minecraft:quartz";
            case IRON_INGOT -> "minecraft:iron";
            case NETHERITE_INGOT -> "minecraft:netherite";
            case REDSTONE -> "minecraft:redstone";
            case COPPER_INGOT -> "minecraft:copper";
            case GOLD_INGOT -> "minecraft:gold";
            case EMERALD -> "minecraft:emerald";
            case DIAMOND -> "minecraft:diamond";
            case LAPIS_LAZULI -> "minecraft:lapis";
            case AMETHYST_SHARD -> "minecraft:amethyst";
            default -> null;
        };
    }

    private void applyTrim(ItemStack item, ShulkerTrim trim) {
        // TODO: Apply trim data to item NBT in the format that Fabric expects
        // This requires writing to the block entity data component
        // For now, we'll use Paper's ItemMeta API to store custom data

        plugin.getLogger().info("Applying trim: " + trim.pattern() + " / " + trim.material());

        // The actual NBT manipulation will be implemented to match Fabric's format
        // This is a placeholder that will be filled in during implementation
    }
}
