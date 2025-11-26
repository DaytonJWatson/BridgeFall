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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class CobbleGeneratorManager {

    private static final double PROGRESS_CAP = 10.0;

    private final Plugin plugin;
    private final ChunkFallConfig config;
    private final CobbleGeneratorAnimationManager animationManager;

    private final Map<Location, CobbleGeneratorState> generators = new HashMap<>();
    private BukkitTask generatorTask;

    public CobbleGeneratorManager(Plugin plugin, ChunkFallConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.animationManager = new CobbleGeneratorAnimationManager(plugin, config);
    }

    public void start() {
        if (!config.isCobbleGeneratorEnabled()) {
            return;
        }

        long period = Math.max(1L, config.getCobbleGeneratorTicksPerCobble());
        generatorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickGenerators, period, period);
        animationManager.start();

        plugin.getLogger().info("[ChunkFall] Cobblestone generator task started, base period=" + period + " ticks.");
    }

    public void stop() {
        if (generatorTask != null) {
            generatorTask.cancel();
            generatorTask = null;
        }
        animationManager.stop();
        generators.clear();
    }

    public void registerGenerator(Block block) {
        if (block == null || block.getType() != Material.BARREL) {
            return;
        }

        Location location = block.getLocation().toBlockLocation();
        generators.put(location, new CobbleGeneratorState());
        debug("Registered generator at " + format(location));
    }

    public void unregisterGenerator(Block block) {
        if (block == null) {
            return;
        }
        Location location = block.getLocation().toBlockLocation();
        generators.remove(location);
        animationManager.hide(location);
        debug("Unregistered generator at " + format(location));
    }

    public boolean isGenerator(Location location) {
        return location != null && generators.containsKey(location.toBlockLocation());
    }

    private void tickGenerators() {
        if (!config.isCobbleGeneratorEnabled() || generators.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<Location, CobbleGeneratorState>> iterator = generators.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, CobbleGeneratorState> entry = iterator.next();
            Location location = entry.getKey();
            CobbleGeneratorState state = entry.getValue();

            World world = location.getWorld();
            if (world == null) {
                iterator.remove();
                animationManager.hide(location);
                continue;
            }

            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                animationManager.hide(location);
                continue;
            }

            Block block = world.getBlockAt(location);
            if (block.getType() != Material.BARREL) {
                iterator.remove();
                animationManager.hide(location);
                continue;
            }

            BlockState blockState = block.getState();
            if (!(blockState instanceof Container container)) {
                iterator.remove();
                animationManager.hide(location);
                continue;
            }

            Inventory inventory = container.getInventory();
            ItemStack pickaxe = inventory.getItem(0);
            if (!isPickaxe(pickaxe)) {
                animationManager.hide(location);
                continue;
            }

            double speedMultiplier = getSpeedMultiplier(pickaxe);
            if (speedMultiplier <= 0) {
                animationManager.hide(location);
                continue;
            }

            boolean hasFuel = hasFuelAvailable(inventory, state);
            if (!hasFuel) {
                animationManager.hide(location);
                continue;
            }

            animationManager.show(location, pickaxe);

            double progress = state.getProgress() + speedMultiplier;
            boolean generated = false;
            boolean inventoryFull = false;

            while (progress >= 1.0) {
                MineResult result = mineNearestStoneInChunk(
                        world,
                        inventory,
                        0,
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ(),
                        pickaxe,
                        state
                );

                if (!result.mined) {
                    inventoryFull = result.inventoryFull;
                    break;
                }

                generated = true;
                progress -= 1.0;

                if (result.pickBroke) {
                    animationManager.hide(location);
                    break;
                }
            }

            if (generated) {
                spawnWorkingAnimation(world, location.getBlockX(), location.getBlockY(), location.getBlockZ(), true);
                playMineSound(world, location);
            }

            if (inventoryFull) {
                state.setProgress(progress);
            } else {
                state.setProgress(Math.min(Math.max(0.0, progress), PROGRESS_CAP));
            }
        }
    }

    private void playMineSound(World world, Location location) {
        if (!config.isCobbleSoundOnMine()) {
            return;
        }
        Location soundLoc = location.toCenterLocation().add(0, 0.6, 0);
        world.playSound(soundLoc, Sound.BLOCK_STONE_BREAK, 0.6f, 1.0f);
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

        int effLevel = pick.getEnchantmentLevel(Enchantment.EFFICIENCY);
        double effPerLevel = config.getCobbleEfficiencyPerLevel();
        double effMultiplier = 1.0 + (effPerLevel * effLevel);

        return base * effMultiplier;
    }

    private boolean hasFuelAvailable(Inventory inv, CobbleGeneratorState state) {
        if (state.getBufferedFuelUses() > 0) {
            return true;
        }

        for (int slot = 1; slot < inv.getSize(); slot++) {
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
        int smokeCount = harvested ? 4 : 2;
        world.spawnParticle(smokeType, px, py + 0.1, pz, smokeCount, 0.12, 0.1, 0.12, harvested ? 0.0 : 0.01);
    }

    private boolean damagePickaxe(Inventory inv, int slot, World world, int bx, int by, int bz) {
        ItemStack pick = inv.getItem(slot);
        if (!isPickaxe(pick)) {
            return false;
        }

        ItemMeta meta = pick.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return true;
        }

        int unbreakingLevel = pick.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreakingLevel > 0) {
            double chanceToDamage = 1.0 / (unbreakingLevel + 1);
            if (ThreadLocalRandom.current().nextDouble() >= chanceToDamage) {
                return true;
            }
        }

        int newDamage = damageable.getDamage() + 1;
        int maxDurability = pick.getType().getMaxDurability();

        if (newDamage >= maxDurability) {
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

    private boolean consumeFuelUse(Inventory inv, CobbleGeneratorState state) {
        int remaining = state.getBufferedFuelUses();
        if (remaining > 0) {
            state.setBufferedFuelUses(remaining - 1);
            return true;
        }

        for (int slot = 1; slot < inv.getSize(); slot++) {
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

            int newAmount = stack.getAmount() - 1;
            if (newAmount <= 0) {
                inv.setItem(slot, null);
            } else {
                stack.setAmount(newAmount);
            }

            state.setBufferedFuelUses(uses - 1);
            return true;
        }

        return false;
    }

    private boolean isFuel(Material type) {
        return type != null && type.isFuel();
    }

    private int getFuelUses(Material type) {
        return switch (type) {
            case LAVA_BUCKET -> 100;
            case COAL_BLOCK -> 72;
            case COAL, CHARCOAL, BLAZE_ROD -> 8;
            default -> 4;
        };
    }

    private MineResult mineNearestStoneInChunk(World world,
                                               Inventory inv,
                                               int pickSlot,
                                               int barrelX,
                                               int barrelY,
                                               int barrelZ,
                                               ItemStack pickForAnimation,
                                               CobbleGeneratorState state) {

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

        for (int dy = 0; dy <= maxYRange; dy++) {
            int[] ys = {barrelY + dy, barrelY - dy};

            for (int y : ys) {
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
            return result;
        }

        if (!hasSpaceForCobble(inv)) {
            result.inventoryFull = true;
            return result;
        }

        if (!consumeFuelUse(inv, state)) {
            return result;
        }

        BlockData minedData = bestBlock.getBlockData();

        if (!addOneCobble(inv)) {
            result.inventoryFull = true;
            return result;
        }

        spawnMiningAnimation(bestBlock, minedData, pickForAnimation);

        bestBlock.setType(Material.AIR, false);

        boolean stillExists = damagePickaxe(inv, pickSlot, world, barrelX, barrelY, barrelZ);

        result.mined = true;
        result.pickBroke = !stillExists;
        return result;
    }

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

    private boolean addOneCobble(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() == Material.COBBLESTONE &&
                    stack.getAmount() < stack.getMaxStackSize()) {
                stack.setAmount(stack.getAmount() + 1);
                inv.setItem(i, stack);
                return true;
            }
        }

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) {
                inv.setItem(i, new ItemStack(Material.COBBLESTONE, 1));
                return true;
            }
        }

        return false;
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

        Location standLoc = blockLoc.add(0.5, 0.2, 0.5);
        ArmorStand stand = world.spawn(standLoc, ArmorStand.class, armorStand -> {
            armorStand.setInvisible(true);
            armorStand.setMarker(true);
            armorStand.setGravity(false);
            armorStand.setSilent(true);
            armorStand.setSmall(true);
            armorStand.setCollidable(false);
            armorStand.setArms(true);
            armorStand.setRemoveWhenFarAway(true);
            if (armorStand.getEquipment() != null) {
                armorStand.getEquipment().setItemInMainHand(displayPick);
            }
            armorStand.setRightArmPose(new EulerAngle(Math.toRadians(-100), 0, Math.toRadians(25)));
            armorStand.setRotation(ThreadLocalRandom.current().nextFloat() * 360f, 0f);
        });

        new BukkitRunnable() {
            private int ticks;

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

    private void debug(String message) {
        if (config.isCobbleDebug()) {
            plugin.getLogger().info("[CobbleGen] " + message);
        }
    }

    private String format(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private static class MineResult {
        boolean mined = false;
        boolean pickBroke = false;
        boolean inventoryFull = false;
    }

    private static class CobbleGeneratorState {
        private double progress;
        private int bufferedFuelUses;

        public double getProgress() {
            return progress;
        }

        public void setProgress(double progress) {
            this.progress = progress;
        }

        public int getBufferedFuelUses() {
            return bufferedFuelUses;
        }

        public void setBufferedFuelUses(int bufferedFuelUses) {
            this.bufferedFuelUses = Math.max(0, bufferedFuelUses);
        }
    }
}
