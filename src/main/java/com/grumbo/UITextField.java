package com.grumbo;

import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.glfw.GLFW;

public class UITextField extends UIElement {

    private static final float MIN_TEXT_FIELD_WIDTH = 250.0f;
    private static final float MIN_TEXT_FIELD_HEIGHT = 16.0f;
    private static final float PADDING = 10.0f;

    private StringBuilder text;
    private boolean focused;
    private Runnable onCommit; // Called when Enter pressed

    public UITextField(float x, float y, float width, float height, String initial) {
        super(x, y, width, height, MIN_TEXT_FIELD_WIDTH, MIN_TEXT_FIELD_HEIGHT, 0.0f, 0.0f);
        this.text = new StringBuilder(initial == null ? "" : initial);
        this.focused = false;
    }

    public UITextField(String initial) {
        this(0, 0, 0, 0, initial);
    }


    public void setText(String s) { this.text = new StringBuilder(s == null ? "" : s); }
    public void setTextFromValue(Object value) { this.text = new StringBuilder(String.valueOf(value)); }
    public String getText() { return text.toString(); }
    public boolean isFocused() { return focused; }
    public void setOnCommit(Runnable onCommit) { this.onCommit = onCommit; }

    @Override
    public boolean handleMousePress(double mouseX, double mouseY) {
        focused = hitTest(mouseX, mouseY) && !focused;
        if (focused) {
            System.out.println("focused: " + focused);
        }
        return focused;
    }

    @Override
    public void handleMouseDrag(double mouseX, double mouseY) {
        // No dragging for text fields
    }

    @Override
    public void handleMouseRelease() {
        focused = false;
        forceUpdate = false;
    }

    @Override
    public boolean handleKeyPress(int key, int action, int mods) {

        if (!focused) return false;
        System.out.println("key: " + key+" action: "+action+" mods: "+mods);

        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return false;
        if (key == GLFW.GLFW_KEY_ENTER) {
            if (onCommit != null) onCommit.run();
            focused = false;
            forceUpdate = false;
            return true;
        } else if (key == GLFW.GLFW_KEY_ESCAPE) {
            focused = false;
            forceUpdate = false;
            return true;
        } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (text.length() > 0) text.deleteCharAt(text.length() - 1);
            forceUpdate = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleCharPress(int codepoint) {
        if (!focused) return false;
        // Basic filtering; allow printable ASCII and period/minus
        if (codepoint >= 32 && codepoint <= 126) {
            text.append((char) codepoint);
            forceUpdate = true;
            return true;
        }
        return false;
    }

    @Override
    public void calculateSize(BitmapFont font) {
        float charH = (font != null ? font.getCharHeight() : 16.0f);
        float verticalPad = Math.max(4.0f, charH * 0.25f);
        float tfHGlobal = charH + verticalPad; // box taller than text so text centers automatically
        float rowHeight = Math.max(18.0f, Math.max(charH * 1.3f, tfHGlobal + 4.0f));
        float charWidth = font.getCharWidth(font.getFontSize());
        float spacing = 1.0f;
        int numChars = text.length();
        float textWidth = (numChars <= 0) ? 0 : (numChars * charWidth + (numChars-1) * spacing);
        this.height = rowHeight;
        this.width = textWidth+PADDING*2;
        this.xRenderOffset = 0.0f;
        this.yRenderOffset = -verticalPad;
    }
    @Override
    public void draw(BitmapFont font) {
        // Background
        glColor3f(0.1f, 0.1f, 0.1f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        // Border depending on focus
        if (focused) glColor3f(0.2f, 0.6f, 1.0f); else glColor3f(0.6f, 0.6f, 0.6f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        // Text
        if (font != null && font.isLoaded()) {
            float textY = y + (height - font.getCharHeight());
            font.drawText(text.toString(), x + PADDING, textY, 1.0f, font.getFontSize());
        }
    }

    protected boolean hitTest(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}


