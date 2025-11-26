package com.daytonjwatson.chunkfall.logic;

import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import com.daytonjwatson.chunkfall.util.ChunkKeyUtil;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class ChunkProcessor {

    private final ChunkFallConfig config;
    private final OverworldProtection overworldProtection;
    private final NetherProtection netherProtection;

    private final Set<Long> processedChunks = new HashSet<>();

    public ChunkProcessor(ChunkFallConfig config) {
        this.config = config;
        this.overworldProtection = new OverworldProtection(config);
        this.netherProtection = new NetherProtection(config);
    }

    public void handleChunkLoad(Chunk chunk) {
        World world = chunk.getWorld();
        int cx = chunk.getX();
        int cz = chunk.getZ();

        long key = ChunkKeyUtil.chunkKey(world, cx, cz);
        if (!processedChunks.add(key)) {
            return; // already processed this chunk in this server run
        }

        int regionX = Math.floorDiv(cx, config.getRegionSizeChunks());
        int regionZ = Math.floorDiv(cz, config.getRegionSizeChunks());
        int[] kept = getKeptChunkInRegion(world, regionX, regionZ);

        Environment env = world.getEnvironment();

        // Overworld: protect End Portal + neighbors
        if (env == Environment.NORMAL &&
                overworldProtection.shouldProtectOverworldChunk(world, chunk, key)) {
            return;
        }

        // Nether: protect blaze spawner / nether wart chunks
        if (env == Environment.NETHER &&
                netherProtection.shouldProtectNetherChunk(chunk)) {
            return;
        }

        // Keep the island chunk
        if (cx == kept[0] && cz == kept[1]) {
            return;
        }

        // Otherwise void the chunk (but optionally keep certain blocks)
        makeChunkVoid(chunk);
    }

    public void ensureSpawnOnIsland(World world) {
        // Region (0,0) forced to keep chunk (0,0) so fresh worlds never spawn in void
        int[] kept = getKeptChunkInRegion(world, 0, 0);
        int keptChunkX = kept[0];
        int keptChunkZ = kept[1];

        Chunk islandChunk = world.getChunkAt(keptChunkX, keptChunkZ);
        islandChunk.load(true);

        int blockX = (keptChunkX << 4) + 8;
        int blockZ = (keptChunkZ << 4) + 8;

        int y = world.getHighestBlockYAt(blockX, blockZ);
        // Fallback if for some reason height is at or below min
        if (y <= world.getMinHeight()) {
            y = world.getMinHeight() + 64;
        }

        int platformY = y;

        Location spawnLoc = new Location(world, blockX + 0.5, platformY + 1, blockZ + 0.5);
        world.setSpawnLocation(spawnLoc);

        // Force players to spawn exactly at the spawn location, not within a random radius
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
    }

    private int[] getKeptChunkInRegion(World world, int regionX, int regionZ) {
        // Special case: region (0,0) always keeps chunk (0,0) so fresh worlds never spawn in void
        if (regionX == 0 && regionZ == 0) {
            return new int[]{0, 0};
        }

        long seed = world.getSeed()
                ^ (regionX * 341873128712L)
                ^ (regionZ * 132897987541L);

        Random random = new Random(seed);
        int regionSize = config.getRegionSizeChunks();
        int offsetX = random.nextInt(regionSize);
        int offsetZ = random.nextInt(regionSize);

        return new int[]{
                regionX * regionSize + offsetX,
                regionZ * regionSize + offsetZ
        };
    }

    /**
     * Void the chunk, but keep certain blocks depending on dimension.
     * In the Nether we keep NETHER_BRICKS in otherwise-void chunks.
     */
    private void makeChunkVoid(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        Environment env = world.getEnvironment();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Material type = chunk.getBlock(x, y, z).getType();

                    // Decide whether to preserve this block when voiding
                    if (shouldPreserveInVoid(env, type)) {
                        continue; // leave the block as-is
                    }

                    // Otherwise, clear it
                    chunk.getBlock(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    /**
     * Decide which blocks to keep when voiding a chunk.
     * Currently:
     *  - Nether: keep NETHER_BRICKS
     *  - Overworld/other: keep nothing
     */
    private boolean shouldPreserveInVoid(Environment env, Material type) {
    	if (env == Environment.NETHER) {
    	    return type == Material.NETHER_BRICKS
    	        || type == Material.NETHER_BRICK_FENCE
    	        || type == Material.NETHER_BRICK_STAIRS
    	        || type == Material.NETHER_BRICK_SLAB;
    	}
        return false;
    }

    public OverworldProtection getOverworldProtection() {
        return overworldProtection;
    }

    public NetherProtection getNetherProtection() {
        return netherProtection;
    }
}
