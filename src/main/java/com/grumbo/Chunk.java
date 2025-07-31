package com.grumbo;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class Chunk {
	


	public static AtomicLong counterSame = new AtomicLong(0);
	
	public static AtomicLong counterDiff = new AtomicLong(0);
	
	public static AtomicLong counterCom = new AtomicLong(0);
	
	ArrayList<Planet> planets;
	
	
	double mass;
	
	CoordKey center;
	

	public Chunk(Planet p) {
		this(p.chunkCenter);
		addPlanet(p);
	}

	public Chunk(CoordKey center) {
		planets = new ArrayList<Planet>();
		this.center = center;
		//System.out.println("Chunk created at " + this.center.x + "," + this.center.y + "," + this.center.z+" and chunk object "+this);
	}

	// Constructor for legacy support with long[] coordinates
	public Chunk(long[] center) {
		planets = new ArrayList<Planet>();
		this.center = new CoordKey(center[0], center[1], center[2]);
		//System.out.println("Chunk created at " + this.center.x + "," + this.center.y + "," + this.center.z+" and chunk object "+this);
	}
	
	
	public void addPlanet(Planet p) {
		synchronized (planets) {
			planets.add(p);
			mass+=p.mass;
			p.chunkCenter = this.center; // Set planet's reference to this chunk's CoordKey
		}
	}
	
	public void removePlanet(int p) {
		synchronized (planets) {
			mass-=planets.get(p).mass;
			planets.remove(p);
		}
	}
	
	/**
	 * Thread-safe version that accumulates forces instead of modifying planets directly
	 */
	public void attractWithAccumulation(Chunk chunk2, Map<Planet, double[]> forceAccumulator) {
		if (chunk2.equals(this.center)) {
			sameChunkAttractionWithAccumulation(forceAccumulator);
			return;
		}
		
		if (distance(this,chunk2)>Settings.getInstance().getMaxDistanceForFullInteraction()) {
			attractByCenterOfMassWithAccumulation(chunk2, forceAccumulator);
			return;
		}
		
		long t=System.currentTimeMillis();
		for (int i=0;i<planets.size();i++) {
			for (int j=0;j<chunk2.planets.size();j++) {
				Planet p = planets.get(i);
				Planet q = chunk2.planets.get(j);
				
				// Calculate forces without modifying planets
				Map<Planet, double[]> forces = Planet.calculateAttractionForces(p, q, Settings.getInstance().getTickSize());
				
				// Accumulate forces
				for (Map.Entry<Planet, double[]> entry : forces.entrySet()) {
					Planet planet = entry.getKey();
					double[] force = entry.getValue();
					
					forceAccumulator.computeIfAbsent(planet, k -> new double[3]);
					forceAccumulator.get(planet)[0] += force[0]; // x component
					forceAccumulator.get(planet)[1] += force[1]; // y component
					forceAccumulator.get(planet)[2] += force[2]; // z component
				}
			}
		}
		counterDiff.addAndGet(System.currentTimeMillis()-t);
	}
	
	
	
	public void moveAllPlanets() {
		for (Planet p : planets) {
			moveOnePlanet(p);
		}
	}

	public void moveOnePlanet(Planet p) {
		p.move(Settings.getInstance().getTickSize());
		planets.set(planets.indexOf(p), p);
	}

	public ArrayList<Planet> moveAllPlanetsAndReturnChunklessPlanets() {
		ArrayList<Planet> chunklessPlanets = new ArrayList<Planet>();
		for (Planet p : planets) {
			moveOnePlanet(p);
			if (p.updateChunkCenter()) {
				chunklessPlanets.add(p);
				planets.remove(p);
			}
		}
		return chunklessPlanets;
	}

	
	@Override
	public boolean equals(Object o) {
		//checks if the x and y coordinates are correct
		if (o.getClass()==Chunk.class) {
			Chunk c= (Chunk)o;
			return c.center.equals(center);
		}
		if (o.getClass()==CoordKey.class) {
			
			CoordKey d=(CoordKey)o;
			
			return d.equals(center);
		}
		return false;
	}
	


	private static double distance(Chunk chunk1, Chunk chunk2) {
		return Math.sqrt(Math.pow(chunk1.center.x-chunk2.center.x,2) + Math.pow(chunk1.center.y-chunk2.center.y,2) + Math.pow(chunk1.center.z-chunk2.center.z,2));
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
				Map<Planet, double[]> forces;
				if (Planet.distance(a,b) < (a.getRadius()+b.getRadius())) {
					//forces = Planet.calculateCollisionForces(a, b, TICK_SIZE);
					//accumulateForces(a,b,forces,forceAccumulator);
				}
				else {
					forces = Planet.calculateAttractionForces(a, b, Settings.getInstance().getTickSize());
					accumulateForces(a,b,forces,forceAccumulator);
				}
				
			}
		}
		counterSame.addAndGet(System.currentTimeMillis()-t);
	}

	private void accumulateForces(Planet a, Planet b, Map<Planet, double[]> forces, Map<Planet, double[]> forceAccumulator) {
		for (Map.Entry<Planet, double[]> entry : forces.entrySet()) {
			Planet planet = entry.getKey();
			double[] force = entry.getValue();
			
			forceAccumulator.computeIfAbsent(planet, k -> new double[3]);
			forceAccumulator.get(planet)[0] += force[0]; // x component
			forceAccumulator.get(planet)[1] += force[1]; // y component
			forceAccumulator.get(planet)[2] += force[2]; // z component
		}
	}
		
	
	/**
	 * Thread-safe version for center of mass attraction with force accumulation
	 */
	private void attractByCenterOfMassWithAccumulation(Chunk chunk2, Map<Planet, double[]> forceAccumulator) {
		if (chunk2.mass < Settings.getInstance().getMinMassForAttractByCenterOfMass()) return;
		
		long t = System.currentTimeMillis();
		
		Planet centerOfMass2 = new Planet(Settings.getInstance().getChunkSize()*chunk2.center.x,Settings.getInstance().getChunkSize()*chunk2.center.y, Settings.getInstance().getChunkSize()*chunk2.center.z,
		0, 0, 0, chunk2.mass);
		Planet centerOfMass1 = new Planet(Settings.getInstance().getChunkSize()*center.x, Settings.getInstance().getChunkSize()*center.y, Settings.getInstance().getChunkSize()*center.z,
		 0, 0, 0, 0);
		
		double[] residuals = Planet.forceOfAttract(centerOfMass1, centerOfMass2, Settings.getInstance().getTickSize());
		
		// Accumulate forces for all planets in this chunk
		for (int i = 0; i < planets.size(); i++) {
			Planet p = planets.get(i);
			
			forceAccumulator.computeIfAbsent(p, k -> new double[3]);
			forceAccumulator.get(p)[0] -= residuals[0]; // x component
			forceAccumulator.get(p)[1] -= residuals[1]; // y component
			forceAccumulator.get(p)[2] -= residuals[2]; // z component
		}
		counterCom.addAndGet(System.currentTimeMillis()-t);
	}
	

}
