package com.grumbo;

import static org.lwjgl.opengl.GL11.*;

public class UIButton extends UIElement {

    private static final float MIN_BUTTON_WIDTH = 28.0f;
    private static final float MIN_BUTTON_HEIGHT = 16.0f;

    private String label;
    private Runnable onClick;

    public UIButton(float x, float y, float width, float height, String label, Runnable onClick) {
        super(x, y, width, height, MIN_BUTTON_WIDTH, MIN_BUTTON_HEIGHT);
        this.label = label;
        this.onClick = onClick;
    }



    public void setLabel(String label) {
        this.label = label;
    }
    public String getLabel() {
        return label;
    }

    @Override
    public boolean handleMousePress(double mouseX, double mouseY) {
        if (hitTest(mouseX, mouseY)) {
            if (onClick != null) onClick.run();
            System.out.println("Button clicked: " + label);
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

        // Label centered
        if (font != null && font.isLoaded() && label != null) {
            float textWidth = font.getTextWidth(label, font.getFontSize());
            float textX = x + (width - textWidth) / 2.0f;
            float textY = y + (height - font.getCharHeight()) / 2.0f;
            font.drawText(label, textX, textY, 1.0f, font.getFontSize());
        }
    }

    private boolean hitTest(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}


