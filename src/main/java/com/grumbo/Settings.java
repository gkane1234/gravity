package com.grumbo;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Settings {

    private static final String SETTINGS_FILE = "settings.json";
    private static Settings instance;

    // Property map to store all settings
    private Map<String, Property<?>> properties = new HashMap<>();

    private Settings() {
        initializeProperties();
        loadSettings();
    }

    public static Settings getInstance() {
        if (instance == null) {
            instance = new Settings();
        }
        return instance;
    }

	// ===== AUTO-GENERATED: Property Initialization =====
	// This section is automatically generated from defaultProperties.json
	// Any changes made here will be overwritten when regenerating
	private void initializeProperties() {
		// Window width in pixels
		properties.put("width", Property.createIntProperty("width", 1000, 1000, 100, 3000));

		// Window height in pixels
		properties.put("height", Property.createIntProperty("height", 1000, 1000, 100, 3000));

		// Camera zoom level
		properties.put("zoom", Property.createDoubleProperty("zoom", 0.01, 0.01, 0.001000, 1.000000));

		// Follow mode enabled
		properties.put("follow", Property.createBooleanProperty("follow", false, false));

		// Camera shift position
		properties.put("shift", new Property<>("shift", new double[]{0.0, 0.0, -100.0}, new double[]{0.0, 0.0, -100.0}));

		// Size of physics chunks
		properties.put("chunkSize", Property.createDoubleProperty("chunkSize", 100, 10, 1.000000, 100000000.000000));

		// Length of planet trails
		properties.put("tailLength", Property.createIntProperty("tailLength", 10, 10, 0, 1000));

		// Draw planet trails
		properties.put("drawTail", Property.createBooleanProperty("drawTail", false, false));

		// Planet density
		properties.put("density", Property.createDoubleProperty("density", 0.01, 0.01, 0.001000, 1.000000));

		// Force exponent
		properties.put("expo", Property.createDoubleProperty("expo", -2.0, -2.0, -10.000000, 10.000000));

		// Collision elasticity
		properties.put("elasticity", Property.createDoubleProperty("elasticity", 1.0, 1.0, 0.000000, 2.000000));

		// Default planet color
		properties.put("defaultPlanetColor", Property.createColorPropertyFromRGB("defaultPlanetColor", 0x6dbdef, 0x6dbdef));

		// Default background color
		properties.put("defaultBackgroundColor", Property.createColorPropertyFromRGB("defaultBackgroundColor", 0x000000, 0x000000));

		// Default text color
		properties.put("defaultTextColor", Property.createColorPropertyFromRGB("defaultTextColor", 0xffffff, 0xffffff));

		// Minimum mass for attract by center of mass
		properties.put("minMassForAttractByCenterOfMass", Property.createDoubleProperty("minMassForAttractByCenterOfMass", 100, 100, 0.000000, 179769313486231570000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000.000000));

		// Maximum distance for full interaction
		properties.put("maxDistanceForFullInteraction", Property.createIntProperty("maxDistanceForFullInteraction", 1, 1, -2147483648, 2147483647));

		// Tick size
		properties.put("tickSize", Property.createDoubleProperty("tickSize", 0.1, 0.1, 0.000000, 179769313486231570000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000.000000));

	}
	// ===== END AUTO-GENERATED: Property Initialization =====

	// ===== AUTO-GENERATED: Generic Getter/Setter Methods =====
	// These methods provide type-safe access to properties
	// Any changes made here will be overwritten when regenerating
	@SuppressWarnings("unchecked")
	public <T> T getValue(String propertyName) {
		Property<T> prop = (Property<T>) properties.get(propertyName);
		if (prop == null) {
			throw new IllegalArgumentException("Property not found: " + propertyName);
		}
		return prop.getValue();
	}

	@SuppressWarnings("unchecked")
	public <T> void setValue(String propertyName, T value) {
		Property<T> prop = (Property<T>) properties.get(propertyName);
		if (prop == null) {
			throw new IllegalArgumentException("Property not found: " + propertyName);
		}
		prop.setValue(value);
	}
	// ===== END AUTO-GENERATED: Generic Getter/Setter Methods =====

	// ===== AUTO-GENERATED: Property-Specific Getter/Setter Methods =====
	// These methods are automatically generated for each property in defaultProperties.json
	// Any changes made here will be overwritten when regenerating
	public int getWidth() { return getValue("width"); }
	public void setWidth(int value) { setValue("width", value); }

	public int getHeight() { return getValue("height"); }
	public void setHeight(int value) { setValue("height", value); }

	public double getZoom() { return getValue("zoom"); }
	public void setZoom(double value) { setValue("zoom", value); }

	public boolean isFollow() { return getValue("follow"); }
	public void setFollow(boolean value) { setValue("follow", value); }

	public double[] getShift() { return getValue("shift"); }
	public void setShift(double[] value) { setValue("shift", value); }

	public double getChunkSize() { return getValue("chunkSize"); }
	public void setChunkSize(double value) { setValue("chunkSize", value); }

	public int getTailLength() { return getValue("tailLength"); }
	public void setTailLength(int value) { setValue("tailLength", value); }

	public boolean isDrawTail() { return getValue("drawTail"); }
	public void setDrawTail(boolean value) { setValue("drawTail", value); }

	public void toggleDrawTail() { setDrawTail(!isDrawTail()); }
	public double getDensity() { return getValue("density"); }
	public void setDensity(double value) { setValue("density", value); }

	public double getExpo() { return getValue("expo"); }
	public void setExpo(double value) { setValue("expo", value); }

	public double getElasticity() { return getValue("elasticity"); }
	public void setElasticity(double value) { setValue("elasticity", value); }

	public Color getDefaultPlanetColor() { return getValue("defaultPlanetColor"); }
	public void setDefaultPlanetColor(Color value) { setValue("defaultPlanetColor", value); }

	public Color getDefaultBackgroundColor() { return getValue("defaultBackgroundColor"); }
	public void setDefaultBackgroundColor(Color value) { setValue("defaultBackgroundColor", value); }

	public Color getDefaultTextColor() { return getValue("defaultTextColor"); }
	public void setDefaultTextColor(Color value) { setValue("defaultTextColor", value); }

	public double getMinMassForAttractByCenterOfMass() { return getValue("minMassForAttractByCenterOfMass"); }
	public void setMinMassForAttractByCenterOfMass(double value) { setValue("minMassForAttractByCenterOfMass", value); }

	public int getMaxDistanceForFullInteraction() { return getValue("maxDistanceForFullInteraction"); }
	public void setMaxDistanceForFullInteraction(int value) { setValue("maxDistanceForFullInteraction", value); }

	public double getTickSize() { return getValue("tickSize"); }
	public void setTickSize(double value) { setValue("tickSize", value); }

	// ===== END AUTO-GENERATED: Property-Specific Getter/Setter Methods =====

	// ===== FIXED: Non-Configurable Values =====
	// These methods return fixed values that are not configurable
	public int getNumThreads() { return 100; }
	// ===== END FIXED: Non-Configurable Values =====

	// ===== PRESERVED: Custom Convenience Methods =====
	// These methods are preserved from the previous generation
	// You can modify these methods and they will be preserved
	// These methods are preserved from the previous generation
	// You can modify these methods and they will be preserved
	// These methods are preserved from the previous generation
	// You can modify these methods and they will be preserved
		// These methods are preserved from the previous generation
		// You can modify these methods and they will be preserved
		// You can also override the methods in the auto-generated section by creating them
		// with the same name

	public void changeZoom(double newZoom) {
		setZoom(newZoom);
		saveSettings();
	}

	public void toggleFollow() {
		setFollow(!isFollow());
		setShift(new double[] {0, 0, 0});
		saveSettings();
	}

	public void moveCamera(double[] ds) {
		double[] currentShift = getShift();
		setShift(new double[] {
			currentShift[0] + ds[0],
			currentShift[1] + ds[1],
			currentShift[2] + ds[2]
		});
		saveSettings();
	}


	// ===== END PRESERVED: Custom Convenience Methods =====

	// ===== AUTO-GENERATED: Utility Methods =====
	// These utility methods are always included
	// Any changes made here will be overwritten when regenerating
	public <T> void addProperty(String name, Property<T> property) {
		properties.put(name, property);
	}

	public Property<?> getProperty(String name) {
		return properties.get(name);
	}
	// ===== END AUTO-GENERATED: Utility Methods =====

	// ===== AUTO-GENERATED: Load/Save Methods =====
	// These methods handle JSON serialization/deserialization
	// Any changes made here will be overwritten when regenerating
	public void loadSettings() {
		File file = new File(SETTINGS_FILE);
		if (file.exists()) {
			try {
				ObjectMapper mapper = new ObjectMapper();
				Map<String, Object> jsonData = mapper.readValue(file, Map.class);
				
				for (Map.Entry<String, Object> entry : jsonData.entrySet()) {
					String key = entry.getKey();
					Object value = entry.getValue();
					
					if (properties.containsKey(key)) {
						Property<?> prop = properties.get(key);
						
						// Handle different types appropriately
						if (prop.getValue() instanceof Color) {
							// Colors are stored as RGB integers
							if (value instanceof Integer) {
								((Property<Color>)prop).setRGBValue((Integer)value);
							}
						} else if (prop.getValue() instanceof double[]) {
							// Arrays need special handling
							if (value instanceof java.util.List) {
								java.util.List<?> list = (java.util.List<?>)value;
								double[] array = new double[list.size()];
								for (int i = 0; i < list.size(); i++) {
									array[i] = ((Number)list.get(i)).doubleValue();
								}
								((Property<double[]>)prop).setValue(array);
							}
						} else {
							// For other types, try to set directly
							try {
								((Property<Object>)prop).setValue(value);
							} catch (Exception e) {
								System.err.println("Failed to load property " + key + ": " + e.getMessage());
							}
						}
					}
				}
				
				System.out.println("Settings loaded from " + SETTINGS_FILE);
			} catch (IOException e) {
				System.err.println("Failed to load settings: " + e.getMessage());
				System.out.println("Using default settings");
			}
		} else {
			System.out.println("Settings file not found, using default settings");
		}
	}
	
	public void saveSettings() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			Map<String, Object> jsonData = new HashMap<>();
			
			for (Map.Entry<String, Property<?>> entry : properties.entrySet()) {
				String key = entry.getKey();
				Property<?> prop = entry.getValue();
				
				// Handle different types appropriately
				if (prop.getValue() instanceof Color) {
					jsonData.put(key, ((Property<Color>)prop).getRGBValue());
				} else {
					jsonData.put(key, prop.getValue());
				}
			}
			
			mapper.writerWithDefaultPrettyPrinter().writeValue(new File(SETTINGS_FILE), jsonData);
			System.out.println("Settings saved to " + SETTINGS_FILE);
		} catch (IOException e) {
			System.err.println("Failed to save settings: " + e.getMessage());
		}
	}
	// ===== END AUTO-GENERATED: Load/Save Methods =====
	
}
