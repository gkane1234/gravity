package com.grumbo.simulation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderModeProfilesTest {

    @Test
    void impAndImpGlowCoverTheSceneAndOtherModesKeepTheirOwnValues() {
        RenderModeProfiles profiles = new RenderModeProfiles();
        profiles.setBodyCover(4000f);

        Map<String, Number> imp = profiles.valuesFor("imp");
        assertEquals(4000f, imp.get("bodyRenderDistance").floatValue(), 0.01f);

        Map<String, Number> edited = new LinkedHashMap<>(imp);
        edited.put("bodyRenderDistance", 9000f);
        profiles.remember("imp", edited);

        Map<String, Number> node = profiles.valuesFor("impNodeGlow");
        assertEquals(1000f, node.get("bodyRenderDistance").floatValue(), 0.01f);
        assertEquals(0.003f, node.get("nodeGlowIntensity").floatValue(), 1e-6f);

        Map<String, Number> glow = profiles.valuesFor("impGlow");
        assertEquals(4000f, glow.get("bodyRenderDistance").floatValue(), 0.01f);

        Map<String, Number> impAgain = profiles.valuesFor("imp");
        assertEquals(9000f, impAgain.get("bodyRenderDistance").floatValue(), 0.01f);

        Map<String, Number> small = new LinkedHashMap<>(impAgain);
        small.put("bodyRenderDistance", 100f);
        profiles.remember("imp", small);
        assertEquals(4000f, profiles.valuesFor("imp").get("bodyRenderDistance").floatValue(), 0.01f);
    }

    @Test
    void profilesRoundTripThroughJson() throws Exception {
        Path file = Files.createTempFile("render-modes", ".json");
        RenderModeProfiles profiles = new RenderModeProfiles();
        profiles.setBodyCover(2500f);
        profiles.remember("imp", RenderModeProfiles.defaults("imp", 2500f));
        profiles.save(file.toFile());

        RenderModeProfiles loaded = new RenderModeProfiles();
        loaded.load(file.toFile());
        assertEquals(2500f, loaded.getBodyCover(), 0.01f);
        assertTrue(loaded.has("imp"));
        assertEquals(2500f, loaded.stored("imp").get("bodyRenderDistance").floatValue(), 0.01f);
        Files.deleteIfExists(file);
    }
}
