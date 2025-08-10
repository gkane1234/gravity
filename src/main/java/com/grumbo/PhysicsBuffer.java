package com.grumbo;
 
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * PhysicsBuffer - Thread-safe physics state management
 * ===================================================
 * Manages physics data with proper locking to prevent rendering interference
 * with physics calculations. Uses a single buffer with read-write locks.
 */
public class PhysicsBuffer {
    
    // Single buffer for physics state with thread-safe access
    private ChunkList buffer;
    private final ReentrantReadWriteLock bufferLock;
    
    // Performance tracking
    private long lastUpdateTime = 0;
    private long updateCount = 0;
    
    public PhysicsBuffer() {
        buffer = new ChunkList();
        bufferLock = new ReentrantReadWriteLock();
    }
    
    /**
     * Get the buffer for physics calculations (write access)
     */
    public ChunkList getBuffer() {
        return buffer;
    }
    
    /**
     * Get read lock for safe reading during rendering
     */
    public ReentrantReadWriteLock.ReadLock getReadLock() {
        return bufferLock.readLock();
    }
    
    /**
     * Get write lock for physics calculations
     */
    public ReentrantReadWriteLock.WriteLock getWriteLock() {
        return bufferLock.writeLock();
    }
    
    /**
     * Update performance tracking after physics calculations
     */
    public void updateComplete() {
        lastUpdateTime = System.currentTimeMillis();
        updateCount++;
    }
    
    /**
     * Get performance statistics
     */
    public String getPerformanceStats() {
        return String.format("Updates: %d, Last update: %dms ago", 
                           updateCount, System.currentTimeMillis() - lastUpdateTime);
    }
    
    /**
     * Get the current buffer state for debugging
     */
    public String getBufferState() {
        return String.format("Chunks: %d", buffer.getNumChunks());
    }
} 