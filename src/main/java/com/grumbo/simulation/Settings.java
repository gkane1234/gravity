// ===== AUTO-GENERATED: Settings.java =====
package com.grumbo.simulation;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
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
    private Map<String, Property<?>> properties = new LinkedHashMap<>();

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
		{ Property<Integer> p = Property.createIntProperty("width", 1000, 1000); p.setEditable(false); properties.put("width", p); }

		// Window height in pixels
		{ Property<Integer> p = Property.createIntProperty("height", 1000, 1000); p.setEditable(false); properties.put("height", p); }

		// Render mode
		properties.put("renderMode", Property.createSelectorProperty("renderMode", "impGlow", "impGlow", new String[]{"off", "points", "imp", "impGlow", "mesh"}, true));

		// Show regions
		properties.put("showRegions", Property.createBooleanProperty("showRegions", false, false, true));

		// Wrap around
		properties.put("wrapAround", Property.createBooleanProperty("wrapAround", false, false, true));

		// Collision merging or neither
		properties.put("mergingCollisionOrNeither", Property.createSelectorProperty("mergingCollisionOrNeither", "none", "none", new String[]{"none", "merge", "collision"}, true));

		// Simulation bounds
		properties.put("dynamic", Property.createSelectorProperty("dynamic", "static", "static", new String[]{"static", "dynamic"}, true));

		// Time step
		{ Property<Float> p = Property.createFloatProperty("dt", 1f, 1f); p.setEditable(true); properties.put("dt", p); }

		// Barnes-Hut acceptance criterion
		{ Property<Float> p = Property.createFloatProperty("theta", 0.6f, 0.6f); p.setEditable(true); properties.put("theta", p); }

		// Relative to
		{ Property<Integer> p = Property.createIntProperty("relativeTo", 0, 0); p.setEditable(true); properties.put("relativeTo", p); }

		// Camera scale
		{ Property<Float> p = Property.createFloatProperty("cameraScale", 10.0f, 10.0f); p.setEditable(true); properties.put("cameraScale", p); }

		// Field of view for camera
		{ Property<Float> p = Property.createFloatProperty("fov", 45.0f, 45.0f); p.setEditable(true); properties.put("fov", p); }

		// Softening parameter
		{ Property<Float> p = Property.createFloatProperty("softening", 1.0E-12f, 1.0E-12f); p.setEditable(true); properties.put("softening", p); }

		// Collision elasticity
		properties.put("elasticity", Property.createFloatProperty("elasticity", 1.0f, 1.0f, 0.0f,  1.0f, true));

		// Number of segments for sphere rendering
		{ Property<Integer> p = Property.createIntProperty("sphereSegments", 12, 12); p.setEditable(true); properties.put("sphereSegments", p); }

		// Minimum and maximum depth for regions
		{ Property<Integer> p = Property.createIntProperty("minDepth", 0, 0); p.setEditable(true); properties.put("minDepth", p); }

		// Minimum and maximum depth for regions
		{ Property<Integer> p = Property.createIntProperty("maxDepth", 100, 100); p.setEditable(true); properties.put("maxDepth", p); }

		// Near plane for camera
		{ Property<Float> p = Property.createFloatProperty("nearPlane", 1.0E-7f, 1.0E-7f); p.setEditable(true); properties.put("nearPlane", p); }

		// Far plane for camera
		{ Property<Float> p = Property.createFloatProperty("farPlane", 1.0E8f, 1.0E8f); p.setEditable(true); properties.put("farPlane", p); }

		// Camera move speed
		{ Property<Float> p = Property.createFloatProperty("cameraMoveSpeed", 1f, 1f); p.setEditable(true); properties.put("cameraMoveSpeed", p); }

		// WASD movement sensitivity
		{ Property<Float> p = Property.createFloatProperty("WASDSensitivity", 1f, 1f); p.setEditable(true); properties.put("WASDSensitivity", p); }

		// Mouse wheel sensitivity
		{ Property<Float> p = Property.createFloatProperty("mouseWheelSensitivity", 1f, 1f); p.setEditable(true); properties.put("mouseWheelSensitivity", p); }

		// Mouse rotation sensitivity
		{ Property<Float> p = Property.createFloatProperty("mouseRotationSensitivity", 0.2f, 0.2f); p.setEditable(true); properties.put("mouseRotationSensitivity", p); }

		// Camera shift position
		{ Property<double[]> p = new Property<>("shift", new double[]{0.0, 0.0, 0.0}, new double[]{0.0, 0.0, 0.0}); p.setTypeName("DOUBLE_ARRAY"); p.setEditable(true); properties.put("shift", p); }

		// Camera position
		properties.put("cameraPos", Property.createVector3fProperty("cameraPos", new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f(0.0f, 0.0f, 1.0f), true));

		// Camera front vector
		properties.put("cameraFront", Property.createVector3fProperty("cameraFront", new Vector3f(0.0f, 0.0f, -1.0f), new Vector3f(0.0f, 0.0f, -1.0f), true));

		// Camera up vector
		properties.put("cameraUp", Property.createVector3fProperty("cameraUp", new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f), true));

		// Camera yaw
		{ Property<Float> p = Property.createFloatProperty("yaw", -90.0f, -90.0f); p.setEditable(true); properties.put("yaw", p); }

		// Camera pitch
		{ Property<Float> p = Property.createFloatProperty("pitch", 0.0f, 0.0f); p.setEditable(true); properties.put("pitch", p); }

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
	 * Gets the selected index of a given selector property.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	@SuppressWarnings("unchecked")
	public int getSelectedIndex(String propertyName) {
		Property<String> prop = (Property<String>) properties.get(propertyName);
		if (prop == null) {
			throw new IllegalArgumentException("Property not found: " + propertyName);
		}
		return prop.getSelectedIndex();
	}
	/**
	 * Gets the value of theint property width.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getWidth() { return getValue("width"); }
	/**
	 * Sets the value of the int property width.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setWidth(int value) { setValue("width", value); }

	/**
	 * Gets the value of theint property height.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getHeight() { return getValue("height"); }
	/**
	 * Sets the value of the int property height.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setHeight(int value) { setValue("height", value); }

	/**
	 * Gets the value of theselector property renderMode.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public String getRenderMode() { return getValue("renderMode"); }
	/**
	 * Sets the value of the selector property renderMode.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setRenderMode(String value) { setValue("renderMode", value); }

	/**
	 * Gets the selected index of the selector property renderMode.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getSelectedIndexRenderMode() { return getSelectedIndex("renderMode"); }

	/**
	 * Gets the value of theboolean property showRegions.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public boolean isShowRegions() { return getValue("showRegions"); }
	/**
	 * Sets the value of the boolean property showRegions.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setShowRegions(boolean value) { setValue("showRegions", value); }

	/**
	 * Toggles the value of the boolean property showRegions.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void toggleShowRegions() { setShowRegions(!isShowRegions()); }
	/**
	 * Gets the value of theboolean property wrapAround.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public boolean isWrapAround() { return getValue("wrapAround"); }
	/**
	 * Sets the value of the boolean property wrapAround.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setWrapAround(boolean value) { setValue("wrapAround", value); }

	/**
	 * Toggles the value of the boolean property wrapAround.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void toggleWrapAround() { setWrapAround(!isWrapAround()); }
	/**
	 * Gets the value of theselector property mergingCollisionOrNeither.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public String getMergingCollisionOrNeither() { return getValue("mergingCollisionOrNeither"); }
	/**
	 * Sets the value of the selector property mergingCollisionOrNeither.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setMergingCollisionOrNeither(String value) { setValue("mergingCollisionOrNeither", value); }

	/**
	 * Gets the selected index of the selector property mergingCollisionOrNeither.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getSelectedIndexMergingCollisionOrNeither() { return getSelectedIndex("mergingCollisionOrNeither"); }

	/**
	 * Gets the value of theselector property dynamic.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public String getDynamic() { return getValue("dynamic"); }
	/**
	 * Sets the value of the selector property dynamic.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setDynamic(String value) { setValue("dynamic", value); }

	/**
	 * Gets the selected index of the selector property dynamic.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getSelectedIndexDynamic() { return getSelectedIndex("dynamic"); }

	/**
	 * Gets the value of thefloat property dt.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getDt() { return getValue("dt"); }
	/**
	 * Sets the value of the float property dt.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setDt(float value) { setValue("dt", value); }

	/**
	 * Gets the value of thefloat property theta.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getTheta() { return getValue("theta"); }
	/**
	 * Sets the value of the float property theta.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setTheta(float value) { setValue("theta", value); }

	/**
	 * Gets the value of theint property relativeTo.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getRelativeTo() { return getValue("relativeTo"); }
	/**
	 * Sets the value of the int property relativeTo.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setRelativeTo(int value) { setValue("relativeTo", value); }

	/**
	 * Gets the value of thefloat property cameraScale.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getCameraScale() { return getValue("cameraScale"); }
	/**
	 * Sets the value of the float property cameraScale.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setCameraScale(float value) { setValue("cameraScale", value); }

	/**
	 * Gets the value of thefloat property fov.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getFov() { return getValue("fov"); }
	/**
	 * Sets the value of the float property fov.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setFov(float value) { setValue("fov", value); }

	/**
	 * Gets the value of thefloat property softening.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getSoftening() { return getValue("softening"); }
	/**
	 * Sets the value of the float property softening.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setSoftening(float value) { setValue("softening", value); }

	/**
	 * Gets the value of thefloat property elasticity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getElasticity() { return getValue("elasticity"); }
	/**
	 * Sets the value of the float property elasticity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setElasticity(float value) { setValue("elasticity", value); }

	/**
	 * Gets the value of theint property sphereSegments.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getSphereSegments() { return getValue("sphereSegments"); }
	/**
	 * Sets the value of the int property sphereSegments.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setSphereSegments(int value) { setValue("sphereSegments", value); }

	/**
	 * Gets the value of theint property minDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getMinDepth() { return getValue("minDepth"); }
	/**
	 * Sets the value of the int property minDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setMinDepth(int value) { setValue("minDepth", value); }

	/**
	 * Gets the value of theint property maxDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getMaxDepth() { return getValue("maxDepth"); }
	/**
	 * Sets the value of the int property maxDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setMaxDepth(int value) { setValue("maxDepth", value); }

	/**
	 * Gets the value of thefloat property nearPlane.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getNearPlane() { return getValue("nearPlane"); }
	/**
	 * Sets the value of the float property nearPlane.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNearPlane(float value) { setValue("nearPlane", value); }

	/**
	 * Gets the value of thefloat property farPlane.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getFarPlane() { return getValue("farPlane"); }
	/**
	 * Sets the value of the float property farPlane.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setFarPlane(float value) { setValue("farPlane", value); }

	/**
	 * Gets the value of thefloat property cameraMoveSpeed.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getCameraMoveSpeed() { return getValue("cameraMoveSpeed"); }
	/**
	 * Sets the value of the float property cameraMoveSpeed.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setCameraMoveSpeed(float value) { setValue("cameraMoveSpeed", value); }

	/**
	 * Gets the value of thefloat property WASDSensitivity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getWASDSensitivity() { return getValue("WASDSensitivity"); }
	/**
	 * Sets the value of the float property WASDSensitivity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setWASDSensitivity(float value) { setValue("WASDSensitivity", value); }

	/**
	 * Gets the value of thefloat property mouseWheelSensitivity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getMouseWheelSensitivity() { return getValue("mouseWheelSensitivity"); }
	/**
	 * Sets the value of the float property mouseWheelSensitivity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setMouseWheelSensitivity(float value) { setValue("mouseWheelSensitivity", value); }

	/**
	 * Gets the value of thefloat property mouseRotationSensitivity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getMouseRotationSensitivity() { return getValue("mouseRotationSensitivity"); }
	/**
	 * Sets the value of the float property mouseRotationSensitivity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setMouseRotationSensitivity(float value) { setValue("mouseRotationSensitivity", value); }

	/**
	 * Gets the value of thedoubleArray property shift.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public double[] getShift() { return getValue("shift"); }
	/**
	 * Sets the value of the doubleArray property shift.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setShift(double[] value) { setValue("shift", value); }

	/**
	 * Gets the value of thevector3f property cameraPos.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public Vector3f getCameraPos() { return getValue("cameraPos"); }
	/**
	 * Sets the value of the vector3f property cameraPos.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setCameraPos(Vector3f value) { setValue("cameraPos", value); }

	/**
	 * Gets the value of thevector3f property cameraFront.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public Vector3f getCameraFront() { return getValue("cameraFront"); }
	/**
	 * Sets the value of the vector3f property cameraFront.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setCameraFront(Vector3f value) { setValue("cameraFront", value); }

	/**
	 * Gets the value of thevector3f property cameraUp.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public Vector3f getCameraUp() { return getValue("cameraUp"); }
	/**
	 * Sets the value of the vector3f property cameraUp.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setCameraUp(Vector3f value) { setValue("cameraUp", value); }

	/**
	 * Gets the value of thefloat property yaw.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getYaw() { return getValue("yaw"); }
	/**
	 * Sets the value of the float property yaw.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setYaw(float value) { setValue("yaw", value); }

	/**
	 * Gets the value of thefloat property pitch.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getPitch() { return getValue("pitch"); }
	/**
	 * Sets the value of the float property pitch.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setPitch(float value) { setValue("pitch", value); }

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
			java.nio.file.Path settingsPath = moduleRoot.resolve("src/main/resources/settings/settings.json");
			return settingsPath.toFile();
		} catch (Exception e) {
			java.nio.file.Path userDir = java.nio.file.Paths.get(System.getProperty("user.dir"));
			java.nio.file.Path candidate = userDir.resolve("gravitychunk/src/main/resources/settings/settings.json");
			if (!java.nio.file.Files.exists(candidate.getParent())) {
				candidate = userDir.resolve("src/main/resources/settings/settings.json");
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
			Map<String, Object> jsonData = new LinkedHashMap<>();
			
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
