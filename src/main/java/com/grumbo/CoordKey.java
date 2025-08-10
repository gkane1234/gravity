package com.grumbo;

import java.util.HashMap;
import java.util.Map;

public class CoordKey {
    final long x, y, z;

    public static Map<CoordKey, CoordKey> coordKeys = new HashMap<>();

    public static CoordKey getCoordKey(double[] spaceCoordinates) {
        long chunkX = (long) Math.floor(spaceCoordinates[0] / Settings.getInstance().getChunkSize() + 0.5);
        long chunkY = (long) Math.floor(spaceCoordinates[1] / Settings.getInstance().getChunkSize() + 0.5);
        long chunkZ = (long) Math.floor(spaceCoordinates[2] / Settings.getInstance().getChunkSize() + 0.5);

        CoordKey coordKey = new CoordKey(chunkX, chunkY, chunkZ);

        if (coordKeys.containsKey(coordKey)) {
            return coordKeys.get(coordKey);
        } else {
            coordKeys.put(coordKey, coordKey);
            return coordKey;
        }
    }

    // Constructor for space coordinates (world coordinates)
    private CoordKey(double[] spaceCoordinates) {

        this(new long[] {(long) spaceCoordinates[0], (long) spaceCoordinates[1], (long) spaceCoordinates[2]});

	}

	private CoordKey(long[] spaceCoordinates) {
        long chunkX = (long) Math.floor(spaceCoordinates[0] / Settings.getInstance().getChunkSize() + 0.5);
        long chunkY = (long) Math.floor(spaceCoordinates[1] / Settings.getInstance().getChunkSize() + 0.5);
		long chunkZ = (long) Math.floor(spaceCoordinates[2] / Settings.getInstance().getChunkSize() + 0.5);
        
        this.x = chunkX;
        this.y = chunkY;
        this.z = chunkZ;
	}
	
    // Constructor for already-calculated chunk center coordinates
    private CoordKey(long chunkX, long chunkY, long chunkZ) {
        this.x = chunkX;
        this.y = chunkY;
        this.z = chunkZ;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoordKey)) return false;
        CoordKey other = (CoordKey) o;
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(x) * 62 + Long.hashCode(y) * 31 + Long.hashCode(z);
    }
}