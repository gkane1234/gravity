package com.grumbo;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Encapsulates the settings panel UI: building controls, drawing, and input handling.
 */
public class SettingsPane {


    private final Map<String, UIElement> propertyNameToElements = new HashMap<>();

    private UISlider activeSlider = null;
    private double mouseX = 0.0;
    private double mouseY = 0.0;


    public SettingsPane() {}

    // -------- Public input hooks (to be called from window callbacks) --------
    public void onMouseMove(double x, double y) {
        mouseX = x;
        mouseY = y;
        if (activeSlider != null) {
            activeSlider.handleMouseDrag(mouseX, mouseY);
        }
    }

    public void onMouseButton(int button, int action) {
        if (button != GLFW_MOUSE_BUTTON_LEFT) return;

        if (action == GLFW_PRESS) {
            activeSlider = null;
            for (UIElement element : propertyNameToElements.values()) {
                if (element.handleMousePress(mouseX, mouseY)) {
                    if (element instanceof UISlider) {
                            activeSlider = (UISlider) element;
                        }
                    return;
                }
            }
        } else if (action == GLFW_RELEASE) {
            if (activeSlider != null) {
                activeSlider.handleMouseRelease();
                activeSlider = null;
            }
        }
    }

    public boolean onKey(int key, int action, int mods) {
        for (UIElement element : propertyNameToElements.values()) {
            if (element.handleKeyPress(key, action, mods)) {
                return true;
            }
        }
        return false;
    }

    public boolean onChar(int codepoint) {
        for (UIElement element : propertyNameToElements.values()) {
            if (element.handleCharPress(codepoint)) {
                return true;
            }
        }
        return false;
    }

    // -------- Rendering --------
    public void draw(BitmapFont font) {
        // Switch to 2D rendering mode
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, Settings.getInstance().getWidth(), Settings.getInstance().getHeight(), 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        // Disable depth testing for UI
        glDisable(GL_DEPTH_TEST);

        glColor3f(1.0f, 1.0f, 1.0f);

        float yPos = 30.0f;
        float charH = (font != null ? font.getCharHeight() : 16.0f);
        float verticalPad = Math.max(4.0f, charH * 0.25f);
        float tfHGlobal = charH + verticalPad; // box taller than text so text centers automatically
        float rowHeight = Math.max(18.0f, Math.max(charH * 1.3f, tfHGlobal + 4.0f));

        // Title
        drawText("=== SETTINGS ===", 20.0f, yPos, font);
        yPos += rowHeight * 2;

        // Layout and draw per-property controls
        Settings settings = Settings.getInstance();
        for (String name : settings.getPropertyNames()) {
            Property<?> prop = settings.getProperty(name);
            if (prop == null || !prop.isEditable()) continue;

            try {
                Object value = settings.getValue(name);
                String label = name + ":";
                float labelX = 20.0f;
                drawText(label, labelX, yPos, font);

                // Match renderer width = chars * charWidth + (chars-1) * spacing
                float charWidth = (font != null ? font.getCharWidth(font.getFontSize()) : 8.0f);
                float spacing = 1.0f; // default spacing used by renderer
                int numChars = label.length();
                float labelWidth = (numChars <= 0) ? 0 : (numChars * charWidth + (numChars - 1) * spacing);

                float padding = 10.0f; // keep existing padding
                float controlsX = labelX + labelWidth + padding;
                float textFieldHeight = tfHGlobal;
                float topY = yPos + (charH - textFieldHeight) / 2.0f; // center box around the label line

                // Dispatch to type-specific editor renderers
                String type = prop.getTypeName();
                if ("int".equals(type)) {
                    renderIntEditor(name, prop, controlsX, topY, textFieldHeight, font, value);
                } else if ("double".equals(type)) {
                    renderDoubleEditor(name, prop, controlsX, topY, textFieldHeight, font, value);
                } else if ("float".equals(type)) {
                    renderFloatEditor(name, prop, controlsX, topY, textFieldHeight, font, value);
                } else if ("boolean".equals(type)) {
                    renderBooleanEditor(name, prop, controlsX, topY, textFieldHeight, font, value);
                } else if ("string".equals(type)) {
                    renderStringEditor(name, prop, controlsX, topY, textFieldHeight, font, value);
                } else if ("color".equals(type)) {
                    renderColorEditor(name, prop, controlsX, topY, textFieldHeight, font, value);
                } else if ("vector3f".equals(type)) {
                    renderVector3fEditor(name, prop, controlsX, topY, textFieldHeight, font, value);
                } else if ("doubleArray".equals(type)) {
                    renderDoubleArrayEditor(name, prop, controlsX, topY, textFieldHeight, font, value);
                } else {
                    // Fallback: generic text field
                    renderStringEditor(name, prop, controlsX, topY, textFieldHeight, font, value);
                }

                yPos += rowHeight;
            } catch (Exception ignore) {
                // Skip missing/invalid property
            }
        }

        // Instructions
        yPos += rowHeight;
        drawText("Press ESC to capture/release mouse. Click fields to edit. Enter to commit.", 20.0f, yPos, font);

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);

        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    // -------- Type-specific renderers --------
    private void renderIntEditor(String name, Property<?> prop, float x, float y, float h, BitmapFont font, Object value) {
        UITextField textField = ensureTextField(name, prop, font);
        float textFieldWidth = 220.0f;
        textField.setPosition(x, y);
        textField.setSize(textFieldWidth, h);
        if (!textField.isFocused()) textField.setTextFromValue(value);
        textField.draw(font);

        if (prop.isNumeric() && prop.hasRange()) {
            UISlider slider = ensureSlider(name, prop, font);
            slider.setPosition(x + textFieldWidth + 8.0f, y);
            slider.setSize(220.0f, h);
            slider.setValue(((Number) value).doubleValue());
            slider.draw(font);
        } else {
            ensurePlusMinus(name, prop, x + textFieldWidth + 8.0f, y, h, font);
        }
    }

