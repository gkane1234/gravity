package com.grumbo.simulation;
import java.util.ArrayList;
import org.joml.Vector3f;
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

		this(new Vector3f(x, y, z), new Vector3f(xVelocity, yVelocity, zVelocity), mass, density);
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

		this(new Vector3f(x, y, z), new Vector3f(xVelocity, yVelocity, zVelocity), mass, 1);
	
	}

    /**
     * Constructor for the Planet class.
     * @param position the position of the planet
     * @param velocity the velocity of the planet
     * @param mass the mass of the planet
     * @param density the density of the planet
     */
	public Planet(Vector3f position, Vector3f velocity, float mass, float density) {
		this.position = position;
		this.velocity = velocity;
		this.mass = mass;
		this.density = density;
	}
	/**
	 * Constructor for the Planet class.
	 * @param position the position of the planet
	 * @param velocity the velocity of the planet
	 * @param mass the mass of the planet
	 */
	public Planet(Vector3f position, Vector3f velocity, float mass) {
		this(position, velocity, mass, 1);
	}

	/**
	 * Returns the dead planet.
	 * @return a dead planet
	 */
	public static Planet deadBody() {
		return new Planet(0, 0, 0, 0, 0, 0, 0, 0);
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
