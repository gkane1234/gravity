package com.grumbo.simulation;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Predicate;
import org.joml.Vector3f;

import com.grumbo.ui.*;

/**
 * Property class used for accessing and setting the simulation settings.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class Property<T> {
    public static final int DEFAULT_ROUNDING = 3;
    private String name;
    private T value;
    private T defaultValue;
    private T minValue;
    private T maxValue;
    private Predicate<T> validator;
    private boolean isNumeric;
    private boolean editable = true;
    private PropertyType typeName;
    private int numericalRounding;
    // Cached UI row
    private UIRow editorRow;
    private T cachedValue;
    private String[] options; // Options for a selector property
    private int selectedIndex; // Index of the selected option for a selector property
    /**
     * Enum for the type of the property.
     */
    private enum PropertyType {
        INT,
        DOUBLE,
        FLOAT,
        BOOLEAN,
        COLOR,
        DOUBLE_ARRAY,
        VECTOR3F,
        STRING,
        SELECTOR;
    }
    // Constructors

    /**
     * Constructor for the Property class.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param numericalRounding the numerical rounding of the property
     */
    public Property(String name, T value, T defaultValue, int numericalRounding, String[] options) {
        this.name = name;
        this.value = value;
        this.defaultValue = defaultValue;
        this.isNumeric = isNumericType(value);
        if (isNumeric) {
            this.numericalRounding = numericalRounding;
        }
        this.options = options;
    }

        /**
     * Constructor for the Property class.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param numericalRounding the numerical rounding of the property
     */
    public Property(String name, T value, T defaultValue, String[] options) {
        this(name, value, defaultValue, DEFAULT_ROUNDING, options);
    }

    /**
     * Constructor for the Property class.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param numericalRounding the numerical rounding of the property
     */
    public Property(String name, T value, T defaultValue, int numericalRounding) {
        this(name, value, defaultValue, numericalRounding, null);
    }


    /**
     * Constructor for the Property class.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     */
    public Property(String name, T value, T defaultValue) {
        this(name, value, defaultValue, DEFAULT_ROUNDING);
    }

    /**
     * Constructor for the Property class.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param minValue the minimum value of the property
     * @param maxValue the maximum value of the property
     */
    public Property(String name, T value, T defaultValue, T minValue, T maxValue) {
        this(name, value, defaultValue);
        if (!isNumericType(value)) {
            throw new IllegalArgumentException("Value is not a numeric type");
        }
        this.minValue = minValue;
        this.maxValue = maxValue;
        validateAndSet(value);
    }
    
    /**
     * Constructor for the Property class.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param validator the validator of the property
     */
    public Property(String name, T value, T defaultValue, Predicate<T> validator) {
        this(name, value, defaultValue);
        this.validator = validator;
        validateAndSet(value);
    }

    /**
     * Constructor for the Property class.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param minValue the minimum value of the property
     * @param maxValue the maximum value of the property
     * @param numericalRounding the numerical rounding of the property
     */
    public Property(String name, T value, T defaultValue, T minValue, T maxValue, int numericalRounding) {
        this(name, value, defaultValue, numericalRounding);
        if (!isNumericType(value)) {
            throw new IllegalArgumentException("Value is not a numeric type");
        }
        this.minValue = minValue;
        this.maxValue = maxValue;
        validateAndSet(value);
    }
    
    /**
     * Constructor for the Property class.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param validator the validator of the property
     * @param numericalRounding the numerical rounding of the property
     */
    public Property(String name, T value, T defaultValue, Predicate<T> validator, int numericalRounding) {
        this(name, value, defaultValue, numericalRounding);
        this.validator = validator;
        validateAndSet(value);
    }


    
    // Getters and setters
    /**
     * Gets the name of the property.
     * @return the name of the property
     */
    public String getName() { return name; }
    /**
     * Sets the name of the property.
     * @param name the name of the property
     */
    public void setName(String name) { this.name = name; }
    
    /**
     * Gets the value of the property.
     * @return the value of the property
     */
    public T getValue() { return value; }
    
    /**
     * Sets the value of the property.
     * @param value the value of the property
     */
    public void setValue(T value) {
        validateAndSet(value);
    }
    
    /**
     * Gets the default value of the property.
     * @return the default value of the property
     */
    public T getDefaultValue() { return defaultValue; }
    /**
     * Sets the default value of the property.
     * @param defaultValue the default value of the property
     */
    public void setDefaultValue(T defaultValue) { this.defaultValue = defaultValue; }
    
    /**
     * Gets the minimum value of the property.
     * @return the minimum value of the property
     */
    public T getMinValue() { return minValue; }
    /**
     * Sets the minimum value of the property.
     * @param minValue the minimum value of the property
     */
    public void setMinValue(T minValue) { this.minValue = minValue; }
    
    /**
     * Gets the maximum value of the property.
     * @return the maximum value of the property
     */
    public T getMaxValue() { return maxValue; }
    /**
     * Sets the maximum value of the property.
     * @param maxValue the maximum value of the property
     */
    public void setMaxValue(T maxValue) { this.maxValue = maxValue; }
    
    /**
     * Gets the numeric status of the property.
     * @return the numeric status of the property
     */
    public boolean isNumeric() { return isNumeric; }
    /**
     * Gets the editable status of the property.
     * @return the editable status of the property
     */
    public boolean isEditable() { return editable; }
    /**
     * Sets the editable status of the property.
     * @param editable the editable status of the property
     */
    public void setEditable(boolean editable) { this.editable = editable; }
    /**
     * Gets the type name of the property.
     * @return the type name of the property
     */
    public String getTypeName() { return typeName.name(); }
    /**
     * Sets the type name of the property.
     * @param typeName the type name of the property
     */
    public void setTypeName(String typeName) { this.typeName = PropertyType.valueOf(typeName); }
    /**
     * Gets the range status of the property.
     * @return the range status of the property
     */
    public boolean hasRange() { return isNumeric && minValue != null && maxValue != null; }
    /**
     * Gets the minimum value of the property as a double.
     * @return the minimum value of the property as a double
     */
    public Double getMinAsDouble() {
        if (!isNumeric || minValue == null) return null;
        return ((Number) minValue).doubleValue();
    }
    /**
     * Gets the maximum value of the property as a double.
     * @return the maximum value of the property as a double
     */
    public Double getMaxAsDouble() {
        if (!isNumeric || maxValue == null) return null;
        return ((Number) maxValue).doubleValue();
    }

    /**
     * Gets the selected index of the property.
     * @return the selected index of the property
     */
    public int getSelectedIndex() { 
        if (typeName != PropertyType.SELECTOR) throw new IllegalArgumentException("Property is not a selector"); 
        return selectedIndex; 
    }
    /**
     * Sets the selected index of the property.
     * @param selectedIndex the selected index of the property
     */
    public void setSelectedIndex(int selectedIndex) { 
        if (typeName != PropertyType.SELECTOR) throw new IllegalArgumentException("Property is not a selector"); 
        this.selectedIndex = selectedIndex; 
    }

    /**
     * Updates the property.
     */
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
            if (typeName == PropertyType.SELECTOR) {
                selectedIndex = Arrays.asList(options).indexOf(value);
            }
        }
    }
    
    // Validation methods
    /**
     * Validates and sets the value of the property.
     * @param newValue the new value of the property
     */
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
    
    /**
     * Checks if the value is in range.
     * @param value the value to check
     * @param min the minimum value
     * @param max the maximum value
     * @return true if the value is in range, false otherwise
     */
    private boolean isInRange(T value, T min, T max) {
        if (value instanceof Number && min instanceof Number && max instanceof Number) {
            double val = ((Number) value).doubleValue();
            double minVal = ((Number) min).doubleValue();
            double maxVal = ((Number) max).doubleValue();
            return val >= minVal && val <= maxVal;
        }
        return true; // Non-numeric types don't have range validation
    }
    
    /**
     * Checks if the value is a numeric type.
     * @param value the value to check
     * @return true if the value is a numeric type, false otherwise
     */
    private boolean isNumericType(T value) {
        return value instanceof Number;
    }
    
    /**
     * Resets the value of the property to the default value.
     */
    public void reset() {
        this.value = defaultValue;
    }
    
    // Convenience methods for common types
    /**
     * Creates an integer property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @return the integer property
     */
    public static Property<Integer> createIntProperty(String name, int value, int defaultValue) {
        Property<Integer> p = new Property<>(name, value, defaultValue);
        p.typeName = PropertyType.INT;
        return p;
    }
    
    /**
     * Creates an integer property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param min the minimum value of the property
     * @param max the maximum value of the property
     * @return the integer property
     */
    public static Property<Integer> createIntProperty(String name, int value, int defaultValue, int min, int max) {
        Property<Integer> p = new Property<>(name, value, defaultValue, min, max);
        p.typeName = PropertyType.INT;
        return p;
    }
    /**
     * Creates an integer property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param min the minimum value of the property
     * @param max the maximum value of the property
     * @param editable the editable status of the property
     * @return the integer property
     */
    public static Property<Integer> createIntProperty(String name, int value, int defaultValue, int min, int max, boolean editable) {
        Property<Integer> p = createIntProperty(name, value, defaultValue, min, max);
        p.setEditable(editable);
        return p;
    }
    /**
     * Creates an integer property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param min the minimum value of the property
     * @param max the maximum value of the property
     * @param editable the editable status of the property
     * @return the integer property
     */
    public static Property<Integer> createIntProperty(String name, int value, int defaultValue, Integer min, Integer max, boolean editable) {
        Property<Integer> p = (min != null && max != null)
            ? new Property<>(name, value, defaultValue, min, max)
            : new Property<>(name, value, defaultValue);
        p.typeName = PropertyType.INT;
        p.setEditable(editable);
        if (min != null) p.setMinValue(min);
        if (max != null) p.setMaxValue(max);
        return p;
    }
    
    /**
     * Creates a double property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @return the double property
     */
    public static Property<Double> createDoubleProperty(String name, double value, double defaultValue) {
        Property<Double> p = new Property<>(name, value, defaultValue);
        p.typeName = PropertyType.DOUBLE;
        return p;
    }
    
    /**
     * Creates a double property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param min the minimum value of the property
     * @param max the maximum value of the property
     * @return the double property
     */
    public static Property<Double> createDoubleProperty(String name, double value, double defaultValue, double min, double max) {
        Property<Double> p = new Property<>(name, value, defaultValue, min, max);
        p.typeName = PropertyType.DOUBLE;
        return p;
    }
    /**
     * Creates a double property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param min the minimum value of the property
     * @param max the maximum value of the property
     * @param editable the editable status of the property
     * @return the double property
     */
    public static Property<Double> createDoubleProperty(String name, double value, double defaultValue, double min, double max, boolean editable) {
        Property<Double> p = createDoubleProperty(name, value, defaultValue, min, max);
        p.setEditable(editable);
        return p;
    }
    /**
     * Creates a double property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param min the minimum value of the property
     * @param max the maximum value of the property
     * @param editable the editable status of the property
     * @return the double property
     */
    public static Property<Double> createDoubleProperty(String name, double value, double defaultValue, Double min, Double max, boolean editable) {
        Property<Double> p = (min != null && max != null)
            ? new Property<>(name, value, defaultValue, min, max)
            : new Property<>(name, value, defaultValue);
        p.typeName = PropertyType.DOUBLE;
        p.setEditable(editable);
        if (min != null) p.setMinValue(min);
        if (max != null) p.setMaxValue(max);
        return p;
    }

    /**
     * Creates a float property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @return the float property
     */
    public static Property<Float> createFloatProperty(String name, float value, float defaultValue) {
        Property<Float> p = new Property<>(name, value, defaultValue);
        p.typeName = PropertyType.FLOAT;
        return p;
    }
    
    /**
     * Creates a float property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param min the minimum value of the property
     * @param max the maximum value of the property
     * @return the float property
     */
    public static Property<Float> createFloatProperty(String name, float value, float defaultValue, float min, float max) {
        Property<Float> p = new Property<>(name, value, defaultValue, min, max);
        p.typeName = PropertyType.FLOAT;
        return p;
    }
    /**
     * Creates a float property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param min the minimum value of the property
     * @param max the maximum value of the property
     * @param editable the editable status of the property
     * @return the float property
     */
    public static Property<Float> createFloatProperty(String name, float value, float defaultValue, float min, float max, boolean editable) {
        Property<Float> p = createFloatProperty(name, value, defaultValue, min, max);
        p.setEditable(editable);
        return p;
    }
    /**
     * Creates a float property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param min the minimum value of the property
     * @param max the maximum value of the property
     * @param editable the editable status of the property
     * @return the float property
     */
    public static Property<Float> createFloatProperty(String name, float value, float defaultValue, Float min, Float max, boolean editable) {
        Property<Float> p = (min != null && max != null)
            ? new Property<>(name, value, defaultValue, min, max)
            : new Property<>(name, value, defaultValue);
        p.typeName = PropertyType.FLOAT;
        p.setEditable(editable);
        if (min != null) p.setMinValue(min);
        if (max != null) p.setMaxValue(max);
        return p;
    }
    
    /**
     * Creates a boolean property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @return the boolean property
     */
    public static Property<Boolean> createBooleanProperty(String name, boolean value, boolean defaultValue) {
        Property<Boolean> p = new Property<>(name, value, defaultValue);
        p.typeName = PropertyType.BOOLEAN;
        return p;
    }
    /**
     * Creates a boolean property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param editable the editable status of the property
     * @return the boolean property
     */
    public static Property<Boolean> createBooleanProperty(String name, boolean value, boolean defaultValue, boolean editable) {
        Property<Boolean> p = createBooleanProperty(name, value, defaultValue);
        p.setEditable(editable);
        return p;
    }
    
    /**
     * Creates a string property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @return the string property
     */
    public static Property<String> createStringProperty(String name, String value, String defaultValue) {
        Property<String> p = new Property<>(name, value, defaultValue);
        p.typeName = PropertyType.STRING;
        return p;
    }
    /**
     * Creates a string property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param editable the editable status of the property
     * @return the string property
     */
    public static Property<String> createStringProperty(String name, String value, String defaultValue, boolean editable) {
        Property<String> p = createStringProperty(name, value, defaultValue);
        p.setEditable(editable);
        return p;
    }
    /**
     * Creates a selector property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param options the options for the selector property
     * @param editable the editable status of the property
     * @return the selector property
     */
    public static Property<String> createSelectorProperty(String name, String value, String defaultValue, String[] options, boolean editable) {
        Property<String> p = new Property<>(name, value, defaultValue, options);
        p.typeName = PropertyType.SELECTOR;
        p.setEditable(editable);
        p.options = options;
        p.selectedIndex = Arrays.asList(options).indexOf(value);
        return p;
    }

    
    /**
     * Creates a color property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @return the color property
     */
    public static Property<Color> createColorProperty(String name, Color value, Color defaultValue) {
        Property<Color> p = new Property<>(name, value, defaultValue);
        p.typeName = PropertyType.COLOR;
        return p;
    }
    /**
     * Creates a color property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param editable the editable status of the property
     * @return the color property
     */
    public static Property<Color> createColorProperty(String name, Color value, Color defaultValue, boolean editable) {
        Property<Color> p = createColorProperty(name, value, defaultValue);
        p.setEditable(editable);
        return p;
    }

    /**
     * Creates a vector3f property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @return the vector3f property
     */
    public static Property<Vector3f> createVector3fProperty(String name, Vector3f value, Vector3f defaultValue) {
        Property<Vector3f> p = new Property<>(name, value, defaultValue);
        p.typeName = PropertyType.VECTOR3F;
        return p;
    }
    /**
     * Creates a vector3f property.
     * @param name the name of the property
     * @param value the value of the property
     * @param defaultValue the default value of the property
     * @param editable the editable status of the property
     * @return the vector3f property
     */
    public static Property<Vector3f> createVector3fProperty(String name, Vector3f value, Vector3f defaultValue, boolean editable) {
        Property<Vector3f> p = createVector3fProperty(name, value, defaultValue);
        p.setEditable(editable);
        return p;
    }

    /**
     * Creates a color property from RGB.
     * @param name the name of the property
     * @param rgbValue the RGB value of the property
     * @param defaultRGB the default RGB value of the property
     * @return the color property
     */
    public static Property<Color> createColorPropertyFromRGB(String name, int rgbValue, int defaultRGB) {
        Property<Color> prop = new Property<>(name, new Color(rgbValue), new Color(defaultRGB));
        prop.typeName = PropertyType.COLOR;
        // Add custom validator to ensure valid RGB values
        prop.validator = color -> color != null ;
        return prop;
    }
    /**
     * Creates a color property from RGB.
     * @param name the name of the property
     * @param rgbValue the RGB value of the property
     * @param defaultRGB the default RGB value of the property
     * @param editable the editable status of the property
     * @return the color property
     */
    public static Property<Color> createColorPropertyFromRGB(String name, int rgbValue, int defaultRGB, boolean editable) {
        Property<Color> p = createColorPropertyFromRGB(name, rgbValue, defaultRGB);
        p.setEditable(editable);
        return p;
    }
    
    /**
     * Gets the RGB value of the property.
     * @return the RGB value of the property
     */
    public int getRGBValue() {
        if (value instanceof Color) {
            return ((Color) value).getRGB();
        }
        throw new IllegalStateException("Property is not a Color type");
    }
    
    /**
     * Sets the RGB value of the property.
     * @param rgb the RGB value of the property
     */
    @SuppressWarnings("unchecked")
    public void setRGBValue(int rgb) {
        if (value instanceof Color) {
            setValue((T) new Color(rgb));
        } else {
            throw new IllegalStateException("Property is not a Color type");
        }
    }
    
    /**
     * Returns the string representation of the property.
     * @return the string representation of the property
     */
    @Override
    public String toString() {
        return String.format("Property{name='%s', value=%s, defaultValue=%s, min=%s, max=%s}", 
            name, value, defaultValue, minValue, maxValue);
    }

    /**
     * Gets the editor row of the property.
     * @return the editor row of the property
     */
    public UIRow getEditorRow() {
        if (editorRow != null) {
            return editorRow;
        }
        ArrayList<UIElement> elements;
        switch (typeName) {
            case INT: elements = createIntEditorElements(); break;
            case DOUBLE: elements = createDoubleEditorElements(); break;
            case FLOAT: elements = createFloatEditorElements(); break;
            case BOOLEAN: elements = createBooleanEditorElements(); break;
            case STRING: elements = createStringEditorElements(); break;
            case COLOR: elements = createColorEditorElements(); break;
            case VECTOR3F: elements = createVector3fEditorElements(); break;
            case DOUBLE_ARRAY: elements = createDoubleArrayEditorElements(); break;
            case SELECTOR: elements = createSelectorEditorElements(); break;
            default: elements = createStringEditorElements(); break;
        }
        editorRow = new UIRow(elements);
        return editorRow;
    }

    /**
     * Syncs the editor row from the value.
     */
    private void syncEditorRowFromValue() {
        if (editorRow == null) return;
        for (UIElement element : editorRow.getElements()) {
            element.update(value);

        }
    }

    /**
     * Creates the editor elements for an integer property.
     * @return the editor elements for an integer property
     */
    private ArrayList<UIElement> createIntEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf((Object) value));
        tf.setOnCommit(() -> {
            try {
                int parsed = Integer.parseInt(tf.getText().trim());
                Settings.getInstance().setValue(name, parsed);
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {
                revert(tf);
            }
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

    /**
     * Creates the editor elements for a double property.
     * @return the editor elements for a double property
     */
    private ArrayList<UIElement> createDoubleEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf((Object) value));
        tf.setNumericalRounding(numericalRounding);
        tf.setMinWidth(10);
        tf.setTextFromValue(value);
        tf.setOnCommit(() -> {
            try {
                double parsed = Double.parseDouble(tf.getText().trim());
                Settings.getInstance().setValue(name, parsed);
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {
                revert(tf);
            }
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

    /**
     * Creates the editor elements for a float property.
     * @return the editor elements for a float property
     */
    private ArrayList<UIElement> createFloatEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf((Object) value));
        tf.setNumericalRounding(numericalRounding);
        tf.setMinWidth(10);
        tf.setOnCommit(() -> {
            try {
                float parsed = Float.parseFloat(tf.getText().trim());
                Settings.getInstance().setValue(name, parsed);
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {
                revert(tf);
            }
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
    /**
     * Creates the editor elements for a boolean property.
     * Essentially a selector property with two options.
     * @return the editor elements for a boolean property
     */
    private ArrayList<UIElement> createBooleanEditorElements() {
        options = new String[]{"true", "false"};
        boolean initialState = (Boolean) Settings.getInstance().getValue(name);
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UIButton[] buttons = new UIButton[options.length];
        for (int i = 0; i < options.length; i++) {
            String option = options[i];
            UIButton button = new UIButton(option);
            buttons[i] = button;
        }
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            final String option = options[index];
            buttons[i].setOnClick(() -> {
                Settings.getInstance().setValue(name, option.equals("true") ? true : false);
                Settings.getInstance().saveSettings();
                for (int j = 0; j < options.length; j++) {
                    buttons[j].setSelected(j == index);
                }
            });
            buttons[i].setOnRelease(() -> {});
            buttons[i].setSelected(i == (initialState ? 0 : 1));
            elements.add(buttons[i]);
        }
        return elements;
    }

    /**
     * Creates the editor elements for a string property.
     * @return the editor elements for a string property
     */
    private ArrayList<UIElement> createStringEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf((Object) value));
        tf.setMinWidth(10);
        tf.setOnCommit(() -> {
            Settings.getInstance().setValue(name, tf.getText());
            Settings.getInstance().saveSettings();
        });
        elements.add(tf);
        return elements;
    }

    /**
     * Creates the editor elements for a selector property.
     * @return the editor elements for a selector property
     */
    private ArrayList<UIElement> createSelectorEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UISelector selector = new UISelector(name, options);
        elements.add(selector);
        return elements;
    }

    /**
     * Creates the editor elements for a color property.
     * @return the editor elements for a color property
     */
    private ArrayList<UIElement> createColorEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tfRed = new UITextField(String.valueOf(((Color) Settings.getInstance().getValue(name)).getRed()));
        tfRed.setOnCommit(() -> {
            try {
                int red = Integer.parseInt(tfRed.getText().trim());
                int green = ((Color) Settings.getInstance().getValue(name)).getGreen();
                int blue = ((Color) Settings.getInstance().getValue(name)).getBlue();
                Settings.getInstance().setValue(name, new Color(red, green, blue));
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {
                tfRed.setText(String.valueOf(((Color) Settings.getInstance().getValue(name)).getRed()));
            }
        });
        tfRed.setUpdateFunction(() -> {
            tfRed.setText(String.valueOf(((Color) Settings.getInstance().getValue(name)).getRed()));
        });

        UITextField tfGreen = new UITextField(String.valueOf(((Color) Settings.getInstance().getValue(name)).getGreen()));
        tfGreen.setOnCommit(() -> {
            try {
                int green = Integer.parseInt(tfGreen.getText().trim());
                int red = ((Color) Settings.getInstance().getValue(name)).getRed();
                int blue = ((Color) Settings.getInstance().getValue(name)).getBlue();
                Settings.getInstance().setValue(name, new Color(red, green, blue));
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {
                tfGreen.setText(String.valueOf(((Color) Settings.getInstance().getValue(name)).getGreen()));
            }
        });
        tfGreen.setUpdateFunction(() -> {
            tfGreen.setText(String.valueOf(((Color) Settings.getInstance().getValue(name)).getGreen()));
        });

        UITextField tfBlue = new UITextField(String.valueOf(((Color) Settings.getInstance().getValue(name)).getBlue()));
        tfBlue.setOnCommit(() -> {
            try {
                int blue = Integer.parseInt(tfBlue.getText().trim());
                int red = ((Color) Settings.getInstance().getValue(name)).getRed();
                int green = ((Color) Settings.getInstance().getValue(name)).getGreen();
                Settings.getInstance().setValue(name, new Color(red, green, blue));
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {
                tfBlue.setText(String.valueOf(((Color) Settings.getInstance().getValue(name)).getBlue()));
            }
        });
        tfBlue.setUpdateFunction(() -> {
            tfBlue.setText(String.valueOf(((Color) Settings.getInstance().getValue(name)).getBlue()));
        });
        elements.add(tfRed);
        elements.add(tfGreen);
        elements.add(tfBlue);
        System.out.println("Red: " + tfRed.getText() + " Green: " + tfGreen.getText() + " Blue: " + tfBlue.getText());
        return elements;
    }

    /**
     * Creates the editor elements for a vector3f property.
     * @return the editor elements for a vector3f property
     */
    private ArrayList<UIElement> createVector3fEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf((Object) value));
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
            } catch (Exception ignore) {
                revert(tf);
            }
        });
        elements.add(tf);
        return elements;
    }

    /**
     * Creates the editor elements for a double array property.
     * @return the editor elements for a double array property
     */
    private ArrayList<UIElement> createDoubleArrayEditorElements() {
        ArrayList<UIElement> elements = new ArrayList<>();
        elements.add(new UIText(name + ":"));
        UITextField tf = new UITextField(String.valueOf((Object) value));
        tf.setOnCommit(() -> {
            try {
                String[] parts = tf.getText().trim().split(",");
                double[] arr = new double[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    arr[i] = Double.parseDouble(parts[i].trim());
                }
                Settings.getInstance().setValue(name, arr);
                Settings.getInstance().saveSettings();
            } catch (Exception ignore) {
                revert(tf);
            }
        });
        elements.add(tf);
        return elements;
    }

    /**
     * Reverts the value of the property to the default value.
     * @param tf the text field to revert
     */
    private void revert(UITextField tf) {
        tf.setText(String.valueOf((Object) Settings.getInstance().getValue(name)));
    }
}
