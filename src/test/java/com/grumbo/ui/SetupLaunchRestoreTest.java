package com.grumbo.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.grumbo.simulation.Planet;
import com.grumbo.simulation.SimulationSetup;
import com.grumbo.simulation.SimulationSetup.ObjectType;
import com.grumbo.simulation.SimulationSetup.Preset;
import com.grumbo.simulation.SimulationSetup.SceneItem;
import com.grumbo.simulation.UnitSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupLaunchRestoreTest {

    @BeforeEach
    @AfterEach
    void clearLaunchMemory() {
        SetupScreen.forgetLaunch();
    }

    @Test
    void firstOpenUsesTheFullMerger() {
        SetupScreen screen = new SetupScreen();
        screen.prepareInitialScene();
        assertEquals(Preset.MERGER_FULL, screen.scene().getLastPreset());
        assertSame(UnitSet.SOLAR_SYSTEM_SECOND, screen.scene().getUnitSet());
        assertEquals(14, screen.scene().getItems().size());
    }

    @Test
    void returnToSetupRestoresTheLaunchedScene() {
        SetupScreen launched = new SetupScreen();
        launched.scene().applyPreset(Preset.MERGER_FULL);
        assertTrue(launched.scene().randomizeDisks(new Random(7)));

        Planet spice = new Planet(4f, 5f, 6f, 0.1f, 0.2f, 0.3f, 7f, 8f, UnitSet.METRIC);
        spice.name = 399;
        List<Planet> planets = new ArrayList<>();
        planets.add(spice);
        launched.scene().addSpiceBodies(planets, "demo.bsp");

        String[] fields = {"1", "2", "3", "40", "5", "6", "7", "8", "9"};
        launched.setAddForm(ObjectType.BOX, fields);
        List<SceneItem> launchedItems = new ArrayList<>(launched.scene().getItems());
        launched.rememberLaunch();

        spice.changeUnitSet(UnitSet.SOLAR_SYSTEM_HOUR);
        launched.scene().applyPreset(Preset.SOLAR_SYSTEM);

        SetupScreen again = new SetupScreen();
        again.prepareInitialScene();

        assertEquals(ObjectType.BOX, again.addObjectType());
        assertArrayEquals(fields, again.addObjectFields());
        assertEquals(Preset.MERGER_FULL, again.scene().getLastPreset());
        assertSame(UnitSet.SOLAR_SYSTEM_SECOND, again.scene().getUnitSet());
        assertEquals(launchedItems.size(), again.scene().getItems().size());

        boolean movedDisk = false;
        for (int i = 0; i < launchedItems.size(); i++) {
            SceneItem original = launchedItems.get(i);
            SceneItem restored = again.scene().getItems().get(i);
            assertEquals(original.type, restored.type);
            assertEquals(original.label, restored.label);
            assertEquals(original.cx, restored.cx, 0f);
            assertEquals(original.cy, restored.cy, 0f);
            assertEquals(original.cz, restored.cz, 0f);
            assertEquals(original.radius, restored.radius, 0f);
            assertEquals(original.centerMass, restored.centerMass, 0f);
            assertEquals(original.nx, restored.nx, 0f);
            assertEquals(original.ny, restored.ny, 0f);
            assertEquals(original.nz, restored.nz, 0f);
            assertEquals(original.ccw, restored.ccw);
            assertEquals(original.baseCx, restored.baseCx, 0f);
            assertEquals(original.baseRadius, restored.baseRadius, 0f);
            if (original.type == ObjectType.DISK
                && (original.cx != original.baseCx || original.ny != 1f)) {
                movedDisk = true;
            }
            if (original.type == ObjectType.SPICE_KERNEL) {
                assertEquals(1, restored.planets.size());
                Planet copy = restored.planets.get(0);
                assertNotSame(spice, copy);
                assertEquals(399, copy.name);
                assertEquals(4f, copy.position.x, 0f);
                assertEquals(5f, copy.position.y, 0f);
                assertEquals(6f, copy.position.z, 0f);
                assertEquals(0.1f, copy.velocity.x, 0f);
                assertEquals(0.2f, copy.velocity.y, 0f);
                assertEquals(0.3f, copy.velocity.z, 0f);
                assertEquals(7f, copy.mass, 0f);
                assertEquals(8f, copy.density, 0f);
                assertSame(UnitSet.METRIC, copy.getUnitSet());
                assertSame(UnitSet.SOLAR_SYSTEM_HOUR, spice.getUnitSet());
                assertTrue(spice.position.x != 4f);
            }
        }
        assertTrue(movedDisk);
    }
}
