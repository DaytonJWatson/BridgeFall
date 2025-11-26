package com.daytonjwatson.chunkfall.logic;

import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;

public class NetherProtection {

    private final ChunkFallConfig config;

    public NetherProtection(ChunkFallConfig config) {
        this.config = config;
    }

    public boolean shouldProtectNetherChunk(Chunk chunk) {
        if (!config.isProtectNetherFortressEssentials()) {
            return false;
        }
        return chunkHasBlazeSpawnerOrNetherWart(chunk);
    }

    private boolean chunkHasBlazeSpawnerOrNetherWart(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    Material type = block.getType();

                    if (type == Material.NETHER_WART) {
                        return true;
                    }

                    if (type == Material.SPAWNER) {
                        BlockState state = block.getState();
                        if (state instanceof CreatureSpawner spawner) {
                            if (spawner.getSpawnedType() == EntityType.BLAZE) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
