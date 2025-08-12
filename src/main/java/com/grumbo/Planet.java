package com.grumbo;
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

	public static ArrayList<Planet> makeNew(int num, float[] x, float[] y, float[] z, float[] xV, float[] yV, float[] zV, float[] m) {
		ArrayList<Planet> ret = new ArrayList<>();
		for (int i=0;i<num;i++) {
			ret.add(new Planet(randomInRange(x), randomInRange(y), randomInRange(z), randomInRange(xV), randomInRange(yV), randomInRange(zV), randomInRange(m)));			
		}
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
