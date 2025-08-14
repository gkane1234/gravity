package com.grumbo;

import java.util.ArrayList;

import static org.lwjgl.opengl.GL11.*;

public class UIRow extends UIElement {

    private ArrayList<UIElement> elements;

    private float padding = 10.0f;

    public UIRow(ArrayList<UIElement> elements) {
        super();
        this.elements = elements;

    }

    public ArrayList<UIElement> getElements() {
        return elements;
    }

    public boolean getForceUpdate() {
        boolean forceUpdate = false;
        for (UIElement element : elements) {
            forceUpdate = forceUpdate || element.forceUpdate;
        }
        return forceUpdate;
    }


    @Override
    public void calculateSize(BitmapFont font) {
        float height = 0.0f;
        float width = 0.0f;
        float yOffset = 0.0f;
        float xOffset = 0.0f;
        for (UIElement element : elements) {
            element.calculateSize(font);
            height = Math.max(height, element.height+element.yRenderOffset);
            width += element.width + element.xRenderOffset + padding;
            yOffset = Math.min(yOffset, element.yRenderOffset);
            xOffset = Math.min(xOffset, element.xRenderOffset);
        }
        this.height = height-yOffset;
        this.width = width-xOffset;
        this.xRenderOffset = xOffset;
        this.yRenderOffset = yOffset;
    }

    @Override
    public void draw(BitmapFont font) {
        float xComponent = x;
        float yComponent = y;
        

        for (UIElement element : elements) {
            glColor3f(element.defaultTextColor[0], element.defaultTextColor[1], element.defaultTextColor[2]);
            element.setPosition(xComponent-xRenderOffset+element.xRenderOffset, yComponent-yRenderOffset+element.yRenderOffset);
            element.draw(font);
            xComponent += element.width + padding;
        }
    }

    @Override 
    public boolean handleMousePress(double mouseX, double mouseY) {
        boolean mousePressed = false;
        for (UIElement element : elements) {
            if (element.handleMousePress(mouseX, mouseY)) {
                mousePressed = true;
            }
        }
        return mousePressed;
    }

    @Override
    public void handleMouseRelease() {
        for (UIElement element : elements) {
            element.handleMouseRelease();
        }
    }

    @Override
    public boolean handleKeyPress(int key, int action, int mods) {
        boolean keyPressed = false;
        for (UIElement element : elements) {
            if (element.handleKeyPress(key, action, mods)) {
                keyPressed = true;
            }
        }
        return keyPressed;
    }

    @Override
    public boolean handleCharPress(int codepoint) {
        boolean charPressed = false;
        for (UIElement element : elements) {
            if (element.handleCharPress(codepoint)) {
                charPressed = true;
            }
        }
        return charPressed;
    }

    @Override
    public void handleMouseDrag(double mouseX, double mouseY) {
        for (UIElement element : elements) {
            element.handleMouseDrag(mouseX, mouseY);
        }
    }
}
