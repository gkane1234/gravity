package com.grumbo.record;

public class Recording {
    private final FrameRecorder recorder = new FrameRecorder();
    private OffscreenFramebuffer offscreen;

    public void start(java.nio.file.Path outputDir, int width, int height, boolean flipVertically, String filePrefix, boolean offscreenRender, boolean withDepth, int writerThreads) {
        FrameRecorder.Config cfg = new FrameRecorder.Config(outputDir, width, height, flipVertically, filePrefix, writerThreads);
        recorder.startSession(cfg);
        if (offscreenRender) {
            offscreen = new OffscreenFramebuffer(width, height, withDepth);
        }
    }

    public boolean isRunning() {
        return recorder.isRunning();
    }

    public void stop() {
        recorder.stopAndJoin();
        if (offscreen != null) {
            offscreen.delete();
            offscreen = null;
        }
    }

    public OffscreenFramebuffer getOffscreen() {
        return offscreen;
    }

    public void capture(int frameIndex) {
        if (offscreen != null) {
            recorder.captureFramebuffer(offscreen.getFbo(), offscreen.getWidth(), offscreen.getHeight(), frameIndex);
        } else {
            recorder.captureCurrentFramebuffer(frameIndex);
        }
    }
}
