package com.grumbo.UI;

import static org.lwjgl.opengl.GL11.*;

/**
 * UIButton class is a button UI element.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class UIButton extends UIElement {

    private static final float MIN_BUTTON_WIDTH = 28.0f;
    private static final float MIN_BUTTON_HEIGHT = 16.0f;
    private static final float PADDING = 10.0f;
    private String text;
    private Runnable onClick;

    /**
     * Constructor for the UIButton class.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param width The width.
     * @param height The height.
     * @param text The text of the button.
     * @param onClick The action to perform when the button is clicked.
     */
    public UIButton(float x, float y, float width, float height, String text, Runnable onClick) {
        super(x, y, width, height, MIN_BUTTON_WIDTH, MIN_BUTTON_HEIGHT, 0.0f, 0.0f);
        this.text = text;
        this.onClick = onClick;
    }

    /**
     * Constructor for the UIButton class.
     * @param text The text of the button.
     * @param onClick The action to perform when the button is clicked.
     */
    public UIButton (String text, Runnable onClick) {
        this(0, 0, 0, 0, text, onClick);
    }

    /**
     * Constructor for the UIButton class.
     * @param text The text of the button.
     */
    public UIButton (String text) {
        this(0, 0, 0, 0, text, null);
    }

    /**
     * Constructor for the UIButton class.
     */
    public UIButton () {
        this(0, 0, 0, 0, "", null);
    }



    /**
     * Sets the text of the button.
     * @param text The text of the button.
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Gets the text of the button.
     * @return The text of the button.
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the action to perform when the button is clicked.
     * @param onClick The action to perform when the button is clicked.
     */
    public void setOnClick(Runnable onClick) {
        this.onClick = onClick;
    }

    @Override
    public boolean handleMousePress(double mouseX, double mouseY) {
        if (hitTest(mouseX, mouseY)) {
            if (onClick != null) onClick.run();
            return true;
        }
        return false;
    }


    @Override
    public void handleMouseRelease() {
        // No action needed for buttons
    }

    @Override
    public void handleMouseDrag(double mouseX, double mouseY) {
        // No action needed for buttons
    }


    @Override
    public boolean handleKeyPress(int key, int action, int mods) {
        return false;
    }
    @Override
    public boolean handleCharPress(int codepoint) {
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
        glColor3f(defaultBackgroundColor[0], defaultBackgroundColor[1], defaultBackgroundColor[2]);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        // Border
        glColor3f(defaultTextColor[0], defaultTextColor[1], defaultTextColor[2]);
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
    public boolean hitTest(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}


