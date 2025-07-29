package com.grumbo;
import java.awt.Color;
import java.util.Map;
import java.util.HashMap;

public class Planet {
	

	public static int num=0;
	
	public double x;
	public double y;
	//public double z;
	public double xVelocity;
	public double yVelocity;
	public double zVelocity;
	public double xVResidual=0;
	public double yVResidual=0;
	//public double zVResidual=0;
	public double mass;
	
	
	private Color color;
	
	
	public long[] chunkCenter;
	
	public int name;

	public long[][] tail;
	public int tailIndex= 0;
	
	public Planet(double x, double y, double xVelocity, double yVelocity, double mass) {
		this.x=x;
		this.y=y;
		//this.z=z;
		this.xVelocity=xVelocity;
		this.yVelocity=yVelocity;
		//this.zVelocity=zVelocity;
		this.mass=mass;
		this.chunkCenter=new long[2];
		updateChunkCenter();
		name=num++;
		
		color=Global.DEFAULT_PLANET_COLOR;

		//initialize tail
		tail = new long[Global.tailLength][2];
		for (int i=0;i<Global.tailLength;i++) {
			tail[i]=new long[] {(long)x,(long)y};
		}
		tailIndex=0;
		
	}
	
	public void move(double tickSize) {
		x+=tickSize*(xVelocity+=xVResidual);
		y+=tickSize*(yVelocity+=yVResidual);
		//z+=(zVelocity+=zVResidual);
		xVResidual=(yVResidual=0);

		if (Global.tailLength>0) {
			tail[tailIndex][0]=(long)x;
			tail[tailIndex][1]=(long)y;
			tailIndex=(tailIndex+1)%Global.tailLength;
		}
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
	
	public static Map<Planet, double[]> ricochet(Planet i,Planet j) {
		Map<Planet, double[]> forces = new HashMap<>();
		
		
		//https://physics.stackexchange.com/questions/681396/elastic-collision-3d-eqaution
		double dX=j.x-i.x;
		double dY=j.y-i.y;

		
		double dVX=j.xVelocity-i.xVelocity;
		double dVY=j.yVelocity-i.yVelocity;
		
		
		
		double d=distance(i,j);
		
		double[] n = new double[] {dX/d,dY/d};
		
		double mEff=1/(1/i.mass+1/j.mass);
		
		//vimp=n dot (v1-v2) since n is normalized we factor out 1/d
		
		double vImpact= (n[0]*dVX+n[1]*dVY);
		
		double J = (1+Global.elasticity)*mEff*vImpact;
		
		

		
		forces.put(i, new double[]{J/i.mass*n[0], J/i.mass*n[1]});
		forces.put(j, new double[]{-J/j.mass*n[0], -J/j.mass*n[1]});

		return forces;
	}
	
	public static Map<Planet, double[]> gravitationalForces(Planet i, Planet j, double tickSize) {
		Map<Planet, double[]> forces = new HashMap<>();
		double force = Math.pow(distance(i,j),Global.EXPO)*tickSize;
		double theta= Math.atan2(i.y-j.y, i.x-j.x);
		double forceX = force * Math.cos(theta);
		double forceY = force * Math.sin(theta);

		forces.put(i, new double[]{-forceX * j.mass, -forceY * j.mass});
		forces.put(j, new double[]{forceX * i.mass, forceY * i.mass});

		return forces;
	}
	
	/**
	 * Calculates attractive forces between two planets without modifying them.
	 * Returns force vectors that should be applied to each planet.
	 * @return Map with planets as keys and force vectors [fx, fy] as values
	 */
	public static Map<Planet, double[]> calculateAttractionForces(Planet i, Planet j, double tickSize) {
		Map<Planet, double[]> forces = new HashMap<>();
		forces = gravitationalForces(i, j, tickSize);
		
		return forces;
	}
	
	public static Map<Planet, double[]> calculateCollisionForces(Planet i, Planet j, double tickSize) {
		Map<Planet, double[]> forces = new HashMap<>();

		forces = ricochet(i, j);
		
		return forces;
	}
		
		
	public static double[] forceOfAttract(Planet i, Planet j, double tickSize) {
		double force = Math.pow(distance(i,j),Global.EXPO)*tickSize;
		double theta= Math.atan2(i.y-j.y, i.x-j.x);

		return new double[] {force*Math.cos(theta)*j.mass,force*Math.sin(theta)*j.mass};
	}
	
	

	public static double distance(Planet i, Planet j) {
		return Math.sqrt(Math.pow(i.x-j.x,2)+Math.pow(i.y-j.y,2));
	}
	
	public double getRadius() {
		return Math.sqrt(this.mass)*Global.DENSITY/2;
	}
	
	public Color getColor() {
		return color;
	}

	public static Planet[] makeNew(int num,double[] x, double[] y, double[] xV, double[] yV,double[] m) {
		Planet[] ret = new Planet[num];
		for (int i=0;i<num;i++) {
			ret[i]=new Planet(rando(x),rando(y),rando(xV),rando(yV),rando(m));			
		}
		return ret;
		
	}
	
	private static double rando(double[] range) {
		return Math.random()*(range[1]-range[0])+range[0];
	}

	    /**
     * Updates the chunk center of a planet if it has moved to a new chunk
     * @return true if the chunk center was updated, false otherwise
     */
    public boolean updateChunkCenter() {
        // Calculate new chunk coordinates
		long[] chunkCoordinates = Chunk.getChunkCenter(new double[] {x, y});
		long chunkX = chunkCoordinates[0];
		long chunkY = chunkCoordinates[1];
        
        // Create new array for atomic update if needed
        if (chunkCenter[0] != chunkX || chunkCenter[1] != chunkY) {
			chunkCenter[0]=chunkX;
			chunkCenter[1]=chunkY;

            return true;
        }
        return false;
    }

}
