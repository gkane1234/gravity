package com.grumbo.simulation;
import java.awt.Color;
import java.util.ArrayList;
import org.joml.Vector3f;

public class Planet {
	

	public static int num=0;
	
	public float mass;
	public Vector3f position;
	public Vector3f velocity;
	
	
	public Color color;
	

	
	public int name;




	
	public Planet(float x, float y, float z, float xVelocity, float yVelocity, float zVelocity, float mass) {

		this(new Vector3f(x, y, z), new Vector3f(xVelocity, yVelocity, zVelocity), mass);
		


	}


	
	// Convenience constructor for 2D (sets z=0)
	public Planet(float x, float y, float xVelocity, float yVelocity, float mass) {
		this(new Vector3f(x, y, 0f), new Vector3f(xVelocity, yVelocity, 0f), mass);
	}


	public Planet(Vector3f position, Vector3f velocity, float mass) {
		this.position = position;
		this.velocity = velocity;
		this.mass = mass;

		name=num++;
		
		// Generate random color instead of using default
		color = generateRandomColor();
	}

	public static Planet deadBody() {
		return new Planet(0, 0, 0, 0, 0, 0, 0);
	}

	
	//public double[] getCoordinates(double zFactor, double theta, double phi) {
		//sqrt(((cos(theta)sin(phi))^2*X)^2+(sin(phi)^2)*z)^2))
		//return new double[] {zFactor*Math.sqrt(Math.pow(x*Math.pow(Math.cos(theta)*Math.sin(phi),2),2)+Math.pow(z*Math.pow(Math.cos(phi),2),2)),
		                   //zFactor*Math.sqrt(Math.pow(y*Math.pow(Math.sin(theta)*Math.sin(phi),2),2)+Math.pow(z*Math.pow(Math.cos(phi),2),2))};
	//}
	
	public void merge(Planet o) {
		position = position.mul(mass).add(o.position.mul(o.mass)).div(mass+o.mass);
		velocity = velocity.mul(mass).add(o.velocity.mul(o.mass)).div(mass+o.mass);
		//z=(z*mass+o.z*o.mass)/(mass*o.mass);
		//zVelocity=(zVelocity*mass+o.zVelocity*o.mass)/(mass*o.mass);
		mass=mass+o.mass;
	}
	
	
	public double getRadius() {
		return Math.sqrt(this.mass)*Settings.getInstance().getDensity()/2;
	}
	
	public Color getColor() {
		return color;
	}

