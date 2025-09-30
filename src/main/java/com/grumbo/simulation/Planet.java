package com.grumbo.simulation;
import org.joml.Vector3f;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
/**
 * Planet class for the simulation.
 * Represents the java analog of the Body struct in the GLSL code.
 * Used for initializing the bodies in the simulation.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class Planet {

	public float mass;
	public Vector3f position;
	public Vector3f velocity;
	public float density;
	public int name;

	private UnitSet unitSet;

    /**
     * Constructor for the Planet class.
     * @param x the x position of the planet
     * @param y the y position of the planet
     * @param z the z position of the planet
     * @param xVelocity the x velocity of the planet
     * @param yVelocity the y velocity of the planet
     * @param zVelocity the z velocity of the planet
     * @param mass the mass of the planet
     * @param density the density of the planet
     */
	public Planet(float x, float y, float z, float xVelocity, float yVelocity, float zVelocity, float mass, float density, UnitSet unitSet) {

		this(new Vector3f(x, y, z), new Vector3f(xVelocity, yVelocity, zVelocity), mass, density, unitSet);
	}

	    /**
     * Constructor for the Planet class.
     * @param x the x position of the planet
     * @param y the y position of the planet
     * @param z the z position of the planet
     * @param xVelocity the x velocity of the planet
     * @param yVelocity the y velocity of the planet
     * @param zVelocity the z velocity of the planet
     * @param mass the mass of the planet
     * @param density the density of the planet
     */
	public Planet(float x, float y, float z, float xVelocity, float yVelocity, float zVelocity, float mass, float density) {

		this(new Vector3f(x, y, z), new Vector3f(xVelocity, yVelocity, zVelocity), mass, density, null);
	}

    /**
     * Constructor for the Planet class.
     * @param x the x position of the planet
     * @param y the y position of the planet
     * @param z the z position of the planet
     * @param xVelocity the x velocity of the planet
     * @param yVelocity the y velocity of the planet
     * @param zVelocity the z velocity of the planet
     * @param mass the mass of the planet
     */
	public Planet(float x, float y, float z, float xVelocity, float yVelocity, float zVelocity, float mass, UnitSet unitSet) {

		this(new Vector3f(x, y, z), new Vector3f(xVelocity, yVelocity, zVelocity), mass, 1, unitSet);
	
	}

	    /**
     * Constructor for the Planet class.
     * @param x the x position of the planet
     * @param y the y position of the planet
     * @param z the z position of the planet
     * @param xVelocity the x velocity of the planet
     * @param yVelocity the y velocity of the planet
     * @param zVelocity the z velocity of the planet
     * @param mass the mass of the planet
     */
	public Planet(float x, float y, float z, float xVelocity, float yVelocity, float zVelocity, float mass) {

		this(new Vector3f(x, y, z), new Vector3f(xVelocity, yVelocity, zVelocity), mass, 1, null);
	
	}

    /**
     * Constructor for the Planet class.
     * @param position the position of the planet
     * @param velocity the velocity of the planet
     * @param mass the mass of the planet
     * @param density the density of the planet
     */
	public Planet(Vector3f position, Vector3f velocity, float mass, float density, UnitSet unitSet) {
		this.position = position;
		this.velocity = velocity;
		this.mass = mass;
		this.density = density;
		if (unitSet == null) {
			this.unitSet = UnitSet.SOLAR_SYSTEM_SECOND;
		} else {
			this.unitSet = unitSet;
		}
	}

	    /**
     * Constructor for the Planet class.
     * @param position the position of the planet
     * @param velocity the velocity of the planet
     * @param mass the mass of the planet
     * @param density the density of the planet
     */
	public Planet(Vector3f position, Vector3f velocity, float mass, float density) {
		this(position, velocity, mass, density, null);
	}
	/**
	 * Constructor for the Planet class.
	 * @param position the position of the planet
	 * @param velocity the velocity of the planet
	 * @param mass the mass of the planet
	 */
	public Planet(Vector3f position, Vector3f velocity, float mass, UnitSet unitSet) {
		this(position, velocity, mass, 1, unitSet);
	}

		/**
	 * Constructor for the Planet class.
	 * @param position the position of the planet
	 * @param velocity the velocity of the planet
	 * @param mass the mass of the planet
	 */
	public Planet(Vector3f position, Vector3f velocity, float mass) {
		this(position, velocity, mass, 1, null);
	}

	public static Planet fromJson(String json) {
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode jsonNode = null;
		try {
			jsonNode = objectMapper.readTree(json);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		System.out.println("jsonNode: " + jsonNode);
		Vector3f position = new Vector3f(jsonNode.get("position").get(0).floatValue(), jsonNode.get("position").get(1).floatValue(), jsonNode.get("position").get(2).floatValue());
		Vector3f velocity = new Vector3f(jsonNode.get("velocity").get(0).floatValue(), jsonNode.get("velocity").get(1).floatValue(), jsonNode.get("velocity").get(2).floatValue());
		float mass = jsonNode.get("mass").floatValue();
		float density = 100f;

		System.out.println("position: " + position);
		System.out.println("velocity: " + velocity);
		System.out.println("mass: " + mass);
		System.out.println("density: " + density);
		
		return new Planet(position, velocity, mass, density, UnitSet.METRIC);
	}

	/**
	 * Returns the dead planet.
	 * @return a dead planet
	 */
	public static Planet deadBody() {
		return new Planet(0, 0, 0, 0, 0, 0, 0, 0, null);
	}
   
	/**
	 * Changes the unit set of the planet.
	 * @param newUnitSet the new unit set
	 */
	public void changeUnitSet(UnitSet newUnitSet) {
		if (this.unitSet == newUnitSet) {
			return;
		}
		UnitSet oldUnitSet = this.unitSet;
		this.mass = (float)(oldUnitSet.mass() * this.mass);
		this.density = (float)(oldUnitSet.density() * this.density);
		this.position = this.position.mul((float)oldUnitSet.len());
		this.velocity = this.velocity.mul((float)oldUnitSet.len()).div((float)oldUnitSet.time());

		this.mass = (float)(this.mass/newUnitSet.mass() );
		this.density = (float)(this.density/newUnitSet.density() );
		this.position = this.position.div((float)newUnitSet.len());
		this.velocity = this.velocity.div((float)newUnitSet.len()).mul((float)newUnitSet.time());



		System.out.println(this.density);

		this.unitSet = newUnitSet;
	}

	/**
	 * Gets the unit set of the planet.
	 * @return the unit set of the planet
	 */
	public UnitSet getUnitSet() {
		return unitSet;
	}

	
	/**
	 * Returns a string representation of the planet.
	 * @return a string representation of the planet
	 */
	@Override
	public String toString() {
		return "Planet [mass=" + mass + ", position=" + position + ", velocity=" + velocity + ", density=" + density + ", name=" + name + "]";
	}


	

}
