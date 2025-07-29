package com.grumbo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ChunkList {
    private ArrayList<Chunk> chunks;
    private HashMap<CoordKey, Integer> chunkMap;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private static class CoordKey {
        final long x, y;

        CoordKey(long[] coord) {
            long[] chunkCenter = Chunk.getChunkCenter(coord);
            this.x = chunkCenter[0];
            this.y = chunkCenter[1];
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CoordKey)) return false;
            CoordKey other = (CoordKey) o;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(x) * 31 + Long.hashCode(y);
        }
    }

    public ChunkList() {
        chunks = new ArrayList<Chunk>();
        chunkMap = new HashMap<CoordKey, Integer>();
    }

    public void addChunk(long[] center) {
        lock.writeLock().lock();
        try {
            chunks.add(new Chunk(center));
            chunkMap.put(new CoordKey(center), chunks.size() - 1);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addChunkWithPlanet(Planet p) {
        lock.writeLock().lock();
        try {
            chunks.add(new Chunk(p));
            chunkMap.put(new CoordKey(p.chunkCenter), chunks.size() - 1);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void removeChunk(long[] center) {
        lock.writeLock().lock();
        try {
            CoordKey key = new CoordKey(center);
            Integer index = chunkMap.get(key);
            if (index == null) return;
            
            chunks.remove(index.intValue());
            chunkMap.remove(key);
            
            // Update all indices greater than the removed index
            for (Map.Entry<CoordKey, Integer> entry : chunkMap.entrySet()) {
                if (entry.getValue() > index) {
                    entry.setValue(entry.getValue() - 1);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Chunk getChunk(long[] center) {
        lock.readLock().lock();
        try {
            Integer index = chunkMap.get(new CoordKey(center));
            return index != null ? chunks.get(index) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Chunk getChunk(int index) {
        lock.readLock().lock();
        try {
            return index >= 0 && index < chunks.size() ? chunks.get(index) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public ArrayList<Chunk> getChunks() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(chunks);  // Return a copy to prevent concurrent modification
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getChunkIndex(long[] center) {
        lock.readLock().lock();
        try {
            Integer index = chunkMap.get(new CoordKey(center));
            return index != null ? index : -1;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getNumChunks() {
        lock.readLock().lock();
        try {
            return chunks.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
