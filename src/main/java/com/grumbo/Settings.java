package com.grumbo;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.joml.Vector3f;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;


public class Settings {

    private static final String SETTINGS_FILE = getSettingsFile().getAbsolutePath();
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
    public ArrayList<String> getPropertyNames() {
        return new ArrayList<>(properties.keySet());
    }

	// ===== AUTO-GENERATED: Property Initialization =====
	// This section is automatically generated from defaultProperties.json
	// Any changes made here will be overwritten when regenerating
	private void initializeProperties() {
		// Window width in pixels
		{ Property<Integer> p = Property.createIntProperty("width", 1000, 1000); p.setEditable(true); properties.put("width", p); }

		// Window height in pixels
		{ Property<Integer> p = Property.createIntProperty("height", 1000, 1000); p.setEditable(true); properties.put("height", p); }

		// Camera zoom level
		{ Property<Double> p = Property.createDoubleProperty("zoom", 0.01, 0.01); p.setEditable(true); properties.put("zoom", p); }

		// Follow mode enabled
		properties.put("follow", Property.createBooleanProperty("follow", false, false, true));

		// Camera shift position
		{ Property<double[]> p = new Property<>("shift", new double[]{0.0, 0.0, 0.0}, new double[]{0.0, 0.0, 0.0}); p.setTypeName("doubleArray"); p.setEditable(true); properties.put("shift", p); }

		// Time step
		{ Property<Float> p = Property.createFloatProperty("dt", 0.001f, 0.001f); p.setEditable(true); properties.put("dt", p); }

		// Softening parameter
		{ Property<Float> p = Property.createFloatProperty("softening", 0.001f, 0.001f); p.setEditable(true); properties.put("softening", p); }

		// Length of planet trails
		properties.put("tailLength", Property.createIntProperty("tailLength", 10, 10, 0, 1000, true));

		// Draw planet trails
		properties.put("drawTail", Property.createBooleanProperty("drawTail", false, false, true));

		// Planet density
		{ Property<Float> p = Property.createFloatProperty("density", 0.5f, 0.5f); p.setEditable(true); properties.put("density", p); }

		// Collision elasticity
		properties.put("elasticity", Property.createDoubleProperty("elasticity", 1.0, 1.0, 0.0, 1.0, true));

		// Default planet color
		properties.put("defaultPlanetColor", Property.createColorPropertyFromRGB("defaultPlanetColor", 0x6dbdef, 0x6dbdef, true));

		// Default background color
		properties.put("defaultBackgroundColor", Property.createColorPropertyFromRGB("defaultBackgroundColor", 0x000000, 0x000000, true));

		// Default text color
		properties.put("defaultTextColor", Property.createColorPropertyFromRGB("defaultTextColor", 0xffffff, 0xffffff, true));

		// Number of segments for sphere rendering
		{ Property<Integer> p = Property.createIntProperty("sphereSegments", 12, 12); p.setEditable(true); properties.put("sphereSegments", p); }

		// Field of view for camera
		{ Property<Float> p = Property.createFloatProperty("fov", 45.0f, 45.0f); p.setEditable(true); properties.put("fov", p); }

		// Near plane for camera
		{ Property<Float> p = Property.createFloatProperty("nearPlane", 0.1f, 0.1f); p.setEditable(true); properties.put("nearPlane", p); }

		// Far plane for camera
		{ Property<Float> p = Property.createFloatProperty("farPlane", 1000000.0f, 1000000.0f); p.setEditable(true); properties.put("farPlane", p); }

		// Camera move speed
		{ Property<Float> p = Property.createFloatProperty("cameraMoveSpeed", 5.0f, 5.0f); p.setEditable(true); properties.put("cameraMoveSpeed", p); }

		// WASD movement sensitivity
		{ Property<Float> p = Property.createFloatProperty("WASDSensitivity", 10f, 10f); p.setEditable(true); properties.put("WASDSensitivity", p); }

		// Mouse wheel sensitivity
		{ Property<Float> p = Property.createFloatProperty("mouseWheelSensitivity", 20.0f, 20.0f); p.setEditable(true); properties.put("mouseWheelSensitivity", p); }

		// Mouse rotation sensitivity
		{ Property<Float> p = Property.createFloatProperty("mouseRotationSensitivity", 0.2f, 0.2f); p.setEditable(true); properties.put("mouseRotationSensitivity", p); }

		// Camera position
		properties.put("cameraPos", Property.createVector3fProperty("cameraPos", new Vector3f(0.0f, 0.0f, 100.0f), new Vector3f(0.0f, 0.0f, 100.0f), true));

		// Camera front vector
		properties.put("cameraFront", Property.createVector3fProperty("cameraFront", new Vector3f(0.0f, 0.0f, -1.0f), new Vector3f(0.0f, 0.0f, -1.0f), true));

		// Camera up vector
		properties.put("cameraUp", Property.createVector3fProperty("cameraUp", new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f), true));

