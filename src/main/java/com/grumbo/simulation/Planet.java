package com.grumbo.simulation;
import java.awt.Color;
import java.util.ArrayList;
import org.joml.Vector3f;

public class Planet {

	public float mass;
	public Vector3f position;
	public Vector3f velocity;
	public float density;
	public Color color;
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
		color = generateRandomColor();
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
	
	public Color getColor() {
		return color;
	}

	public static ArrayList<Planet> makeNewRandomBox(int num, float[] x, float[] y, float[] z, float[] xV, float[] yV, float[] zV, float[] m, float[] density) {
		ArrayList<Planet> ret = new ArrayList<>();
		for (int i=0;i<num;i++) {
			ret.add(new Planet(randomInRange(x), randomInRange(y), randomInRange(z), randomInRange(xV), randomInRange(yV), randomInRange(zV), randomInRange(m), randomInRange(density)));			
		}
		return ret;
	}
	private static float adherenceCalculation(float adherenceToPlane) {
		//returns the difference in radians from the plane for the phi value
		double factor = 1-Math.pow(Math.random(), (1.0-adherenceToPlane)); 
		return (float)(Math.PI*factor);
	}

	private static Vector3f polarToPlanarCoordinates(float r,float theta, Vector3f u, Vector3f v) {
		Vector3f uC = new Vector3f(u);
		Vector3f vC = new Vector3f(v);
		Vector3f cuTr = uC.mul(r*(float)Math.cos(theta));
		Vector3f cvTr = vC.mul(r*(float)Math.sin(theta));
		return cuTr.add(cvTr);
	}
	public static ArrayList<Planet> createSeveralDisks(int numDisks, int[] numPlanetsRange, float[] radiusRangeLow, float[] stellarDensityRange, float[] mRange, 
			float[] densityRange, float[] centerX, float[] centerY, float[] centerZ, float[] relativeVelocityX, float[] relativeVelocityY, float[] relativeVelocityZ, 
			float[] phiRange, float[] centerMassRange, float[] centerDensityRange, 
			float[] adherenceToPlaneRange, float orbitalFactor, boolean giveOrbitalVelocity) {

		ArrayList<Planet> planets = new ArrayList<>();
		for (int i = 0; i < numDisks; i++) {
			int num = randomInRange(numPlanetsRange);
			float[] radius = {randomInRange(radiusRangeLow), num/randomInRange(stellarDensityRange)};
			float[] mass = {randomInRange(mRange), randomInRange(mRange)};
			float[] density = {randomInRange(densityRange), randomInRange(densityRange)};
			float[] center = {randomInRange(centerX), randomInRange(centerY), randomInRange(centerZ)};
			float[] relativeVelocity = {randomInRange(relativeVelocityX), randomInRange(relativeVelocityY), randomInRange(relativeVelocityZ)};
			float phi = randomInRange(phiRange);
			float centerMass = randomInRange(centerMassRange);
			float centerDensity = randomInRange(centerDensityRange);
			float adherenceToPlane = randomInRange(adherenceToPlaneRange);
			orbitalFactor = orbitalFactor;
			boolean ccw = false;
			giveOrbitalVelocity = giveOrbitalVelocity;

			planets.addAll(Planet.makeNewRandomDisk(num, radius, mass, density, 
			center, relativeVelocity, phi, centerMass, centerDensity, adherenceToPlane, orbitalFactor, ccw, giveOrbitalVelocity));
		}
		return planets;
}