    private void renderDoubleEditor(String name, Property<?> prop, float x, float y, float h, BitmapFont font, Object value) {
        renderIntEditor(name, prop, x, y, h, font, value); // same behavior: slider if ranged, +/- else
    }

    private void renderFloatEditor(String name, Property<?> prop, float x, float y, float h, BitmapFont font, Object value) {
        renderIntEditor(name, prop, x, y, h, font, value); // same behavior
    }

    private void renderStringEditor(String name, Property<?> prop, float x, float y, float h, BitmapFont font, Object value) {
        UITextField textField = ensureTextField(name, prop, font);
        float textFieldWidth = 220.0f;
        textField.setPosition(x, y);
        textField.setSize(textFieldWidth, h);
        if (!textField.isFocused()) textField.setTextFromValue(value);
        textField.draw(font);
    }

    private void renderBooleanEditor(String name, Property<?> prop, float x, float y, float h, BitmapFont font, Object value) {
        UIButton button = ensureToggleButton(name, prop, font);
        float buttonWidth = Math.max(28.0f, (font != null ? font.getCharWidth(font.getFontSize()) : 8.0f) * 6.0f);
        button.setPosition(x, y);
        button.setSize(buttonWidth, h);
        button.setLabel(((Boolean) value) ? "True" : "False");
        button.draw(font);
    }

    private void renderColorEditor(String name, Property<?> prop, float x, float y, float h, BitmapFont font, Object value) {
        // Display-only text field for now
        UITextField textField = ensureTextField(name, prop, font);
        float textFieldWidth = 300.0f;
        textField.setPosition(x, y);
        textField.setSize(textFieldWidth, h);
        if (!textField.isFocused()) textField.setTextFromValue(value);
        textField.draw(font);
    }

    private void renderVector3fEditor(String name, Property<?> prop, float x, float y, float h, BitmapFont font, Object value) {
        renderStringEditor(name, prop, x, y, h, font, value);
    }

    private void renderDoubleArrayEditor(String name, Property<?> prop, float x, float y, float h, BitmapFont font, Object value) {
        renderStringEditor(name, prop, x, y, h, font, value);
    }

    // -------- Control creation helpers --------
    private UITextField ensureTextField(String name, Property<?> prop, BitmapFont font) {
        final String textFieldName = name+".textField";
        UITextField tf = (UITextField) propertyNameToElements.get(textFieldName);
        if (tf == null) {
            tf = new UITextField(0f, 0f, 250.0f, Math.max(16.0f, font != null ? font.getCharHeight() : 16.0f), String.valueOf(prop.getValue()));
            Property<?> finalProp = prop;
            UITextField finalTf = tf;
            tf.setOnCommit(() -> {
                String txt = finalTf.getText();
                Settings settings = Settings.getInstance();
                settings.setValue(name, txt);

            });
            propertyNameToElements.put(name+".textField", tf);
        }
        return tf;
    }

    private UISlider ensureSlider(String name, Property<?> prop, BitmapFont font) {
        final String sliderName = name+".slider";
        UISlider slider = (UISlider) propertyNameToElements.get(sliderName);
        if (slider == null) {
            double min = prop.getMinAsDouble();
            double max = prop.getMaxAsDouble();
            double current = ((Number) prop.getValue()).doubleValue();
            slider = new UISlider(0f, 0f, 300.0f, Math.max(16.0f, font != null ? font.getCharHeight() : 16.0f), min, max, current, (val) -> {
                String type = prop.getTypeName();
                Settings settings = Settings.getInstance();
                if ("int".equals(type)) settings.setValue(name, (int) Math.round(val));
                else if ("double".equals(type)) settings.setValue(name, val);
                else if ("float".equals(type)) settings.setValue(name, (float)val.doubleValue());
                settings.saveSettings();
                syncTextField(name, settings);
            });
            propertyNameToElements.put(name+".slider", slider);
        }
        return slider;
    }

