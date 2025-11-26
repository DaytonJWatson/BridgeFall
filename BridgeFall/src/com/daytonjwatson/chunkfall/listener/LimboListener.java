package com.daytonjwatson.chunkfall.listener;

import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import com.daytonjwatson.chunkfall.logic.LimboManager;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class LimboListener implements Listener {

    private final ChunkFallConfig config;
    private final LimboManager limboManager;

    // Platform center and size must match LimboManager
    private static final int PLATFORM_CENTER_X = 1;
    private static final int PLATFORM_CENTER_Z = 1;
    private static final int PLATFORM_HALF_SIZE_NEG = 4; // centerX-4
    private static final int PLATFORM_HALF_SIZE_POS = 5; // centerX+5

    public LimboListener(ChunkFallConfig config, LimboManager limboManager) {
        this.config = config;
        this.limboManager = limboManager;
    }

    private boolean isInLimboWorld(World world) {
        return world != null && world.getName().equals(config.getLimboWorldName());
    }

    private boolean isInPlatformColumn(int x, int z) {
        int minX = PLATFORM_CENTER_X - PLATFORM_HALF_SIZE_NEG;
        int maxX = PLATFORM_CENTER_X + PLATFORM_HALF_SIZE_POS;
        int minZ = PLATFORM_CENTER_Z - PLATFORM_HALF_SIZE_NEG;
        int maxZ = PLATFORM_CENTER_Z + PLATFORM_HALF_SIZE_POS;

        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    // 1) PROTECTION: no building/breaking on/above/below the bedrock platform

    @EventHandler
    public void onProtectedBlockPlace(BlockPlaceEvent event) {
        World world = event.getBlockPlaced().getWorld();
        if (!isInLimboWorld(world)) {
            return;
        }

        Block block = event.getBlockPlaced();
        if (isInPlatformColumn(block.getX(), block.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProtectedBlockBreak(BlockBreakEvent event) {
        World world = event.getBlock().getWorld();
        if (!isInLimboWorld(world)) {
            return;
        }

        Block block = event.getBlock();
        if (isInPlatformColumn(block.getX(), block.getZ())) {
            event.setCancelled(true);
        }
    }

    // 2) Infinite cobblestone in Limbo (only if placement wasn't cancelled)

    @EventHandler
    public void onLimboCobblestonePlace(BlockPlaceEvent event) {
        if (event.isCancelled()) {
            return; // protection already blocked it
        }

        World world = event.getBlockPlaced().getWorld();
        if (!isInLimboWorld(world)) {
            return;
        }

        if (event.getBlockPlaced().getType() != Material.COBBLESTONE) {
            return;
        }

        ItemStack inHand = event.getItemInHand();
        if (inHand == null || inHand.getType() != Material.COBBLESTONE) {
            return;
        }

        inHand.setAmount(config.getLimboCobblestoneStackSize());
    }

    // 3) Respawn anchor use → escape Limbo

    @EventHandler
    public void onLimboAnchorUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.RESPAWN_ANCHOR) {
            return;
        }

        World world = clicked.getWorld();
        if (!isInLimboWorld(world)) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }

        Player player = event.getPlayer();

        event.setCancelled(true);
        limboManager.useRespawnAnchor(player, clicked);
    }
}
