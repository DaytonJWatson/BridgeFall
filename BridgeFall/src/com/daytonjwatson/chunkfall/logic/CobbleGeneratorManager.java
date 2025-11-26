package com.daytonjwatson.chunkfall.logic;

import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CobbleGeneratorManager {

    private final Plugin plugin;
    private final ChunkFallConfig config;

    // Barrels acting as generators
    private final Set<Location> generators = new HashSet<>();
    // Per-generator progress counter (how close we are to next cobble)
    private final Map<Location, Double> progress = new HashMap<>();
    // Per-generator remaining "uses" of the currently consumed fuel item
    private final Map<Location, Integer> fuelUsesRemaining = new HashMap<>();

    public CobbleGeneratorManager(Plugin plugin, ChunkFallConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (!config.isCobbleGeneratorEnabled()) {
            return;
        }

        long period = config.getCobbleGeneratorTicksPerCobble();
        if (period <= 0L) {
            period = 20L;
        }

        plugin.getLogger().info("[ChunkFall] Cobblestone generator task started, base period=" + period + " ticks.");

        Bukkit.getScheduler().runTaskTimer(plugin, this::tickGenerators, period, period);
    }

    public void registerGenerator(Block block) {
        if (block.getType() != Material.BARREL) {
            return;
        }
        Location loc = block.getLocation();
        generators.add(loc);
        progress.put(loc, 0.0);
        fuelUsesRemaining.put(loc, 0);
        plugin.getLogger().info("[ChunkFall] Registered cobble generator at " +
                loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
    }

    public void unregisterGenerator(Block block) {
        Location loc = block.getLocation();
        generators.remove(loc);
        progress.remove(loc);
        fuelUsesRemaining.remove(loc);
        plugin.getLogger().info("[ChunkFall] Unregistered cobble generator at " +
                loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
    }

    private void tickGenerators() {
        if (!config.isCobbleGeneratorEnabled()) {
            return;
        }

        if (generators.isEmpty()) {
            return;
        }

        Iterator<Location> it = generators.iterator();
        while (it.hasNext()) {
            Location loc = it.next();
            World world = loc.getWorld();
            if (world == null) {
                it.remove();
                progress.remove(loc);
                fuelUsesRemaining.remove(loc);
                continue;
            }

            int bx = loc.getBlockX();
            int by = loc.getBlockY();
            int bz = loc.getBlockZ();

            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                continue;
            }

            Block block = world.getBlockAt(bx, by, bz);
            if (block.getType() != Material.BARREL) {
                it.remove();
                progress.remove(loc);
                fuelUsesRemaining.remove(loc);
                continue;
            }

            BlockState state = block.getState();
            if (!(state instanceof Container container)) {
                it.remove();
                progress.remove(loc);
                fuelUsesRemaining.remove(loc);
                continue;
            }

            Inventory inv = container.getInventory();

            // Any pickaxe type in slot 0
            ItemStack pick = inv.getItem(0);
            if (!isPickaxe(pick)) {
                continue;
            }

            double speedMultiplier = getSpeedMultiplier(pick);
            if (speedMultiplier <= 0) {
                continue;
            }

            boolean hasFuel = hasFuelAvailable(inv, loc);
            if (hasFuel && config.isCobbleParticlesEnabled()) {
                spawnWorkingAnimation(world, bx, by, bz, false);
            }

            double prog = progress.getOrDefault(loc, 0.0);
            prog += speedMultiplier;

            boolean inventoryFull = false;
            boolean generatedThisTick = false;

            while (prog >= 1.0) {
                ItemStack currentPick = inv.getItem(0);
                MineResult result = mineNearestStoneInChunk(world, inv, 0, bx, by, bz, loc, currentPick);

                if (!result.mined) {
                    // Could not mine (no stone, no fuel, or inventory full)
                    inventoryFull = result.inventoryFull;
                    break;
                }

                generatedThisTick = true;
                prog -= 1.0;

                if (result.pickBroke) {
                    // Pick broke; stop for this tick
                    break;
                }
            }

            // Visual + audio feedback when we actually mined at least one stone
            if (generatedThisTick) {
                spawnWorkingAnimation(world, bx, by, bz, true);

                if (config.isCobbleSoundOnMine()) {
                    double px = bx + 0.5;
                    double py = by + 1.2;
                    double pz = bz + 0.5;

                    world.playSound(new Location(world, px, py, pz), Sound.BLOCK_STONE_BREAK, 0.5f, 1.0f);
                }
            }

            if (inventoryFull) {
                // Keep current progress; next time it will try again
                progress.put(loc, prog);
            } else {
                if (prog < 0) prog = 0;
                if (prog > 10) prog = 10; // arbitrary cap
                progress.put(loc, prog);
            }
        }
    }

    private boolean isPickaxe(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return type == Material.WOODEN_PICKAXE
                || type == Material.STONE_PICKAXE
                || type == Material.COPPER_PICKAXE
                || type == Material.IRON_PICKAXE
                || type == Material.GOLDEN_PICKAXE
                || type == Material.DIAMOND_PICKAXE
                || type == Material.NETHERITE_PICKAXE;
    }

    /**
     * Base multipliers per tick are configured in config.yml under cobble-generator.tier-speed.
     * Efficiency adds +X per level (X from config).
     */
    private double getSpeedMultiplier(ItemStack pick) {
        Material type = pick.getType();
        double base;

        switch (type) {
            case WOODEN_PICKAXE -> base = config.getCobbleSpeedWooden();
            case STONE_PICKAXE -> base = config.getCobbleSpeedStone();
            case COPPER_PICKAXE -> base = config.getCobbleSpeedCopper();
            case IRON_PICKAXE -> base = config.getCobbleSpeedIron();
            case GOLDEN_PICKAXE -> base = config.getCobbleSpeedGold();
            case DIAMOND_PICKAXE -> base = config.getCobbleSpeedDiamond();
            case NETHERITE_PICKAXE -> base = config.getCobbleSpeedNetherite();
            default -> base = 0.0;
        }

        if (base <= 0.0) {
            return 0.0;
        }

        int effLevel = pick.getEnchantmentLevel(Enchantment.EFFICIENCY); // Efficiency
        double effPerLevel = config.getCobbleEfficiencyPerLevel();
        double effMultiplier = 1.0 + (effPerLevel * effLevel);

        return base * effMultiplier;
    }

    private boolean hasFuelAvailable(Inventory inv, Location generatorLoc) {
        if (fuelUsesRemaining.getOrDefault(generatorLoc, 0) > 0) {
            return true;
        }

        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot == 0) {
                continue;
            }

            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }

            if (isFuel(stack.getType())) {
                return true;
            }
        }

        return false;
    }

    private void spawnWorkingAnimation(World world, int bx, int by, int bz, boolean harvested) {
        if (!config.isCobbleParticlesEnabled()) {
            return;
        }

        BlockData cobbleData = Material.COBBLESTONE.createBlockData();
        double px = bx + 0.5;
        double py = by + 1.15;
        double pz = bz + 0.5;

        int crackCount = harvested ? 6 : 3;
        world.spawnParticle(Particle.BLOCK_CRUMBLE, px, py, pz, crackCount, 0.25, 0.15, 0.25, 0.0, cobbleData);

        Particle smokeType = harvested ? Particle.CAMPFIRE_COSY_SMOKE : Particle.SMOKE;
        world.spawnParticle(Particle.BLOCK_CRUMBLE, px, py, pz, crackCount, 0.25, 0.15, 0.25, 0.0, cobbleData);

        int smokeCount = harvested ? 4 : 2;
        world.spawnParticle(smokeType, px, py + 0.1, pz, smokeCount, 0.12, 0.1, 0.12, harvested ? 0.0 : 0.01);
    }

    /**
     * Damage the pickaxe in a given slot by 1, obeying Unbreaking.
     * Plays a sound when the pick breaks (if enabled).
     *
     * @return false if the pick broke or disappeared, true otherwise.
     */
    private boolean damagePickaxe(Inventory inv, int slot, World world, int bx, int by, int bz) {
        ItemStack pick = inv.getItem(slot);
        if (!isPickaxe(pick)) {
            return false;
        }

        ItemMeta meta = pick.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return true;
        }

        // Unbreaking reduces the chance to actually take durability damage
        int unbreakingLevel = pick.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreakingLevel > 0) {
            double chanceToDamage = 1.0 / (unbreakingLevel + 1);
            if (ThreadLocalRandom.current().nextDouble() >= chanceToDamage) {
                // This “use” did not consume durability
                return true;
            }
        }

        int newDamage = damageable.getDamage() + 1;
        int maxDurability = pick.getType().getMaxDurability();

        if (newDamage >= maxDurability) {
            // Break the pick
            inv.setItem(slot, null);

            if (config.isCobbleSoundOnBreak()) {
                world.playSound(
                        new Location(world, bx + 0.5, by + 0.5, bz + 0.5),
                        Sound.ENTITY_ITEM_BREAK,
                        0.8f,
                        0.9f
                );
            }

            return false;
        } else {
            damageable.setDamage(newDamage);
            pick.setItemMeta(meta);
            inv.setItem(slot, pick);
            return true;
        }
    }

    /**
     * Consume one "fuel use" for this generator. If no buffered uses remain, tries to
     * take a new fuel item from the barrel (any slot except 0).
     *
     * Returns true if fuel was successfully consumed (or buffered), false if there is no fuel.
     */
    private boolean consumeFuelUse(Location generatorLoc, Inventory inv) {
        int remaining = fuelUsesRemaining.getOrDefault(generatorLoc, 0);
        if (remaining > 0) {
            fuelUsesRemaining.put(generatorLoc, remaining - 1);
            return true;
        }

        // Need to pull a new fuel item from the barrel.
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot == 0) {
                // slot 0 is reserved for the pickaxe
                continue;
            }

            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }

            Material type = stack.getType();
            if (!isFuel(type)) {
                continue;
            }

            int uses = getFuelUses(type);
            if (uses <= 0) {
                continue;
            }

            // Consume one item of this fuel stack
            int newAmount = stack.getAmount() - 1;
            if (newAmount <= 0) {
                inv.setItem(slot, null);
            } else {
                stack.setAmount(newAmount);
            }

            // We immediately consume one "use" for this mining, buffer the rest
            fuelUsesRemaining.put(generatorLoc, uses - 1);
            return true;
        }

        // No suitable fuel found
        return false;
    }

    /**
     * "Is this a valid furnace fuel" — use Bukkit's built-in knowledge.
     */
    private boolean isFuel(Material type) {
        return type != null && type.isFuel();
    }

    /**
     * How many cobblestone "mines" a single item of this fuel should provide.
     * This does NOT try to be pixel-perfect with vanilla burn times, but roughly
     * respects stronger vs weaker fuels.
     */
    private int getFuelUses(Material type) {
        return switch (type) {
            case LAVA_BUCKET -> 100;          // very strong fuel
            case COAL_BLOCK -> 72;            // 9 * 8
            case COAL, CHARCOAL, BLAZE_ROD -> 8;
            default -> 4;                     // generic fuel (logs, planks, etc.)
        };
    }

    /**
     * Find and mine the closest STONE block in the barrel's chunk.
     * Requires:
     *  - free inventory space for cobble,
     *  - at least 1 "fuel use" available or consumable from inventory.
     * If any of those is missing, this returns mined=false and does nothing.
     */
    private MineResult mineNearestStoneInChunk(World world,
                                               Inventory inv,
                                               int pickSlot,
                                               int barrelX,
                                               int barrelY,
                                               int barrelZ,
                                               Location generatorLoc,
                                               ItemStack pickForAnimation) {

        MineResult result = new MineResult();

        int chunkX = barrelX >> 4;
        int chunkZ = barrelZ >> 4;
        int chunkMinX = chunkX << 4;
        int chunkMinZ = chunkZ << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;

        int maxYRange = Math.max(1, config.getCobbleVerticalSearchRange());

        Block bestBlock = null;
        int bestDistSq = Integer.MAX_VALUE;

        // Search outward in vertical "rings" around barrelY
        for (int dy = 0; dy <= maxYRange; dy++) {
            int[] ys = {barrelY + dy, barrelY - dy};

            for (int yi = 0; yi < 2; yi++) {
                int y = ys[yi];
                if (y < minY || y > maxY) {
                    continue;
                }

                for (int x = chunkMinX; x <= chunkMaxX; x++) {
                    for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                        Block candidate = world.getBlockAt(x, y, z);
                        if (candidate.getType() != Material.STONE) {
                            continue;
                        }

                        int dx = x - barrelX;
                        int dz = z - barrelZ;
                        int dy2 = y - barrelY;
                        int distSq = dx * dx + dy2 * dy2 + dz * dz;

                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            bestBlock = candidate;
                        }
                    }
                }
            }

            if (bestBlock != null) {
                break;
            }
        }

        if (bestBlock == null) {
            // No stone found in chunk (within the vertical range)
            return result;
        }

        // Check inventory space for 1 cobblestone BEFORE consuming fuel
        if (!hasSpaceForCobble(inv)) {
            result.inventoryFull = true;
            return result;
        }

        // Require fuel: consume one "fuel use" (buffered or a new item)
        if (!consumeFuelUse(generatorLoc, inv)) {
            // No fuel: cannot mine
            return result;
        }

        BlockData minedData = bestBlock.getBlockData();

        // Actually add 1 cobblestone to the barrel
        if (!addOneCobble(inv)) {
            // Shouldn't happen since we checked space, but be defensive:
            result.inventoryFull = true;
            return result;
        }

        spawnMiningAnimation(bestBlock, minedData, pickForAnimation);

        // Mine the stone: turn it into air
        bestBlock.setType(Material.AIR, false);

        // Damage the pickaxe
        boolean stillExists = damagePickaxe(inv, pickSlot, world, barrelX, barrelY, barrelZ);

        result.mined = true;
        result.pickBroke = !stillExists;
        return result;
    }

    /**
     * Check if the inventory has room to add one cobblestone (either stacking or empty slot).
     */
    private boolean hasSpaceForCobble(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) {
                return true;
            }
            if (stack.getType() == Material.COBBLESTONE && stack.getAmount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Actually add a single cobblestone item to the inventory, assuming there is space.
     */
    private boolean addOneCobble(Inventory inv) {
        // First try to stack onto existing cobble
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() == Material.COBBLESTONE &&
                    stack.getAmount() < stack.getMaxStackSize()) {
                stack.setAmount(stack.getAmount() + 1);
                inv.setItem(i, stack);
                return true;
            }
        }

        // Otherwise, use an empty slot
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) {
                inv.setItem(i, new ItemStack(Material.COBBLESTONE, 1));
                return true;
            }
        }

        return false;
    }

    private static class MineResult {
        boolean mined = false;
        boolean pickBroke = false;
        boolean inventoryFull = false;
    }

    private void spawnMiningAnimation(Block block, BlockData minedData, ItemStack pickForAnimation) {
        if (!config.isCobbleParticlesEnabled()) {
            return;
        }

        Location loc = block.getLocation();
        World world = block.getWorld();
        double px = loc.getX() + 0.5;
        double py = loc.getY() + 0.5;
        double pz = loc.getZ() + 0.5;

        world.spawnParticle(Particle.BLOCK_CRUMBLE, px, py, pz, 12, 0.2, 0.2, 0.2, 0.0, minedData);
        world.spawnParticle(Particle.CRIT, px, py, pz, 6, 0.15, 0.25, 0.15, 0.01);

        if (isPickaxe(pickForAnimation)) {
            spawnPickaxeSwing(loc, pickForAnimation);
        }
    }

    private void spawnPickaxeSwing(Location blockLoc, ItemStack pickForAnimation) {
        World world = blockLoc.getWorld();
        if (world == null) {
            return;
        }

        ItemStack displayPick = pickForAnimation.clone();
        displayPick.setAmount(1);

        Location standLoc = blockLoc.toCenterLocation().add(0, 0.2, 0);
        ArmorStand stand = world.spawn(standLoc, ArmorStand.class, armorStand -> {
            armorStand.setInvisible(true);
            armorStand.setMarker(true);
            armorStand.setGravity(false);
            armorStand.setSilent(true);
            armorStand.setSmall(true);
            armorStand.setCollidable(false);
            armorStand.setArms(true);
            if (armorStand.getEquipment() != null) {
                armorStand.getEquipment().setItemInMainHand(displayPick);
            }
            armorStand.setRightArmPose(new EulerAngle(Math.toRadians(-100), 0, Math.toRadians(25)));
            armorStand.setRotation(ThreadLocalRandom.current().nextFloat() * 360f, 0f);
        });

        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!stand.isValid()) {
                    cancel();
                    return;
                }

                switch (ticks) {
                    case 1 -> {
                        stand.setRightArmPose(new EulerAngle(Math.toRadians(-40), 0, Math.toRadians(-5)));
                        stand.teleport(stand.getLocation().add(0, 0.08, 0));
                    }
                    case 2 -> {
                        stand.setRightArmPose(new EulerAngle(Math.toRadians(-160), 0, Math.toRadians(10)));
                        stand.teleport(stand.getLocation().add(0, -0.12, 0));
                    }
                    case 3 -> stand.setRightArmPose(new EulerAngle(Math.toRadians(-75), 0, Math.toRadians(18)));
                    default -> {
                    }
                }

                if (ticks >= 4) {
                    stand.remove();
                    cancel();
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
