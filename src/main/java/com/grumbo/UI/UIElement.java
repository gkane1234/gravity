package com.grumbo.UI;


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

    protected static final float[] defaultBackgroundColor = {0.2f, 0.2f, 0.2f};
    protected static final float[] defaultTextColor = {0.9f, 0.9f, 0.9f};
    
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

    public UIElement() {
        this(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);

    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setRenderOffset(float x, float y) {
        this.xRenderOffset = x;
        this.yRenderOffset = y;
    }

    public void setSize(float width, float height) {    
        this.width = Math.max(width, minWidth);
        this.height = Math.max(height, minHeight);
    }

    public float getWidth(BitmapFont font, String text) {
        return Math.max(font.getCharWidth(font.getFontSize()) * text.length(), minWidth);
    }
    public float getHeight(BitmapFont font) {
        return Math.max(font.getCharHeight(), minHeight);
    }

    public void update(Object value) {
    }

    public abstract void calculateSize(BitmapFont font);

    public abstract void draw(BitmapFont font);
    public abstract boolean handleMousePress(double mouseX, double mouseY);
    public abstract void handleMouseRelease();
    public abstract void handleMouseDrag(double mouseX, double mouseY);
    public abstract boolean handleKeyPress(int key, int action, int mods);
    public abstract boolean handleCharPress(int codepoint);
    // Default Sizes, should be overridden by subclasses
}
