package com.grumbo.simulation;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

public class PlanetGenerator {
    private List<Planet> planetChunk;
    private int chunkSize;
    public int planetsGenerated;
    private int numPlanets;
    private int planetOffset;
    private PlanetGeneratorFunction planetGeneratorFunction;
    private HasNextFunction hasNextFunction;


    private interface PlanetGeneratorFunction {
        public Planet generateNextPlanet();
    }

    private interface HasNextFunction {
        public boolean hasNextFunction();
    }

    public int getNumPlanets() {
        return numPlanets;
    }

    public boolean hasNext() {
        return hasNextFunction.hasNextFunction();
    }


    private static final int DEFAULT_CHUNK_SIZE = 100000;


    public PlanetGenerator(int numPlanets) {
        this(null, numPlanets, DEFAULT_CHUNK_SIZE);
    }
    public PlanetGenerator(PlanetGeneratorFunction planetGeneratorFunction, int numPlanets, int chunkSize) {
        this.chunkSize = chunkSize;
        this.planetGeneratorFunction = planetGeneratorFunction;
        this.numPlanets = numPlanets;
        this.planetsGenerated = 0;
        this.hasNextFunction = new HasNextFunction() {
            @Override
            public boolean hasNextFunction() {
                return planetsGenerated < numPlanets;
            }
        };
    }
    public PlanetGenerator(PlanetGeneratorFunction planetGeneratorFunction, int numPlanets) {
        this(planetGeneratorFunction, numPlanets, DEFAULT_CHUNK_SIZE);
    }

    public PlanetGenerator(List<Planet> planets) {
        this(planets.size());

        final int originalNumPlanets = this.numPlanets;
        this.planetGeneratorFunction = new PlanetGeneratorFunction() {
            @Override
            public Planet generateNextPlanet() {
                return planets.get(planetsGenerated-1);
            }
        };
        this.hasNextFunction = new HasNextFunction() {
            @Override
            public boolean hasNextFunction() {
                return planetsGenerated < originalNumPlanets;
            }
        };
    }

    public PlanetGenerator(Planet planet) {
        this(1);
        this.planetGeneratorFunction = new PlanetGeneratorFunction() {
            @Override
            public Planet generateNextPlanet() {
                return planet;
            }
        };
        this.hasNextFunction = new HasNextFunction() {
            @Override
            public boolean hasNextFunction() {
                return planetsGenerated == 0;
            }
        };
    }

    public PlanetGenerator() {
        this(0);
        this.planetGeneratorFunction = new PlanetGeneratorFunction() {
            @Override
            public Planet generateNextPlanet() {
                return null;
            }
        };
        this.hasNextFunction = new HasNextFunction() {
            @Override
            public boolean hasNextFunction() {
                return false;
            }
        };
    }

    public PlanetGenerator(PlanetGenerator pg1, PlanetGenerator pg2) {
        this(pg1.numPlanets+pg2.numPlanets);
        final int previousNumPlanets = pg1.numPlanets;
        this.planetGeneratorFunction = new PlanetGeneratorFunction() {
            @Override
            public Planet generateNextPlanet() {
                if (planetsGenerated <= previousNumPlanets) {
                    return pg1.nextPlanet();
                } else {
                    return pg2.nextPlanet();
                }
            }
        };
        this.hasNextFunction = new HasNextFunction() {
            @Override
            public boolean hasNextFunction() {
                return planetsGenerated < numPlanets;
            }
        };
    }


