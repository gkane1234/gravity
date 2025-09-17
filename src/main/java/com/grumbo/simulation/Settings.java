// ===== AUTO-GENERATED: Settings.java =====
package com.grumbo.simulation;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.joml.Vector3f;

/**
 * Settings - Class for storing and accessing the simulation settings as singleton objects.
 * This class is automatically generated from defaultProperties.json
 * Any changes made here will be overwritten when regenerating EXCEPT for the methods in the "preserved" section at the bottom of the file.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class Settings {

    private static Settings instance;

    // Property map to store all settings
    private Map<String, Property<?>> properties = new HashMap<>();

    private Settings() {
        initializeProperties();
        loadSettings();
    }

    /**
     * Gets the instance of the Settings class object for a given property.
     * @return the instance of the Settings class
     */
    public static Settings getInstance() {
        if (instance == null) {
            instance = new Settings();
        }
        return instance;
    }
    /**
     * Gets the names of all the properties in the Settings class.
     * @return the names of all the properties in the Settings class
     */
    public ArrayList<String> getPropertyNames() {
        return new ArrayList<>(properties.keySet());
    }

	/**
	 * Initializes the properties as read from defaultProperties.json.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
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
		{ Property<Float> p = Property.createFloatProperty("dt", 6f, 6f); p.setEditable(true); properties.put("dt", p); }

		// Softening parameter
		{ Property<Float> p = Property.createFloatProperty("softening", 0.001f, 0.001f); p.setEditable(true); properties.put("softening", p); }

		// Length of planet trails
		properties.put("tailLength", Property.createIntProperty("tailLength", 10, 10, 0, 1000, true));

		// Draw planet trails
		properties.put("drawTail", Property.createBooleanProperty("drawTail", false, false, true));

		// Planet density
		{ Property<Float> p = Property.createFloatProperty("density", 1f, 1f); p.setEditable(true); properties.put("density", p); }

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
		{ Property<Float> p = Property.createFloatProperty("farPlane", 1.0E10f, 1.0E10f); p.setEditable(true); properties.put("farPlane", p); }

		// Camera move speed
		{ Property<Float> p = Property.createFloatProperty("cameraMoveSpeed", 5.0f, 5.0f); p.setEditable(true); properties.put("cameraMoveSpeed", p); }

		// WASD movement sensitivity
		{ Property<Float> p = Property.createFloatProperty("WASDSensitivity", 10000f, 10000f); p.setEditable(true); properties.put("WASDSensitivity", p); }

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
		{ Property<Float> p = Property.createFloatProperty("theta", 0.6f, 0.6f); p.setEditable(true); properties.put("theta", p); }

		// Minimum and maximum depth for regions
		{ Property<Integer> p = Property.createIntProperty("minDepth", 0, 0); p.setEditable(true); properties.put("minDepth", p); }

		// Minimum and maximum depth for regions
		{ Property<Integer> p = Property.createIntProperty("maxDepth", 100, 100); p.setEditable(true); properties.put("maxDepth", p); }

		// Merge bodies
		properties.put("mergeBodies", Property.createBooleanProperty("mergeBodies", false, false, true));

		// Wrap around
		properties.put("wrapAround", Property.createBooleanProperty("wrapAround", false, false, true));

	}
	/**
	 * Gets the value of a given property.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	@SuppressWarnings("unchecked")
	public <T> T getValue(String propertyName) {
		Property<T> prop = (Property<T>) properties.get(propertyName);
		if (prop == null) {
			throw new IllegalArgumentException("Property not found: " + propertyName);
		}
		return prop.getValue();
	}

	/**
	 * Sets the value of a given property.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	@SuppressWarnings("unchecked")
	public <T> void setValue(String propertyName, T value) {
		Property<T> prop = (Property<T>) properties.get(propertyName);
		if (prop == null) {
			throw new IllegalArgumentException("Property not found: " + propertyName);
		}
		prop.setValue(value);
	}
	/**
	 * Gets the value of the property width.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getWidth() { return getValue("width"); }
	/**
	 * Sets the value of the property width.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setWidth(int value) { setValue("width", value); }

	/**
	 * Gets the value of the property height.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getHeight() { return getValue("height"); }
	/**
	 * Sets the value of the property height.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setHeight(int value) { setValue("height", value); }

	/**
	 * Gets the value of the property zoom.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public double getZoom() { return getValue("zoom"); }
	/**
	 * Sets the value of the property zoom.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setZoom(double value) { setValue("zoom", value); }

	/**
	 * Gets the value of the property follow.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public boolean isFollow() { return getValue("follow"); }
	/**
	 * Sets the value of the property follow.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setFollow(boolean value) { setValue("follow", value); }

	/**
	 * Gets the value of the property shift.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public double[] getShift() { return getValue("shift"); }
	/**
	 * Sets the value of the property shift.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setShift(double[] value) { setValue("shift", value); }

	/**
	 * Gets the value of the property dt.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getDt() { return getValue("dt"); }
	/**
	 * Sets the value of the property dt.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setDt(float value) { setValue("dt", value); }

	/**
	 * Gets the value of the property softening.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getSoftening() { return getValue("softening"); }
	/**
	 * Sets the value of the property softening.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setSoftening(float value) { setValue("softening", value); }

	/**
	 * Gets the value of the property tailLength.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getTailLength() { return getValue("tailLength"); }
	/**
	 * Sets the value of the property tailLength.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setTailLength(int value) { setValue("tailLength", value); }

	/**
	 * Gets the value of the property drawTail.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public boolean isDrawTail() { return getValue("drawTail"); }
	/**
	 * Sets the value of the property drawTail.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setDrawTail(boolean value) { setValue("drawTail", value); }

	/**
	 * Toggles the value of the property drawTail.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void toggleDrawTail() { setDrawTail(!isDrawTail()); }
	/**
	 * Gets the value of the property density.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getDensity() { return getValue("density"); }
	/**
	 * Sets the value of the property density.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setDensity(float value) { setValue("density", value); }

	/**
	 * Gets the value of the property elasticity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public double getElasticity() { return getValue("elasticity"); }
	/**
	 * Sets the value of the property elasticity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setElasticity(double value) { setValue("elasticity", value); }

	/**
	 * Gets the value of the property defaultPlanetColor.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public Color getDefaultPlanetColor() { return getValue("defaultPlanetColor"); }
	/**
	 * Sets the value of the property defaultPlanetColor.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setDefaultPlanetColor(Color value) { setValue("defaultPlanetColor", value); }

	/**
	 * Gets the value of the property defaultBackgroundColor.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public Color getDefaultBackgroundColor() { return getValue("defaultBackgroundColor"); }
	/**
	 * Sets the value of the property defaultBackgroundColor.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setDefaultBackgroundColor(Color value) { setValue("defaultBackgroundColor", value); }

	/**
	 * Gets the value of the property defaultTextColor.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public Color getDefaultTextColor() { return getValue("defaultTextColor"); }
	/**
	 * Sets the value of the property defaultTextColor.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setDefaultTextColor(Color value) { setValue("defaultTextColor", value); }

	/**
	 * Gets the value of the property sphereSegments.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getSphereSegments() { return getValue("sphereSegments"); }
	/**
	 * Sets the value of the property sphereSegments.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setSphereSegments(int value) { setValue("sphereSegments", value); }

	/**
	 * Gets the value of the property fov.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getFov() { return getValue("fov"); }
	/**
	 * Sets the value of the property fov.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setFov(float value) { setValue("fov", value); }

	/**
	 * Gets the value of the property nearPlane.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getNearPlane() { return getValue("nearPlane"); }
	/**
	 * Sets the value of the property nearPlane.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNearPlane(float value) { setValue("nearPlane", value); }

	/**
	 * Gets the value of the property farPlane.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getFarPlane() { return getValue("farPlane"); }
	/**
	 * Sets the value of the property farPlane.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setFarPlane(float value) { setValue("farPlane", value); }

	/**
	 * Gets the value of the property cameraMoveSpeed.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getCameraMoveSpeed() { return getValue("cameraMoveSpeed"); }
	/**
	 * Sets the value of the property cameraMoveSpeed.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setCameraMoveSpeed(float value) { setValue("cameraMoveSpeed", value); }

	/**
	 * Gets the value of the property WASDSensitivity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getWASDSensitivity() { return getValue("WASDSensitivity"); }
	/**
	 * Sets the value of the property WASDSensitivity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setWASDSensitivity(float value) { setValue("WASDSensitivity", value); }

	/**
	 * Gets the value of the property mouseWheelSensitivity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getMouseWheelSensitivity() { return getValue("mouseWheelSensitivity"); }
	/**
	 * Sets the value of the property mouseWheelSensitivity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setMouseWheelSensitivity(float value) { setValue("mouseWheelSensitivity", value); }

	/**
	 * Gets the value of the property mouseRotationSensitivity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getMouseRotationSensitivity() { return getValue("mouseRotationSensitivity"); }
	/**
	 * Sets the value of the property mouseRotationSensitivity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setMouseRotationSensitivity(float value) { setValue("mouseRotationSensitivity", value); }

	/**
	 * Gets the value of the property cameraPos.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public Vector3f getCameraPos() { return getValue("cameraPos"); }
	/**
	 * Sets the value of the property cameraPos.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setCameraPos(Vector3f value) { setValue("cameraPos", value); }

	/**
	 * Gets the value of the property cameraFront.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public Vector3f getCameraFront() { return getValue("cameraFront"); }
	/**
	 * Sets the value of the property cameraFront.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setCameraFront(Vector3f value) { setValue("cameraFront", value); }

	/**
	 * Gets the value of the property cameraUp.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public Vector3f getCameraUp() { return getValue("cameraUp"); }
	/**
	 * Sets the value of the property cameraUp.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setCameraUp(Vector3f value) { setValue("cameraUp", value); }

	/**
	 * Gets the value of the property yaw.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getYaw() { return getValue("yaw"); }
	/**
	 * Sets the value of the property yaw.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setYaw(float value) { setValue("yaw", value); }

	/**
	 * Gets the value of the property pitch.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getPitch() { return getValue("pitch"); }
	/**
	 * Sets the value of the property pitch.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setPitch(float value) { setValue("pitch", value); }

	/**
	 * Gets the value of the property theta.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getTheta() { return getValue("theta"); }
	/**
	 * Sets the value of the property theta.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setTheta(float value) { setValue("theta", value); }

	/**
	 * Gets the value of the property minDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getMinDepth() { return getValue("minDepth"); }
	/**
	 * Sets the value of the property minDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setMinDepth(int value) { setValue("minDepth", value); }

	/**
	 * Gets the value of the property maxDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getMaxDepth() { return getValue("maxDepth"); }
	/**
	 * Sets the value of the property maxDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setMaxDepth(int value) { setValue("maxDepth", value); }

	/**
	 * Gets the value of the property mergeBodies.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public boolean isMergeBodies() { return getValue("mergeBodies"); }
	/**
	 * Sets the value of the property mergeBodies.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setMergeBodies(boolean value) { setValue("mergeBodies", value); }

	/**
	 * Toggles the value of the property mergeBodies.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void toggleMergeBodies() { setMergeBodies(!isMergeBodies()); }
	/**
	 * Gets the value of the property wrapAround.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public boolean isWrapAround() { return getValue("wrapAround"); }
	/**
	 * Sets the value of the property wrapAround.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setWrapAround(boolean value) { setValue("wrapAround", value); }

	/**
	 * Toggles the value of the property wrapAround.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void toggleWrapAround() { setWrapAround(!isWrapAround()); }
	/**
	 * Adds a property to the Settings class.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public <T> void addProperty(String name, Property<T> property) {
		properties.put(name, property);
	}

	/**
	 * Gets a property from the Settings class.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public Property<?> getProperty(String name) {
		return properties.get(name);
	}
	/**
	 * Loads the settings file.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
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
	/**
	 * Loads the settings from the settings file.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
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
	
	/**
	 * Saves the settings to the settings file.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
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
		} catch (IOException e) {
			System.err.println("Failed to save settings: " + e.getMessage());
		}
	}
	
	// ===== PRESERVED METHODS =====
	// These methods are preserved across regenerations of the file

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


	// ===== END PRESERVED METHODS =====

}
