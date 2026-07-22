package com.grumbo.simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes initial conditions for a simulation before GPU buffers are allocated.
 * Items are built into a single {@link PlanetGenerator} on launch.
 */
public class SimulationSetup {

    public enum ObjectType {
        DISK,
        BOX,
        SINGLE,
        SOLAR_SYSTEM
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

        public SceneItem(ObjectType type, String label, int bodyCount,
                         float cx, float cy, float cz,
                         float radius, float mass, float density,
                         float centerMass, float halfExtent) {
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
        }

        public int estimatedBodies() {
            return switch (type) {
                case DISK -> bodyCount + 1; // disk particles + center
                case BOX, SINGLE -> bodyCount;
                case SOLAR_SYSTEM -> 24; // solar_system.json size
            };
        }

        @Override
        public String toString() {
            return switch (type) {
                case DISK -> String.format("Disk n=%d r=%.0f @ (%.0f,%.0f,%.0f)", bodyCount, radius, cx, cy, cz);
                case BOX -> String.format("Box n=%d extent=%.0f @ (%.0f,%.0f,%.0f)", bodyCount, halfExtent, cx, cy, cz);
                case SINGLE -> String.format("Body m=%.3g @ (%.0f,%.0f,%.0f)", mass, cx, cy, cz);
                case SOLAR_SYSTEM -> "Solar System (JSON)";
            };
        }
    }

    private final List<SceneItem> items = new ArrayList<>();
    private UnitSet unitSet = UnitSet.SOLAR_SYSTEM_SECOND;

    public List<SceneItem> getItems() {
        return items;
    }

    public UnitSet getUnitSet() {
        return unitSet;
    }

    public void setUnitSet(UnitSet unitSet) {
        this.unitSet = unitSet;
    }

    public void clear() {
        items.clear();
    }

