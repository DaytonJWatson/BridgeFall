package com.daytonjwatson.chunkfall.listener;

import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class ElytraListener implements Listener {

    private final ChunkFallConfig config;

    public ElytraListener(ChunkFallConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onPlayerUseFireworkWhileGliding(PlayerInteractEvent event) {
        if (!config.isDisableElytraRocketBoost()) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Only care about the hand actually used
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }

        if (event.getItem() == null || event.getItem().getType() != Material.FIREWORK_ROCKET) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isGliding()) {
            return; // rocket use is fine when not gliding
        }

        // Cancel the interaction so no rocket boost happens
        event.setCancelled(true);
    }
}