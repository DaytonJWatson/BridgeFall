package com.daytonjwatson.chunkfall.listener;

import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import com.daytonjwatson.chunkfall.logic.CobbleGeneratorManager;
import com.daytonjwatson.chunkfall.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class CobbleGeneratorListener implements Listener {

    private final ChunkFallConfig config;
    private final CobbleGeneratorManager manager;

    public CobbleGeneratorListener(ChunkFallConfig config, CobbleGeneratorManager manager) {
        this.config = config;
        this.manager = manager;
    }

    private boolean isEnabled() {
        return config.isCobbleGeneratorEnabled();
    }

    private boolean isPickaxe(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.WOODEN_PICKAXE
                || type == Material.STONE_PICKAXE
                || type == Material.COPPER_PICKAXE
                || type == Material.IRON_PICKAXE
                || type == Material.GOLDEN_PICKAXE
                || type == Material.DIAMOND_PICKAXE
                || type == Material.NETHERITE_PICKAXE;
    }

    @EventHandler
    public void onCreateGenerator(PlayerInteractEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BARREL) {
            return;
        }

        Player player = event.getPlayer();

        // Must be sneaking and holding a pickaxe
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!player.isSneaking() || !isPickaxe(inHand)) {
            return;
        }

        BlockState state = block.getState();
        if (!(state instanceof Container container)) {
            MessageUtil.error(player,
                    "This block cannot function as a cobblestone generator.");
            return;
        }

        Inventory inv = container.getInventory();

        // If slot 0 is already used, don't override it
        if (inv.getItem(0) != null) {
            MessageUtil.warning(player,
                    "Slot 0 of this barrel is already occupied. Clear it before creating a generator.");
            return;
        }

        // 1) Put the pickaxe into slot 0 of the barrel.
        inv.setItem(0, inHand.clone());

        // 2) Clear the player's hand.
        player.getInventory().setItemInMainHand(null);

        // 3) Register this barrel as a generator.
        manager.registerGenerator(block);

        // 4) Feedback, sound (if enabled), and show the barrel inventory so the player can see the pick.
        MessageUtil.success(player,
                "Cobblestone generator created. Your pickaxe has been placed into slot 0 of this barrel.");

        if (config.isCobbleSoundOnCreate()) {
            block.getWorld().playSound(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    Sound.BLOCK_BEACON_ACTIVATE,
                    0.8f,
                    1.2f
            );
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onGeneratorBroken(BlockBreakEvent event) {
        if (!isEnabled()) {
            return;
        }

        Block block = event.getBlock();
        if (block.getType() != Material.BARREL) {
            return;
        }

        manager.unregisterGenerator(block);

        // If you want to notify the breaker:
        if (event.getPlayer() != null) {
            MessageUtil.info(event.getPlayer(),
                    "Cobblestone generator removed.");
        }
    }
}
