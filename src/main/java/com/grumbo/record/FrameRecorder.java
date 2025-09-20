package com.grumbo.record;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryUtil;

/**
 * Captures OpenGL frames and writes PNG images asynchronously.
 * Usage:
 * - call startSession(outputDir, width, height)
 * - call captureCurrentFramebuffer(frameIndex) from the GL thread after rendering each frame
 * - call stopAndJoin() to flush and shutdown
 */
public class FrameRecorder {
    public static class Config {
        public final Path outputDirectory;
        public final int width;
        public final int height;
        public final boolean flipVertically;
        public final String filePrefix;
        public final int writerThreads;

        public Config(Path outputDirectory, int width, int height, boolean flipVertically, String filePrefix, int writerThreads) {
            this.outputDirectory = Objects.requireNonNull(outputDirectory);
            this.width = width;
            this.height = height;
            this.flipVertically = flipVertically;
            this.filePrefix = filePrefix == null ? "frame" : filePrefix;
            this.writerThreads = Math.max(1, writerThreads);
        }
    }

    private static class FrameJob {
        final ByteBuffer rgba;
        final int width;
        final int height;
        final Path filePath;

        FrameJob(ByteBuffer rgba, int width, int height, Path filePath) {
            this.rgba = rgba;
            this.width = width;
            this.height = height;
            this.filePath = filePath;
        }
    }

    private final BlockingQueue<FrameJob> jobs;
    private final ExecutorService writers;
    private volatile boolean running = false;
    private Config config;

    public FrameRecorder() {
        // Small bounded queue to apply backpressure if writing is slower than capture
        this.jobs = new ArrayBlockingQueue<>(8);
        this.writers = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }

    public synchronized void startSession(Config config) {
        if (running) return;
        this.config = config;
        try {
            Files.createDirectories(config.outputDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory: " + e.getMessage(), e);
        }
        STBImageWrite.stbi_flip_vertically_on_write(config.flipVertically);
        running = true;
        // Submit background writer loop(s)
        for (int i = 0; i < config.writerThreads; i++) {
            writers.submit(this::writerLoop);
        }
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void stopAndJoin() {
        if (!running) return;
        running = false;
        // Wait for queue to drain
        while (!jobs.isEmpty()) {
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
        writers.shutdown();
        try {
            writers.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
    }

    private void writerLoop() {
        while (running || !jobs.isEmpty()) {
            FrameJob job = jobs.poll();
            if (job == null) {
                try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                continue;
            }
            try {
                // Ensure parent exists
                Files.createDirectories(job.filePath.getParent());
                boolean ok = STBImageWrite.stbi_write_png(job.filePath.toString(), job.width, job.height, 4, job.rgba, job.width * 4);
                if (!ok) {
                    System.err.println("Failed to write frame: " + job.filePath);
                }
            } catch (Exception e) {
                System.err.println("Error writing frame: " + job.filePath + " - " + e.getMessage());
            } finally {
                MemoryUtil.memFree(job.rgba);
            }
        }
    }

    /**
     * Capture the default framebuffer. Call from GL thread after rendering, before swap if you want backbuffer.
     */
    public void captureCurrentFramebuffer(int frameIndex) {
        if (!running) return;
        int width = config.width;
        int height = config.height;

        ByteBuffer rgba = MemoryUtil.memAlloc(width * height * 4);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadBuffer(GL_BACK);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, rgba);

        Path path = config.outputDirectory.resolve(filenameFor(frameIndex));
        enqueue(new FrameJob(rgba, width, height, path));
    }

    /**
     * Capture from a specific FBO color attachment 0.
     */
    public void captureFramebuffer(int fbo, int width, int height, int frameIndex) {
        if (!running) return;
        int prevFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        ByteBuffer rgba = MemoryUtil.memAlloc(width * height * 4);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, prevFbo);

        Path path = config.outputDirectory.resolve(filenameFor(frameIndex));
        enqueue(new FrameJob(rgba, width, height, path));
    }

    private void enqueue(FrameJob job) {
        while (running) {
            if (jobs.offer(job)) return;
            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        }
        // if we stopped while waiting, free buffer
        MemoryUtil.memFree(job.rgba);
    }

    private String filenameFor(int frameIndex) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return config.filePrefix + "_" + timestamp + "_" + String.format("%06d", frameIndex) + ".png";
    }
}


