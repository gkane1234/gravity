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
    private static final float CHAR_SPACING = 1.0f;
    /** Character columns used when a numeric field has no min width. */
    private static final int DEFAULT_FIXED_CHARS = 12;

    private int minWidth;

    private StringBuilder text;
    private boolean focused;
    private Runnable onCommit; // Called when Enter pressed
    private Runnable updateFunction;
    private int numericalRounding;
    /** True after setNumericalRounding, which marks the field fixed-width. */
    private boolean numericalRoundingSet;
    /** Explicit fixed width, set by numeric property editors. */
    private boolean fixedWidth;
    /** Horizontal scroll in pixels. Positive values show later characters. */
    private float scrollOffset;
    private float glyphWidth = 16.0f;
    private boolean scrollDragging;
    private double lastDragX;
    private final double[] projection = new double[16];
    private final int[] viewport = new int[4];
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
     * Locks the field to a character width so the box does not grow with the text.
     * @param fixedWidth True to lock the width.
     */
    public void setFixedWidth(boolean fixedWidth) { this.fixedWidth = fixedWidth; }


    /**
     * Sets the text.
     * @param s The text.
     */
    public void setText(String s) {
        this.text = new StringBuilder(s == null ? "" : s);
        keepScroll();
    }
    /**
     * Sets the text from a value.
     * @param value The value.
     */
    public void setTextFromValue(Object value) { 
        String unroundedString = String.valueOf(value);
        int unroundedLength = unroundedString.length();
        
        if (value instanceof Number) {
            this.text = new StringBuilder(String.format("%." + numericalRounding + "e", ((Number) value).doubleValue()));
            if (this.text.length() > unroundedLength) {
                this.text = new StringBuilder(unroundedString);
            }
        } else {
            this.text = new StringBuilder(unroundedString);
        }
        keepScroll();
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
     * Gets if a fixed-width field is being dragged to scroll.
     * @return True if a scroll drag is active.
     */
    public boolean isScrollDragging() { return scrollDragging; }
    /**
     * Sets the on commit function.
     * @param onCommit The on commit function.
     */
    public void setOnCommit(Runnable onCommit) { this.onCommit = onCommit; }
    /**
     * Sets the numerical rounding. Calling this marks the field fixed-width.
     * @param numericalRounding The numerical rounding.
     */
    public void setNumericalRounding(int numericalRounding) {
        this.numericalRounding = numericalRounding;
        this.numericalRoundingSet = true;
    }

    @Override
    public boolean handleMousePress(double mouseX, double mouseY) {
        boolean hit = hitTest(mouseX, mouseY);
        if (hit && isFixedWidth()) {
            scrollDragging = true;
            lastDragX = mouseX;
        } else {
            scrollDragging = false;
        }
        focused = hit && !focused;
        return focused;
    }

    @Override
    public void handleMouseDrag(double mouseX, double mouseY) {
        if (!scrollDragging) {
            return;
        }
        // Drag the text with the pointer.
        scrollOffset -= (float) (mouseX - lastDragX);
        lastDragX = mouseX;
        clampScroll();
    }

    @Override
    public void handleMouseRelease() {
        scrollDragging = false;
    }

    /**
     * Scrolls a fixed-width field horizontally. Wheel up moves toward the start of the text.
     * @param xoffset Horizontal wheel offset.
     * @param yoffset Vertical wheel offset.
     * @return True if this field consumed the scroll.
     */
    public boolean handleScroll(double xoffset, double yoffset) {
        if (!isFixedWidth()) {
            return false;
        }
        float step = glyphWidth + CHAR_SPACING;
        scrollOffset -= (float) yoffset * step;
        scrollOffset += (float) xoffset * step;
        clampScroll();
        return true;
    }

    @Override
    public boolean handleKeyPress(int key, int action, int mods) {

        if (!focused) return false;

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
            if (isFixedWidth()) {
                revealEnd();
            }
            return true;
        } else if (isFixedWidth() && key == GLFW.GLFW_KEY_LEFT) {
            scrollOffset -= glyphWidth + CHAR_SPACING;
            clampScroll();
            return true;
        } else if (isFixedWidth() && key == GLFW.GLFW_KEY_RIGHT) {
            scrollOffset += glyphWidth + CHAR_SPACING;
            clampScroll();
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
            if (isFixedWidth()) {
                revealEnd();
            }
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
        this.glyphWidth = charWidth;
        this.height = rowHeight;
        if (isFixedWidth()) {
            int chars = minWidth > 0 ? minWidth : DEFAULT_FIXED_CHARS;
            float span = chars * charWidth + (chars - 1) * CHAR_SPACING;
            this.width = span + PADDING * 2;
            clampScroll();
        } else {
            int numChars = text.length();
            float textWidth = (numChars <= 0) ? 0 : (numChars * charWidth + (numChars-1) * CHAR_SPACING);
            this.width = Math.max(textWidth+PADDING*2, minWidth*charWidth);
        }
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
            float textX = x + PADDING;
            if (isFixedWidth()) {
                clampScroll();
                textX -= scrollOffset;
                clipTextToField();
            }
            font.drawText(text.toString(), textX, textY, CHAR_SPACING, font.getFontSize());
            if (isFixedWidth()) {
                glDisable(GL_SCISSOR_TEST);
            }
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

    private boolean isFixedWidth() {
        return fixedWidth || numericalRoundingSet;
    }

    private float currentTextWidth() {
        int numChars = text.length();
        if (numChars <= 0) {
            return 0f;
        }
        return numChars * glyphWidth + (numChars - 1) * CHAR_SPACING;
    }

    private float visibleTextWidth() {
        return Math.max(0f, width - PADDING * 2f);
    }

    /** Keeps a scrolled view when a live value rewrites the text. */
    private void keepScroll() {
        if (glyphWidth > 0f) {
            clampScroll();
        }
    }

    private void clampScroll() {
        float maxScroll = Math.max(0f, currentTextWidth() - visibleTextWidth());
        if (scrollOffset < 0f) {
            scrollOffset = 0f;
        }
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    private void revealEnd() {
        scrollOffset = currentTextWidth() - visibleTextWidth();
        clampScroll();
    }

    /**
     * Clips glyphs to the field. UI coordinates are mapped through the current
     * projection so this stays correct when the viewport is 1:1 with the layout
     * and when a caller stretches that layout across the framebuffer.
     */
    private void clipTextToField() {
        float inset = 1f;
        float cx = x + inset;
        float cy = y + inset;
        float cw = width - inset * 2f;
        float ch = height - inset * 2f;
        if (cw <= 0f || ch <= 0f) {
            cx = x;
            cy = y;
            cw = width;
            ch = height;
        }
        glGetDoublev(GL_PROJECTION_MATRIX, projection);
        glGetIntegerv(GL_VIEWPORT, viewport);
        double x0 = windowX(cx);
        double y0 = windowY(cy + ch);
        double x1 = windowX(cx + cw);
        double y1 = windowY(cy);
        int sx = (int) Math.floor(Math.min(x0, x1));
        int sy = (int) Math.floor(Math.min(y0, y1));
        int sw = (int) Math.ceil(Math.max(x0, x1)) - sx;
        int sh = (int) Math.ceil(Math.max(y0, y1)) - sy;
        if (sw < 0) sw = 0;
        if (sh < 0) sh = 0;
        glEnable(GL_SCISSOR_TEST);
        glScissor(sx, sy, sw, sh);
    }

    private double windowX(float px) {
        double ndcX = projection[0] * px + projection[12];
        return viewport[0] + (ndcX + 1.0) * 0.5 * viewport[2];
    }

    private double windowY(float py) {
        double ndcY = projection[5] * py + projection[13];
        return viewport[1] + (ndcY + 1.0) * 0.5 * viewport[3];
    }
}
