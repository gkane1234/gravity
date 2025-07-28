package com.grumbo;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class Chunk {
	
	private static final double MIN_MASS_FOR_ATTRACT_BY_CENTER_OF_MASS = 100;

	private static final int MAX_DISTANCE_FOR_FULL_INTERACTION = 1; //in chunks

	private static final double TICK_SIZE=0.1;

	public static AtomicLong counterSame = new AtomicLong(0);
	
	public static AtomicLong counterDiff = new AtomicLong(0);
	
	public static AtomicLong counterCom = new AtomicLong(0);
	
	ArrayList<Planet> planets;
	
	
	double mass;
	
	double[] center;
	

	public Chunk(Planet p) {
		this(p.chunkCenter);
		addPlanet(p);
	}

	public Chunk(double[] center) {
		planets = new ArrayList<Planet>();
		this.center = center;

	}
	
	
	public void addPlanet(Planet p) {
		planets.add(p);
		mass+=p.mass;
	}
	
	public void removePlanet(int p) {
		mass-=planets.get(p).mass;
		planets.remove(p);
	}
	
	public void attract(Chunk chunk2) {
		if (chunk2.equals(this.center)) {
			sameChunkAttractionWithAccumulation(null);
			return;
		}
		
		
		if (distance(this,chunk2)>MAX_DISTANCE_FOR_FULL_INTERACTION) {

			attractByCenterOfMass(chunk2);
			return;
		}
		long t=System.currentTimeMillis();
		for (int i=0;i<planets.size();i++) {
			for (int j=0;j<chunk2.planets.size();j++) {
				Planet p = planets.get(i);
				Planet q= chunk2.planets.get(j);
				Planet.attract(p, q, TICK_SIZE);
				planets.set(i, p);
				chunk2.planets.set(j,q);
			}
		}
		counterDiff.addAndGet(System.currentTimeMillis()-t);
	}
	
	/**
	 * Thread-safe version that accumulates forces instead of modifying planets directly
	 */
	public void attractWithAccumulation(Chunk chunk2, Map<Planet, double[]> forceAccumulator) {
		if (chunk2.equals(this.center)) {
			sameChunkAttractionWithAccumulation(forceAccumulator);
			return;
		}
		
		if (distance(this,chunk2)>MAX_DISTANCE_FOR_FULL_INTERACTION) {
			attractByCenterOfMassWithAccumulation(chunk2, forceAccumulator);
			return;
		}
		
		long t=System.currentTimeMillis();
		for (int i=0;i<planets.size();i++) {
			for (int j=0;j<chunk2.planets.size();j++) {
				Planet p = planets.get(i);
				Planet q = chunk2.planets.get(j);
				
				// Calculate forces without modifying planets
				Map<Planet, double[]> forces = Planet.calculateAttractionForces(p, q, TICK_SIZE);
				
				// Accumulate forces
				for (Map.Entry<Planet, double[]> entry : forces.entrySet()) {
					Planet planet = entry.getKey();
					double[] force = entry.getValue();
					
					forceAccumulator.computeIfAbsent(planet, k -> new double[2]);
					forceAccumulator.get(planet)[0] += force[0]; // x component
					forceAccumulator.get(planet)[1] += force[1]; // y component
				}
			}
		}
		counterDiff.addAndGet(System.currentTimeMillis()-t);
	}
	
	
	
	public void move() {
		for (int planet=0;planet<planets.size();planet++) {
			Planet p = planets.get(planet);
			p.move(TICK_SIZE);
			planets.set(planet, p);
		}
	}

	
	@Override
	public boolean equals(Object o) {
		//checks if the x and y coordinates are correct
		if (o.getClass()==Chunk.class) {
			Chunk c= (Chunk)o;
			return equalComponents(c.center,center);
		}
		if (o.getClass()==double[].class) {
			
			double[] d=(double[])o;
			
			return equalComponents(d,center);
		}
		return false;
	}
	
	private boolean equalComponents(double[] d1,double[] d2) {
		
		return d1[0]==d2[0]&&d1[1]==d2[1];
	}


	private static double distance(Chunk chunk1, Chunk chunk2) {
		return Math.hypot(chunk1.center[0]-chunk2.center[0],chunk1.center[1]-chunk2.center[1]);
	}


	private void sameChunkAttraction() {
		
		for (int i=0;i<planets.size()-1;i++) {
			for (int j=i+1;j<planets.size();j++) {
				Planet a = planets.get(i);
				Planet b=planets.get(j);
				if (Planet.distance(a,b)<(a.getRadius()+b.getRadius())) {
					boolean Instantmerge=false;
					if (Instantmerge || a.justHit(b.name)) {
						
						a.merge(b);
						planets.set(i, a);
						planets.remove(b);
						
						j--;
						
					}
					else {
						Planet.ricochet(a, b);
						a.hit(b.name);
						
					}
					
				}
				else {
				if (a.justHit(b.name)) {
					a.noHit(b.name);
				}

				Planet.attract(a,b,TICK_SIZE);
				planets.set(i, a);
				planets.set(j,b);
				
				}
			}
		}
		
	}

	private void attractByCenterOfMass(Chunk chunk2) {
		if (chunk2.mass<MIN_MASS_FOR_ATTRACT_BY_CENTER_OF_MASS) return;
		
		
		
		long t=System.currentTimeMillis();
		
		Planet centerOfMass2= new Planet(Global.chunkSize*chunk2.center[0],Global.chunkSize*chunk2.center[1],0,0,chunk2.mass);
		Planet centerOfMass1= new Planet(Global.chunkSize*center[0],Global.chunkSize*center[1],0,0,0);
		
		double[] residuals=Planet.forceOfAttract(centerOfMass1, centerOfMass2, TICK_SIZE);
		
		for (int i=0;i<planets.size();i++) {

			Planet p = planets.get(i);
			
			p.xVResidual-=residuals[0];
			p.yVResidual-=residuals[1];
		}
		counterCom.addAndGet(System.currentTimeMillis()-t);
	}
	
	/**
	 * Thread-safe version for same chunk attraction with force accumulation
	 * Note: Collision handling is simplified to avoid race conditions
	 */
	private void sameChunkAttractionWithAccumulation(Map<Planet, double[]> forceAccumulator) {
		long t=System.currentTimeMillis();
		for (int i=0;i<planets.size()-1;i++) {
			for (int j=i+1;j<planets.size();j++) {
				Planet a = planets.get(i);
				Planet b = planets.get(j);
				
				// Skip collision handling in threaded version for now
				// TODO: Implement thread-safe collision handling
				if (Planet.distance(a,b) >= (a.getRadius()+b.getRadius())) {
					// Only handle attraction forces, not collisions
					Map<Planet, double[]> forces = Planet.calculateAttractionForces(a, b, TICK_SIZE);
					
					// Accumulate forces
					for (Map.Entry<Planet, double[]> entry : forces.entrySet()) {
						Planet planet = entry.getKey();
						double[] force = entry.getValue();
						
						forceAccumulator.computeIfAbsent(planet, k -> new double[2]);
						forceAccumulator.get(planet)[0] += force[0]; // x component
						forceAccumulator.get(planet)[1] += force[1]; // y component
					}
				}
			}
		}
		counterSame.addAndGet(System.currentTimeMillis()-t);
	}
	
	/**
	 * Thread-safe version for center of mass attraction with force accumulation
	 */
	private void attractByCenterOfMassWithAccumulation(Chunk chunk2, Map<Planet, double[]> forceAccumulator) {
		if (chunk2.mass < MIN_MASS_FOR_ATTRACT_BY_CENTER_OF_MASS) return;
		
		long t = System.currentTimeMillis();
		
		Planet centerOfMass2 = new Planet(Global.chunkSize*chunk2.center[0], Global.chunkSize*chunk2.center[1], 0, 0, chunk2.mass);
		Planet centerOfMass1 = new Planet(Global.chunkSize*center[0], Global.chunkSize*center[1], 0, 0, 0);
		
		double[] residuals = Planet.forceOfAttract(centerOfMass1, centerOfMass2, TICK_SIZE);
		
		// Accumulate forces for all planets in this chunk
		for (int i = 0; i < planets.size(); i++) {
			Planet p = planets.get(i);
			
			forceAccumulator.computeIfAbsent(p, k -> new double[2]);
			forceAccumulator.get(p)[0] -= residuals[0]; // x component
			forceAccumulator.get(p)[1] -= residuals[1]; // y component
		}
		counterCom.addAndGet(System.currentTimeMillis()-t);
	}
	
	
}
