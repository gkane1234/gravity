package com.grumbo.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Vector3f;

/**
 * Composes initial conditions for a simulation before GPU buffers are allocated.
 * Items are built into a single {@link PlanetGenerator} on launch.
 */
public class SimulationSetup {

    public enum ObjectType {
        DISK,
        BOX,
        SINGLE,
        SOLAR_SYSTEM,
        SPICE_KERNEL
    }

    public enum Preset {
        EMPTY("Empty"),
        SOLAR_SYSTEM("Solar System"),
        SMALL_GALAXY("Small Galaxy"),
        MERGER_SCALED("Galaxy Merger (scaled)"),
        MERGER_FULL("Full Merger (~3.8M)");

        public final String label;
        Preset(String label) { this.label = label; }
    }

    public static final class SceneItem {
        public final ObjectType type;
        public final String label;
        public final int bodyCount;
        public final float cx, cy, cz;
        public final float radius;
        public final float mass;
        public final float density;
        public final float centerMass;
        public final float halfExtent; // box half-size
        /** Planets loaded from a SPICE kernel. Empty for every other object type. */
        public final List<Planet> planets;
        /** Layout this disk was created with. Randomize rolls back to these so repeats do not drift. */
        public final float baseCx, baseCy, baseCz, baseRadius, baseCenterMass;
        /** Unit disk normal. (0, 1, 0) is the flat XZ disk used by the presets. */
        public final float nx, ny, nz;
        /** Spin direction passed to the disk generator. */
        public final boolean ccw;
        /** Inner edge of a disk. Particles are drawn from here out to {@link #radius}. */
        public final float innerRadius;
        /** Particle mass range passed to the disk or box generator. */
        public final float massMin, massMax;
        /** Particle density range passed to the disk or box generator. */
        public final float densityMin, densityMax;
        /** Central body density, and the disk surface-density exponent. */
        public final float centerDensity;
        /** Velocity added to every body. For a disk this is the bulk velocity. For a single body it is the velocity. */
        public final float vx, vy, vz;
        public final float adherence;
        public final float orbitalFactor;
        public final boolean giveOrbitalVelocity;
        /** Box particle velocity ranges. */
        public final float vxMin, vxMax, vyMin, vyMax, vzMin, vzMax;

        public SceneItem(ObjectType type, String label, int bodyCount,
                         float cx, float cy, float cz,
                         float radius, float mass, float density,
                         float centerMass, float halfExtent) {
            this(type, label, bodyCount, cx, cy, cz, radius, mass, density, centerMass, halfExtent, List.of());
        }

        public SceneItem(ObjectType type, String label, int bodyCount,
                         float cx, float cy, float cz,
                         float radius, float mass, float density,
                         float centerMass, float halfExtent, List<Planet> planets) {
            this(type, label, bodyCount, cx, cy, cz, radius, mass, density, centerMass, halfExtent,
                cx, cy, cz, radius, centerMass, 0f, 1f, 0f, false, planets);
        }

        private SceneItem(ObjectType type, String label, int bodyCount,
                          float cx, float cy, float cz,
                          float radius, float mass, float density,
                          float centerMass, float halfExtent,
                          float baseCx, float baseCy, float baseCz, float baseRadius, float baseCenterMass,
                          float nx, float ny, float nz, boolean ccw, List<Planet> planets) {
            this(type, label, bodyCount, cx, cy, cz, radius, mass, density, centerMass, halfExtent,
                baseCx, baseCy, baseCz, baseRadius, baseCenterMass, nx, ny, nz, ccw,
                DISK_INNER_RADIUS,
                defaultMassMin(type, mass), defaultMassMax(type, mass),
                density, density,
                type == ObjectType.DISK ? Math.max(0.5f, density) : density,
                0f, 0f, 0f,
                0.98f, 1f, true,
                -0.01f, 0.01f, -0.01f, 0.01f, -0.01f, 0.01f,
                planets);
        }

