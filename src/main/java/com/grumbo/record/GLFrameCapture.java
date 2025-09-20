package com.grumbo.record;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

/**
 * Utility for reading pixels from an FBO or the default framebuffer.
 */
public class GLFrameCapture {
    public static ByteBuffer readBackbufferRGBA(int x, int y, int width, int height) {
        ByteBuffer rgba = BufferUtils.createByteBuffer(width * height * 4);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadBuffer(GL_BACK);
        glReadPixels(x, y, width, height, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        return rgba;
    }

    public static ByteBuffer readFboRGBA(int fbo, int x, int y, int width, int height) {
        int prevFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        ByteBuffer rgba = BufferUtils.createByteBuffer(width * height * 4);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glReadPixels(x, y, width, height, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, prevFbo);
        return rgba;
    }
}


