package com.grumbo;

import java.awt.Color;
import java.util.ArrayList;
import java.util.function.Predicate;
import org.joml.Vector3f;

public class Property<T> {
    private String name;
    private T value;
    private T defaultValue;
    private T minValue;
    private T maxValue;
    private Predicate<T> validator;
    private boolean isNumeric;
    private boolean editable = true;
    private String typeName; // e.g., "int", "double", "float", "boolean", "color", "doubleArray", "vector3f", "string"
    
    // Cached UI row
    private UIRow editorRow;
    private T cachedValue;
    
    // Constructors
    public Property(String name, T value, T defaultValue) {
        this.name = name;
        this.value = value;
        this.defaultValue = defaultValue;
        this.isNumeric = isNumericType(value);
    }
    
    public Property(String name, T value, T defaultValue, T minValue, T maxValue) {
        this(name, value, defaultValue);
        this.minValue = minValue;
        this.maxValue = maxValue;
        validateAndSet(value);
    }
    
    public Property(String name, T value, T defaultValue, Predicate<T> validator) {
        this(name, value, defaultValue);
        this.validator = validator;
        validateAndSet(value);
    }
    
    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public T getValue() { return value; }
    
    public void setValue(T value) {
        validateAndSet(value);
    }
    
    public T getDefaultValue() { return defaultValue; }
    public void setDefaultValue(T defaultValue) { this.defaultValue = defaultValue; }
    
    public T getMinValue() { return minValue; }
    public void setMinValue(T minValue) { this.minValue = minValue; }
    
    public T getMaxValue() { return maxValue; }
    public void setMaxValue(T maxValue) { this.maxValue = maxValue; }
    
