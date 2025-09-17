package com.grumbo.simulation;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

/**
 * PlanetGenerator class for generating planets.
 * Used for generating planets for the simulation without holding them all in RAM.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class PlanetGenerator {
    private List<Planet> planetChunk;
    private int chunkSize;
    public int planetsGenerated;
    private int numPlanets;
    private int planetOffset;
    private PlanetGeneratorFunction planetGeneratorFunction;
    private HasNextFunction hasNextFunction;

    /**
     * Interface for generating the next planet.
     */
    private interface PlanetGeneratorFunction {
        public Planet generateNextPlanet();
    }

    /**
     * Interface for checking if there are more planets to generate.
     */
    private interface HasNextFunction {
        public boolean hasNextFunction();
    }

    /**
     * Gets the number of planets.
     * @return the number of planets
     */
    public int getNumPlanets() {
        return numPlanets;
    }

    /**
     * Checks if there are more planets to generate.
     * @return true if there are more planets to generate, false otherwise
     */
    public boolean hasNext() {
        return hasNextFunction.hasNextFunction();
    }


    private static final int DEFAULT_CHUNK_SIZE = 100_000;

    /**
     * Constructor for the PlanetGenerator class.
     * @param planetGeneratorFunction the function to generate the next planet
     * @param numPlanets the number of planets to generate
     * @param chunkSize the largest size of planets to hold in RAM at any given time
     */
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

    /**
     * Constructor for the PlanetGenerator class.
     * @param numPlanets the number of planets to generate
     */
    public PlanetGenerator(int numPlanets) {
        this(null, numPlanets, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Constructor for the PlanetGenerator class.
     * @param planetGeneratorFunction the function to generate the next planet
     * @param numPlanets the number of planets to generate
     */
    public PlanetGenerator(PlanetGeneratorFunction planetGeneratorFunction, int numPlanets) {
        this(planetGeneratorFunction, numPlanets, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Constructor for the PlanetGenerator class.
     * @param planets the list of planets to generate
     */
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

    /**
     * Constructor for the PlanetGenerator class.
     * @param planet the planet to generate
     */
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

    /**
     * Default constructor for the PlanetGenerator class.
     */
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

    /**
     * Constructor for the PlanetGenerator class.
     * @param pg1 the first planet generator
     * @param pg2 the second planet generator
     */
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


    // public void add(PlanetGenerator pg) {
    //     final int prevNumPlanets = this.numPlanets;

    //     this.numPlanets += pg.numPlanets;

    //     final PlanetGeneratorFunction prevGen = this.planetGeneratorFunction;

    //     this.planetGeneratorFunction = new PlanetGeneratorFunction() {
    //         @Override
    //         public Planet generateNextPlanet() {
    //             if (planetsGenerated <= prevNumPlanets) {
    //                 return prevGen.generateNextPlanet();
    //             } else {
    //                 //System.out.println("on the second one");
    //                 return pg.nextPlanet();
    //             }
    //         }
    //     };
    
    //     this.hasNextFunction = new HasNextFunction() {
    //         @Override
    //         public boolean hasNextFunction() {
    //             return planetsGenerated < numPlanets;
    //         }
    //     };
    // }
    // public void add(Planet planet) {
    //     add(new PlanetGenerator(planet));
    // }
    // public void add(List<Planet> planets) {
    //     add(new PlanetGenerator(planets));
    // }

    /**
     * Gets the next chunk of planets.
     * @return the next chunk of planets
     */
    public List<Planet> nextChunk() {
        if (!hasNextFunction.hasNextFunction()) {
            throw new RuntimeException("No more planets");
        }
        List<Planet> ret = new ArrayList<>();
        for (int i = 0; i < Math.min(chunkSize, numPlanets - planetsGenerated); i++) {
            ret.add(nextPlanet());
        }
        return ret;
    }

    /**
     * Gets the next planet.
     * @return the next planet
     */
    public Planet nextPlanet() {
        if (!hasNextFunction.hasNextFunction()) {
            throw new RuntimeException("No more planets");
        }
        planetsGenerated++;
        return planetGeneratorFunction.generateNextPlanet();
    }

    
    /**
     * Makes a new random set of planets confined to a box.
     * @param num the number of planets to generate
     * @param x the x range
     * @param y the y range
     * @param z the z range
     * @param xV the x velocity range
     * @param yV the y velocity range
     * @param zV the z velocity range
     * @param m the mass range
     * @param density the density range
     * @return the new random box
     */
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

    /**
     * Calculates the adherence to a plane, used in making disks.
     * @param adherenceToPlane the adherence to a plane
     * @return the adherence to a plane
     */
	private static float adherenceCalculation(float adherenceToPlane) {
		//returns the difference in radians from the plane for the phi value
		double factor = 1-Math.pow(Math.random(), (1.0-adherenceToPlane)); 
		return (float)(Math.PI*factor);
	}

    /**
     * Converts polar coordinates to planar coordinates, used in making disks.
     * @param r the radius
     * @param theta the theta
     * @param u the u
     * @param v the v
     * @return the planar coordinates
     */
	private static Vector3f polarToPlanarCoordinates(float r,float theta, Vector3f u, Vector3f v) {
		Vector3f uC = new Vector3f(u);
		Vector3f vC = new Vector3f(v);
		Vector3f cuTr = uC.mul(r*(float)Math.cos(theta));
		Vector3f cvTr = vC.mul(r*(float)Math.sin(theta));
		return cuTr.add(cvTr);
	}

    /**
     * Creates a new set of disks.
     * @param numDisks the number of disks to create
     * @param numPlanetsRange the number of planets per disk range
     * @param radiusRangeLow the inner radius of a disk range 
     * @param stellarDensityRange the stellar density range for inside the disk, this decides the outer radius of the disk
     * @param mRange the mass range for the planets inside the disk
     * @param densityRange the density range for the planets inside the disk
     * @param centerXRange the range for the center x of the disk
     * @param centerYRange the range for the center y of the disk
     * @param centerZRange the range for the center z of the disk
     * @param relativeVelocityX the range for the relative velocity x of the planets in the disk
     * @param relativeVelocityY the range for the relative velocity y of the planets in the disk
     * @param relativeVelocityZ the range for the relative velocity z of the planets in the disk
     * @param phiRange the phi range for the disk. Generally 0 to pi, larger or smaller values reverse direction of rotation
     * @param centerMassRange the range of the mass of the center body of the disk
     * @param centerDensityRange the range of the density of the center body of the disk
     * @param adherenceToPlaneRange the adherence to plane range for the disk
     * @param orbitalFactor the orbital velocity factor for the planets in the disk
     * @param giveOrbitalVelocity Give orbital velocity to the planets in the disk
     * @return the new set of disks
     */
	public static PlanetGenerator createSeveralDisks(int numDisks, int[] numPlanetsRange, float[] radiusRangeLow, float[] stellarDensityRange, float[] mRange, 
			float[] densityRange, float[] centerXRange, float[] centerYRange, float[] centerZRange, float[] relativeVelocityX, float[] relativeVelocityY, float[] relativeVelocityZ, 
			float[] phiRange, float[] centerMassRange, float[] centerDensityRange, 
			float[] adherenceToPlaneRange, float orbitalFactor, boolean giveOrbitalVelocity) {

		PlanetGenerator pg = new PlanetGenerator();

		for (int i = 0; i < numDisks; i++) {
			int num = randomInRange(numPlanetsRange);
			float[] radius = {randomInRange(radiusRangeLow), num/randomInRange(stellarDensityRange)};
			float[] mass = {randomInRange(mRange), randomInRange(mRange)};
			float[] density = {randomInRange(densityRange), randomInRange(densityRange)};
			float[] center = {randomInRange(centerXRange), randomInRange(centerYRange), randomInRange(centerZRange)};
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
    
    /**
     * Makes a new random disk.
     * @param num the number of planets in the disk
     * @param radius the radius range for the disk
     * @param mass the mass range for the planets in the disk
     * @param density the density range for the planets in the disk
     * @param center the center range for the disk
     * @param relativeVelocity the relative velocity range for the planets in the disk
     * @param phi the phi for the disk
     * @param centerMass the center mass for the disk
     * @param centerDensity the center density for the disk
     * @param adherenceToPlane the adherence to plane for the disk
     * @param orbitalFactor the orbital factor for the disk
     * @param ccw the ccw for the disk
     * @param giveOrbitalVelocity the give orbital velocity for the disk
     * @return the new random disk
     */
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

		Planet centerPlanet = new Planet(new Vector3f(center), new Vector3f(relativeVelocity), centerMass, centerDensity);
		PlanetGenerator pg2 = new PlanetGenerator(centerPlanet);
        PlanetGenerator ret = new PlanetGenerator(pg, pg2);
		return ret;
	}

    /**
     * Makes a new planet in orbit around a center body.
     * @param radius the radius range for the planet
     * @param mass the mass range for the planet
     * @param density the density range for the planet
     * @param center the center body
     * @return the new planet in orbit
     */
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

    /**
     * Makes a new set of planets in orbit around a center body.
     * @param num the number of planets to generate
     * @param mass the mass range for the planets
     * @param density the density range for the planets
     * @param center the center body
     * @param radius the radius range for the planets
     * @return the new set of planets in orbit
     */
	public static ArrayList<Planet> makeNewInOrbit(int num, float[] mass, float[] density, Planet center, float[] radius) {
		ArrayList<Planet> ret = new ArrayList<>();
		for (int i=0;i<num;i++) {
			ret.add(makeNewInOrbit(radius, mass, density, center));
		}
		return ret;
	}

    /**
     * Randomly selects a value from a range.
     * @param range the range
     * @param density the bias towards one side of the range. 1.0 is no bias, inf is all at lower value, 0 is all at higher value
     * @return the randomly selected value
     */
	private static float randomInRange(float[] range, double density) {
		return (float)(Math.pow(Math.random(), density)*(range[1]-range[0])+range[0]);
	}

    /**
     * Randomly selects a value from a range.
     * @param range the range
     * @return the randomly selected value
     */
	private static float randomInRange(float[] range) {
		return randomInRange(range, 1.0);
	}

    /**
     * Randomly selects a value from a range.
     * @param range the range
     * @return the randomly selected value
     */
	private static int randomInRange(int[] range) {
		float[] rangeFloat = {range[0], range[1]};
		return (int) randomInRange(rangeFloat, 1.0);
	}

    /**
     * Randomly selects a value from a range.
     * @param range the range
     * @param density the bias towards one side of the range. 1.0 is no bias, inf is all at lower value, 0 is all at higher value
     * @return the randomly selected value
     */
	private static int randomInRange(int[] range, double density) {
		float[] rangeFloat = {range[0], range[1]};
		return (int) randomInRange(rangeFloat, density);
	}
}
