package com.grumbo.record;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;

/**
 * Simple FBO with color RGBA8 and optional depth renderbuffer.
 */
public class OffscreenFramebuffer {
    private int fbo = 0;
    private int colorTex = 0;
    private int depthRbo = 0;
    private int width;
    private int height;

    public OffscreenFramebuffer(int width, int height, boolean withDepth) {
        this.width = width;
        this.height = height;
        create(width, height, withDepth);
    }

    private void create(int w, int h, boolean withDepth) {
        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        colorTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);

        if (withDepth) {
            depthRbo = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, depthRbo);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, w, h);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthRbo);
        }

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Offscreen FBO incomplete");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, width, height);
    }

    public void unbind(int prevWidth, int prevHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, prevWidth, prevHeight);
    }

    public int getFbo() { return fbo; }
    public int getColorTex() { return colorTex; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void delete() {
        if (depthRbo != 0) glDeleteRenderbuffers(depthRbo);
        if (colorTex != 0) glDeleteTextures(colorTex);
        if (fbo != 0) glDeleteFramebuffers(fbo);
        depthRbo = 0; colorTex = 0; fbo = 0;
    }
}


