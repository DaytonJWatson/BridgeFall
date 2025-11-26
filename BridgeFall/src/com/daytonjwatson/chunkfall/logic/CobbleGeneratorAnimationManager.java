package com.daytonjwatson.chunkfall.logic;

import com.daytonjwatson.chunkfall.config.ChunkFallConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the floating pickaxe animation shown above active cobble generators.
 */
public class CobbleGeneratorAnimationManager {

    private static final double BOB_AMPLITUDE = 0.18d;
    private static final double BOB_SPEED = 0.12d;
    private static final float ROTATION_SPEED = 4.5f;

    private final Plugin plugin;
    private final ChunkFallConfig config;

    private final Map<Location, AnimationState> animations = new HashMap<>();
    private BukkitTask task;

    public CobbleGeneratorAnimationManager(Plugin plugin, ChunkFallConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (!config.isCobbleAnimationEnabled()) {
            return;
        }
        int period = Math.max(1, config.getCobbleAnimationUpdateTicks());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clear();
    }

    public void show(Location barrelLoc, ItemStack pickaxe) {
        if (!config.isCobbleAnimationEnabled()) {
            return;
        }

        Location key = barrelLoc.toBlockLocation();
        AnimationState state = animations.get(key);
        ArmorStand stand = state != null ? state.getStand() : null;

        if (stand != null && !stand.isDead()) {
            updateEquipment(stand, pickaxe);
            state.setBaseLocation(key);
            return;
        }

        World world = key.getWorld();
        if (world == null || !world.isChunkLoaded(key.getBlockX() >> 4, key.getBlockZ() >> 4)) {
            return;
        }

        Location spawnLoc = key.clone().add(0.5, 1.25, 0.5);
        ArmorStand spawned = world.spawn(spawnLoc, ArmorStand.class, armorStand -> {
            armorStand.setInvisible(true);
            armorStand.setGravity(false);
            armorStand.setMarker(true);
            armorStand.setSilent(true);
            armorStand.setSmall(true);
            armorStand.setPersistent(false);
            armorStand.setCanPickupItems(false);
            armorStand.setInvulnerable(true);
            armorStand.setBasePlate(false);
            armorStand.setCollidable(false);
            updateEquipment(armorStand, pickaxe);
        });

        animations.put(key, new AnimationState(spawned, key));
    }

    public void hide(Location barrelLoc) {
        Location key = barrelLoc.toBlockLocation();
        AnimationState state = animations.remove(key);
        if (state != null) {
            ArmorStand stand = state.getStand();
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
    }

    public void clear() {
        Iterator<Map.Entry<Location, AnimationState>> it = animations.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, AnimationState> entry = it.next();
            ArmorStand stand = entry.getValue().getStand();
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
            it.remove();
        }
    }

    private void tick() {
        if (animations.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<Location, AnimationState>> it = animations.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, AnimationState> entry = it.next();
            AnimationState state = entry.getValue();
            ArmorStand stand = state.getStand();
            Location base = state.getBaseLocation();

            if (stand == null || stand.isDead() || base.getWorld() == null) {
                it.remove();
                continue;
            }

            World world = base.getWorld();
            if (!world.isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) {
                stand.remove();
                it.remove();
                continue;
            }

            double bob = Math.sin(state.incrementAndGetPhase(BOB_SPEED)) * BOB_AMPLITUDE;
            Location target = base.clone().add(0.5, 1.1 + bob, 0.5);
            stand.teleport(target);

            float newYaw = (stand.getLocation().getYaw() + ROTATION_SPEED) % 360f;
            stand.setRotation(newYaw, 0f);
        }
    }

    private void updateEquipment(ArmorStand stand, ItemStack pickaxe) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment == null) {
            return;
        }

        ItemStack display = pickaxe == null || pickaxe.getType() == Material.AIR
                ? new ItemStack(Material.STONE_PICKAXE)
                : pickaxe.clone();
        display.setAmount(1);

        equipment.setItemInMainHand(display);
    }

    private static class AnimationState {
        private final UUID standId;
        private Location baseLocation;
        private double phaseOffset;

        AnimationState(ArmorStand stand, Location baseLocation) {
            this.standId = stand.getUniqueId();
            this.baseLocation = baseLocation.toBlockLocation();
            this.phaseOffset = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        }

        ArmorStand getStand() {
            Entity entity = Bukkit.getEntity(standId);
            if (entity instanceof ArmorStand armorStand) {
                return armorStand;
            }
            return null;
        }

        Location getBaseLocation() {
            return baseLocation;
        }

        void setBaseLocation(Location baseLocation) {
            this.baseLocation = baseLocation.toBlockLocation();
        }

        double incrementAndGetPhase(double delta) {
            this.phaseOffset += delta;
            return phaseOffset;
        }
    }
}
