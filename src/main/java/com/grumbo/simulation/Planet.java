package com.grumbo.simulation;
import java.awt.Color;
import java.util.ArrayList;

public class Planet {
	

	public static int num=0;
	
	public float x;
	public float y;
	public float z;
	public float xVelocity;
	public float yVelocity;
	public float zVelocity;
	public float mass;
	
	
	public Color color;
	

	
	public int name;

	public long[][] tail;
	public int tailIndex= 0;


	
	public Planet(float x, float y, float z, float xVelocity, float yVelocity, float zVelocity, float mass) {
		this.x=x;
		this.y=y;
		this.z=z;
		this.xVelocity=xVelocity;
		this.yVelocity=yVelocity;
		this.zVelocity=zVelocity;
		this.mass=mass;
		
		name=num++;
		
		// Generate random color instead of using default
		color = generateRandomColor();

		//initialize tail
		tail = new long[Settings.getInstance().getTailLength()][3]; // 3D tail
		for (int i=0;i<Settings.getInstance().getTailLength();i++) {
			tail[i]=new long[] {(long)x,(long)y,(long)z};
		}
		tailIndex=0;
		
	}
	
	// Convenience constructor for 2D (sets z=0)
	public Planet(float x, float y, float xVelocity, float yVelocity, float mass) {
		this(x, y, 0f, xVelocity, yVelocity, 0f, mass);
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
		x=(x*mass+o.x*o.mass)/(mass+o.mass);
		y=(y*mass+o.y*o.mass)/(mass+o.mass);
		//z=(z*mass+o.z*o.mass)/(mass*o.mass);
		xVelocity=(xVelocity*mass+o.xVelocity*o.mass)/(mass+o.mass);
		yVelocity=(yVelocity*mass+o.yVelocity*o.mass)/(mass+o.mass);
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
	public static ArrayList<Planet> makeNewRandomDisk(int num, float[] radius, float[] m, float phi, boolean ccw, boolean giveOrbitalVelocity, float mass) {
		ArrayList<Planet> ret = new ArrayList<>();
	
		// Disk normal tilted from +z by phi around the x-axis
		final float nx = 0f;
		final float ny = (float)Math.sin(phi);
		final float nz = (float)Math.cos(phi);
	
		// Build an orthonormal basis (u, v) in the plane perpendicular to n
		// Choose a helper axis not colinear with n
		float ax, ay, az;
		if (Math.abs(nx) < 0.9f) { ax = 1f; ay = 0f; az = 0f; } else { ax = 0f; ay = 1f; az = 0f; }
	
		// u = normalize(n x a)
		float ux = ny*az - nz*ay;
		float uy = nz*ax - nx*az;
		float uz = nx*ay - ny*ax;
		float uLen = (float)Math.sqrt(ux*ux + uy*uy + uz*uz);
		ux /= uLen; uy /= uLen; uz /= uLen;
	
		// v = n x u  (already normalized)
		float vx = ny*uz - nz*uy;
		float vy = nz*ux - nx*uz;
		float vz = nx*uy - ny*ux;
	
		for (int i=0;i<num;i++) {
			float r = randomInRange(radius);
			float theta = (float)(Math.random()*2*Math.PI);
			float cosT = (float)Math.cos(theta);
			float sinT = (float)Math.sin(theta);
	
			// Position in the inclined disk plane
			float x = r*(ux*cosT + vx*sinT);
			float y = r*(uy*cosT + vy*sinT);
			float z = r*(uz*cosT + vz*sinT);
	
			float xV = 0f, yV = 0f, zV = 0f;
			if (giveOrbitalVelocity) {
				int dir = ccw ? 1 : -1;
				float orbitalSpeed = (float)(11*Math.sqrt(mass/r));
	
				// Tangent direction: t̂ = normalize(n × r̂)
				float tx = ny*z - nz*y;
				float ty = nz*x - nx*z;
				float tz = nx*y - ny*x;
				float tLen = (float)Math.sqrt(tx*tx + ty*ty + tz*tz);
				if (tLen > 0f) { tx /= tLen; ty /= tLen; tz /= tLen; }
	
				xV = dir * orbitalSpeed * tx;
				yV = dir * orbitalSpeed * ty;
				zV = dir * orbitalSpeed * tz;
			}
	
			ret.add(new Planet(x, y, z, xV, yV, zV, randomInRange(m)));
		}
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
				if (planets.get(i).getRadius() + planets.get(j).getRadius() > Math.sqrt(Math.pow(planets.get(i).x - planets.get(j).x, 2) + Math.pow(planets.get(i).y - planets.get(j).y, 2) + Math.pow(planets.get(i).z - planets.get(j).z, 2))) {
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


	private static float randomInRange(float[] range) {
		return (float)(Math.random()*(range[1]-range[0])+range[0]);
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