    public void remove(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
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

    private static SceneItem disk(String label, int n, float cx, float cy, float cz,
                                  float radius, float mass, float density, float centerMass) {
        return new SceneItem(ObjectType.DISK, label, n, cx, cy, cz, radius, mass, density, centerMass, 0);
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
                new float[] {1f, item.radius},
                new float[] {item.mass * 0.2f, item.mass * 2f},
                new float[] {item.density, item.density},
                new float[] {item.cx, item.cy, item.cz},
                new float[] {0, 0, 0},
                (float) (Math.PI / 2),
                item.centerMass,
                Math.max(0.5f, item.density),
                0.98f,
                1f,
                false,
                true,
                unitSet
            );
            case BOX -> {
                float h = item.halfExtent;
                yield PlanetGenerator.makeNewRandomBox(
                    item.bodyCount,
                    new float[] {item.cx - h, item.cx + h},
                    new float[] {item.cy - h, item.cy + h},
                    new float[] {item.cz - h, item.cz + h},
                    new float[] {-0.01f, 0.01f},
                    new float[] {-0.01f, 0.01f},
                    new float[] {-0.01f, 0.01f},
                    new float[] {item.mass * 0.5f, item.mass * 1.5f},
                    new float[] {item.density, item.density}
                );
            }
            case SINGLE -> new PlanetGenerator(new Planet(
                item.cx, item.cy, item.cz, 0, 0, 0, item.mass, item.density, unitSet
            ));
            case SOLAR_SYSTEM -> {
                PlanetGenerator pg = PlanetGenerator.makeSolarSystem();
                if (pg == null) {
                    throw new IllegalStateException("Failed to load solar_system.json");
                }
                pg.changeUnitSet(UnitSet.SOLAR_SYSTEM_HOUR);
                yield pg;
            }
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
            };
            maxR = Math.max(maxR, reach);
        }
        return Math.max(20f, maxR);
    }

    /**
     * Scale-aware starting params from scene size / mass and {@link #unitSet}.
     * Softening is intentionally not suggested.
     * <p>
     * Uses scene-scale dynamical time (not the densest local core). Taking the
     * minimum period over satellite centers made galaxy/merger dt far too small.
     */
    public SuggestedSettings suggestSettings() {
        final float theta = 0.7f;
        final float bounds = suggestedSquareBounds();
        // Keep solar-system-sized scenes near the historical cameraScale≈10 look.
        final float cameraScale = clamp(500f / bounds, 1e-4f, 1e3f);

        if (items.isEmpty()) {
            return new SuggestedSettings(100f, theta, cameraScale);
        }

        double G = unitSet.gravitationalConstant();
        if (!(G > 0.0) || Double.isNaN(G) || Double.isInfinite(G)) {
            return new SuggestedSettings(100f, theta, cameraScale);
        }

        boolean onlySolar = true;
        double maxObjectR = 0.0;
        double totalM = 0.0;
        for (SceneItem item : items) {
            if (item.type != ObjectType.SOLAR_SYSTEM) {
                onlySolar = false;
            }
            double[] rm = characteristicRM(item);
            maxObjectR = Math.max(maxObjectR, rm[0]);
            totalM += rm[1];
        }

        // Solar-only: ~100 steps per Earth year (matches old default dt≈100 with HOUR units).
        if (onlySolar) {
            double period = 2.0 * Math.PI * Math.sqrt(1.0 / G); // R=1, M=1
            float dt = (float) clamp(period / 100.0, 1e-6, 1e12);
            return new SuggestedSettings(dt, theta, cameraScale);
        }

        // Scene half-extent so mergers aren't dominated by a small dense core radius.
        double R = Math.max(maxObjectR, bounds * 0.5);
        double M = Math.max(1e-12, totalM);
        double period = 2.0 * Math.PI * Math.sqrt((R * R * R) / (G * M));
        float dt;
        if (!Double.isFinite(period) || period <= 0.0) {
            dt = 100f;
        } else {
            // ~10 steps per scene crossing/orbit — viz-friendly for galaxies (still ~100 for solar above).
            dt = (float) clamp(period / 10.0, 1e-6, 1e12);
        }
        return new SuggestedSettings(dt, theta, cameraScale);
    }

    /**
     * Characteristic radius and mass contribution for scene-scale estimates (sim units).
     * Disk mass is dominated by the center; particle mass is a rough add-on.
     */
    private static double[] characteristicRM(SceneItem item) {
        return switch (item.type) {
            case DISK -> {
                double R = Math.max(1.0, item.radius);
                double M = Math.max(1e-12, item.centerMass + 0.5 * (double) item.bodyCount * item.mass);
                yield new double[] { R, M };
            }
            case BOX -> {
                double R = Math.max(1.0, item.halfExtent);
                double M = Math.max(1e-12, (double) item.bodyCount * item.mass);
                yield new double[] { R, M };
            }
            case SINGLE -> {
                double dist = Math.sqrt(item.cx * item.cx + item.cy * item.cy + item.cz * item.cz);
                double R = Math.max(1.0, dist > 0.0 ? dist : 1.0);
                double M = Math.max(1e-12, item.mass);
                yield new double[] { R, M };
            }
            case SOLAR_SYSTEM -> new double[] { 1.0, 1.0 };
        };
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static final class SuggestedSettings {
        public final float dt;
        public final float theta;
        public final float cameraScale;

        public SuggestedSettings(float dt, float theta, float cameraScale) {
            this.dt = dt;
            this.theta = theta;
            this.cameraScale = cameraScale;
        }

        public void apply(Settings settings) {
            settings.setDt(dt);
            settings.setTheta(theta);
            settings.setCameraScale(cameraScale);
        }

        @Override
        public String toString() {
            return String.format("dt=%.4g theta=%.2f cameraScale=%.4g", dt, theta, cameraScale);
        }
    }

    public static final class LaunchConfig {
        public final PlanetGenerator generator;
        public final float squareBounds;
        public final int bodyCount;
        public final SuggestedSettings suggestedSettings;

        public LaunchConfig(PlanetGenerator generator, float squareBounds, int bodyCount,
                            SuggestedSettings suggestedSettings) {
            this.generator = generator;
            this.squareBounds = squareBounds;
            this.bodyCount = bodyCount;
            this.suggestedSettings = suggestedSettings;
        }
    }

    public LaunchConfig toLaunchConfig() {
        PlanetGenerator generator = buildGenerator();
        SuggestedSettings suggested = suggestSettings();
        return new LaunchConfig(generator, suggestedSquareBounds(), generator.getNumPlanets(), suggested);
    }
}
