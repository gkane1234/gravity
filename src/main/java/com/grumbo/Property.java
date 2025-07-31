package com.grumbo;

import java.awt.Color;
import java.util.function.Predicate;

public class Property<T> {
    private String name;
    private T value;
    private T defaultValue;
    private T minValue;
    private T maxValue;
    private Predicate<T> validator;
    private boolean isNumeric;
    
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
        return new Property<>(name, value, defaultValue);
    }
    
    public static Property<Integer> createIntProperty(String name, int value, int defaultValue, int min, int max) {
        return new Property<>(name, value, defaultValue, min, max);
    }
    
    public static Property<Double> createDoubleProperty(String name, double value, double defaultValue) {
        return new Property<>(name, value, defaultValue);
    }
    
    public static Property<Double> createDoubleProperty(String name, double value, double defaultValue, double min, double max) {
        return new Property<>(name, value, defaultValue, min, max);
    }
    
    public static Property<Boolean> createBooleanProperty(String name, boolean value, boolean defaultValue) {
        return new Property<>(name, value, defaultValue);
    }
    
    public static Property<String> createStringProperty(String name, String value, String defaultValue) {
        return new Property<>(name, value, defaultValue);
    }
    
    public static Property<Color> createColorProperty(String name, Color value, Color defaultValue) {
        return new Property<>(name, value, defaultValue);
    }
    
    // Special method for Color properties that work with RGB integers
    public static Property<Color> createColorPropertyFromRGB(String name, int rgbValue, int defaultRGB) {
        Property<Color> prop = new Property<>(name, new Color(rgbValue), new Color(defaultRGB));
        // Add custom validator to ensure valid RGB values
        prop.validator = color -> color != null ;
        return prop;
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
}