        private SceneItem(ObjectType type, String label, int bodyCount,
                          float cx, float cy, float cz,
                          float radius, float mass, float density,
                          float centerMass, float halfExtent,
                          float baseCx, float baseCy, float baseCz, float baseRadius, float baseCenterMass,
                          float nx, float ny, float nz, boolean ccw,
                          float innerRadius,
                          float massMin, float massMax,
                          float densityMin, float densityMax,
                          float centerDensity,
                          float vx, float vy, float vz,
                          float adherence, float orbitalFactor, boolean giveOrbitalVelocity,
                          float vxMin, float vxMax, float vyMin, float vyMax, float vzMin, float vzMax,
                          List<Planet> planets) {
            this.type = type;
            this.label = label;
            this.bodyCount = bodyCount;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.radius = radius;
            this.mass = mass;
            this.density = density;
            this.centerMass = centerMass;
            this.halfExtent = halfExtent;
            this.baseCx = baseCx;
            this.baseCy = baseCy;
            this.baseCz = baseCz;
            this.baseRadius = baseRadius;
            this.baseCenterMass = baseCenterMass;
            float nlen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nlen < 1e-6f) {
                this.nx = 0f;
                this.ny = 1f;
                this.nz = 0f;
            } else {
                this.nx = nx / nlen;
                this.ny = ny / nlen;
                this.nz = nz / nlen;
            }
            this.ccw = ccw;
            this.innerRadius = innerRadius;
            this.massMin = massMin;
            this.massMax = massMax;
            this.densityMin = densityMin;
            this.densityMax = densityMax;
            this.centerDensity = centerDensity;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.adherence = adherence;
            this.orbitalFactor = orbitalFactor;
            this.giveOrbitalVelocity = giveOrbitalVelocity;
            this.vxMin = vxMin;
            this.vxMax = vxMax;
            this.vyMin = vyMin;
            this.vyMax = vyMax;
            this.vzMin = vzMin;
            this.vzMax = vzMax;
            this.planets = planets == null ? List.of() : planets;
        }

        private static float defaultMassMin(ObjectType type, float mass) {
            return switch (type) {
                case DISK -> mass * 0.2f;
                case BOX -> mass * 0.5f;
                default -> mass;
            };
        }

        private static float defaultMassMax(ObjectType type, float mass) {
            return switch (type) {
                case DISK -> mass * 2f;
                case BOX -> mass * 1.5f;
                default -> mass;
            };
        }

        /**
         * Same disk, with a new center, radius, core mass, and orientation.
         * Canonical placement stays on the base fields.
         */
        public SceneItem withDiskLayout(float cx, float cy, float cz, float radius, float centerMass,
                                        float nx, float ny, float nz, boolean ccw) {
            return new SceneItem(type, label, bodyCount, cx, cy, cz, radius, mass, density, centerMass, halfExtent,
                baseCx, baseCy, baseCz, baseRadius, baseCenterMass, nx, ny, nz, ccw,
                innerRadius, massMin, massMax, densityMin, densityMax, centerDensity,
                vx, vy, vz, adherence, orbitalFactor, giveOrbitalVelocity,
                vxMin, vxMax, vyMin, vyMax, vzMin, vzMax, planets);
        }

        /** Deep copy. SPICE planets are new objects so a later unit conversion cannot touch the copy. */
        public SceneItem copy() {
            return copyWithLabel(label);
        }

        /** Deep copy with a different list name. */
        public SceneItem copyWithLabel(String newLabel) {
            return new SceneItem(type, newLabel, bodyCount, cx, cy, cz, radius, mass, density, centerMass, halfExtent,
                baseCx, baseCy, baseCz, baseRadius, baseCenterMass, nx, ny, nz, ccw,
                innerRadius, massMin, massMax, densityMin, densityMax, centerDensity,
                vx, vy, vz, adherence, orbitalFactor, giveOrbitalVelocity,
                vxMin, vxMax, vyMin, vyMax, vzMin, vzMax, copyPlanets(planets));
        }

        /** A new disk whose randomize base is the placement passed in. */
        public static SceneItem newDisk(String label, int bodyCount,
                                        float cx, float cy, float cz,
                                        float innerRadius, float radius,
                                        float massMin, float massMax,
                                        float densityMin, float densityMax,
                                        float centerMass, float centerDensity,
                                        float nx, float ny, float nz,
                                        float vx, float vy, float vz,
                                        float adherence, float orbitalFactor,
                                        boolean ccw, boolean giveOrbitalVelocity) {
            float nominalMass = (massMin + massMax) * 0.5f;
            float nominalDensity = (densityMin + densityMax) * 0.5f;
            SceneItem seed = new SceneItem(ObjectType.DISK, label, bodyCount, cx, cy, cz, radius,
                nominalMass, nominalDensity, centerMass, 0);
            return seed.withDiskSettings(label, bodyCount, cx, cy, cz, innerRadius, radius,
                massMin, massMax, densityMin, densityMax, centerMass, centerDensity,
                nx, ny, nz, vx, vy, vz, adherence, orbitalFactor, ccw, giveOrbitalVelocity);
        }