    private UIButton ensureToggleButton(String name, Property<?> prop, BitmapFont font) {
        final String buttonName = name+".button";
        UIButton button = (UIButton) propertyNameToElements.get(buttonName);
        if (button == null) {
            button = new UIButton(0f, 0f, 250.0f, Math.max(16.0f, font != null ? font.getCharHeight() : 16.0f), String.valueOf(prop.getValue()), () -> {
                System.out.println("Button clicked: " + name);
                Settings settings = Settings.getInstance();
                settings.setValue(name, !((Boolean) settings.getValue(name)));
                settings.saveSettings();
                syncToggleButton(name, settings);
            });
            propertyNameToElements.put(name+".button", button);
        }
        return button;
    }

    private void ensurePlusMinus(String name, Property<?> prop, float startX, float y, float h, BitmapFont font) {
        final String plusName = name+".plus";
        final String minusName = name+".minus";
        UIButton plusButton = (UIButton) propertyNameToElements.get(plusName);
        UIButton minusButton = (UIButton) propertyNameToElements.get(minusName);
        if (plusButton == null || minusButton == null) {
            float btnW = 28.0f;
            Runnable bumpUp = () -> { bumpValue(Settings.getInstance(), prop, name, 1.1); syncTextField(name, Settings.getInstance()); };
            Runnable bumpDown = () -> { bumpValue(Settings.getInstance(), prop, name, 1.0/1.1); syncTextField(name, Settings.getInstance()); };
            plusButton = new UIButton(0f, 0f, btnW, h, "+", bumpUp);
            minusButton = new UIButton(0f, 0f, btnW, h, "-", bumpDown);
            propertyNameToElements.put(name+".plus", plusButton);
            propertyNameToElements.put(name+".minus", minusButton);
        }
        float btnW = 28.0f;
        plusButton.setPosition(startX, y);
        plusButton.setSize(btnW, h);
        minusButton.setPosition(startX + btnW + 6.0f, y);
        minusButton.setSize(btnW, h);
        plusButton.draw(font);
        minusButton.draw(font);
    }

    private void bumpValue(Settings settings, Property<?> prop, String name, double factor) {
        String type = prop.getTypeName();
        try {
            if ("int".equals(type)) {
                int current = (Integer) settings.getValue(name);
                int newValue = (int) Math.round(current * factor);
                if (newValue == current) {
                    newValue += factor>1?1:-1;
                }
                settings.setValue(name, newValue);
            } else if ("double".equals(type)) {
                double current = (Double) settings.getValue(name);
                settings.setValue(name, current * factor);
            } else if ("float".equals(type)) {
                float current = (Float) settings.getValue(name);
                settings.setValue(name, (float) (current * factor));
            }
            settings.saveSettings();
        } catch (Exception ignore) {}
    }

    private void syncTextField(String name, Settings settings) {
        UITextField textField = (UITextField) propertyNameToElements.get(name+".textField");
        if (textField != null && !textField.isFocused()) {
            Object v = settings.getValue(name);
            textField.setTextFromValue(v);
        }
    }

    private void syncToggleButton(String name, Settings settings) {
        UIButton button = (UIButton) propertyNameToElements.get(name+".button");
        if (button != null) {
            Object v = settings.getValue(name);
            button.setLabel(((Boolean) v) ? "True" : "False");
        }
    }

    private void drawText(String text, float x, float y, BitmapFont font) {
        if (font != null && font.isLoaded()) {
            font.drawText(text, x, y, 1.0f, font.getFontSize());
        } else {
            drawSimpleText(text, x, y);
        }
    }

    private void drawSimpleText(String text, float x, float y) {
        float charWidth = 8.0f;
        float charHeight = 12.0f;

        glBegin(GL_LINES);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float charX = x + i * charWidth;

            if (c != ' ') {
                glVertex2f(charX, y);
                glVertex2f(charX + charWidth - 2, y);
                glVertex2f(charX + charWidth - 2, y);
                glVertex2f(charX + charWidth - 2, y + charHeight);
                glVertex2f(charX + charWidth - 2, y + charHeight);
                glVertex2f(charX, y + charHeight);
                glVertex2f(charX, y + charHeight);
                glVertex2f(charX, y);
            }
        }
        glEnd();
    }
}
