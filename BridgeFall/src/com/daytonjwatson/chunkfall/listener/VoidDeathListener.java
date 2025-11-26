package com.daytonjwatson.chunkfall.listener;

import com.daytonjwatson.chunkfall.ChunkFallPlugin;
import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import com.daytonjwatson.chunkfall.logic.LimboManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VoidDeathListener implements Listener {

    private final ChunkFallPlugin plugin;
    private final ChunkFallConfig config;
    private final LimboManager limboManager;

    // Void deaths outside Limbo → enter Limbo
    private final Set<UUID> voidDeathOutsideLimbo = new HashSet<>();
    // Any deaths in Limbo → respawn in Limbo
    private final Set<UUID> deathInLimbo = new HashSet<>();

    public VoidDeathListener(ChunkFallPlugin plugin,
                             ChunkFallConfig config,
                             LimboManager limboManager) {
        this.plugin = plugin;
        this.config = config;
        this.limboManager = limboManager;
    }

    private boolean isLimboWorld(World world) {
        return world != null && world.getName().equals(config.getLimboWorldName());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getEntity().getLastDamageCause() == null) {
            return;
        }

        World world = event.getEntity().getWorld();
        boolean inLimbo = isLimboWorld(world);
        DamageCause cause = event.getEntity().getLastDamageCause().getCause();
        UUID id = event.getEntity().getUniqueId();

        if (inLimbo) {
            // Any death in Limbo → track for Limbo respawn
            deathInLimbo.add(id);
        } else {
            // Only void deaths outside Limbo trigger Limbo entry
            if (cause == DamageCause.VOID) {
                voidDeathOutsideLimbo.add(id);
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        UUID id = event.getPlayer().getUniqueId();

        // Case 1: death happened in Limbo → respawn in Limbo, no new anchors
        if (deathInLimbo.remove(id)) {
            World limbo = limboManager.getOrCreateLimboWorld();
            if (limbo == null) {
                return;
            }

            int y = config.getLimboBedrockY();
            Location limboSpawn = new Location(limbo, 1.5, y + 1, 1.5);
            event.setRespawnLocation(limboSpawn);

            // Re-initialize Limbo for this player without spawning additional anchors
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    limboManager.sendPlayerToLimbo(event.getPlayer(), false)
            );
            return;
        }

        // Case 2: void death outside Limbo → send them to Limbo, spawn anchors
        if (voidDeathOutsideLimbo.remove(id)) {
            World limbo = limboManager.getOrCreateLimboWorld();
            if (limbo == null) {
                return;
            }

            int y = config.getLimboBedrockY();
            Location limboSpawn = new Location(limbo, 1.5, y + 1, 1.5);
            event.setRespawnLocation(limboSpawn);

            plugin.getServer().getScheduler().runTask(plugin, () ->
                    limboManager.sendPlayerToLimbo(event.getPlayer(), true)
            );
            return;
        }

        // Case 3: any other death → vanilla respawn behavior
        // (do nothing; let Minecraft handle it)
    }
}