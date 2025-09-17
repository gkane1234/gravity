package com.grumbo.UI;



public class UIText extends UIElement {
    public String text;


    public UIText(String text) {
        super();
        this.text = text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public void calculateSize(BitmapFont font) {
        this.width = font.getCharWidth(font.getFontSize()) * text.length();
        this.height = font.getCharHeight();
    }

    @Override
    public void draw(BitmapFont font) {
        font.drawText(text, x, y, 1.0f, font.getFontSize());
    }

    @Override   
    public boolean handleMousePress(double x, double y) {
        return false;
    }

    @Override
    public void handleMouseRelease() {
        // no-op
    }

    @Override
    public void handleMouseDrag(double x, double y) {
        // no-op
    }

    @Override
    public boolean handleCharPress(int codepoint) {
        return false;
    }

    @Override
    public boolean handleKeyPress(int key, int action, int mods) {
        return false;
    }

    @Override
    public boolean hitTest(double mx, double my) {
        return false;
    }
}
