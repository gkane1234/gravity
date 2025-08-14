package com.grumbo;

import static org.lwjgl.opengl.GL11.*;
import java.util.function.Consumer;

public class UISlider extends UIElement {

    private static final float MIN_SLIDER_WIDTH = 220.0f;
    private static final float MIN_SLIDER_HEIGHT = 16.0f;

    private double minValue;
    private double maxValue;
    private double value;

    private boolean dragging;
    private Consumer<Double> onChange;


    public UISlider(float x, float y, float width, float height, double minValue, double maxValue, double initialValue, Consumer<Double> onChange) {
        super(x, y, width, height, MIN_SLIDER_WIDTH, MIN_SLIDER_HEIGHT, 0.0f, 0.0f);
        this.minValue = minValue;
        this.maxValue = maxValue <= minValue ? minValue + 1.0 : maxValue;
        this.value = clamp(initialValue);
        this.onChange = onChange;
    }

    public UISlider(double minValue, double maxValue, double initialValue, Consumer<Double> onChange) {
        this(0, 0, 0, 0, minValue, maxValue, initialValue, onChange);
    }


    public void setRange(double min, double max) {
        this.minValue = min;
        this.maxValue = max <= min ? min + 1.0 : max;
        setValue(this.value);
    }

    public double getValue() {
        return value;
    }

    public void setValue(double newValue) {
        this.value = clamp(newValue);
    }
    @Override 
    public void calculateSize(BitmapFont font) {
        this.width = MIN_SLIDER_WIDTH;
        this.height = MIN_SLIDER_HEIGHT;
    }
    @Override
    public void draw(BitmapFont font) {
        // Track
        glColor3f(0.7f, 0.7f, 0.7f);
        glBegin(GL_QUADS);
        glVertex2f(x, y + height / 2 - 2);
        glVertex2f(x + width, y + height / 2 - 2);
        glVertex2f(x + width, y + height / 2 + 2);
        glVertex2f(x, y + height / 2 + 2);
        glEnd();

        // Knob
        float knobX = getKnobX();
        float knobHalf = height * 0.6f;
        glColor3f(0.9f, 0.9f, 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(knobX - knobHalf, y);
        glVertex2f(knobX + knobHalf, y);
        glVertex2f(knobX + knobHalf, y + height);
        glVertex2f(knobX - knobHalf, y + height);
        glEnd();
    }
    @Override
    public boolean handleMousePress(double mouseX, double mouseY) {
        if (isOverKnob(mouseX, mouseY) || isOverTrack(mouseX, mouseY)) {
            dragging = true;
            updateValueFromMouse(mouseX);
            return true;
        }
        return false;
    }
    @Override
    public void handleMouseDrag(double mouseX, double mouseY) {
        if (dragging) {
            updateValueFromMouse(mouseX);
        }
    }


    @Override
    public void handleMouseRelease() {
        dragging = false;
    }

    @Override
    public boolean handleKeyPress(int key, int action, int mods) {
        return false;
    }
    @Override
    public boolean handleCharPress(int codepoint) {
        return false;
    }

    public boolean isDragging() {
        return dragging;
    }

    private void updateValueFromMouse(double mouseX) {
        double t = (mouseX - x) / width;
        t = Math.max(0.0, Math.min(1.0, t));
        double newValue = minValue + t * (maxValue - minValue);
        if (newValue != value) {
            value = newValue;
            if (onChange != null) onChange.accept(value);
        }
    }

    private boolean isOverTrack(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private boolean isOverKnob(double mouseX, double mouseY) {
        float knobX = getKnobX();
        float knobHalf = height * 0.6f;
        return mouseX >= knobX - knobHalf && mouseX <= knobX + knobHalf && mouseY >= y && mouseY <= y + height;
    }

    private float getKnobX() {
        double t = (value - minValue) / (maxValue - minValue);
        return (float) (x + t * width);
    }

    private double clamp(double v) {
        if (v < minValue) return minValue;
        if (v > maxValue) return maxValue;
        return v;
    }

}


