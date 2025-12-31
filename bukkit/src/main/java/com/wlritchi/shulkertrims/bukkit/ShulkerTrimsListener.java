package com.wlritchi.shulkertrims.bukkit;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

/**
 * Handles events for shulker box trim functionality:
 * - Smithing table: Apply trims to shulker boxes
 * - Block place: Transfer trim from item to block entity
 * - Block break: Transfer trim from block entity to dropped item
 */
public class ShulkerTrimsListener implements Listener {
    private final ShulkerTrimsPlugin plugin;

    public ShulkerTrimsListener(ShulkerTrimsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle smithing table preview to show trimmed shulker result.
     */
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

        // Create result: copy of base shulker with trim applied
        ShulkerTrim trim = new ShulkerTrim(pattern, material);
        ItemStack result = ShulkerTrimStorage.writeTrimToItem(base.clone(), trim);

        event.setResult(result);
    }

    /**
     * When a shulker box is placed, transfer trim from item to block entity.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!isShulkerBox(block.getType())) {
            return;
        }

        ItemStack placedItem = event.getItemInHand();
        ShulkerTrim trim = ShulkerTrimStorage.readTrimFromItem(placedItem);
        if (trim == null) {
            return;
        }

        // Transfer trim to block entity
        if (block.getState() instanceof ShulkerBox shulkerBox) {
            ShulkerTrimStorage.writeTrimToBlock(shulkerBox, trim);
        }
    }

    /**
     * When a shulker box is broken, transfer trim from block entity to dropped item.
     * Note: This uses MONITOR priority and the actual item modification happens via
     * the BlockDropItemEvent for proper handling.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isShulkerBox(block.getType())) {
            return;
        }

        // Read trim before block is destroyed
        if (block.getState() instanceof ShulkerBox shulkerBox) {
            ShulkerTrim trim = ShulkerTrimStorage.readTrimFromBlock(shulkerBox);
            if (trim != null) {
                // Store trim temporarily for BlockDropItemEvent
                // The drop event will apply the trim to the dropped item
                block.setMetadata("shulker_trims:pending_trim",
                    new org.bukkit.metadata.FixedMetadataValue(plugin,
                        trim.pattern() + "|" + trim.material()));
            }
        }
    }

    /**
     * Apply pending trim to dropped shulker box items.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDropItem(org.bukkit.event.block.BlockDropItemEvent event) {
        Block block = event.getBlock();
        if (!block.hasMetadata("shulker_trims:pending_trim")) {
            return;
        }

        String trimData = block.getMetadata("shulker_trims:pending_trim").get(0).asString();
        block.removeMetadata("shulker_trims:pending_trim", plugin);

        String[] parts = trimData.split("\\|", 2);
        if (parts.length != 2) {
            return;
        }

        ShulkerTrim trim = new ShulkerTrim(parts[0], parts[1]);

        // Apply trim to all dropped shulker box items
        for (org.bukkit.entity.Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (isShulkerBox(stack.getType())) {
                item.setItemStack(ShulkerTrimStorage.writeTrimToItem(stack, trim));
            }
        }
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
}
