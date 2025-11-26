package com.daytonjwatson.chunkfall;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import com.daytonjwatson.chunkfall.command.ChunkFallCommand;
import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import com.daytonjwatson.chunkfall.listener.ChunkLoadListener;
import com.daytonjwatson.chunkfall.listener.CobbleGeneratorListener;
import com.daytonjwatson.chunkfall.listener.ElytraListener;
import com.daytonjwatson.chunkfall.listener.LimboChunkListener;
import com.daytonjwatson.chunkfall.listener.LimboListener;
import com.daytonjwatson.chunkfall.listener.VoidDeathListener;
import com.daytonjwatson.chunkfall.logic.ChunkProcessor;
import com.daytonjwatson.chunkfall.logic.CobbleGeneratorManager;
import com.daytonjwatson.chunkfall.logic.LimboManager;

public class ChunkFallPlugin extends JavaPlugin {

    private ChunkFallConfig chunkFallConfig;
    private ChunkProcessor chunkProcessor;
    private LimboManager limboManager;
    private CobbleGeneratorManager cobbleGeneratorManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Load config and core logic
        this.chunkFallConfig = new ChunkFallConfig(this);
        this.chunkProcessor = new ChunkProcessor(chunkFallConfig);
        this.limboManager = new LimboManager(chunkFallConfig);

        // Register main world / nether chunk logic
        Bukkit.getPluginManager().registerEvents(
                new ChunkLoadListener(chunkFallConfig, chunkProcessor),
                this
        );

        // Limbo void generation
        Bukkit.getPluginManager().registerEvents(
                new LimboChunkListener(chunkFallConfig),
                this
        );

        // Elytra rocket blocking
        Bukkit.getPluginManager().registerEvents(
                new ElytraListener(chunkFallConfig),
                this
        );

        // Limbo interaction (infinite cobble + anchor escape)
        Bukkit.getPluginManager().registerEvents(
                new LimboListener(chunkFallConfig, limboManager),
                this
        );

        // Void-death routing to Limbo
        Bukkit.getPluginManager().registerEvents(
                new VoidDeathListener(this, chunkFallConfig, limboManager),
                this
        );

        // Cobblestone generator: INSTANTIATE before using
        if (chunkFallConfig.isCobbleGeneratorEnabled()) {
            this.cobbleGeneratorManager = new CobbleGeneratorManager(this, chunkFallConfig);

            Bukkit.getPluginManager().registerEvents(
                    new CobbleGeneratorListener(chunkFallConfig, cobbleGeneratorManager),
                    this
            );

            cobbleGeneratorManager.start();
        }

        // Setup overworld spawn island
        World overworld = Bukkit.getWorld(chunkFallConfig.getTargetWorldName());
        if (overworld != null && chunkFallConfig.isSetOverworldSpawnOnIsland()) {
            chunkProcessor.ensureSpawnOnIsland(overworld);
        }

        ChunkFallCommand command = new ChunkFallCommand(this);
        if (getCommand("chunkfall") != null) {
            getCommand("chunkfall").setExecutor(command);
            getCommand("chunkfall").setTabCompleter(command);
        }
    }

    public ChunkFallConfig getChunkFallConfig() {
        return chunkFallConfig;
    }

    public ChunkProcessor getChunkProcessor() {
        return chunkProcessor;
    }

    public LimboManager getLimboManager() {
        return limboManager;
    }

    public CobbleGeneratorManager getCobbleGeneratorManager() {
        return cobbleGeneratorManager;
    }
}