	public static ArrayList<Planet> makeNewRandomDisk(int num, float[] radius, float[] mass, float[] density, float[] center, float[] relativeVelocity, float phi,  float centerMass, float centerDensity, float adherenceToPlane,float orbitalFactor,boolean ccw, boolean giveOrbitalVelocity) {
		ArrayList<Planet> ret = new ArrayList<>();

		// Disk normal tilted from +z by phi around the x-axis
		final Vector3f normal = new Vector3f(0f, (float)(Math.sin(phi)), (float)Math.cos(phi));

		// Build an orthonormal basis (u, v) in the plane perpendicular to n (i.e in the disk)
		// Choose a helper axis not colinear with n
		Vector3f a;
		if (Math.abs(normal.x) < 0.9f) { a = new Vector3f(1f, 0f, 0f); } else { a = new Vector3f(0f, 1f, 0f); }
	
		// u = normalize(n x a)
		Vector3f u = new Vector3f(normal).cross(a).normalize();
		//System.out.println("u: " + u.x + ", " + u.y + ", " + u.z);
	
		// v = n x u  (already normalized)
		Vector3f v = new Vector3f(normal).cross(u);
		//System.out.println("v: " + v.x + ", " + v.y + ", " + v.z);

		for (int i=0;i<num;i++) {
			float r = randomInRange(radius, 1);
			float theta = (float)(Math.random()*2*Math.PI);

			float devianceFromPlane = adherenceCalculation(adherenceToPlane);

			boolean abovePlane = Math.random() < 0.5;

			devianceFromPlane = abovePlane ? devianceFromPlane : -devianceFromPlane;

			Vector3f planarPlanetDir = polarToPlanarCoordinates(1, theta, u, v);

			//System.out.println("planarPlanetDir: " + planarPlanetDir.x + ", " + planarPlanetDir.y + ", " + planarPlanetDir.z);

			Vector3f planetDirWithDeviance = polarToPlanarCoordinates(1, devianceFromPlane, planarPlanetDir, normal);

			Vector3f newNormal = polarToPlanarCoordinates(1, devianceFromPlane, normal, planarPlanetDir);

			float approxMassWithinRadius = (mass[1]+mass[0])/2 * num * (float)Math.pow(r/radius[1], centerDensity+1);
	
			// Position in the inclined disk plane
			Vector3f position = planetDirWithDeviance.mul(r);
	
			Vector3f velocity = new Vector3f(0f, 0f, 0f);
			if (giveOrbitalVelocity) {
				int dir = ccw ? 1 : -1;
				float orbitalSpeed = (float)(Math.sqrt((centerMass+approxMassWithinRadius)/r))*orbitalFactor;
			
	
				// Tangent direction: t̂ = normalize(n × r̂)
				Vector3f tangent = new Vector3f(newNormal).cross(planetDirWithDeviance).normalize();

	
				velocity = tangent.mul(orbitalSpeed * dir);
			}

			position = position.add(new Vector3f(center));
			velocity = velocity.add(new Vector3f(relativeVelocity));
	
			ret.add(new Planet(position, velocity, randomInRange(mass), randomInRange(density)));
		}

		Planet centerPlanet = new Planet(new Vector3f(center), new Vector3f(relativeVelocity), centerMass, centerDensity);
		ret.add(centerPlanet);
		return ret;
	}

	public static Planet makeNewInOrbit(float[] radius, float[] mass, float[] density, Planet center) {
		float r = (float)(Math.random()*(radius[1]-radius[0])+radius[0]);
		float orbitalSpeed = (float)(1.1*Math.sqrt(center.mass/r));
		float theta = (float)(Math.random()*2*Math.PI);
		float phi = (float)(Math.PI/2);
		float x = (float)(r*Math.cos(theta)*Math.sin(phi));
		float y = (float)(r*Math.sin(theta)*Math.sin(phi));
		float z = (float)(r*Math.cos(phi));
		float xV = (float)(-orbitalSpeed*Math.sin(theta)*Math.sin(phi));
		float yV = (float)(orbitalSpeed*Math.cos(theta)*Math.sin(phi));
		float zV = (float)(-orbitalSpeed*Math.cos(phi));
		Planet ret = new Planet(x, y, z, xV, yV, zV, randomInRange(mass), randomInRange(density));
		return ret;
	}

	public static ArrayList<Planet> makeNewInOrbit(int num, float[] mass, float[] density, Planet center, float[] radius) {
		ArrayList<Planet> ret = new ArrayList<>();
		for (int i=0;i<num;i++) {
			ret.add(makeNewInOrbit(radius, mass, density, center));
		}
		return ret;
	}

	public static ArrayList<Planet> mergeOverlappingPlanets(ArrayList<Planet> planets) {
		ArrayList<Planet> ret = new ArrayList<>();
		for (Planet planet : planets) {
			ret.add(planet);
		}
		int remaining = planets.size();
		for (int i=0;i<remaining;i++) {
			for (int j=i+1;j<remaining;j++) {
				if (planets.get(i).getRadius() + planets.get(j).getRadius() > Math.sqrt(Math.pow(planets.get(i).position.x - planets.get(j).position.x, 2) + Math.pow(planets.get(i).position.y - planets.get(j).position.y, 2) + Math.pow(planets.get(i).position.z - planets.get(j).position.z, 2))) {
					planets.get(i).merge(planets.get(j));
					ret.set(i, planets.get(i));
					ret.remove(j);
					remaining--;
					j--;
				}
			}
		}

		System.out.println("Merged " + (planets.size() - remaining) + " planets");
		return ret;
	}


	private static float randomInRange(float[] range, float density) {
		return (float)(Math.pow(Math.random(), density)*(range[1]-range[0])+range[0]);
	}

	private static float randomInRange(float[] range) {
		return randomInRange(range, 1.0f);
	}
	private static int randomInRange(int[] range) {
		float[] rangeFloat = {range[0], range[1]};
		return (int) randomInRange(rangeFloat, 1.0f);
	}
	private static int randomInRange(int[] range, float density) {
		float[] rangeFloat = {range[0], range[1]};
		return (int) randomInRange(rangeFloat, density);
	}

	private static Color generateRandomColor() {
		// Generate random RGB values with some constraints for better visibility
		double min = 55;
		double max = 255;
		int red = (int)(Math.random() * (max - min) + min); 
		int green = (int)(Math.random() * (max - min) + min);  
		int blue = (int)(Math.random() * (max - min) + min);
		
		return new Color(red, red, red);
	}
}