		// Camera yaw
		{ Property<Float> p = Property.createFloatProperty("yaw", -90.0f, -90.0f); p.setEditable(true); properties.put("yaw", p); }

		// Camera pitch
		{ Property<Float> p = Property.createFloatProperty("pitch", 0.0f, 0.0f); p.setEditable(true); properties.put("pitch", p); }

		// Barnes-Hut acceptance criterion
		{ Property<Float> p = Property.createFloatProperty("theta",0.5f, 0.5f); p.setEditable(true); properties.put("theta", p); }

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

	public float getDt() { return getValue("dt"); }
	public void setDt(float value) { setValue("dt", value); }

	public float getSoftening() { return getValue("softening"); }
	public void setSoftening(float value) { setValue("softening", value); }

	public int getTailLength() { return getValue("tailLength"); }
	public void setTailLength(int value) { setValue("tailLength", value); }

	public boolean isDrawTail() { return getValue("drawTail"); }
	public void setDrawTail(boolean value) { setValue("drawTail", value); }

	public void toggleDrawTail() { setDrawTail(!isDrawTail()); }
	public float getDensity() { return getValue("density"); }
	public void setDensity(float value) { setValue("density", value); }

	public double getElasticity() { return getValue("elasticity"); }
	public void setElasticity(double value) { setValue("elasticity", value); }

	public Color getDefaultPlanetColor() { return getValue("defaultPlanetColor"); }
	public void setDefaultPlanetColor(Color value) { setValue("defaultPlanetColor", value); }

	public Color getDefaultBackgroundColor() { return getValue("defaultBackgroundColor"); }
	public void setDefaultBackgroundColor(Color value) { setValue("defaultBackgroundColor", value); }

	public Color getDefaultTextColor() { return getValue("defaultTextColor"); }
	public void setDefaultTextColor(Color value) { setValue("defaultTextColor", value); }

	public int getSphereSegments() { return getValue("sphereSegments"); }
	public void setSphereSegments(int value) { setValue("sphereSegments", value); }

	public float getFov() { return getValue("fov"); }
	public void setFov(float value) { setValue("fov", value); }

	public float getNearPlane() { return getValue("nearPlane"); }
	public void setNearPlane(float value) { setValue("nearPlane", value); }

	public float getFarPlane() { return getValue("farPlane"); }
	public void setFarPlane(float value) { setValue("farPlane", value); }

	public float getCameraMoveSpeed() { return getValue("cameraMoveSpeed"); }
	public void setCameraMoveSpeed(float value) { setValue("cameraMoveSpeed", value); }

	public float getWASDSensitivity() { return getValue("WASDSensitivity"); }
	public void setWASDSensitivity(float value) { setValue("WASDSensitivity", value); }

	public float getMouseWheelSensitivity() { return getValue("mouseWheelSensitivity"); }
	public void setMouseWheelSensitivity(float value) { setValue("mouseWheelSensitivity", value); }

	public float getMouseRotationSensitivity() { return getValue("mouseRotationSensitivity"); }
	public void setMouseRotationSensitivity(float value) { setValue("mouseRotationSensitivity", value); }

	public Vector3f getCameraPos() { return getValue("cameraPos"); }
	public void setCameraPos(Vector3f value) { setValue("cameraPos", value); }

	public Vector3f getCameraFront() { return getValue("cameraFront"); }
	public void setCameraFront(Vector3f value) { setValue("cameraFront", value); }