    public void add(PlanetGenerator pg) {
        final int prevNumPlanets = this.numPlanets;

        this.numPlanets += pg.numPlanets;

        final PlanetGeneratorFunction prevGen = this.planetGeneratorFunction;

        this.planetGeneratorFunction = new PlanetGeneratorFunction() {
            @Override
            public Planet generateNextPlanet() {
                if (planetsGenerated <= prevNumPlanets) {
                    return prevGen.generateNextPlanet();
                } else {
                    //System.out.println("on the second one");
                    return pg.nextPlanet();
                }
            }
        };
    
        this.hasNextFunction = new HasNextFunction() {
            @Override
            public boolean hasNextFunction() {
                return planetsGenerated < numPlanets;
            }
        };
    }
    public void add(Planet planet) {
        add(new PlanetGenerator(planet));
    }
    public void add(List<Planet> planets) {
        add(new PlanetGenerator(planets));
    }

    public List<Planet> nextChunk() {
        System.out.println("Next chunk");
        if (!hasNextFunction.hasNextFunction()) {
            throw new RuntimeException("No more planets");
        }
        List<Planet> ret = new ArrayList<>();
        for (int i = 0; i < Math.min(chunkSize, numPlanets - planetsGenerated); i++) {
            ret.add(nextPlanet());
        }
        System.out.println("Generated " + ret.size() + " planets" + " " + numPlanets + " " + planetsGenerated);
        return ret;
    }

    public Planet nextPlanet() {
        if (!hasNextFunction.hasNextFunction()) {
            throw new RuntimeException("No more planets");
        }
        planetsGenerated++;
        return planetGeneratorFunction.generateNextPlanet();
    }

    
	public static PlanetGenerator makeNewRandomBox(int num, float[] x, float[] y, float[] z, float[] xV, float[] yV, float[] zV, float[] m, float[] density) {
		
        PlanetGeneratorFunction planetGeneratorFunction = new PlanetGeneratorFunction() {
			@Override
			public Planet generateNextPlanet() {
				return new Planet(randomInRange(x), randomInRange(y), randomInRange(z), randomInRange(xV), randomInRange(yV), randomInRange(zV), randomInRange(m), randomInRange(density));
			}
		};
        
        PlanetGenerator ret = new PlanetGenerator(planetGeneratorFunction, num);
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
	public static PlanetGenerator createSeveralDisks(int numDisks, int[] numPlanetsRange, float[] radiusRangeLow, float[] stellarDensityRange, float[] mRange, 
			float[] densityRange, float[] centerX, float[] centerY, float[] centerZ, float[] relativeVelocityX, float[] relativeVelocityY, float[] relativeVelocityZ, 
			float[] phiRange, float[] centerMassRange, float[] centerDensityRange, 
			float[] adherenceToPlaneRange, float orbitalFactor, boolean giveOrbitalVelocity) {

		PlanetGenerator pg = new PlanetGenerator();

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

			PlanetGenerator pg2 = (PlanetGenerator.makeNewRandomDisk(num, radius, mass, density, 
			center, relativeVelocity, phi, centerMass, centerDensity, adherenceToPlane, orbitalFactor, ccw, giveOrbitalVelocity));
			pg= new PlanetGenerator(pg, pg2);
		}
		return pg;
}

	public static PlanetGenerator makeNewRandomDisk(int num, float[] radius, float[] mass, float[] density, float[] center, float[] relativeVelocity, float phi,  float centerMass, float centerDensity, float adherenceToPlane,float orbitalFactor,boolean ccw, boolean giveOrbitalVelocity) {

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

        PlanetGenerator pg = new PlanetGenerator(new PlanetGeneratorFunction() {

            @Override 
            public Planet generateNextPlanet() {

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

                return new Planet(position, velocity, randomInRange(mass), randomInRange(density));
            }
        }, num);

        System.out.println("Planet generator num planets: " + pg.getNumPlanets());

		Planet centerPlanet = new Planet(new Vector3f(center), new Vector3f(relativeVelocity), centerMass, centerDensity);
		PlanetGenerator pg2 = new PlanetGenerator(centerPlanet);
        System.out.println("Planet generator num planets: " + pg2.getNumPlanets());
        PlanetGenerator ret = new PlanetGenerator(pg, pg2);
        System.out.println("Planet generator num planets: " + ret.getNumPlanets());
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
}