	public static ArrayList<Planet> makeNewRandomBox(int num, float[] x, float[] y, float[] z, float[] xV, float[] yV, float[] zV, float[] m) {
		ArrayList<Planet> ret = new ArrayList<>();
		for (int i=0;i<num;i++) {
			ret.add(new Planet(randomInRange(x), randomInRange(y), randomInRange(z), randomInRange(xV), randomInRange(yV), randomInRange(zV), randomInRange(m)));			
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
		// System.out.println("r: " + r + ", theta: " + theta);

		// System.out.println("u: " + u.x + ", " + u.y + ", " + u.z);
		// System.out.println("v: " + v.x + ", " + v.y + ", " + v.z);
		// System.out.println(r*(float)Math.cos(theta));
		Vector3f cuTr = uC.mul(r*(float)Math.cos(theta));
		Vector3f cvTr = vC.mul(r*(float)Math.sin(theta));
		// System.out.println("cuTr: " + cuTr.x + ", " + cuTr.y + ", " + cuTr.z);
		// System.out.println("cvTr: " + cvTr.x + ", " + cvTr.y + ", " + cvTr.z);
		return cuTr.add(cvTr);
	}

	public static ArrayList<Planet> makeNewRandomDisk(int num, float[] radius, float[] m, float[] center, float[] relativeVelocity, float phi,  float mass, float adherenceToPlane,float centerDensity,float orbitalFactor,boolean ccw, boolean giveOrbitalVelocity) {
		ArrayList<Planet> ret = new ArrayList<>();
		//System.out.println(adherenceCalculation(adherenceToPlane)	);

		
		// Disk normal tilted from +z by phi around the x-axis
		final Vector3f normal = new Vector3f(0f, (float)(Math.sin(phi)), (float)Math.cos(phi));

		//float orbitalFactor = 1f;



		
	
		// Build an orthonormal basis (u, v) in the plane perpendicular to n (i.e in the disk)
		// Choose a helper axis not colinear with n
		Vector3f a;
		if (Math.abs(normal.x) < 0.9f) { a = new Vector3f(1f, 0f, 0f); } else { a = new Vector3f(0f, 1f, 0f); }
	
		// u = normalize(n x a)
		Vector3f u = new Vector3f(normal).cross(a).normalize();
		System.out.println("u: " + u.x + ", " + u.y + ", " + u.z);
	
		// v = n x u  (already normalized)
		Vector3f v = new Vector3f(normal).cross(u);
		System.out.println("v: " + v.x + ", " + v.y + ", " + v.z);

		
	
		for (int i=0;i<num;i++) {

			
			float r = randomInRange(radius, centerDensity);
			float theta = (float)(Math.random()*2*Math.PI);

			float devianceFromPlane = adherenceCalculation(adherenceToPlane);

			boolean abovePlane = Math.random() < 0.5;

			devianceFromPlane = abovePlane ? devianceFromPlane : -devianceFromPlane;

			Vector3f planarPlanetDir = polarToPlanarCoordinates(1, theta, u, v);

			//System.out.println("planarPlanetDir: " + planarPlanetDir.x + ", " + planarPlanetDir.y + ", " + planarPlanetDir.z);

			Vector3f planetDirWithDeviance = polarToPlanarCoordinates(1, devianceFromPlane, planarPlanetDir, normal);

			Vector3f newNormal = polarToPlanarCoordinates(1, devianceFromPlane, normal, planarPlanetDir);



			float approxMassWithinRadius = (m[1]+m[0])/2 * num * (float)Math.pow(r/radius[1], centerDensity+1);
	
			// Position in the inclined disk plane
			Vector3f position = planetDirWithDeviance.mul(r);
	
			Vector3f velocity = new Vector3f(0f, 0f, 0f);
			if (giveOrbitalVelocity) {
				int dir = ccw ? 1 : -1;
				float orbitalSpeed = (float)(Math.sqrt(approxMassWithinRadius/r))*orbitalFactor;
	
				// Tangent direction: t̂ = normalize(n × r̂)
				Vector3f t = new Vector3f(newNormal).cross(position).normalize();
	
				velocity = t.mul(orbitalSpeed * dir);
			}

			position = position.add(new Vector3f(center));
			velocity = velocity.add(new Vector3f(relativeVelocity));
	
			ret.add(new Planet(position, velocity, randomInRange(m)));
		}

		Planet centerPlanet = new Planet(new Vector3f(center), new Vector3f(relativeVelocity), mass);
		ret.add(centerPlanet);
		return ret;
	}

	public static Planet makeNewInOrbit(float[] radius, float[] m, Planet center) {
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
		Planet ret = new Planet(x, y, z, xV, yV, zV, randomInRange(m));
		return ret;
	}

	public static ArrayList<Planet> makeNewInOrbit(int num, float[] m, Planet center, float[] radius) {
		ArrayList<Planet> ret = new ArrayList<>();
		for (int i=0;i<num;i++) {
			ret.add(makeNewInOrbit(radius, m, center));
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
	
	/**
	 * Generates a random color for planets
	 * @return A random Color object
	 */
	private static Color generateRandomColor() {
		// Generate random RGB values with some constraints for better visibility
		int red = (int)(Math.random() * 200) + 55;   // 55-255 for good visibility
		int green = (int)(Math.random() * 200) + 55;  
		int blue = (int)(Math.random() * 200) + 55;  
		
		return new Color(red, green, blue);
	}


}
