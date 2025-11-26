package com.daytonjwatson.chunkfall.config;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class ChunkFallConfig {

    private final String targetWorldName;
    private final int regionSizeChunks;
    private final boolean setOverworldSpawnOnIsland;

    private final boolean protectEndPortalRoom;
    private final boolean protectEndPortalNeighbors;
    private final boolean protectNetherFortressEssentials;

    private final boolean disableElytraRocketBoost;

    private final boolean starterKitEnabled;

    // Limbo settings
    private final String limboWorldName;
    private final int limboBedrockY;
    private final int limboCobblestoneStackSize;
    private final int limboAnchorsPerEntry;
    private final int limboAnchorMinY;
    private final int limboAnchorMaxY;
    private final int limboAnchorRadius;

    // Cobble generator settings
    private final boolean cobbleGeneratorEnabled;
    private final int cobbleGeneratorTicksPerCobble;
    private final int cobbleVerticalSearchRange;
    private final double cobbleEfficiencyPerLevel;

    private final double cobbleSpeedWooden;
    private final double cobbleSpeedStone;
    private final double cobbleSpeedCopper;
    private final double cobbleSpeedIron;
    private final double cobbleSpeedGold;
    private final double cobbleSpeedDiamond;
    private final double cobbleSpeedNetherite;

    private final boolean cobbleParticlesEnabled;
    private final boolean cobbleSoundOnCreate;
    private final boolean cobbleSoundOnMine;
    private final boolean cobbleSoundOnBreak;
    private final boolean cobbleAnimationEnabled;
    private final int cobbleAnimationUpdateTicks;
    private final boolean cobbleDebug;

    public ChunkFallConfig(Plugin plugin) {
        FileConfiguration cfg = plugin.getConfig();

        this.targetWorldName = cfg.getString("target-world", "world");
        this.regionSizeChunks = Math.max(1, cfg.getInt("region-size-chunks", 64));
        this.setOverworldSpawnOnIsland = cfg.getBoolean("set-overworld-spawn-on-island", true);

        this.protectEndPortalRoom = cfg.getBoolean("protect-end-portal-room", true);
        this.protectEndPortalNeighbors = cfg.getBoolean("protect-end-portal-neighbors", true);

        this.protectNetherFortressEssentials =
                cfg.getBoolean("protect-nether-fortress-essential-chunks", true);

        this.disableElytraRocketBoost =
                cfg.getBoolean("disable-elytra-rocket-boost", true);

        this.starterKitEnabled =
                cfg.getConfigurationSection("starter-kit") != null
                        && cfg.getBoolean("starter-kit.enabled", true);

        // Limbo
        this.limboWorldName = cfg.getString("limbo.world-name", "limbo");
        this.limboBedrockY = cfg.getInt("limbo.bedrock-y", 64);
        this.limboCobblestoneStackSize = cfg.getInt("limbo.cobblestone-stack-size", 64);
        this.limboAnchorsPerEntry = cfg.getInt("limbo.anchors-per-entry", 3);
        this.limboAnchorMinY = cfg.getInt("limbo.anchor-min-y", 40);
        this.limboAnchorMaxY = cfg.getInt("limbo.anchor-max-y", 120);
        this.limboAnchorRadius = cfg.getInt("limbo.anchor-radius", 256);

        // Cobble generator
        ConfigurationSection cg = cfg.getConfigurationSection("cobble-generator");

        this.cobbleGeneratorEnabled =
                cg != null && cg.getBoolean("enabled", true);

        this.cobbleGeneratorTicksPerCobble =
                cg != null ? cg.getInt("ticks-per-cobble", 20) : 20;

        this.cobbleVerticalSearchRange =
                cg != null ? cg.getInt("vertical-search-range", 64) : 64;

        this.cobbleEfficiencyPerLevel =
                cg != null ? cg.getDouble("efficiency-multiplier-per-level", 0.2) : 0.2;

        ConfigurationSection tier = cg != null ? cg.getConfigurationSection("tier-speed") : null;
        this.cobbleSpeedWooden = tier != null ? tier.getDouble("wooden", 0.25) : 0.25;
        this.cobbleSpeedStone = tier != null ? tier.getDouble("stone", 0.5) : 0.5;
        this.cobbleSpeedCopper = tier != null ? tier.getDouble("copper", 0.625) : 0.625;
        this.cobbleSpeedIron = tier != null ? tier.getDouble("iron", 0.75) : 0.75;
        this.cobbleSpeedGold = tier != null ? tier.getDouble("gold", 1.25) : 1.25;
        this.cobbleSpeedDiamond = tier != null ? tier.getDouble("diamond", 1.0) : 1.0;
        this.cobbleSpeedNetherite = tier != null ? tier.getDouble("netherite", 1.1) : 1.1;

        this.cobbleParticlesEnabled =
                cg != null && cg.getBoolean("particles", true);

        ConfigurationSection snd = cg != null ? cg.getConfigurationSection("sound") : null;
        this.cobbleSoundOnCreate = snd != null ? snd.getBoolean("on-create", true) : true;
        this.cobbleSoundOnMine = snd != null ? snd.getBoolean("on-mine", true) : true;
        this.cobbleSoundOnBreak = snd != null ? snd.getBoolean("on-break", true) : true;

        ConfigurationSection animation = cg != null ? cg.getConfigurationSection("animation") : null;
        this.cobbleAnimationEnabled = animation == null || animation.getBoolean("enabled", true);
        this.cobbleAnimationUpdateTicks = animation != null ? Math.max(1, animation.getInt("update-ticks", 5)) : 5;

        this.cobbleDebug = cg != null && cg.getBoolean("debug", false);
    }

    public String getTargetWorldName() {
        return targetWorldName;
    }

    public int getRegionSizeChunks() {
        return regionSizeChunks;
    }

    public boolean isSetOverworldSpawnOnIsland() {
        return setOverworldSpawnOnIsland;
    }

    public boolean isProtectEndPortalRoom() {
        return protectEndPortalRoom;
    }

    public boolean isProtectEndPortalNeighbors() {
        return protectEndPortalNeighbors;
    }

    public boolean isProtectNetherFortressEssentials() {
        return protectNetherFortressEssentials;
    }

    public boolean isDisableElytraRocketBoost() {
        return disableElytraRocketBoost;
    }

    public boolean isStarterKitEnabled() {
        return starterKitEnabled;
    }

    // Limbo getters
    public String getLimboWorldName() {
        return limboWorldName;
    }

    public int getLimboBedrockY() {
        return limboBedrockY;
    }

    public int getLimboCobblestoneStackSize() {
        return limboCobblestoneStackSize;
    }

    public int getLimboAnchorsPerEntry() {
        return limboAnchorsPerEntry;
    }

    public int getLimboAnchorMinY() {
        return limboAnchorMinY;
    }

    public int getLimboAnchorMaxY() {
        return limboAnchorMaxY;
    }

    public int getLimboAnchorRadius() {
        return limboAnchorRadius;
    }

    // Cobble generator getters
    public boolean isCobbleGeneratorEnabled() {
        return cobbleGeneratorEnabled;
    }

    public int getCobbleGeneratorTicksPerCobble() {
        return cobbleGeneratorTicksPerCobble;
    }

    public int getCobbleVerticalSearchRange() {
        return cobbleVerticalSearchRange;
    }

    public double getCobbleEfficiencyPerLevel() {
        return cobbleEfficiencyPerLevel;
    }

    public double getCobbleSpeedWooden() {
        return cobbleSpeedWooden;
    }

    public double getCobbleSpeedStone() {
        return cobbleSpeedStone;
    }

    public double getCobbleSpeedCopper() {
        return cobbleSpeedCopper;
    }

    public double getCobbleSpeedIron() {
        return cobbleSpeedIron;
    }

    public double getCobbleSpeedGold() {
        return cobbleSpeedGold;
    }

    public double getCobbleSpeedDiamond() {
        return cobbleSpeedDiamond;
    }

    public double getCobbleSpeedNetherite() {
        return cobbleSpeedNetherite;
    }

    public boolean isCobbleParticlesEnabled() {
        return cobbleParticlesEnabled;
    }

    public boolean isCobbleSoundOnCreate() {
        return cobbleSoundOnCreate;
    }

    public boolean isCobbleSoundOnMine() {
        return cobbleSoundOnMine;
    }

    public boolean isCobbleSoundOnBreak() {
        return cobbleSoundOnBreak;
    }

    public boolean isCobbleAnimationEnabled() {
        return cobbleAnimationEnabled;
    }

    public int getCobbleAnimationUpdateTicks() {
        return cobbleAnimationUpdateTicks;
    }

    public boolean isCobbleDebug() {
        return cobbleDebug;
    }

    public boolean isTargetWorld(World world) {
        if (world.getEnvironment() == Environment.THE_END) {
            return false;
        }
        String name = world.getName();
        if (name.equals(targetWorldName)) {
            return true; // overworld
        }
        return name.equals(targetWorldName + "_nether"); // nether
    }
}
