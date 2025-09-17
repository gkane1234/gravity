package com.grumbo.UI;

/**
 * UIElement class is the base class for all UI elements.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public abstract class UIElement {
    protected float x;
    protected float y;
    protected float width;
    protected float height;
    protected float minWidth;
    protected float minHeight;
    protected float xRenderOffset;
    protected float yRenderOffset;
    protected boolean forceUpdate;

    protected float[] defaultBackgroundColor = {0.2f, 0.2f, 0.2f};
    protected float[] defaultTextColor = {0.9f, 0.9f, 0.9f};
    /**
     * Constructor for the UIElement class.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param width The width.
     * @param height The height.
     * @param minWidth The minimum width.
     * @param minHeight The minimum height.
     * @param xRenderOffset The x render offset.
     * @param yRenderOffset The y render offset.
     */
    public UIElement(float x, float y, float width, float height, float minWidth, float minHeight, float xRenderOffset, float yRenderOffset) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.xRenderOffset = xRenderOffset;
        this.yRenderOffset = yRenderOffset;
        this.forceUpdate = false;
    }

    /**
     * Constructor for the UIElement class.
     */
    public UIElement() {
        this(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);

    }

    /**
     * Sets the position of the UIElement.
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Sets the render offset of the UIElement.
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    public void setRenderOffset(float x, float y) {
        this.xRenderOffset = x;
        this.yRenderOffset = y;
    }

    /**
     * Sets the size of the UIElement.
     * @param width The width.
     * @param height The height.
     */
    public void setSize(float width, float height) {    
        this.width = Math.max(width, minWidth);
        this.height = Math.max(height, minHeight);
    }

    /**
     * Gets the width of the UIElement.
     * @param font The font to use.
     * @param text The text to use.
     * @return The width.
     */
    public float getWidth(BitmapFont font, String text) {
        return Math.max(font.getCharWidth(font.getFontSize()) * text.length(), minWidth);
    }
    /**
     * Gets the height of the UIElement.
     * @param font The font to use.
     * @return The height.
     */
    public float getHeight(BitmapFont font) {
        return Math.max(font.getCharHeight(), minHeight);
    }

    /**
     * Updates the UIElement.
     * @param value The value to update.
     */
    public void update(Object value) {
    }

    /**
     * Calculates the size of the UIElement.
     * @param font The font to use.
     */
    public abstract void calculateSize(BitmapFont font);

    /**
     * Draws the UIElement.
     * @param font The font to use.
     */
    public abstract void draw(BitmapFont font);
    /**
     * Handles the mouse press event.
     * @param mouseX The x coordinate.
     * @param mouseY The y coordinate.
     * @return True if the mouse press was handled, false otherwise.
     */
    public abstract boolean handleMousePress(double mouseX, double mouseY);
    /**
     * Handles the mouse release event.
     */
    public abstract void handleMouseRelease();
    /**
     * Handles the mouse drag event.
     * @param mouseX The x coordinate.
     * @param mouseY The y coordinate.
     */
    public abstract void handleMouseDrag(double mouseX, double mouseY);
    /**
     * Handles the key press event.
     * @param key The key that was pressed.
     * @param action The action that was performed.
     * @param mods The modifiers that were pressed.
     * @return True if the key press was handled, false otherwise.
     */
    public abstract boolean handleKeyPress(int key, int action, int mods);
    /**
     * Handles the character press event.
     * @param codepoint The codepoint of the character that was pressed.
     * @return True if the character press was handled, false otherwise.
     */
    public abstract boolean handleCharPress(int codepoint);

    /**
     * Checks if the element is hit by the mouse.
     * @param mx The x coordinate.
     * @param my The y coordinate.
     * @return True if the element is hit, false otherwise.
     */
    public abstract boolean hitTest(double mx, double my);
}