	public Vector3f getCameraUp() { return getValue("cameraUp"); }
	public void setCameraUp(Vector3f value) { setValue("cameraUp", value); }

	public float getYaw() { return getValue("yaw"); }
	public void setYaw(float value) { setValue("yaw", value); }

	public float getPitch() { return getValue("pitch"); }
	public void setPitch(float value) { setValue("pitch", value); }

	public float getTheta() { return getValue("theta"); }
	public void setTheta(float value) { setValue("theta", value); }

	// ===== END AUTO-GENERATED: Property-Specific Getter/Setter Methods =====

	// ===== FIXED: Non-Configurable Values =====
	// These methods return fixed values that are not configurable
	public int getNumThreads() { return 100; }
	// ===== END FIXED: Non-Configurable Values =====

	// ===== PRESERVED: Custom Convenience Methods =====
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

	// Reset all properties to their default values (kept across regenerations)
	public void restoreDefaults() {
		for (Property<?> prop : properties.values()) {
			prop.reset();
		}
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
	private static File getSettingsFile() {
		try {
			java.net.URL loc = Settings.class.getProtectionDomain().getCodeSource().getLocation();
			java.nio.file.Path p = java.nio.file.Paths.get(loc.toURI());
			java.nio.file.Path moduleRoot;
			if (java.nio.file.Files.isDirectory(p) && p.getFileName().toString().equals("classes") && p.getParent() != null && p.getParent().getFileName().toString().equals("target")) {
				moduleRoot = p.getParent().getParent();
			} else if (java.nio.file.Files.isRegularFile(p) && p.getParent() != null && p.getParent().getFileName().toString().equals("target")) {
				moduleRoot = p.getParent().getParent();
			} else {
				java.nio.file.Path q = p;
				java.nio.file.Path found = null;
				while (q != null) {
					if (java.nio.file.Files.exists(q.resolve("pom.xml"))) { found = q; break; }
					q = q.getParent();
				}
				moduleRoot = found != null ? found : java.nio.file.Paths.get(System.getProperty("user.dir"));
			}
			java.nio.file.Path settingsPath = moduleRoot.resolve("src/main/resources/settings.json");
			return settingsPath.toFile();
		} catch (Exception e) {
			java.nio.file.Path userDir = java.nio.file.Paths.get(System.getProperty("user.dir"));
			java.nio.file.Path candidate = userDir.resolve("gravitychunk/src/main/resources/settings.json");
			if (!java.nio.file.Files.exists(candidate.getParent())) {
				candidate = userDir.resolve("src/main/resources/settings.json");
			}
			return candidate.toFile();
		}
	}
	public void loadSettings() {
		File file = getSettingsFile();
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
						} else if (prop.getValue() instanceof Float) {
							// JSON deserializes numbers as Double, need to convert to Float
							if (value instanceof Number) {
								((Property<Float>)prop).setValue(((Number)value).floatValue());
							}
						} else if (prop.getValue() instanceof Vector3f) {
							if (value instanceof java.util.List) {
								java.util.List<?> list = (java.util.List<?>)value;
								float[] array = new float[list.size()];
								for (int i = 0; i < list.size(); i++) {
									array[i] = ((Number)list.get(i)).floatValue();
								}
								((Property<Vector3f>)prop).setValue(new Vector3f(array));
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
				
				System.out.println("Settings loaded from " + file.getAbsolutePath());
			} catch (IOException e) {
				System.err.println("Failed to load settings: " + e.getMessage());
				System.out.println("Using default settings");
			}
		} else {
			System.out.println("Settings file not found, using default settings and Generating new settings file");
			saveSettings();
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
			
			File file = getSettingsFile();
			File parent = file.getParentFile();
			if (parent != null) { parent.mkdirs(); }
			mapper.writerWithDefaultPrettyPrinter().writeValue(file, jsonData);
			System.out.println("Settings saved to " + file.getAbsolutePath());
		} catch (IOException e) {
			System.err.println("Failed to save settings: " + e.getMessage());
		}
	}
	// ===== END AUTO-GENERATED: Load/Save Methods =====
	
}
