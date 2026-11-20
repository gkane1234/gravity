package com.grumbo.simulation;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.grumbo.simulation.SimulationSetup.ObjectType;
import com.grumbo.simulation.SimulationSetup.Preset;
import com.grumbo.simulation.SimulationSetup.SceneItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationSetupRandomizeTest {

    @Test
    void mergerFullKeepsCountsAndStaysInThePresetVolume() {
        SimulationSetup setup = new SimulationSetup();
        setup.applyPreset(Preset.MERGER_FULL);
        int bodies = setup.totalBodies();
        int disks = setup.getItems().size();
        float scatter = 0f;
        for (SceneItem item : setup.getItems()) {
            scatter = Math.max(scatter, length(item.baseCx, item.baseCy, item.baseCz));
        }

        assertTrue(setup.randomizeDisks(new Random(1)));
        assertEquals(bodies, setup.totalBodies());
        assertEquals(disks, setup.getItems().size());
        assertLayout(setup, scatter);

        assertTrue(setup.randomizeDisks(new Random(2)));
        assertEquals(bodies, setup.totalBodies());
        assertLayout(setup, scatter);

        setup.applyPreset(Preset.MERGER_FULL);
        SceneItem center = setup.getItems().get(0);
        assertEquals(0f, center.cx, 1e-3f);
        assertEquals(0f, center.cy, 1e-3f);
        assertEquals(0f, center.cz, 1e-3f);
        assertEquals(1000f, center.radius, 1e-3f);
        assertEquals(200_000f, center.centerMass, 1e-3f);
        assertEquals(0f, center.nx, 1e-4f);
        assertEquals(1f, center.ny, 1e-4f);
        assertEquals(0f, center.nz, 1e-4f);
        assertFalse(center.ccw);
    }

    @Test
    void solarSystemHasNoDisksToRandomize() {
        SimulationSetup setup = new SimulationSetup();
        setup.applyPreset(Preset.SOLAR_SYSTEM);
        assertFalse(setup.randomizeDisks(new Random(1)));
        assertEquals(1, setup.getItems().size());
        assertEquals(ObjectType.SOLAR_SYSTEM, setup.getItems().get(0).type);
    }

    @Test
    void randomizeLeavesNonDisksInPlace() {
        SimulationSetup setup = new SimulationSetup();
        setup.applyPreset(Preset.MERGER_SCALED);
        setup.addSingle(10f, 20f, 30f, 5f, 1f);
        assertTrue(setup.randomizeDisks(new Random(4)));
        boolean found = false;
        for (SceneItem item : setup.getItems()) {
            if (item.type == ObjectType.SINGLE) {
                found = true;
                assertEquals(10f, item.cx, 1e-3f);
                assertEquals(20f, item.cy, 1e-3f);
                assertEquals(30f, item.cz, 1e-3f);
            }
        }
        assertTrue(found);
    }

    private static void assertLayout(SimulationSetup setup, float scatter) {
        boolean moved = false;
        boolean tilted = false;
        for (SceneItem item : setup.getItems()) {
            assertEquals(ObjectType.DISK, item.type);
            float scale = item.radius / item.baseRadius;
            assertTrue(scale >= SimulationSetup.DISK_SCALE_MIN - 1e-4f, "scale " + scale);
            assertTrue(scale <= SimulationSetup.DISK_SCALE_MAX + 1e-4f, "scale " + scale);
            assertEquals(item.baseCenterMass * scale, item.centerMass, 0.1f);
            assertEquals(1f, length(item.nx, item.ny, item.nz), 1e-4f);
            assertTrue(length(item.cx, item.cy, item.cz) <= scatter * 1.001f);
            if (length(item.cx - item.baseCx, item.cy - item.baseCy, item.cz - item.baseCz) > 1f) {
                moved = true;
            }
            if (Math.abs(item.ny) < 0.99f) {
                tilted = true;
            }
        }
        assertTrue(moved);
        assertTrue(tilted);
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }
}
