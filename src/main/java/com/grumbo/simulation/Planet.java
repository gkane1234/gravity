package com.grumbo.simulation;
import java.util.ArrayList;
import org.joml.Vector3f;

public class Planet {

	public float mass;
	public Vector3f position;
	public Vector3f velocity;
	public float density;
	public int name;

	public Planet(float x, float y, float z, float xVelocity, float yVelocity, float zVelocity, float mass, float density) {

		this(new Vector3f(x, y, z), new Vector3f(xVelocity, yVelocity, zVelocity), mass, density);
	}

	public Planet(float x, float y, float z, float xVelocity, float yVelocity, float zVelocity, float mass) {

		this(new Vector3f(x, y, z), new Vector3f(xVelocity, yVelocity, zVelocity), mass, 1);
		


	}

	public Planet(Vector3f position, Vector3f velocity, float mass, float density) {
		this.position = position;
		this.velocity = velocity;
		this.mass = mass;
		this.density = density;
	}

	public Planet(Vector3f position, Vector3f velocity, float mass) {
		this(position, velocity, mass, 1);
	}

	public static Planet deadBody() {
		return new Planet(0, 0, 0, 0, 0, 0, 0, 0);
	}

	public void merge(Planet o) {
		position = position.mul(mass).add(o.position.mul(o.mass)).div(mass+o.mass);
		velocity = velocity.mul(mass).add(o.velocity.mul(o.mass)).div(mass+o.mass);
		mass=mass+o.mass;
		density = density*o.density/(density/mass+o.density/o.mass);
	}
	
	public double getRadius() {
		return Math.sqrt(this.mass)*Settings.getInstance().getDensity()/2;
	}

	@Override
	public String toString() {
		return "Planet [mass=" + mass + ", position=" + position + ", velocity=" + velocity + ", density=" + density + ", name=" + name + "]";
	}
	

}
