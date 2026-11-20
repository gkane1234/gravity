package com.grumbo.simulation;

import org.junit.jupiter.api.Test;

import com.grumbo.simulation.SimulationSetup.ObjectType;
import com.grumbo.simulation.SimulationSetup.Preset;
import com.grumbo.simulation.SimulationSetup.SceneItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneItemSettingsTest {

    @Test
    void addedDiskShowsTheGeneratorRanges() {
        SimulationSetup setup = new SimulationSetup();
        setup.addDisk(20, 1f, 2f, 3f, 5f, 0.5f, 1f, 2f);
        SceneItem disk = setup.getItems().get(0);

        assertEquals(1f, disk.innerRadius, 0f);
        assertEquals(0.1f, disk.massMin, 0f);
        assertEquals(1f, disk.massMax, 0f);
        assertEquals(1f, disk.densityMin, 0f);
        assertEquals(1f, disk.densityMax, 0f);
        assertEquals(1f, disk.centerDensity, 0f);
        assertEquals(0.98f, disk.adherence, 0f);
        assertEquals(1f, disk.orbitalFactor, 0f);
        assertTrue(disk.giveOrbitalVelocity);
        assertFalse(disk.ccw);
        String text = disk.settingsText();
        assertTrue(text.contains("count 20"));
        assertTrue(text.contains("center 1.0, 2.0, 3.0"));
        assertTrue(text.contains("radius 5.0"));
        assertTrue(text.contains("tilt 0.0, 1.0, 0.0"));
        assertFalse(text.contains("particle mass"));
    }

    @Test
    void addedBoxAndBodyUseTheirGeneratorRanges() {
        SimulationSetup setup = new SimulationSetup();
        setup.addBox(10, 1f, 2f, 3f, 4f, 8f, 2f);
        setup.addSingle(4f, 5f, 6f, 7f, 8f);
        SceneItem box = setup.getItems().get(0);
        SceneItem body = setup.getItems().get(1);

        assertEquals(4f, box.massMin, 0f);
        assertEquals(12f, box.massMax, 0f);
        assertEquals(2f, box.densityMin, 0f);
        assertEquals(-0.01f, box.vxMin, 0f);
        assertEquals(0.01f, box.vxMax, 0f);
        assertTrue(box.settingsText().contains("count 10"));
        assertTrue(box.settingsText().contains("center 1.0, 2.0, 3.0"));
        assertTrue(box.settingsText().contains("extent 4.0"));
        assertFalse(box.settingsText().contains("velocity"));

        assertEquals(0f, body.vx, 0f);
        assertEquals(7f, body.mass, 0f);
        assertTrue(body.settingsText().contains("center 4.0, 5.0, 6.0"));
        assertTrue(body.settingsText().contains("mass 7.0"));
    }

    @Test
    void editingPlacementBecomesTheNewRandomizeBase() {
        SimulationSetup setup = new SimulationSetup();
        setup.applyPreset(Preset.SMALL_GALAXY);
        SceneItem disk = setup.getItems().get(0);
        assertTrue(setup.randomizeDisks(new java.util.Random(3)));
        disk = setup.getItems().get(0);
        float baseCx = disk.baseCx;

        SceneItem unchanged = disk.withDiskSettings(
            disk.label, disk.bodyCount, disk.cx, disk.cy, disk.cz,
            disk.innerRadius, disk.radius, disk.massMin, disk.massMax,
            disk.densityMin, disk.densityMax, disk.centerMass, disk.centerDensity,
            disk.nx, disk.ny, disk.nz, disk.vx, disk.vy, disk.vz,
            disk.adherence, disk.orbitalFactor, disk.ccw, disk.giveOrbitalVelocity);
        assertEquals(baseCx, unchanged.baseCx, 0f);

        SceneItem moved = disk.withDiskSettings(
            "Moved", disk.bodyCount, disk.cx + 25f, disk.cy, disk.cz,
            2f, disk.radius, 3f, 4f,
            disk.densityMin, disk.densityMax, disk.centerMass, 9f,
            0f, 1f, 0f, 1f, 0f, 0f,
            0.5f, 1.2f, true, false);
        assertEquals(disk.cx + 25f, moved.baseCx, 0f);
        assertEquals(baseCx, moved.baseCy, 0f);
        assertEquals(3f, moved.massMin, 0f);
        assertEquals(2f, moved.innerRadius, 0f);
        assertFalse(moved.giveOrbitalVelocity);
        assertEquals(ObjectType.DISK, moved.type);

        setup.replace(0, moved);
        assertEquals("Moved", setup.getItems().get(0).label);
        assertEquals(2f, setup.getItems().get(0).innerRadius, 0f);
    }

    @Test
    void duplicateInsertsACopyAfterTheObject() {
        SimulationSetup setup = new SimulationSetup();
        setup.addSingle(4f, 5f, 6f, 7f, 8f);
        setup.addBox(10, 1f, 2f, 3f, 4f, 8f, 2f);
        setup.duplicate(0);

        assertEquals(3, setup.getItems().size());
        assertEquals(ObjectType.SINGLE, setup.getItems().get(0).type);
        assertEquals(ObjectType.SINGLE, setup.getItems().get(1).type);
        assertEquals(ObjectType.BOX, setup.getItems().get(2).type);
        assertEquals("Body (1)", setup.getItems().get(1).label);
        assertEquals(4f, setup.getItems().get(1).cx, 0f);
        assertEquals(7f, setup.getItems().get(1).mass, 0f);

        setup.duplicate(1);
        assertEquals("Body (2)", setup.getItems().get(2).label);
        setup.duplicate(0);
        assertEquals("Body (3)", setup.getItems().get(1).label);
    }
}
