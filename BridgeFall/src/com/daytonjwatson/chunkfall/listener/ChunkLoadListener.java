package com.daytonjwatson.chunkfall.listener;

import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import com.daytonjwatson.chunkfall.logic.ChunkProcessor;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class ChunkLoadListener implements Listener {

    private final ChunkFallConfig config;
    private final ChunkProcessor processor;

    public ChunkLoadListener(ChunkFallConfig config, ChunkProcessor processor) {
        this.config = config;
        this.processor = processor;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        if (!config.isTargetWorld(world)) {
            return;
        }

        // IMPORTANT: only process newly generated chunks.
        // Existing chunks (with player-built bridges) will never be touched again.
        if (!event.isNewChunk()) {
            return;
        }

        processor.handleChunkLoad(event.getChunk());
    }
}