    public boolean isNumeric() { return isNumeric; }
    public boolean isEditable() { return editable; }
    public void setEditable(boolean editable) { this.editable = editable; }
    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }
    public boolean hasRange() { return isNumeric && minValue != null && maxValue != null; }
    public Double getMinAsDouble() {
        if (!isNumeric || minValue == null) return null;
        return ((Number) minValue).doubleValue();
    }
    public Double getMaxAsDouble() {
        if (!isNumeric || maxValue == null) return null;
        return ((Number) maxValue).doubleValue();
    }

    public void update() {
        // Lazily create the editor row once
        if (editorRow == null) {
            editorRow = getEditorRow();
            cachedValue = value;
            return;
        }

        // When the underlying value changes, sync existing controls instead of recreating them
        if (cachedValue == null || !cachedValue.equals(value)) {
            syncEditorRowFromValue();
            cachedValue = value;
        }
    }
    
    // Validation methods
    private void validateAndSet(T newValue) {
        if (newValue == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        
        // Check custom validator
        if (validator != null && !validator.test(newValue)) {
            throw new IllegalArgumentException("Value failed custom validation for property: " + name);
        }
        
        // Check min/max bounds for numeric types
        if (isNumeric && minValue != null && maxValue != null) {
            if (!isInRange(newValue, minValue, maxValue)) {
                throw new IllegalArgumentException(
                    String.format("Value %s is outside allowed range [%s, %s] for property: %s", 
                        newValue, minValue, maxValue, name));
            }
        }
        
        this.value = newValue;
    }
    
    private boolean isInRange(T value, T min, T max) {
        if (value instanceof Number && min instanceof Number && max instanceof Number) {
            double val = ((Number) value).doubleValue();
            double minVal = ((Number) min).doubleValue();
            double maxVal = ((Number) max).doubleValue();
            return val >= minVal && val <= maxVal;
        }
        return true; // Non-numeric types don't have range validation
    }
    
    private boolean isNumericType(T value) {
        return value instanceof Number;
    }
    
    // Reset to default value
    public void reset() {
        this.value = defaultValue;
    }
    
    // Convenience methods for common types
    public static Property<Integer> createIntProperty(String name, int value, int defaultValue) {
        Property<Integer> p = new Property<>(name, value, defaultValue);
        p.typeName = "int";
        return p;
    }
    
    public static Property<Integer> createIntProperty(String name, int value, int defaultValue, int min, int max) {
        Property<Integer> p = new Property<>(name, value, defaultValue, min, max);
        p.typeName = "int";
        return p;
    }
    public static Property<Integer> createIntProperty(String name, int value, int defaultValue, int min, int max, boolean editable) {
        Property<Integer> p = createIntProperty(name, value, defaultValue, min, max);
        p.setEditable(editable);
        return p;
    }
    public static Property<Integer> createIntProperty(String name, int value, int defaultValue, Integer min, Integer max, boolean editable) {
        Property<Integer> p = (min != null && max != null)
            ? new Property<>(name, value, defaultValue, min, max)
            : new Property<>(name, value, defaultValue);
        p.typeName = "int";
        p.setEditable(editable);
        if (min != null) p.setMinValue(min);
        if (max != null) p.setMaxValue(max);
        return p;
    }
    
    public static Property<Double> createDoubleProperty(String name, double value, double defaultValue) {
        Property<Double> p = new Property<>(name, value, defaultValue);
        p.typeName = "double";
        return p;
    }
    
    public static Property<Double> createDoubleProperty(String name, double value, double defaultValue, double min, double max) {
        Property<Double> p = new Property<>(name, value, defaultValue, min, max);
        p.typeName = "double";
        return p;
    }
    public static Property<Double> createDoubleProperty(String name, double value, double defaultValue, double min, double max, boolean editable) {
        Property<Double> p = createDoubleProperty(name, value, defaultValue, min, max);
        p.setEditable(editable);
        return p;
    }
    public static Property<Double> createDoubleProperty(String name, double value, double defaultValue, Double min, Double max, boolean editable) {
        Property<Double> p = (min != null && max != null)
            ? new Property<>(name, value, defaultValue, min, max)
            : new Property<>(name, value, defaultValue);
        p.typeName = "double";
        p.setEditable(editable);
        if (min != null) p.setMinValue(min);
        if (max != null) p.setMaxValue(max);
        return p;
    }

    public static Property<Float> createFloatProperty(String name, float value, float defaultValue) {
        Property<Float> p = new Property<>(name, value, defaultValue);
        p.typeName = "float";
        return p;
    }
    
    public static Property<Float> createFloatProperty(String name, float value, float defaultValue, float min, float max) {
        Property<Float> p = new Property<>(name, value, defaultValue, min, max);
        p.typeName = "float";
        return p;
    }
    public static Property<Float> createFloatProperty(String name, float value, float defaultValue, float min, float max, boolean editable) {
        Property<Float> p = createFloatProperty(name, value, defaultValue, min, max);
        p.setEditable(editable);
        return p;
    }
    public static Property<Float> createFloatProperty(String name, float value, float defaultValue, Float min, Float max, boolean editable) {
        Property<Float> p = (min != null && max != null)
            ? new Property<>(name, value, defaultValue, min, max)
            : new Property<>(name, value, defaultValue);
        p.typeName = "float";
        p.setEditable(editable);
        if (min != null) p.setMinValue(min);
        if (max != null) p.setMaxValue(max);
        return p;
    }
    
    public static Property<Boolean> createBooleanProperty(String name, boolean value, boolean defaultValue) {
        Property<Boolean> p = new Property<>(name, value, defaultValue);
        p.typeName = "boolean";
        return p;
    }
    public static Property<Boolean> createBooleanProperty(String name, boolean value, boolean defaultValue, boolean editable) {
        Property<Boolean> p = createBooleanProperty(name, value, defaultValue);
        p.setEditable(editable);
        return p;
    }
    
    public static Property<String> createStringProperty(String name, String value, String defaultValue) {
        Property<String> p = new Property<>(name, value, defaultValue);
        p.typeName = "string";
        return p;
    }
    public static Property<String> createStringProperty(String name, String value, String defaultValue, boolean editable) {
        Property<String> p = createStringProperty(name, value, defaultValue);
        p.setEditable(editable);
        return p;
    }
    
    public static Property<Color> createColorProperty(String name, Color value, Color defaultValue) {
        Property<Color> p = new Property<>(name, value, defaultValue);
        p.typeName = "color";
        return p;
    }
    public static Property<Color> createColorProperty(String name, Color value, Color defaultValue, boolean editable) {
        Property<Color> p = createColorProperty(name, value, defaultValue);
        p.setEditable(editable);
        return p;
    }

    public static Property<Vector3f> createVector3fProperty(String name, Vector3f value, Vector3f defaultValue) {
        Property<Vector3f> p = new Property<>(name, value, defaultValue);
        p.typeName = "vector3f";
        return p;
    }
    public static Property<Vector3f> createVector3fProperty(String name, Vector3f value, Vector3f defaultValue, boolean editable) {
        Property<Vector3f> p = createVector3fProperty(name, value, defaultValue);
        p.setEditable(editable);
        return p;
    }
    
    // Special method for Color properties that work with RGB integers
    public static Property<Color> createColorPropertyFromRGB(String name, int rgbValue, int defaultRGB) {
        Property<Color> prop = new Property<>(name, new Color(rgbValue), new Color(defaultRGB));
        prop.typeName = "color";
        // Add custom validator to ensure valid RGB values
        prop.validator = color -> color != null ;
        return prop;
    }
    public static Property<Color> createColorPropertyFromRGB(String name, int rgbValue, int defaultRGB, boolean editable) {
        Property<Color> p = createColorPropertyFromRGB(name, rgbValue, defaultRGB);
        p.setEditable(editable);
        return p;
    }
    
    // Utility method to get RGB value for Color properties
    public int getRGBValue() {
        if (value instanceof Color) {
            return ((Color) value).getRGB();
        }
        throw new IllegalStateException("Property is not a Color type");
    }
    
    @SuppressWarnings("unchecked")
    public void setRGBValue(int rgb) {
        if (value instanceof Color) {
            setValue((T) new Color(rgb));
        } else {
            throw new IllegalStateException("Property is not a Color type");
        }
    }
    
    @Override
    public String toString() {
        return String.format("Property{name='%s', value=%s, defaultValue=%s, min=%s, max=%s}", 
            name, value, defaultValue, minValue, maxValue);
    }

    public UIRow getEditorRow() {
        if (editorRow != null) {
            return editorRow;
        }
        ArrayList<UIElement> elements;
        switch (typeName) {
            case "int": elements = createIntEditorElements(); break;
            case "double": elements = createDoubleEditorElements(); break;
            case "float": elements = createFloatEditorElements(); break;
            case "boolean": elements = createBooleanEditorElements(); break;
            case "string": elements = createStringEditorElements(); break;
            case "color": elements = createColorEditorElements(); break;
            case "vector3f": elements = createVector3fEditorElements(); break;
            case "doubleArray": elements = createDoubleArrayEditorElements(); break;
            default: elements = createStringEditorElements(); break;
        }
        editorRow = new UIRow(elements);
        return editorRow;
    }

    private void syncEditorRowFromValue() {
        if (editorRow == null) return;
        for (UIElement element : editorRow.getElements()) {
            if (element instanceof UITextField) {
                ((UITextField) element).setTextFromValue(value);
            } else if (element instanceof UISlider) {
                if (value instanceof Number) {
                    ((UISlider) element).setValue(((Number) value).doubleValue());
                }
            }
        }
    }

    private ArrayList<UIElement> createIntEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf(value));
        tf.setOnCommit(() -> {
            try {
                int parsed = Integer.parseInt(tf.getText().trim());
                Settings.getInstance().setValue(name, parsed);
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {}
        });
        elements.add(tf);
        if (hasRange()) {
            double min = getMinAsDouble();
            double max = getMaxAsDouble();
            double current = ((Number) value).doubleValue();
            UISlider slider = new UISlider(min, max, current, (val) -> {
                Settings.getInstance().setValue(name, (int) Math.round(val));
                Settings.getInstance().saveSettings();
            });
            elements.add(slider);
        } else {
            UIButton plus = new UIButton("+", () -> {
                int v = ((Number) value).intValue();
                Settings.getInstance().setValue(name, v + 1);
                Settings.getInstance().saveSettings();
            });
            UIButton minus = new UIButton("-", () -> {
                int v = ((Number) value).intValue();
                Settings.getInstance().setValue(name, v - 1);
                Settings.getInstance().saveSettings();
            });
            elements.add(plus);
            elements.add(minus);
        }
        return elements;
    }

    private ArrayList<UIElement> createDoubleEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf(value));
        tf.setOnCommit(() -> {
            try {
                double parsed = Double.parseDouble(tf.getText().trim());
                Settings.getInstance().setValue(name, parsed);
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {}
        });
        elements.add(tf);
        if (hasRange()) {
            double min = getMinAsDouble();
            double max = getMaxAsDouble();
            double current = ((Number) value).doubleValue();
            UISlider slider = new UISlider(min, max, current, (val) -> {
                Settings.getInstance().setValue(name, val);
                Settings.getInstance().saveSettings();
            });
            elements.add(slider);
        } else {
            UIButton plus = new UIButton("+", () -> {
                double v = ((Number) value).doubleValue();
                Settings.getInstance().setValue(name, v * 1.1);
                Settings.getInstance().saveSettings();
            });
            UIButton minus = new UIButton("-", () -> {
                double v = ((Number) value).doubleValue();
                Settings.getInstance().setValue(name, v / 1.1);
                Settings.getInstance().saveSettings();
            });
            elements.add(plus);
            elements.add(minus);
        }
        return elements;
    }

    private ArrayList<UIElement> createFloatEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf(value));
        tf.setOnCommit(() -> {
            try {
                float parsed = Float.parseFloat(tf.getText().trim());
                Settings.getInstance().setValue(name, parsed);
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {}
        });
        elements.add(tf);
        if (hasRange()) {
            double min = getMinAsDouble();
            double max = getMaxAsDouble();
            double current = ((Number) value).doubleValue();
            UISlider slider = new UISlider(min, max, current, (val) -> {
                Settings.getInstance().setValue(name, (float) val.doubleValue());
                Settings.getInstance().saveSettings();
            });
            elements.add(slider);
        } else {
            UIButton plus = new UIButton("+", () -> {
                float v = ((Number) value).floatValue();
                Settings.getInstance().setValue(name, (float) (v * 1.1));
                Settings.getInstance().saveSettings();
            });
            UIButton minus = new UIButton("-", () -> {
                float v = ((Number) value).floatValue();
                Settings.getInstance().setValue(name, (float) (v / 1.1));
                Settings.getInstance().saveSettings();
            });
            elements.add(plus);
            elements.add(minus);
        }
        return elements;
    }

    private ArrayList<UIElement> createBooleanEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UIButton toggle = new UIButton("", () -> {
            try {
                boolean current = (Boolean) Settings.getInstance().getValue(name);
                Settings.getInstance().setValue(name, !current);
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {}
        });
        elements.add(toggle);
        return elements;
    }

    private ArrayList<UIElement> createStringEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf(value));
        tf.setOnCommit(() -> {
            Settings.getInstance().setValue(name, tf.getText());
            Settings.getInstance().saveSettings();
        });
        elements.add(tf);
        return elements;
    }

    private ArrayList<UIElement> createColorEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf(value));
        tf.setOnCommit(() -> {
            try {
                String txt = tf.getText().trim();
                int rgb;
                if (txt.startsWith("#")) {
                    rgb = (int) Long.parseLong(txt.substring(1), 16);
                } else if (txt.startsWith("0x") || txt.startsWith("0X")) {
                    rgb = (int) Long.parseLong(txt.substring(2), 16);
                } else {
                    rgb = Integer.parseInt(txt);
                }
                // If missing alpha, assume opaque
                Color c = new Color(rgb, (txt.length() > 7));
                Settings.getInstance().setValue(name, c);
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {}
        });
        elements.add(tf);
        return elements;
    }

    private ArrayList<UIElement> createVector3fEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf(value));
        tf.setOnCommit(() -> {
            try {
                String[] parts = tf.getText().trim().split("[,\n\t\r ]+");
                if (parts.length >= 3) {
                    float x = Float.parseFloat(parts[0]);
                    float y = Float.parseFloat(parts[1]);
                    float z = Float.parseFloat(parts[2]);
                    Settings.getInstance().setValue(name, new Vector3f(x, y, z));
                    Settings.getInstance().saveSettings();
                }
            } catch (Exception ignore) {}
        });
        elements.add(tf);
        return elements;
    }

    private ArrayList<UIElement> createDoubleArrayEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf(value));
        tf.setOnCommit(() -> {
            try {
                String[] parts = tf.getText().trim().split(",");
                double[] arr = new double[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    arr[i] = Double.parseDouble(parts[i].trim());
                }
                Settings.getInstance().setValue(name, arr);
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {}
        });
        elements.add(tf);
        return elements;
    }
}
