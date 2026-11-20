package com.grumbo.simulation;

import org.junit.jupiter.api.Test;

import com.grumbo.simulation.SimulationSetup.Preset;
import com.grumbo.simulation.SimulationSetup.SuggestedSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestedDtTest {

    @Test
    void mergerDtIsOneHundredInnerOrbitsNotTheOldHalfMillionStep() {
        assertEquals(1f, SimulationSetup.DISK_INNER_RADIUS);
        assertInnerOrbitDt(Preset.MERGER_FULL, 1_000_000.0, UnitSet.SOLAR_SYSTEM_SECOND);
        assertInnerOrbitDt(Preset.MERGER_SCALED, 1_000_000.0, UnitSet.SOLAR_SYSTEM_SECOND);
    }

    @Test
    void smallGalaxyAndSmallDiskStayUnderAMillionSteps() {
        assertInnerOrbitDt(Preset.SMALL_GALAXY, 50_000.0, UnitSet.SOLAR_SYSTEM_SECOND);

        SimulationSetup setup = new SimulationSetup();
        setup.setUnitSet(UnitSet.SOLAR_SYSTEM_SECOND);
        setup.addDisk(20, 0, 0, 0, 5f, 0.5f, 1f, 2f);
        SuggestedSettings suggested = setup.suggestSettings();
        assertMatchesInnerOrbit(suggested.dt, 2.0, UnitSet.SOLAR_SYSTEM_SECOND);
        assertTrue(suggested.dt < 1_000_000f, "small scene dt " + suggested.dt);
        assertNull(suggested.renderMode);
    }

    @Test
    void solarPresetStaysNearOneHundredHours() {
        SimulationSetup setup = new SimulationSetup();
        setup.applyPreset(Preset.SOLAR_SYSTEM);
        float dt = setup.suggestSettings().dt;
        assertMatchesOrbit(dt, 1.0, 1.0, UnitSet.SOLAR_SYSTEM_HOUR);
        assertTrue(dt > 50f && dt < 150f, "solar dt " + dt);
    }

    private static void assertInnerOrbitDt(Preset preset, double centralMass, UnitSet units) {
        SimulationSetup setup = new SimulationSetup();
        setup.applyPreset(preset);
        SuggestedSettings suggested = setup.suggestSettings();
        assertEquals(units, setup.getUnitSet());
        assertMatchesInnerOrbit(suggested.dt, centralMass, units);
        assertTrue(suggested.dt < 10_000f, preset + " dt " + suggested.dt);
        if (preset == Preset.MERGER_FULL || preset == Preset.MERGER_SCALED) {
            assertEquals(0.77f, suggested.theta, 0f);
            assertEquals(0.2f, suggested.cameraScale, 0f);
            assertEquals("impNodeGlow", suggested.renderMode);
        }
    }

    /** 100 steps around {@code centralMass} at the disk inner edge. */
    private static void assertMatchesInnerOrbit(float dt, double centralMass, UnitSet units) {
        assertMatchesOrbit(dt, SimulationSetup.DISK_INNER_RADIUS, centralMass, units);
    }

    private static void assertMatchesOrbit(float dt, double radius, double centralMass, UnitSet units) {
        double g = units.gravitationalConstant();
        double period = 2.0 * Math.PI * Math.sqrt((radius * radius * radius) / (g * centralMass));
        assertEquals((float) (period / 100.0), dt, 0f);
        assertTrue(dt > 1f, "dt collapsed to " + dt);
    }
}