        /**
         * Disk with edited generator inputs.
         * Placement fields that changed become the new randomize base. Unchanged ones keep the preset base.
         */
        public SceneItem withDiskSettings(String label, int bodyCount,
                                          float cx, float cy, float cz,
                                          float innerRadius, float radius,
                                          float massMin, float massMax,
                                          float densityMin, float densityMax,
                                          float centerMass, float centerDensity,
                                          float nx, float ny, float nz,
                                          float vx, float vy, float vz,
                                          float adherence, float orbitalFactor,
                                          boolean ccw, boolean giveOrbitalVelocity) {
            float nominalMass = (massMin + massMax) * 0.5f;
            float nominalDensity = (densityMin + densityMax) * 0.5f;
            return new SceneItem(type, label, bodyCount, cx, cy, cz, radius, nominalMass, nominalDensity, centerMass, halfExtent,
                changed(cx, this.cx) ? cx : baseCx,
                changed(cy, this.cy) ? cy : baseCy,
                changed(cz, this.cz) ? cz : baseCz,
                changed(radius, this.radius) ? radius : baseRadius,
                changed(centerMass, this.centerMass) ? centerMass : baseCenterMass,
                nx, ny, nz, ccw,
                innerRadius, massMin, massMax, densityMin, densityMax, centerDensity,
                vx, vy, vz, adherence, orbitalFactor, giveOrbitalVelocity,
                vxMin, vxMax, vyMin, vyMax, vzMin, vzMax, planets);
        }

        public SceneItem withBoxSettings(String label, int bodyCount,
                                         float cx, float cy, float cz, float halfExtent,
                                         float massMin, float massMax,
                                         float densityMin, float densityMax,
                                         float vxMin, float vxMax, float vyMin, float vyMax, float vzMin, float vzMax) {
            float nominalMass = (massMin + massMax) * 0.5f;
            float nominalDensity = (densityMin + densityMax) * 0.5f;
            return new SceneItem(type, label, bodyCount, cx, cy, cz, radius, nominalMass, nominalDensity, centerMass, halfExtent,
                baseCx, baseCy, baseCz, baseRadius, baseCenterMass, nx, ny, nz, ccw,
                innerRadius, massMin, massMax, densityMin, densityMax, centerDensity,
                vx, vy, vz, adherence, orbitalFactor, giveOrbitalVelocity,
                vxMin, vxMax, vyMin, vyMax, vzMin, vzMax, planets);
        }

        public SceneItem withSingleSettings(String label,
                                            float cx, float cy, float cz,
                                            float vx, float vy, float vz,
                                            float mass, float density) {
            return new SceneItem(type, label, 1, cx, cy, cz, radius, mass, density, centerMass, halfExtent,
                baseCx, baseCy, baseCz, baseRadius, baseCenterMass, nx, ny, nz, ccw,
                innerRadius, mass, mass, density, density, density,
                vx, vy, vz, adherence, orbitalFactor, giveOrbitalVelocity,
                vxMin, vxMax, vyMin, vyMax, vzMin, vzMax, planets);
        }

        /** Same kernel file, with edited bodies. Radius and total mass are recomputed for the timestep hint. */
        public SceneItem withPlanets(List<Planet> edited, UnitSet sceneUnits) {
            List<Planet> planets = edited == null ? List.of() : edited;
            double len = sceneUnits.len();
            double massUnit = sceneUnits.mass();
            double maxR = 1.0;
            double totalM = 0.0;
            for (Planet planet : planets) {
                double x = planet.position.x;
                double y = planet.position.y;
                double z = planet.position.z;
                maxR = Math.max(maxR, Math.sqrt(x * x + y * y + z * z) / len);
                totalM += planet.mass / massUnit;
            }
            return new SceneItem(type, label, planets.size(), 0, 0, 0, (float) maxR, (float) totalM, 0, 0, 0,
                baseCx, baseCy, baseCz, baseRadius, baseCenterMass, nx, ny, nz, ccw,
                innerRadius, massMin, massMax, densityMin, densityMax, centerDensity,
                vx, vy, vz, adherence, orbitalFactor, giveOrbitalVelocity,
                vxMin, vxMax, vyMin, vyMax, vzMin, vzMax, planets);
        }

        private static boolean changed(float next, float previous) {
            return Float.floatToIntBits(next) != Float.floatToIntBits(previous);
        }

        /** Short list line. The full generator inputs stay in the edit dialog. */
        public String settingsText() {
            String center = num(cx) + ", " + num(cy) + ", " + num(cz);
            return switch (type) {
                case DISK -> String.join("\n",
                    label,
                    "count " + bodyCount,
                    "center " + center,
                    "radius " + num(radius),
                    "tilt " + num(nx) + ", " + num(ny) + ", " + num(nz));
                case BOX -> String.join("\n",
                    label,
                    "count " + bodyCount,
                    "center " + center,
                    "extent " + num(halfExtent));
                case SINGLE -> String.join("\n",
                    label,
                    "center " + center,
                    "mass " + num(mass));
                case SOLAR_SYSTEM -> "Solar System (JSON)";
                case SPICE_KERNEL -> "BSP " + label + "\ncount " + planets.size();
            };
        }

