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
    private static final String DEFAULT_FONT_PATH = "/Users/gabrielkane/Documents/stuff/gravitychunk/gravity/src/main/resources/font.png";
    private static final float FONT_HEIGHT_RATIO = 2f;
    private static final float DEFAULT_FONT_SIZE = 16.0f;
    private static final int CHARS_PER_ROW = 16;
    private static final int NUM_ROWS = 8;
    private static final int FIRST_CHAR = 32;
    private int fontTexture;
    private float fontSize;
    private String fontPath;

    private int readCharWidth;
    private int readCharHeight;
    private boolean loaded;
    
    /**
     * Creates a new bitmap font from the specified PNG file.
     * @param fontPath Path to the PNG font file
     */
    public BitmapFont(String fontPath) {
        this.fontPath = fontPath;
        this.fontSize = DEFAULT_FONT_SIZE;
        this.fontPath = DEFAULT_FONT_PATH;
        loadFont();
    }

    public BitmapFont() {
        this(DEFAULT_FONT_PATH);
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
            this.readCharWidth = (width / CHARS_PER_ROW);
            this.readCharHeight = (height / NUM_ROWS);
            
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
            System.out.println("Bitmap font loaded: " + fontPath + " (" + readCharWidth + "x" + readCharHeight + " chars)");
            
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
        drawText(text, x, y, 1.0f, fontSize);
    }

    private float[] getDesiredCharSize(float fontSize) {
        
        return new float[] {fontSize, fontSize*FONT_HEIGHT_RATIO};
    }
    
    /**
     * Draws text at the specified screen coordinates with scaling.
     * @param text The text to draw
     * @param x Screen X coordinate
     * @param y Screen Y coordinate
     * @param scale Scale factor (1.0 = normal size)
     */
    public void drawText(String text, float x, float y,float spacing, float fontSize) {
        if (!loaded || fontTexture == 0) {
            return; // Font not loaded
        }

        float[] desiredCharSize = getDesiredCharSize(fontSize);
        float desiredCharWidth = desiredCharSize[0];
        float desiredCharHeight = desiredCharSize[1];
        
        // Enable texturing and blending
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        
        glBegin(GL_QUADS);
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float charX = x + i * (desiredCharWidth+spacing);
            
            if (c == ' ') {
                continue; // Skip spaces (don't draw anything)
            }
            
            // Get texture coordinates for this character
            float[] texCoords = getCharTexCoords(c);
            if (texCoords != null) {
                drawCharacterQuad(charX, y, desiredCharWidth, desiredCharHeight, texCoords);
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
        int charIndex = (int)c - FIRST_CHAR;
        
        // Check if character is in our font range
        if (charIndex < 0 || charIndex >= NUM_ROWS * CHARS_PER_ROW) {
            return null; // Character not available
        }
        
        // Calculate grid position
        int col = charIndex % CHARS_PER_ROW;
        int row = charIndex / CHARS_PER_ROW;
        
        // Convert to normalized texture coordinates (0.0 to 1.0)
        float u1 = (float)col / (float)CHARS_PER_ROW;           // Left
        float v1 = (float)row / (float)NUM_ROWS;               // Top
        float u2 = (float)(col + 1) / (float)CHARS_PER_ROW;     // Right
        float v2 = (float)(row + 1) / (float)NUM_ROWS;         // Bottom
        
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
        return getTextWidth(text, fontSize);
    }
    
    /**
     * Measures the width of the given text in pixels with scaling.
     * @param text The text to measure
     * @param scale Scale factor
     * @return Width in pixels
     */
    public float getTextWidth(String text, float fontSize) {
        return text.length() * getCharWidth(fontSize);
    }
    public float getCharWidth(float fontSize) {
        float[] desiredCharSize = getDesiredCharSize(fontSize);
        float desiredCharWidth = desiredCharSize[0];
        return desiredCharWidth;
    }
    public float getCharWidth() {
        return getCharWidth(fontSize);
    }
    /**
     * Gets the height of characters in this font.
     * @return Character height in pixels at scale 1.0
     */
    public float getCharHeight() {
        float[] desiredCharSize = getDesiredCharSize(fontSize);
        float desiredCharHeight = desiredCharSize[1];
        return desiredCharHeight;
    }
    
    /**
     * Gets the height of characters in this font with scaling.
     * @param scale Scale factor
     * @return Character height in pixels
     */
    public float getCharHeight(float fontSize) {
        float[] desiredCharSize = getDesiredCharSize(fontSize);
        float desiredCharHeight = desiredCharSize[1];
        return desiredCharHeight;
    }
    
    /**
     * Checks if the font is loaded and ready to use.
     * @return True if font is loaded
     */
    public boolean isLoaded() {
        return loaded;
    }
    public float getFontSize() {
        return fontSize;
    }   
    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
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
        return String.format("BitmapFont{path='%s', fontSize=%f, loaded=%s}", 
                           fontPath, fontSize, loaded);
    }
}