package com.daytonjwatson.chunkfall.listener;

import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class LimboChunkListener implements Listener {

    private final ChunkFallConfig config;

    public LimboChunkListener(ChunkFallConfig config) {
        this.config = config;
    }

    private boolean isLimbo(World world) {
        return world != null && world.getName().equals(config.getLimboWorldName());
    }

    @EventHandler
    public void onLimboChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        if (!isLimbo(world)) {
            return;
        }

        // Only void *newly generated* chunks so player-built bridges persist across restarts
        if (!event.isNewChunk()) {
            return;
        }

        voidifyChunk(event.getChunk());
    }

    private void voidifyChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    chunk.getBlock(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }
}
