package com.wlritchi.shulkertrims.bukkit;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.data.Directional;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

/**
 * Handles events for shulker box trim functionality:
 * - Smithing table: Apply trims to shulker boxes
 * - Block place/dispenser: Sync trim data to Fabric clients
 * - Block break: Transfer trim from block entity to dropped item
 * - Player join/chunk load: Sync trim data to Fabric clients
 */
public class ShulkerTrimsListener implements Listener {
    private final ShulkerTrimsPlugin plugin;
    private final TrimSyncNetwork network;

    public ShulkerTrimsListener(ShulkerTrimsPlugin plugin, TrimSyncNetwork network) {
        this.plugin = plugin;
        this.network = network;
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
     * When a shulker box is placed, sync trim to Fabric clients.
     * Note: Vanilla handles component transfer from item to block entity.
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

        // Sync to Fabric clients (run next tick to ensure block entity is saved)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            network.sendTrimSync(block.getLocation(), trim);
        }, 1L);
    }

    /**
     * When a dispenser places a shulker box, sync trim to Fabric clients.
     * BlockPlaceEvent only fires for player placement, so we need this for dispensers.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        if (!isShulkerBox(item.getType())) {
            return;
        }

        ShulkerTrim trim = ShulkerTrimStorage.readTrimFromItem(item);
        if (trim == null) {
            return;
        }

        // Calculate where the shulker will be placed
        Block dispenser = event.getBlock();
        if (!(dispenser.getBlockData() instanceof Directional directional)) {
            return;
        }

        Block targetBlock = dispenser.getRelative(directional.getFacing());

        // Sync to Fabric clients after block is placed (give it a couple ticks)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (isShulkerBox(targetBlock.getType())) {
                network.sendTrimSync(targetBlock.getLocation(), trim);
            }
        }, 2L);
    }

    /**
     * When a shulker box is broken, capture trim for transfer to dropped item.
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
    public void onBlockDropItem(BlockDropItemEvent event) {
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

    /**
     * When a player registers our channel (Fabric client), sync all loaded shulkers.
     */
    @EventHandler
    public void onPlayerRegisterChannel(PlayerRegisterChannelEvent event) {
        if (TrimSyncNetwork.CHANNEL.equals(event.getChannel())) {
            // Player has our mod, sync all loaded trimmed shulkers
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                network.syncAllToPlayer(event.getPlayer());
                plugin.getLogger().info("Synced trims to player: " + event.getPlayer().getName());
            }, 20L); // Wait 1 second for chunks to load
        }
    }

    /**
     * When a chunk loads, sync any trimmed shulkers to nearby players.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();

        // Run async to avoid blocking chunk loading
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (org.bukkit.entity.Player player : chunk.getWorld().getPlayers()) {
                // Only sync to players who have our channel registered
                if (player.getListeningPluginChannels().contains(TrimSyncNetwork.CHANNEL)) {
                    network.syncChunkToPlayer(player, chunk);
                }
            }
        }, 5L);
    }
}
