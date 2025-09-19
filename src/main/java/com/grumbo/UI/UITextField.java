package com.grumbo.ui;

import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.glfw.GLFW;
/**
 * UITextField class is a text field UI element.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class UITextField extends UIElement {

    private static final float MIN_TEXT_FIELD_WIDTH = 250.0f;
    private static final float MIN_TEXT_FIELD_HEIGHT = 16.0f;
    private static final float PADDING = 10.0f;

    private int minWidth;

    private StringBuilder text;
    private boolean focused;
    private Runnable onCommit; // Called when Enter pressed
    private Runnable updateFunction;
    private int numericalRounding;
    /**
     * Constructor for the UITextField class.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param width The width.
     * @param height The height.
     * @param initial The initial text.
     * @param updateFunction The function to update the text.
     */
    public UITextField(float x, float y, float width, float height, String initial, Runnable updateFunction) {
        super(x, y, width, height, MIN_TEXT_FIELD_WIDTH, MIN_TEXT_FIELD_HEIGHT, 0.0f, 0.0f);
        this.text = new StringBuilder(initial == null ? "" : initial);
        this.focused = false;
        this.minWidth = 0;
    }

    /**
     * Constructor for the UITextField class.
     * @param initial The initial text.
     */
    public UITextField(String initial) {
        this(0, 0, 0, 0, initial, null);
    }

    /**
     * Sets the update function.
     * @param updateFunction The update function.
     */
    public void setUpdateFunction(Runnable updateFunction) {
        this.updateFunction = updateFunction;
    }

    /**
     * Sets the minimum width.
     * @param minWidth The minimum width.
     */
    public void setMinWidth(int minWidth) { this.minWidth = minWidth; }


    /**
     * Sets the text.
     * @param s The text.
     */
    public void setText(String s) { this.text = new StringBuilder(s == null ? "" : s); }
    /**
     * Sets the text from a value.
     * @param value The value.
     */
    public void setTextFromValue(Object value) { 
        String unroundedString = String.valueOf(value);
        int unroundedLength = unroundedString.length();
        
        if (value instanceof Number) {
            this.text = new StringBuilder(String.format("%." + numericalRounding + "f", ((Number) value).doubleValue()));
            if (this.text.length() > unroundedLength) {
                this.text = new StringBuilder(unroundedString);
            }
        } else {
            this.text = new StringBuilder(unroundedString);
        }
    }
    /**
     * Gets the text.
     * @return The text.
     */
    public String getText() { return text.toString(); }
    /**
     * Gets if the text field is focused.
     * @return True if the text field is focused, false otherwise.
     */
    public boolean isFocused() { return focused; }
    /**
     * Sets the on commit function.
     * @param onCommit The on commit function.
     */
    public void setOnCommit(Runnable onCommit) { this.onCommit = onCommit; }
    /**
     * Sets the numerical rounding.
     * @param numericalRounding The numerical rounding.
     */
    public void setNumericalRounding(int numericalRounding) { this.numericalRounding = numericalRounding; }

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
        this.width = Math.max(textWidth+PADDING*2, minWidth*charWidth);
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

    @Override
    public void update(Object value) {
        if (updateFunction != null) updateFunction.run();
        else setTextFromValue(value);
    }

    @Override
    public boolean hitTest(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}


