package com.daytonjwatson.chunkfall.logic;

import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class LimboManager {

    private final ChunkFallConfig config;
    private final Random random = new Random();

    // Must match LimboListener’s platform definition
    private static final int PLATFORM_CENTER_X = 1;
    private static final int PLATFORM_CENTER_Z = 1;
    private static final int PLATFORM_HALF_SIZE_NEG = 4; // center-4
    private static final int PLATFORM_HALF_SIZE_POS = 5; // center+5

    public LimboManager(ChunkFallConfig config) {
        this.config = config;
    }

    private boolean isInPlatformColumn(int x, int z) {
        int minX = PLATFORM_CENTER_X - PLATFORM_HALF_SIZE_NEG;
        int maxX = PLATFORM_CENTER_X + PLATFORM_HALF_SIZE_POS;
        int minZ = PLATFORM_CENTER_Z - PLATFORM_HALF_SIZE_NEG;
        int maxZ = PLATFORM_CENTER_Z + PLATFORM_HALF_SIZE_POS;

        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * Returns the Limbo world, creating it if it does not exist.
     * Also enforces:
     *  - Always night
     *  - No mob spawning
     */
    public World getOrCreateLimboWorld() {
        String name = config.getLimboWorldName();
        World world = Bukkit.getWorld(name);

        if (world == null) {
            WorldCreator creator = new WorldCreator(name);
            creator.environment(World.Environment.NORMAL);
            creator.generateStructures(false);

            world = creator.createWorld();
        }

        if (world != null) {
            // Always night
            world.setTime(6000L);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);

            // No monsters
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setDifficulty(Difficulty.PEACEFUL);
        }

        return world;
    }

    public World getLimboWorld() {
        return Bukkit.getWorld(config.getLimboWorldName());
    }

    // Default behavior when coming from main world
    public void sendPlayerToLimbo(Player player) {
        sendPlayerToLimbo(player, true);
    }

    /**
     * Send player to Limbo.
     * @param spawnAnchors if true, spawn new random anchors; if false, do not.
     */
    public void sendPlayerToLimbo(Player player, boolean spawnAnchors) {
        World limbo = getOrCreateLimboWorld();
        if (limbo == null) {
            return;
        }

        int bedrockY = config.getLimboBedrockY();

        // Ensure spawn chunk is loaded
        limbo.getChunkAt(0, 0).load(true);

        // 10x10 bedrock platform centered at (1,1)
        for (int x = PLATFORM_CENTER_X - PLATFORM_HALF_SIZE_NEG; x <= PLATFORM_CENTER_X + PLATFORM_HALF_SIZE_POS; x++) {
            for (int z = PLATFORM_CENTER_Z - PLATFORM_HALF_SIZE_NEG; z <= PLATFORM_CENTER_Z + PLATFORM_HALF_SIZE_POS; z++) {
                Block bedrock = limbo.getBlockAt(x, bedrockY, z);
                bedrock.setType(Material.BEDROCK, false);
            }
        }

        // Teleport player above the center of the platform
        Location spawnLoc = new Location(
                limbo,
                PLATFORM_CENTER_X + 0.5,
                bedrockY + 1,
                PLATFORM_CENTER_Z + 0.5
        );
        player.teleport(spawnLoc);

        // Reset inventory and give "infinite" cobblestone stack
        player.getInventory().clear();
        player.getInventory().addItem(
        		new ItemStack(Material.COPPER_PICKAXE, 1),
                new ItemStack(Material.COBBLESTONE, config.getLimboCobblestoneStackSize())
        );

        if (spawnAnchors) {
            spawnRandomAnchors(limbo, config.getLimboAnchorsPerEntry());
            Bukkit.broadcastMessage(ChatColor.RED + player.getName() + " has been sent to Limbo!");
        }
    }

    public void spawnRandomAnchors(World limbo, int count) {
        int minY = config.getLimboAnchorMinY();
        int maxY = config.getLimboAnchorMaxY();
        int radius = config.getLimboAnchorRadius();

        for (int i = 0; i < count; i++) {
            // Try a few times to find a position outside the platform column
            int attempts = 0;
            int x, z;

            do {
                double angle = random.nextDouble() * Math.PI * 2.0;
                int dist = random.nextInt(radius) + 1;

                x = (int) Math.round(Math.cos(angle) * dist);
                z = (int) Math.round(Math.sin(angle) * dist);

                attempts++;
            } while (isInPlatformColumn(x, z) && attempts < 20);

            // If we somehow still landed inside, just skip this anchor
            if (isInPlatformColumn(x, z)) {
                continue;
            }

            int y = minY + random.nextInt(Math.max(1, maxY - minY + 1));

            limbo.getChunkAt(x >> 4, z >> 4).load(true);
            Block block = limbo.getBlockAt(x, y, z);
            block.setType(Material.RESPAWN_ANCHOR, false);
        }
    }

    public void useRespawnAnchor(Player player, Block anchorBlock) {
        anchorBlock.setType(Material.AIR, false);

        World main = Bukkit.getWorld(config.getTargetWorldName());
        if (main == null) {
            return;
        }

        Location mainSpawn = main.getSpawnLocation();
        player.getInventory().clear();
        player.teleport(mainSpawn);
        Bukkit.broadcastMessage(ChatColor.RED + player.getName() + " has escaped Limbo!");
    }
}
