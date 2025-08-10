package com.grumbo;

import static org.lwjgl.opengl.GL11.*;

public class UIButton {
    private float x;
    private float y;
    private float width;
    private float height;
    private String label;
    private Runnable onClick;

    public UIButton(float x, float y, float width, float height, String label, Runnable onClick) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.onClick = onClick;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean handleMouseDown(double mouseX, double mouseY) {
        if (hitTest(mouseX, mouseY)) {
            if (onClick != null) onClick.run();
            return true;
        }
        return false;
    }

    public void draw(BitmapFont font) {
        // Background
        glColor3f(0.2f, 0.2f, 0.2f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        // Border
        glColor3f(0.9f, 0.9f, 0.9f);
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


