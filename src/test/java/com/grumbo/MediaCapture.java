package com.grumbo;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.joml.Vector3f;
import org.lwjgl.stb.STBImageWrite;

import com.grumbo.simulation.GPUSimulation;
import com.grumbo.simulation.Render;
import com.grumbo.simulation.Settings;
import com.grumbo.simulation.SimulationSetup;

/**
 * Renders the scaled galaxy-merger layout once, then writes stills for the
 * render modes that have been used for the landing page.
 *
 * <p>The physics step is shared. Later stills only change render settings and
 * the camera. Body count is {@code GRAVITY_BODY_SCALE} times the scaled merger
 * (default 0.05). Output directory is {@code GRAVITY_MEDIA_DIR} or
 * {@code captures/media}.
 *
 * <p>The landing-page still is the scaled merger ({@code GRAVITY_BODY_SCALE=1}),
 * elevation 42°, azimuth 18°, distance 1000, mode {@code node-default}
 * (impNodeGlow, intensity 0.003, body distance 1000, depth 8, theta 16px),
 * cropped to the galaxies.
 *
 * <p>{@code BarnesHut.updateUnits} writes settings.json. This tool restores that file on exit.
 */
public final class MediaCapture {
    private MediaCapture() {}

    public static void main(String[] args) throws Exception {
        // BarnesHut.updateUnits saves the whole settings file. Snapshot and restore it.
        Path settingsFile = Path.of("src/main/resources/settings/settings.json");
        byte[] savedSettings = Files.exists(settingsFile) ? Files.readAllBytes(settingsFile) : null;
        try {
            run();
        } finally {
            if (savedSettings != null) {
                Files.write(settingsFile, savedSettings);
            }
        }
    }

    private static void run() throws Exception {
        float scale = Float.parseFloat(System.getenv().getOrDefault("GRAVITY_BODY_SCALE", "0.05"));
        Path outDir = Path.of(System.getenv().getOrDefault("GRAVITY_MEDIA_DIR", "captures/media"));
        Files.createDirectories(outDir);

        Settings settings = Settings.getInstance();
        settings.setWidth(1000);
        settings.setHeight(1000);
        settings.setDt(0f);
        settings.setTheta(0.77f);
        settings.setCameraScale(0.2f);
        settings.setShowRegions(false);
        settings.setDynamic("static");
        settings.setMergingCollisionOrNeither("none");
        settings.setNodeGlowActivateDist(0f);
        settings.setMinImpostorSize(1.0E-5f);
        settings.setFov(70f);
        settings.setRenderMode("impNodeGlow");

        SimulationSetup setup = new SimulationSetup();
        int center = Math.max(200, Math.round(80_000 * scale));
        int sat = Math.max(80, Math.round(25_000 * scale));
        setup.addDisk(center, 0, 0, 0, 1000, 0.5f, 1f, 200_000f);
        setup.addDisk(sat, 2000, 500, 0, 400, 0.5f, 1f, 1_000_000f);
        setup.addDisk(sat, -1800, -400, 200, 400, 0.5f, 1f, 1_000_000f);
        setup.addDisk(sat, 0, 2200, -300, 400, 0.5f, 1f, 1_000_000f);
        setup.addDisk(sat, 800, -2000, 100, 400, 0.5f, 1f, 1_000_000f);
        System.out.println("Media scene bodies≈" + setup.totalBodies() + " bounds=" + setup.suggestedSquareBounds());

        GPUSimulation sim = new GPUSimulation(
            setup.buildGenerator(),
            setup.suggestedSquareBounds(),
            Render.RenderMode.IMPOSTOR_WITH_NODE_GLOW,
            false
        );

        // Disks lie in the XZ plane. Elevation is degrees above that plane.
        float elev = (float) Math.toRadians(Double.parseDouble(System.getenv().getOrDefault("GRAVITY_ELEV_DEG", "42")));
        float az = (float) Math.toRadians(Double.parseDouble(System.getenv().getOrDefault("GRAVITY_AZ_DEG", "18")));
        Vector3f viewDir = new Vector3f(
            (float) (Math.cos(elev) * Math.sin(az)),
            (float) Math.sin(elev),
            (float) (Math.cos(elev) * Math.cos(az))
        ).normalize();
        float[] distances = parseFloats(System.getenv().getOrDefault("GRAVITY_DISTS", "1300,900,700"));
        Still[] stills = {
            new Still("node-default", "impNodeGlow", 1000f, 0.003f, 8, 16f),
            new Still("node-filled", "impNodeGlow", 40f, 0.008f, 10, 24f),
            new Still("node-fine", "impNodeGlow", 20f, 0.02f, 6, 12f),
            new Still("node-hero", "impNodeGlow", 35f, 0.012f, 8, 18f),
        };

        try {
            sim.beginCapture(1);
            int w = sim.captureWidth();
            int h = sim.captureHeight();
            for (float dist : distances) {
                Vector3f eye = new Vector3f(viewDir).mul(dist);
                settings.setCameraPos(eye);
                settings.setCameraFront(new Vector3f(eye).negate().normalize());
                settings.setCameraUp(new Vector3f(0f, 1f, 0f));
                for (Still still : stills) {
                    settings.setRenderMode(still.mode);
                    settings.setBodyRenderDistance(still.bodyDist);
                    settings.setNodeGlowIntensity(still.intensity);
                    settings.setNodeGlowMaxDepth(still.depth);
                    settings.setNodeGlowThetaPx(still.thetaPx);
                    ByteBuffer rgba = sim.renderStillRgba();
                    String name = String.format("%s_d%.0f.png", still.name, dist);
                    Path file = outDir.resolve(name);
                    STBImageWrite.stbi_flip_vertically_on_write(true);
                    boolean ok = STBImageWrite.stbi_write_png(file.toString(), w, h, 4, rgba, w * 4);
                    System.out.println((ok ? "wrote " : "FAILED ") + file.toAbsolutePath());
                }
            }
        } finally {
            sim.endCapture();
        }
    }

    private static float[] parseFloats(String text) {
        String[] parts = text.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i].trim());
        }
        return out;
    }

    private record Still(String name, String mode, float bodyDist, float intensity, int depth, float thetaPx) {}
}
