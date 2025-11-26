package com.daytonjwatson.chunkfall.util;

import org.bukkit.World;

import java.util.UUID;

public final class ChunkKeyUtil {

    private ChunkKeyUtil() {
    }

    public static long chunkKey(World world, int x, int z) {
        UUID uuid = world.getUID();
        long worldBits = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        long chunkBits = (((long) x) << 32) ^ (z & 0xffffffffL);
        return worldBits ^ chunkBits;
    }
}
