package com.daytonjwatson.chunkfall.logic;

import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import com.daytonjwatson.chunkfall.util.ChunkKeyUtil;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;

public class OverworldProtection {

    private final ChunkFallConfig config;
    private final Set<Long> protectedChunks = new HashSet<>();

    public OverworldProtection(ChunkFallConfig config) {
        this.config = config;
    }

    public boolean shouldProtectOverworldChunk(World world, Chunk chunk, long key) {
        if (!config.isProtectEndPortalRoom()) {
            return false;
        }

        // Already marked protected (e.g. neighbor of portal chunk)
        if (protectedChunks.contains(key)) {
            return true;
        }

        // Check for END_PORTAL_FRAME in this chunk
        if (chunkHasEndPortalFrame(chunk)) {
            protectedChunks.add(key);

            if (config.isProtectEndPortalNeighbors()) {
                markNeighborChunksProtected(world, chunk.getX(), chunk.getZ());
            }
            return true;
        }

        return false;
    }

    private boolean chunkHasEndPortalFrame(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    if (chunk.getBlock(x, y, z).getType() == Material.END_PORTAL_FRAME) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void markNeighborChunksProtected(World world, int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = cx + dx;
                int nz = cz + dz;
                long nKey = ChunkKeyUtil.chunkKey(world, nx, nz);
                protectedChunks.add(nKey);
            }
        }
    }
}
