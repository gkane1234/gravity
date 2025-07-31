package com.grumbo;

import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL11.*;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Bitmap font renderer that loads characters from a PNG texture atlas.
 * 
 * Expected PNG format:
 * - 16 characters per row
 * - Starts with ASCII 32 (space character)
 * - Standard layout: !"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~
 */
public class BitmapFont {
    private static String DEFAULT_FONT_PATH = "C:/Users/gkane/Documents/Stuff/gravitychunk/src/main/resources/font.png";
    private int fontTexture = 0;
    private int charWidth = 8;
    private int charHeight = 16;
    private int charsPerRow = 16;
    private int firstChar = 32; // ASCII space character
    private int numRows = 8;    // Covers ASCII 32-159 (128 characters)
    private boolean loaded = false;
    private String fontPath;
    
    /**
     * Creates a new bitmap font from the specified PNG file.
     * @param fontPath Path to the PNG font file
     */
    public BitmapFont(String fontPath) {
        this.fontPath = fontPath;
        loadFont();
    }

    public BitmapFont() {
        this.fontPath = DEFAULT_FONT_PATH;
        loadFont();
    }
    
    /**
     * Loads the font texture from the PNG file.
     */
    private void loadFont() {
        try {
            BufferedImage image = ImageIO.read(new File(fontPath));
            
            int width = image.getWidth();
            int height = image.getHeight();
            
            // Calculate character dimensions
            this.charWidth = width / charsPerRow;
            this.charHeight = height / numRows;
            
            // Convert BufferedImage to ByteBuffer for OpenGL
            ByteBuffer buffer = convertImageToBuffer(image, width, height);
            
            // Generate OpenGL texture
            fontTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, fontTexture);
            
            // Set texture parameters for crisp pixel art
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
            
            // Upload texture data to GPU
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            
            loaded = true;
            System.out.println("Bitmap font loaded: " + fontPath + " (" + charWidth + "x" + charHeight + " chars)");
            
        } catch (IOException e) {
            System.err.println("Failed to load bitmap font: " + fontPath);
            e.printStackTrace();
            loaded = false;
        }
    }
    
    /**
     * Converts a BufferedImage to a ByteBuffer in RGBA format for OpenGL.
     */
    private ByteBuffer convertImageToBuffer(BufferedImage image, int width, int height) {
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
                buffer.put((byte) (pixel & 0xFF));         // Blue
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha
            }
        }
        
        buffer.flip();
        return buffer;
    }
    
    /**
     * Draws text at the specified screen coordinates.
     * @param text The text to draw
     * @param x Screen X coordinate
     * @param y Screen Y coordinate
     */
    public void drawText(String text, float x, float y) {
        drawText(text, x, y, 1.0f);
    }
    
    /**
     * Draws text at the specified screen coordinates with scaling.
     * @param text The text to draw
     * @param x Screen X coordinate
     * @param y Screen Y coordinate
     * @param scale Scale factor (1.0 = normal size)
     */
    public void drawText(String text, float x, float y, float scale) {
        if (!loaded || fontTexture == 0) {
            return; // Font not loaded
        }
        
        // Enable texturing and blending
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        float scaledCharWidth = charWidth * scale;
        float scaledCharHeight = charHeight * scale;
        
        glBegin(GL_QUADS);
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float charX = x + i * scaledCharWidth;
            
            if (c == ' ') {
                continue; // Skip spaces (don't draw anything)
            }
            
            // Get texture coordinates for this character
            float[] texCoords = getCharTexCoords(c);
            if (texCoords != null) {
                drawCharacterQuad(charX, y, scaledCharWidth, scaledCharHeight, texCoords);
            }
        }
        
        glEnd();
        glDisable(GL_TEXTURE_2D);
    }
    
    /**
     * Gets the texture coordinates for a specific character.
     * @param c The character
     * @return Array of [u1, v1, u2, v2] texture coordinates, or null if character not found
     */
    private float[] getCharTexCoords(char c) {
        int charIndex = (int)c - firstChar;
        
        // Check if character is in our font range
        if (charIndex < 0 || charIndex >= numRows * charsPerRow) {
            return null; // Character not available
        }
        
        // Calculate grid position
        int col = charIndex % charsPerRow;
        int row = charIndex / charsPerRow;
        
        // Convert to normalized texture coordinates (0.0 to 1.0)
        float u1 = (float)col / (float)charsPerRow;           // Left
        float v1 = (float)row / (float)numRows;               // Top
        float u2 = (float)(col + 1) / (float)charsPerRow;     // Right
        float v2 = (float)(row + 1) / (float)numRows;         // Bottom
        
        return new float[]{u1, v1, u2, v2};
    }
    
    /**
     * Draws a single character quad with the given texture coordinates.
     */
    private void drawCharacterQuad(float x, float y, float width, float height, float[] texCoords) {
        float u1 = texCoords[0], v1 = texCoords[1];
        float u2 = texCoords[2], v2 = texCoords[3];
        
        // Draw textured quad (note: Y coordinates flipped for screen space)
        glTexCoord2f(u1, v1); glVertex2f(x, y);               // Top-left
        glTexCoord2f(u2, v1); glVertex2f(x + width, y);       // Top-right
        glTexCoord2f(u2, v2); glVertex2f(x + width, y + height); // Bottom-right
        glTexCoord2f(u1, v2); glVertex2f(x, y + height);      // Bottom-left
    }
    
    /**
     * Measures the width of the given text in pixels.
     * @param text The text to measure
     * @return Width in pixels at scale 1.0
     */
    public float getTextWidth(String text) {
        return getTextWidth(text, 1.0f);
    }
    
    /**
     * Measures the width of the given text in pixels with scaling.
     * @param text The text to measure
     * @param scale Scale factor
     * @return Width in pixels
     */
    public float getTextWidth(String text, float scale) {
        return text.length() * charWidth * scale;
    }
    
    /**
     * Gets the height of characters in this font.
     * @return Character height in pixels at scale 1.0
     */
    public float getCharHeight() {
        return charHeight;
    }
    
    /**
     * Gets the height of characters in this font with scaling.
     * @param scale Scale factor
     * @return Character height in pixels
     */
    public float getCharHeight(float scale) {
        return charHeight * scale;
    }
    
    /**
     * Checks if the font is loaded and ready to use.
     * @return True if font is loaded
     */
    public boolean isLoaded() {
        return loaded;
    }
    
    /**
     * Cleans up OpenGL resources. Should be called when the font is no longer needed.
     */
    public void cleanup() {
        if (fontTexture != 0) {
            glDeleteTextures(fontTexture);
            fontTexture = 0;
        }
        loaded = false;
    }
    
    /**
     * Gets information about this font.
     * @return String describing the font
     */
    @Override
    public String toString() {
        return String.format("BitmapFont{path='%s', size=%dx%d, loaded=%s}", 
                           fontPath, charWidth, charHeight, loaded);
    }
}