        private static String num(float v) {
            return Float.toString(v);
        }

        public int estimatedBodies() {
            return switch (type) {
                case DISK -> bodyCount + 1; // disk particles + center
                case BOX, SINGLE -> bodyCount;
                case SOLAR_SYSTEM -> 24; // solar_system.json size
                case SPICE_KERNEL -> planets.size();
            };
        }

        @Override
        public String toString() {
            return switch (type) {
                case DISK -> String.format("Disk n=%d r=%.0f @ (%.0f,%.0f,%.0f)", bodyCount, radius, cx, cy, cz);
                case BOX -> String.format("Box n=%d extent=%.0f @ (%.0f,%.0f,%.0f)", bodyCount, halfExtent, cx, cy, cz);
                case SINGLE -> String.format("Body m=%.3g @ (%.0f,%.0f,%.0f)", mass, cx, cy, cz);
                case SOLAR_SYSTEM -> "Solar System (JSON)";
                case SPICE_KERNEL -> "BSP " + label + " n=" + planets.size();
            };
        }
    }

    private final List<SceneItem> items = new ArrayList<>();
    private UnitSet unitSet = UnitSet.SOLAR_SYSTEM_SECOND;
    private Preset lastPreset;

    public Preset getLastPreset() {
        return lastPreset;
    }

    public List<SceneItem> getItems() {
        return items;
    }

    public UnitSet getUnitSet() {
        return unitSet;
    }

    public void setUnitSet(UnitSet unitSet) {
        this.unitSet = unitSet;
    }

    /**
     * Independent scene. Planet positions, velocities, mass, density, and unit set are copied
     * so {@link Planet#changeUnitSet} on a body that is about to be uploaded cannot convert the snapshot too.
     */
    public SimulationSetup copy() {
        SimulationSetup copy = new SimulationSetup();
        copy.restoreFrom(this);
        return copy;
    }

    public void restoreFrom(SimulationSetup other) {
        items.clear();
        unitSet = other.unitSet;
        lastPreset = other.lastPreset;
        for (SceneItem item : other.items) {
            items.add(item.copy());
        }
    }

    private static List<Planet> copyPlanets(List<Planet> planets) {
        if (planets == null || planets.isEmpty()) {
            return List.of();
        }
        List<Planet> copy = new ArrayList<>(planets.size());
        for (Planet planet : planets) {
            Planet next = new Planet(
                new Vector3f(planet.position),
                new Vector3f(planet.velocity),
                planet.mass,
                planet.density,
                planet.getUnitSet()
            );
            next.name = planet.name;
            copy.add(next);
        }
        return copy;
    }

    public void clear() {
        items.clear();
        lastPreset = null;
    }

    public void addItem(SceneItem item) {
        if (item != null) {
            items.add(item);
        }
    }

    public void remove(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
    }

    public void replace(int index, SceneItem item) {
        if (item != null && index >= 0 && index < items.size()) {
            items.set(index, item);
        }
    }

    /** Inserts a deep copy immediately after the object, named with the next "(1)", "(2)", and so on. */
    public void duplicate(int index) {
        if (index >= 0 && index < items.size()) {
            SceneItem source = items.get(index);
            items.add(index + 1, source.copyWithLabel(nextDuplicateLabel(source.label)));
        }
    }

    /**
     * "Disk" becomes "Disk (1)", then "Disk (2)".
     * A name that already ends in " (n)" uses the part before that as the base.
     */
    private String nextDuplicateLabel(String label) {
        String base = label.replaceFirst(" \\(\\d+\\)$", "");
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (SceneItem item : items) {
            java.util.regex.Matcher match = java.util.regex.Pattern
                .compile("^" + java.util.regex.Pattern.quote(base) + " \\((\\d+)\\)$")
                .matcher(item.label);
            if (match.matches()) {
                used.add(Integer.parseInt(match.group(1)));
            }
        }
        int n = 1;
        while (used.contains(n)) {
            n++;
        }
        return base + " (" + n + ")";
    }

    public int totalBodies() {
        int n = 0;
        for (SceneItem item : items) {
            n += item.estimatedBodies();
        }
        return n;
    }

    public void applyPreset(Preset preset) {
        clear();
        lastPreset = preset;
        switch (preset) {
            case EMPTY -> { }
            case SOLAR_SYSTEM -> {
                unitSet = UnitSet.SOLAR_SYSTEM_HOUR;
                items.add(new SceneItem(ObjectType.SOLAR_SYSTEM, "Solar System", 0, 0, 0, 0, 0, 0, 0, 0, 0));
            }
            case SMALL_GALAXY -> {
                unitSet = UnitSet.SOLAR_SYSTEM_SECOND;
                items.add(disk("Galaxy", 50_000, 0, 0, 0, 800, 0.5f, 1f, 50_000f));
            }
            case MERGER_SCALED -> {
                unitSet = UnitSet.SOLAR_SYSTEM_SECOND;
                items.add(disk("Center", 80_000, 0, 0, 0, 1000, 0.5f, 1f, 200_000f));
                items.add(disk("Sat A", 25_000, 2000, 500, 0, 400, 0.5f, 1f, 1_000_000f));
                items.add(disk("Sat B", 25_000, -1800, -400, 200, 400, 0.5f, 1f, 1_000_000f));
                items.add(disk("Sat C", 25_000, 0, 2200, -300, 400, 0.5f, 1f, 1_000_000f));
                items.add(disk("Sat D", 25_000, 800, -2000, 100, 400, 0.5f, 1f, 1_000_000f));
            }
            case MERGER_FULL -> {
                unitSet = UnitSet.SOLAR_SYSTEM_SECOND;
                items.add(disk("Center", 600_000, 0, 0, 0, 1000, 0.5f, 1f, 200_000f));
                float[][] offs = {
                    {2500, 500, 0}, {-2500, -400, 200}, {0, 2800, -200}, {0, -2800, 100},
                    {2000, 2000, 300}, {-2000, -2000, -100}, {2000, -2000, 0}, {-2000, 2000, 0},
                    {1500, 0, 2000}, {-1500, 0, -2000}, {0, 1500, 2500}, {0, -1500, -2500},
                    {1000, 1000, 1000}
                };
                for (int i = 0; i < offs.length; i++) {
                    items.add(disk("Sat " + (i + 1), 250_000, offs[i][0], offs[i][1], offs[i][2], 400, 0.5f, 1f, 1_000_000f));
                }
            }
        }
    }

    public void addDisk(int numBodies, float cx, float cy, float cz, float radius,
                        float mass, float density, float centerMass) {
        items.add(disk("Disk", numBodies, cx, cy, cz, radius, mass, density, centerMass));
    }

    public void addBox(int numBodies, float cx, float cy, float cz, float halfExtent,
                       float mass, float density) {
        items.add(new SceneItem(ObjectType.BOX, "Box", numBodies, cx, cy, cz, 0, mass, density, 0, halfExtent));
    }

    public void addSingle(float cx, float cy, float cz, float mass, float density) {
        items.add(new SceneItem(ObjectType.SINGLE, "Body", 1, cx, cy, cz, 0, mass, density, 0, 0));
    }

    public void addSolarSystem() {
        unitSet = UnitSet.SOLAR_SYSTEM_HOUR;
        items.add(new SceneItem(ObjectType.SOLAR_SYSTEM, "Solar System", 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    /**
     * Adds bodies read from a SPICE kernel. Positions are in {@link UnitSet#METRIC}
     * and are converted to the scene unit set when the generator is built.
     * {@code mass} on the scene item is the total mass in the current unit set, used for the timestep hint.
     * A sun-dominated kernel (total mass above half a solar mass, in solar-mass unit sets) uses an Earth-scale
     * radius for that hint so the outer planets do not push the timestep out to decades.
     */
    public void addSpiceBodies(List<Planet> planets, String fileName) {
        if (planets == null || planets.isEmpty()) {
            return;
        }
        double len = unitSet.len();
        double massUnit = unitSet.mass();
        double maxR = 1.0;
        double totalM = 0.0;
        for (Planet planet : planets) {
            double x = planet.position.x;
            double y = planet.position.y;
            double z = planet.position.z;
            maxR = Math.max(maxR, Math.sqrt(x * x + y * y + z * z) / len);
            totalM += planet.mass / massUnit;
        }
        items.add(new SceneItem(
            ObjectType.SPICE_KERNEL, fileName, planets.size(),
            0, 0, 0, (float) maxR, (float) totalM, 0, 0, 0, planets
        ));
    }

    private static SceneItem disk(String label, int n, float cx, float cy, float cz,
                                  float radius, float mass, float density, float centerMass) {
        return new SceneItem(ObjectType.DISK, label, n, cx, cy, cz, radius, mass, density, centerMass, 0);
    }

    /** Radius scale applied by {@link #randomizeDisks}. */
    static final float DISK_SCALE_MIN = 0.75f;
    static final float DISK_SCALE_MAX = 1.25f;
    /**
     * Inner edge passed to the disk generator. Particles are drawn from here out to the disk radius,
     * so this is the fastest orbit the scene actually integrates.
     */
    static final float DISK_INNER_RADIUS = 1f;

    /**
     * Moves every disk to a new spot in the preset's volume, scales its radius
     * and core mass by a small factor, and gives it a random plane and spin.
     * Body counts stay the same. A second call uses the original preset layout,
     * not the previous roll. Returns false when the scene has no disks.
     */
    public boolean randomizeDisks() {
        return randomizeDisks(new Random());
    }

    public boolean randomizeDisks(Random rng) {
        float scatter = 0f;
        int diskCount = 0;
        for (SceneItem item : items) {
            if (item.type != ObjectType.DISK) {
                continue;
            }
            diskCount++;
            scatter = Math.max(scatter, length(item.baseCx, item.baseCy, item.baseCz));
        }
        if (diskCount == 0) {
            return false;
        }
        if (scatter < 1f) {
            scatter = 0f;
            for (SceneItem item : items) {
                if (item.type == ObjectType.DISK) {
                    scatter = Math.max(scatter, item.baseRadius);
                }
            }
        }

        float[][] placed = new float[diskCount][3];
        float[] placedRadius = new float[diskCount];
        int placedCount = 0;
        List<SceneItem> next = new ArrayList<>(items.size());
        for (SceneItem item : items) {
            if (item.type != ObjectType.DISK) {
                next.add(item);
                continue;
            }
            float scale = DISK_SCALE_MIN + rng.nextFloat() * (DISK_SCALE_MAX - DISK_SCALE_MIN);
            float radius = Math.max(1f, item.baseRadius * scale);
            float centerMass = item.baseCenterMass * scale;
            float[] pos = sampleDiskCenter(rng, scatter, placed, placedRadius, placedCount, radius);
            placed[placedCount][0] = pos[0];
            placed[placedCount][1] = pos[1];
            placed[placedCount][2] = pos[2];
            placedRadius[placedCount] = radius;
            placedCount++;
            float[] normal = randomUnit(rng);
            next.add(item.withDiskLayout(pos[0], pos[1], pos[2], radius, centerMass,
                normal[0], normal[1], normal[2], rng.nextBoolean()));
        }
        items.clear();
        items.addAll(next);
        return true;
    }

    /**
     * Uniform point in the ball. Rejects centers that sit on top of a disk already placed.
     * After several misses, keeps the candidate farthest from its nearest neighbor.
     */
    private static float[] sampleDiskCenter(Random rng, float scatter,
                                            float[][] placed, float[] placedRadius, int placedCount,
                                            float radius) {
        float[] best = null;
        float bestGap = -1f;
        for (int attempt = 0; attempt < 40; attempt++) {
            float[] pos = randomInBall(rng, scatter);
            if (placedCount == 0) {
                return pos;
            }
            float nearest = Float.MAX_VALUE;
            boolean clear = true;
            for (int i = 0; i < placedCount; i++) {
                float dist = length(pos[0] - placed[i][0], pos[1] - placed[i][1], pos[2] - placed[i][2]);
                nearest = Math.min(nearest, dist);
                if (dist < 0.45f * (radius + placedRadius[i])) {
                    clear = false;
                }
            }
            if (clear) {
                return pos;
            }
            if (nearest > bestGap) {
                bestGap = nearest;
                best = pos;
            }
        }
        return best;
    }

    private static float[] randomInBall(Random rng, float radius) {
        float[] dir = randomUnit(rng);
        float r = radius * (float) Math.cbrt(rng.nextDouble());
        return new float[] { dir[0] * r, dir[1] * r, dir[2] * r };
    }

    private static float[] randomUnit(Random rng) {
        double z = rng.nextDouble() * 2.0 - 1.0;
        double theta = rng.nextDouble() * Math.PI * 2.0;
        double rho = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return new float[] {
            (float) (rho * Math.cos(theta)),
            (float) z,
            (float) (rho * Math.sin(theta))
        };
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Builds the generator that will size GPU buffers.
     */
    public PlanetGenerator buildGenerator() {
        if (items.isEmpty()) {
            throw new IllegalStateException("Scene is empty — add a preset or at least one object.");
        }
        PlanetGenerator combined = null;
        for (SceneItem item : items) {
            PlanetGenerator part = buildItem(item);
            if (combined == null) {
                combined = part;
            } else {
                combined = new PlanetGenerator(combined, part);
            }
        }
        if (combined.getUnitSet() != unitSet) {
            combined.changeUnitSet(unitSet);
        }
        return combined;
    }

    private PlanetGenerator buildItem(SceneItem item) {
        return switch (item.type) {
            case DISK -> PlanetGenerator.makeNewRandomDisk(
                item.bodyCount,
                new float[] {item.innerRadius, item.radius},
                new float[] {item.massMin, item.massMax},
                new float[] {item.densityMin, item.densityMax},
                new float[] {item.cx, item.cy, item.cz},
                new float[] {item.vx, item.vy, item.vz},
                new Vector3f(item.nx, item.ny, item.nz),
                item.centerMass,
                item.centerDensity,
                item.adherence,
                item.orbitalFactor,
                item.ccw,
                item.giveOrbitalVelocity,
                unitSet
            );
            case BOX -> {
                float h = item.halfExtent;
                yield PlanetGenerator.makeNewRandomBox(
                    item.bodyCount,
                    new float[] {item.cx - h, item.cx + h},
                    new float[] {item.cy - h, item.cy + h},
                    new float[] {item.cz - h, item.cz + h},
                    new float[] {item.vxMin, item.vxMax},
                    new float[] {item.vyMin, item.vyMax},
                    new float[] {item.vzMin, item.vzMax},
                    new float[] {item.massMin, item.massMax},
                    new float[] {item.densityMin, item.densityMax}
                );
            }
            case SINGLE -> new PlanetGenerator(new Planet(
                item.cx, item.cy, item.cz, item.vx, item.vy, item.vz, item.mass, item.density, unitSet
            ));
            case SOLAR_SYSTEM -> {
                PlanetGenerator pg = PlanetGenerator.makeSolarSystem();
                if (pg == null) {
                    throw new IllegalStateException("Failed to load solar_system.json");
                }
                pg.changeUnitSet(UnitSet.SOLAR_SYSTEM_HOUR);
                yield pg;
            }
            case SPICE_KERNEL -> new PlanetGenerator(item.planets, UnitSet.METRIC);
        };
    }

    /**
     * Rough simulation bounds from placed objects.
     */
    public float suggestedSquareBounds() {
        float maxR = 10f;
        for (SceneItem item : items) {
            float reach = switch (item.type) {
                case DISK -> Math.max(Math.abs(item.cx), Math.max(Math.abs(item.cy), Math.abs(item.cz))) + item.radius * 1.5f;
                case BOX -> Math.max(Math.abs(item.cx), Math.max(Math.abs(item.cy), Math.abs(item.cz))) + item.halfExtent * 1.5f;
                case SINGLE -> Math.max(Math.abs(item.cx), Math.max(Math.abs(item.cy), Math.abs(item.cz))) + 10f;
                case SOLAR_SYSTEM -> 50f;
                case SPICE_KERNEL -> item.radius;
            };
            maxR = Math.max(maxR, reach);
        }
        return Math.max(20f, maxR);
    }

    /**
     * Euclidean distance from the origin to the farthest point of the scene.
     * The origin is the center of this sphere, so a camera that frames it
     * also keeps 0,0,0 in view.
     */
    public float suggestedViewRadius() {
        float maxR = 1f;
        for (SceneItem item : items) {
            float center = length(item.cx, item.cy, item.cz);
            float reach = switch (item.type) {
                case DISK -> center + item.radius;
                case BOX -> center + item.halfExtent * (float) Math.sqrt(3.0);
                case SINGLE -> Math.max(1f, center);
                case SOLAR_SYSTEM -> 50f;
                case SPICE_KERNEL -> Math.max(1f, item.radius);
            };
            maxR = Math.max(maxR, reach);
        }
        return maxR;
    }

    /**
     * Scale-aware starting params from scene size / mass and {@link #unitSet}.
     * Softening is intentionally not suggested.
     * <p>
     * dt is 100 steps along the fastest orbit the generator places. Disk particles
     * start at {@link #DISK_INNER_RADIUS}, not at the outer disk edge. An outer-scene
     * period (or a fixed 500,000s merger step) is many of those inner revolutions per step.
     */
    public SuggestedSettings suggestSettings() {
        final float theta = 0.7f;
        final float bounds = suggestedSquareBounds();
        // Keep solar-system-sized scenes near the historical cameraScale≈10 look.
        final float cameraScale = clamp(500f / bounds, 1e-4f, 1e3f);
        float dt = suggestedDt();
        if (lastPreset == Preset.MERGER_SCALED || lastPreset == Preset.MERGER_FULL) {
            return SuggestedSettings.mergerDefaults(dt);
        }
        return new SuggestedSettings(dt, theta, cameraScale);
    }

    /** Same step count the solar preset uses (~100 per Earth year, dt near 100 in hour units). */
    private static final double STEPS_PER_ORBIT = 100.0;

    private float suggestedDt() {
        if (items.isEmpty()) {
            return 100f;
        }
        double G = unitSet.gravitationalConstant();
        if (!(G > 0.0) || !Double.isFinite(G)) {
            return 100f;
        }
        double minPeriod = Double.POSITIVE_INFINITY;
        for (SceneItem item : items) {
            double period = orbitalPeriod(item, G);
            if (period > 0.0 && Double.isFinite(period)) {
                minPeriod = Math.min(minPeriod, period);
            }
        }
        if (!Double.isFinite(minPeriod)) {
            return 100f;
        }
        return (float) (minPeriod / STEPS_PER_ORBIT);
    }

    /**
     * Orbital period of the fastest motion this object contributes, in the scene time unit.
     * Disks use the inner generator edge and the central mass. A sun-dominated SPICE kernel
     * uses 1 length unit so the outer planets do not stretch the step out to decades.
     */
    private static double orbitalPeriod(SceneItem item, double G) {
        double[] orbit = switch (item.type) {
            case DISK -> {
                double mass = item.centerMass > 0f
                    ? item.centerMass
                    : 0.5 * (double) item.bodyCount * Math.max(0f, item.mass);
                yield new double[] { Math.max(1e-6, item.innerRadius), mass };
            }
            case BOX -> new double[] {
                Math.max(1.0, item.halfExtent),
                (double) item.bodyCount * item.mass
            };
            case SINGLE -> {
                double dist = Math.sqrt(item.cx * item.cx + item.cy * item.cy + item.cz * item.cz);
                yield new double[] { Math.max(1.0, dist), item.mass };
            }
            case SOLAR_SYSTEM -> new double[] { 1.0, 1.0 };
            case SPICE_KERNEL -> {
                double radius = item.mass > 0.5 ? 1.0 : Math.max(1e-6, item.radius);
                yield new double[] { radius, item.mass };
            }
        };
        double radius = orbit[0];
        double mass = orbit[1];
        if (!(radius > 0.0) || !(mass > 0.0) || !(G > 0.0)) {
            return Double.NaN;
        }
        return 2.0 * Math.PI * Math.sqrt((radius * radius * radius) / (G * mass));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static final class SuggestedSettings {
        public final float dt;
        public final float theta;
        public final float cameraScale;
        public final Float nodeGlowIntensity;
        public final Float bodyRenderDistance;
        public final String renderMode;

        public SuggestedSettings(float dt, float theta, float cameraScale) {
            this(dt, theta, cameraScale, null, null, null);
        }

        public SuggestedSettings(float dt, float theta, float cameraScale,
                                 Float nodeGlowIntensity, Float bodyRenderDistance, String renderMode) {
            this.dt = dt;
            this.theta = theta;
            this.cameraScale = cameraScale;
            this.nodeGlowIntensity = nodeGlowIntensity;
            this.bodyRenderDistance = bodyRenderDistance;
            this.renderMode = renderMode;
        }

        /** Visual defaults for galaxy merger presets. {@code dt} is the scene's own orbital step. */
        public static SuggestedSettings mergerDefaults(float dt) {
            return new SuggestedSettings(
                dt, 0.77f, 0.2f,
                0.001f, 50f, "impNodeGlow"
            );
        }

        public void apply(Settings settings) {
            settings.setDt(dt);
            settings.setTheta(theta);
            settings.setCameraScale(cameraScale);
            if (nodeGlowIntensity != null) {
                settings.setNodeGlowIntensity(nodeGlowIntensity);
            }
            if (bodyRenderDistance != null) {
                settings.setBodyRenderDistance(bodyRenderDistance);
            }
            if (renderMode != null) {
                settings.setRenderMode(renderMode);
            }
        }

        @Override
        public String toString() {
            String base = String.format("dt=%.4g theta=%.2f cameraScale=%.4g", dt, theta, cameraScale);
            if (renderMode != null) {
                base += " renderMode=" + renderMode;
            }
            if (nodeGlowIntensity != null) {
                base += String.format(" nodeGlowIntensity=%.4g", nodeGlowIntensity);
            }
            if (bodyRenderDistance != null) {
                base += String.format(" bodyRenderDist=%.4g", bodyRenderDistance);
            }
            return base;
        }
    }

    public static final class LaunchConfig {
        public final PlanetGenerator generator;
        public final float squareBounds;
        public final int bodyCount;
        public final SuggestedSettings suggestedSettings;
        /** Sphere around the origin that contains the scene. */
        public final float viewRadius;

        public LaunchConfig(PlanetGenerator generator, float squareBounds, int bodyCount,
                            SuggestedSettings suggestedSettings) {
            this(generator, squareBounds, bodyCount, suggestedSettings, squareBounds);
        }

        public LaunchConfig(PlanetGenerator generator, float squareBounds, int bodyCount,
                            SuggestedSettings suggestedSettings, float viewRadius) {
            this.generator = generator;
            this.squareBounds = squareBounds;
            this.bodyCount = bodyCount;
            this.suggestedSettings = suggestedSettings;
            this.viewRadius = viewRadius;
        }
    }

    public LaunchConfig toLaunchConfig() {
        PlanetGenerator generator = buildGenerator();
        SuggestedSettings suggested = suggestSettings();
        return new LaunchConfig(generator, suggestedSquareBounds(), generator.getNumPlanets(), suggested, suggestedViewRadius());
    }
}
