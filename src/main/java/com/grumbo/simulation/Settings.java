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
		properties.put("renderMode", Property.createSelectorProperty("renderMode", "impNodeGlow", "impNodeGlow", new String[]{"off", "points", "imp", "impGlow", "impNodeGlow", "mesh"}, true));

		// Max view-space depth (-view.z after cameraScale) at which individual body impostors (imp / impGlow / impNodeGlow body pass) are drawn. Farther bodies are skipped; hierarchical node glow can still show distant structure. Higher = bodies visible farther. Lower = only nearby bodies.
		{ Property<Float> p = Property.createFloatProperty("bodyRenderDistance", 1000.0f, 1000.0f); p.setEditable(true); properties.put("bodyRenderDistance", p); }

		// Show regions
		properties.put("showRegions", Property.createBooleanProperty("showRegions", false, false, true));

		// Wrap around
		properties.put("wrapAround", Property.createBooleanProperty("wrapAround", false, false, true));

		// Collision merging or neither
		properties.put("mergingCollisionOrNeither", Property.createSelectorProperty("mergingCollisionOrNeither", "none", "none", new String[]{"none", "merge", "collision"}, true));

		// Simulation bounds
		properties.put("dynamic", Property.createSelectorProperty("dynamic", "dynamic", "dynamic", new String[]{"static", "dynamic"}, true));

		// Time step
		{ Property<Float> p = Property.createFloatProperty("dt", 100f, 100f); p.setEditable(true); properties.put("dt", p); }

		// Barnes-Hut acceptance criterion
		{ Property<Float> p = Property.createFloatProperty("theta", 0.77f, 0.77f); p.setEditable(true); properties.put("theta", p); }

		// Relative to
		{ Property<Integer> p = Property.createIntProperty("relativeTo", -1, -1); p.setEditable(true); properties.put("relativeTo", p); }

		// Camera scale
		{ Property<Float> p = Property.createFloatProperty("cameraScale", 10.0f, 10.0f); p.setEditable(true); properties.put("cameraScale", p); }

		// Minimum impostor radius in NDC
		{ Property<Float> p = Property.createFloatProperty("minImpostorSize", 1.0E-5f, 1.0E-5f); p.setEditable(true); properties.put("minImpostorSize", p); }

		// Field of view for camera
		{ Property<Float> p = Property.createFloatProperty("fov", 70f, 70f); p.setEditable(true); properties.put("fov", p); }

		// Body mass unit
		{ Property<Float> p = Property.createFloatProperty("mass", 1.0f, 1.0f); p.setEditable(true); properties.put("mass", p); }

		// Body density unit
		{ Property<Float> p = Property.createFloatProperty("density", 1.0f, 1.0f); p.setEditable(true); properties.put("density", p); }

		// Simulation length unit
		{ Property<Float> p = Property.createFloatProperty("length", 1.0f, 1.0f); p.setEditable(true); properties.put("length", p); }

		// Simulation time unit
		{ Property<Float> p = Property.createFloatProperty("time", 1.0f, 1.0f); p.setEditable(true); properties.put("time", p); }

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
		{ Property<Float> p = Property.createFloatProperty("nearPlane", 0.1f, 0.1f); p.setEditable(true); properties.put("nearPlane", p); }

		// Far plane for camera
		{ Property<Float> p = Property.createFloatProperty("farPlane", 1.0E8f, 1.0E8f); p.setEditable(true); properties.put("farPlane", p); }

		// Camera move speed
		{ Property<Float> p = Property.createFloatProperty("cameraMoveSpeed", 1f, 1f); p.setEditable(true); properties.put("cameraMoveSpeed", p); }

		// WASD movement sensitivity
		{ Property<Float> p = Property.createFloatProperty("WASDSensitivity", 100f, 100f); p.setEditable(true); properties.put("WASDSensitivity", p); }

		// Mouse wheel sensitivity
		{ Property<Float> p = Property.createFloatProperty("mouseWheelSensitivity", 0.1f, 0.1f); p.setEditable(true); properties.put("mouseWheelSensitivity", p); }

		// Mouse rotation sensitivity
		{ Property<Float> p = Property.createFloatProperty("mouseRotationSensitivity", 0.04f, 0.04f); p.setEditable(true); properties.put("mouseRotationSensitivity", p); }

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

		// impNodeGlow structure LOD: projected BH AABB size in screen pixels. Glow only for clusters smaller than this (accept band). Higher = glow larger/closer clusters. Lower = wait until clusters are tinier on screen.
		{ Property<Float> p = Property.createFloatProperty("nodeGlowThetaPx", 16.0f, 16.0f); p.setEditable(true); properties.put("nodeGlowThetaPx", p); }

		// impNodeGlow distance gate: view-space depth (-view.z after cameraScale). Hierarchical glow only when the node is farther than this. Higher = glow only farther away. Lower (0) = no near gate. Applies to both rectangles and circles.
		{ Property<Float> p = Property.createFloatProperty("nodeGlowActivateDist", 0.0f, 0.0f); p.setEditable(true); properties.put("nodeGlowActivateDist", p); }

		// Square renderer near cutoff: view-space depth (-view.z after cameraScale), same units as nodeGlowActivateDist. Rectangles are drawn only farther than this. 0 = no extra near cutoff. Circles ignore it.
		{ Property<Float> p = Property.createFloatProperty("nodeGlowRectMinDist", 0.0f, 0.0f); p.setEditable(true); properties.put("nodeGlowRectMinDist", p); }

		// Rectangle glow floor: min post-propagate nodeDepth (height above furthest leaf). Leaves are 0, the root is tall. Rects draw for nodeGlowMinDepth <= nodeDepth <= nodeGlowMaxDepth.
		{ Property<Integer> p = Property.createIntProperty("nodeGlowMinDepth", 1, 1); p.setEditable(true); properties.put("nodeGlowMinDepth", p); }

		// Rectangle glow ceiling: max post-propagate nodeDepth (height above furthest leaf). Rects draw for nodeGlowMinDepth <= nodeDepth <= this.
		{ Property<Integer> p = Property.createIntProperty("nodeGlowMaxDepth", 8, 8); p.setEditable(true); properties.put("nodeGlowMaxDepth", p); }

		// Circle glow floor: min post-propagate nodeDepth. Circles draw for nodeGlowCircleMinDepth <= nodeDepth <= nodeGlowCircleMaxDepth.
		{ Property<Integer> p = Property.createIntProperty("nodeGlowCircleMinDepth", 1, 1); p.setEditable(true); properties.put("nodeGlowCircleMinDepth", p); }

		// Circle glow ceiling: max post-propagate nodeDepth. Circles draw for nodeGlowCircleMinDepth <= nodeDepth <= this.
		{ Property<Integer> p = Property.createIntProperty("nodeGlowCircleMaxDepth", 8, 8); p.setEditable(true); properties.put("nodeGlowCircleMaxDepth", p); }

		// Draw the rectangle node glow. Off skips that pass.
		properties.put("nodeGlowDrawRect", Property.createBooleanProperty("nodeGlowDrawRect", true, true, true));

		// Draw the circle node glow. Off skips that pass. A node inside both depth ranges is drawn twice.
		properties.put("nodeGlowDrawCircle", Property.createBooleanProperty("nodeGlowDrawCircle", false, false, true));

		// impNodeGlow brightness scale applied to hierarchical node-glow surface intensity (before Reinhard). Match body impGlow without crushing substructure. Lower = dimmer haze. Higher = brighter fills.
		{ Property<Float> p = Property.createFloatProperty("nodeGlowIntensity", 0.003f, 0.003f); p.setEditable(true); properties.put("nodeGlowIntensity", p); }

		// Kept so older settings files still load. The draw booleans choose which passes run.
		properties.put("nodeGlowShape", Property.createSelectorProperty("nodeGlowShape", "rect", "rect", new String[]{"rect", "circle"}, false));

		// impNodeGlow rect size relative to the normal square (longest side, or the projected union when project box is on). 1 = that size. 1.2 = 20% longer on each side. Circle mode ignores this.
		{ Property<Float> p = Property.createFloatProperty("nodeGlowRectScale", 1.0f, 1.0f); p.setEditable(true); properties.put("nodeGlowRectScale", p); }

		// impNodeGlow option 1: keep the longest-side square and grow it to cover any of the projected AABB that sticks past it, plus a 1px overlap. Off = the square only. The projected rect alone is smaller than the square when the long axis points at the camera, which opens gaps.
		properties.put("nodeGlowProjectBox", Property.createBooleanProperty("nodeGlowProjectBox", false, false, true));

		// impNodeGlow option 2: write gl_FragDepth at the box center. Off = the quad's interpolated depth. Depth mask stays off either way.
		properties.put("nodeGlowWriteDepth", Property.createBooleanProperty("nodeGlowWriteDepth", false, false, true));

		// impNodeGlow option 3: draw only the coarsest node under theta on each branch. Off = every node in the size band (several generations).
		properties.put("nodeGlowOneLevel", Property.createBooleanProperty("nodeGlowOneLevel", true, true, true));

		// impNodeGlow fine edge of the size band, as a fraction of nodeGlowThetaPx. 0.04 draws nodes down to 4% of theta. 0.5 keeps only the octave just under theta (can hide the glow if those nodes are past nodeGlowMaxDepth).
		{ Property<Float> p = Property.createFloatProperty("nodeGlowMinFrac", 0.04f, 0.04f); p.setEditable(true); properties.put("nodeGlowMinFrac", p); }

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
	 * Gets the value of thefloat property bodyRenderDistance.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getBodyRenderDistance() { return getValue("bodyRenderDistance"); }
	/**
	 * Sets the value of the float property bodyRenderDistance.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setBodyRenderDistance(float value) { setValue("bodyRenderDistance", value); }

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
	 * Gets the value of thefloat property minImpostorSize.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getMinImpostorSize() { return getValue("minImpostorSize"); }
	/**
	 * Sets the value of the float property minImpostorSize.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setMinImpostorSize(float value) { setValue("minImpostorSize", value); }

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
	 * Gets the value of thefloat property mass.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getMass() { return getValue("mass"); }
	/**
	 * Sets the value of the float property mass.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setMass(float value) { setValue("mass", value); }

	/**
	 * Gets the value of thefloat property density.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getDensity() { return getValue("density"); }
	/**
	 * Sets the value of the float property density.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setDensity(float value) { setValue("density", value); }

	/**
	 * Gets the value of thefloat property length.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getLength() { return getValue("length"); }
	/**
	 * Sets the value of the float property length.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setLength(float value) { setValue("length", value); }

	/**
	 * Gets the value of thefloat property time.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getTime() { return getValue("time"); }
	/**
	 * Sets the value of the float property time.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setTime(float value) { setValue("time", value); }

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
	 * Gets the value of thefloat property nodeGlowThetaPx.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getNodeGlowThetaPx() { return getValue("nodeGlowThetaPx"); }
	/**
	 * Sets the value of the float property nodeGlowThetaPx.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowThetaPx(float value) { setValue("nodeGlowThetaPx", value); }

	/**
	 * Gets the value of thefloat property nodeGlowActivateDist.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getNodeGlowActivateDist() { return getValue("nodeGlowActivateDist"); }
	/**
	 * Sets the value of the float property nodeGlowActivateDist.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowActivateDist(float value) { setValue("nodeGlowActivateDist", value); }

	/**
	 * Gets the value of thefloat property nodeGlowRectMinDist.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getNodeGlowRectMinDist() { return getValue("nodeGlowRectMinDist"); }
	/**
	 * Sets the value of the float property nodeGlowRectMinDist.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowRectMinDist(float value) { setValue("nodeGlowRectMinDist", value); }

	/**
	 * Gets the value of theint property nodeGlowMinDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getNodeGlowMinDepth() { return getValue("nodeGlowMinDepth"); }
	/**
	 * Sets the value of the int property nodeGlowMinDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowMinDepth(int value) { setValue("nodeGlowMinDepth", value); }

	/**
	 * Gets the value of theint property nodeGlowMaxDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getNodeGlowMaxDepth() { return getValue("nodeGlowMaxDepth"); }
	/**
	 * Sets the value of the int property nodeGlowMaxDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowMaxDepth(int value) { setValue("nodeGlowMaxDepth", value); }

	/**
	 * Gets the value of theint property nodeGlowCircleMinDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getNodeGlowCircleMinDepth() { return getValue("nodeGlowCircleMinDepth"); }
	/**
	 * Sets the value of the int property nodeGlowCircleMinDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowCircleMinDepth(int value) { setValue("nodeGlowCircleMinDepth", value); }

	/**
	 * Gets the value of theint property nodeGlowCircleMaxDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getNodeGlowCircleMaxDepth() { return getValue("nodeGlowCircleMaxDepth"); }
	/**
	 * Sets the value of the int property nodeGlowCircleMaxDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowCircleMaxDepth(int value) { setValue("nodeGlowCircleMaxDepth", value); }

	/**
	 * Gets the value of theboolean property nodeGlowDrawRect.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public boolean isNodeGlowDrawRect() { return getValue("nodeGlowDrawRect"); }
	/**
	 * Sets the value of the boolean property nodeGlowDrawRect.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowDrawRect(boolean value) { setValue("nodeGlowDrawRect", value); }

	/**
	 * Toggles the value of the boolean property nodeGlowDrawRect.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void toggleNodeGlowDrawRect() { setNodeGlowDrawRect(!isNodeGlowDrawRect()); }
	/**
	 * Gets the value of theboolean property nodeGlowDrawCircle.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public boolean isNodeGlowDrawCircle() { return getValue("nodeGlowDrawCircle"); }
	/**
	 * Sets the value of the boolean property nodeGlowDrawCircle.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowDrawCircle(boolean value) { setValue("nodeGlowDrawCircle", value); }

	/**
	 * Toggles the value of the boolean property nodeGlowDrawCircle.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void toggleNodeGlowDrawCircle() { setNodeGlowDrawCircle(!isNodeGlowDrawCircle()); }
	/**
	 * Gets the value of thefloat property nodeGlowIntensity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getNodeGlowIntensity() { return getValue("nodeGlowIntensity"); }
	/**
	 * Sets the value of the float property nodeGlowIntensity.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowIntensity(float value) { setValue("nodeGlowIntensity", value); }

	/**
	 * Gets the value of theselector property nodeGlowShape.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public String getNodeGlowShape() { return getValue("nodeGlowShape"); }
	/**
	 * Sets the value of the selector property nodeGlowShape.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowShape(String value) { setValue("nodeGlowShape", value); }

	/**
	 * Gets the selected index of the selector property nodeGlowShape.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public int getSelectedIndexNodeGlowShape() { return getSelectedIndex("nodeGlowShape"); }

	/**
	 * Gets the value of thefloat property nodeGlowRectScale.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getNodeGlowRectScale() { return getValue("nodeGlowRectScale"); }
	/**
	 * Sets the value of the float property nodeGlowRectScale.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowRectScale(float value) { setValue("nodeGlowRectScale", value); }

	/**
	 * Gets the value of theboolean property nodeGlowProjectBox.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public boolean isNodeGlowProjectBox() { return getValue("nodeGlowProjectBox"); }
	/**
	 * Sets the value of the boolean property nodeGlowProjectBox.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowProjectBox(boolean value) { setValue("nodeGlowProjectBox", value); }

	/**
	 * Toggles the value of the boolean property nodeGlowProjectBox.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void toggleNodeGlowProjectBox() { setNodeGlowProjectBox(!isNodeGlowProjectBox()); }
	/**
	 * Gets the value of theboolean property nodeGlowWriteDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public boolean isNodeGlowWriteDepth() { return getValue("nodeGlowWriteDepth"); }
	/**
	 * Sets the value of the boolean property nodeGlowWriteDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowWriteDepth(boolean value) { setValue("nodeGlowWriteDepth", value); }

	/**
	 * Toggles the value of the boolean property nodeGlowWriteDepth.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void toggleNodeGlowWriteDepth() { setNodeGlowWriteDepth(!isNodeGlowWriteDepth()); }
	/**
	 * Gets the value of theboolean property nodeGlowOneLevel.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public boolean isNodeGlowOneLevel() { return getValue("nodeGlowOneLevel"); }
	/**
	 * Sets the value of the boolean property nodeGlowOneLevel.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowOneLevel(boolean value) { setValue("nodeGlowOneLevel", value); }

	/**
	 * Toggles the value of the boolean property nodeGlowOneLevel.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void toggleNodeGlowOneLevel() { setNodeGlowOneLevel(!isNodeGlowOneLevel()); }
	/**
	 * Gets the value of thefloat property nodeGlowMinFrac.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public float getNodeGlowMinFrac() { return getValue("nodeGlowMinFrac"); }
	/**
	 * Sets the value of the float property nodeGlowMinFrac.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	public void setNodeGlowMinFrac(float value) { setValue("nodeGlowMinFrac", value); }

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
	 * Resolves the writable settings file path.
	 * Dev builds (Maven project with pom.xml) write under src/main/resources.
	 * Packaged / installed builds write under the user config directory.
	 * This method is automatically generated from defaultProperties.json
	 * Any changes made here will be overwritten when regenerating
	 */
	private static File getSettingsFile() {
		try {
			java.net.URL loc = Settings.class.getProtectionDomain().getCodeSource().getLocation();
			java.nio.file.Path p = java.nio.file.Paths.get(loc.toURI());
			java.nio.file.Path moduleRoot = null;
			if (java.nio.file.Files.isDirectory(p) && p.getFileName().toString().equals("classes") && p.getParent() != null && p.getParent().getFileName().toString().equals("target")) {
				moduleRoot = p.getParent().getParent();
			} else if (java.nio.file.Files.isRegularFile(p) && p.getParent() != null && p.getParent().getFileName().toString().equals("target")) {
				moduleRoot = p.getParent().getParent();
			} else {
				java.nio.file.Path q = p;
				while (q != null) {
					if (java.nio.file.Files.exists(q.resolve("pom.xml"))) { moduleRoot = q; break; }
					q = q.getParent();
				}
			}
			if (moduleRoot != null && java.nio.file.Files.exists(moduleRoot.resolve("pom.xml"))) {
				return moduleRoot.resolve("src/main/resources/settings/settings.json").toFile();
			}
		} catch (Exception ignored) {
		}
		String appData = System.getenv("APPDATA");
		java.nio.file.Path configDir;
		if (appData != null && !appData.isBlank()) {
			configDir = java.nio.file.Paths.get(appData, "GravityChunk");
		} else {
			configDir = java.nio.file.Paths.get(System.getProperty("user.home"), ".gravitychunk");
		}
		return configDir.resolve("settings.json").toFile();
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
				boolean migrated = false;
				// One-shot: old nodeGlowActivatePx was a rename of theta; map to nodeGlowThetaPx then drop.
				if (jsonData.containsKey("nodeGlowActivatePx")) {
					if (!jsonData.containsKey("nodeGlowThetaPx")) {
						jsonData.put("nodeGlowThetaPx", jsonData.get("nodeGlowActivatePx"));
					}
					jsonData.remove("nodeGlowActivatePx");
					migrated = true;
				}
				
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
				if (migrated) {
					saveSettings();
				}
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
	public synchronized void saveSettings() {
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

	private final RenderModeProfiles renderProfiles = new RenderModeProfiles();

	private File renderProfileFile() {
		File settings = getSettingsFile();
		File parent = settings.getParentFile();
		return new File(parent == null ? new File(".") : parent, "renderModeSettings.json");
	}

	private Map<String, Number> snapshotRenderProfile() {
		Map<String, Number> values = new java.util.LinkedHashMap<>();
		values.put("bodyRenderDistance", getBodyRenderDistance());
		values.put("nodeGlowThetaPx", getNodeGlowThetaPx());
		values.put("nodeGlowActivateDist", getNodeGlowActivateDist());
		values.put("nodeGlowMaxDepth", getNodeGlowMaxDepth());
		values.put("nodeGlowIntensity", getNodeGlowIntensity());
		values.put("minImpostorSize", getMinImpostorSize());
		return values;
	}

	private void applyRenderProfile(Map<String, Number> values) {
		setBodyRenderDistance(values.get("bodyRenderDistance").floatValue());
		setNodeGlowThetaPx(values.get("nodeGlowThetaPx").floatValue());
		setNodeGlowActivateDist(values.get("nodeGlowActivateDist").floatValue());
		setNodeGlowMaxDepth(values.get("nodeGlowMaxDepth").intValue());
		setNodeGlowIntensity(values.get("nodeGlowIntensity").floatValue());
		setMinImpostorSize(values.get("minImpostorSize").floatValue());
	}

	/**
	 * Stores the live visual settings under the current render mode before a launch preset overwrites them.
	 */
	public void captureActiveRenderProfile() {
		File file = renderProfileFile();
		renderProfiles.load(file);
		renderProfiles.remember(getRenderMode(), snapshotRenderProfile());
		renderProfiles.save(file);
	}

	/**
	 * After launch settings and the camera frame are applied, restore this mode's saved look.
	 * imp and impGlow draw bodies at least as far as {@code bodyCover}.
	 */
	public void adoptLaunchRenderMode(float bodyCover) {
		File file = renderProfileFile();
		renderProfiles.load(file);
		renderProfiles.setBodyCover(bodyCover);
		String mode = getRenderMode();
		if (renderProfiles.has(mode)) {
			applyRenderProfile(renderProfiles.stored(mode));
			setRenderMode(mode);
		}
		if (RenderModeProfiles.needsBodyCover(mode) && getBodyRenderDistance() < bodyCover) {
			setBodyRenderDistance(bodyCover);
		}
		renderProfiles.remember(mode, snapshotRenderProfile());
		renderProfiles.save(file);
	}

	/**
	 * Switches render mode and loads the settings saved for it.
	 * The mode being left keeps whatever values are on screen now.
	 */
	public void selectRenderMode(String mode) {
		File file = renderProfileFile();
		renderProfiles.load(file);
		renderProfiles.remember(getRenderMode(), snapshotRenderProfile());
		if (!mode.equals(getRenderMode())) {
			setRenderMode(mode);
			applyRenderProfile(renderProfiles.valuesFor(mode));
		}
		renderProfiles.remember(getRenderMode(), snapshotRenderProfile());
		renderProfiles.save(file);
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

	// Live framebuffer size. Not a saved property: width and height stay the windowed size.
	private int drawableWidth;
	private int drawableHeight;

	/**
	 * Pixel width of the current framebuffer. Falls back to the saved window width before the first size event.
	 */
	public int getDrawableWidth() {
		return drawableWidth > 0 ? drawableWidth : Math.max(1, getWidth());
	}

	/**
	 * Pixel height of the current framebuffer. Falls back to the saved window height before the first size event.
	 */
	public int getDrawableHeight() {
		return drawableHeight > 0 ? drawableHeight : Math.max(1, getHeight());
	}

	/**
	 * Records the framebuffer size used for the viewport, projection, and UI.
	 * Does not write the saved window width and height.
	 */
	public void setDrawableSize(int width, int height) {
		drawableWidth = Math.max(1, width);
		drawableHeight = Math.max(1, height);
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
