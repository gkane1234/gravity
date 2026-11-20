package com.grumbo.simulation;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Visual settings remembered per render mode.
 * imp and impGlow keep a body draw distance at least as far as the framed scene.
 */
public final class RenderModeProfiles {

    private final Map<String, Map<String, Number>> modes = new LinkedHashMap<>();
    private String active;
    private float bodyCover = 1000f;

    public void setBodyCover(float cover) {
        bodyCover = Math.max(cover, 1f);
    }

    public float getBodyCover() {
        return bodyCover;
    }

    public String getActive() {
        return active;
    }

    public boolean has(String mode) {
        return modes.containsKey(mode);
    }

    public void remember(String mode, Map<String, Number> values) {
        modes.put(mode, copy(values));
        active = mode;
    }

    public Map<String, Number> stored(String mode) {
        Map<String, Number> saved = modes.get(mode);
        return saved == null ? null : copy(saved);
    }

    /**
     * Profile for {@code mode}. Creates the automatic defaults the first time
     * that mode is selected. imp and impGlow are raised to {@link #bodyCover}.
     */
    public Map<String, Number> valuesFor(String mode) {
        Map<String, Number> saved = modes.get(mode);
        if (saved == null) {
            saved = defaults(mode, bodyCover);
            modes.put(mode, copy(saved));
        }
        Map<String, Number> out = copy(saved);
        if (needsBodyCover(mode)) {
            float body = out.get("bodyRenderDistance").floatValue();
            if (body < bodyCover) {
                out.put("bodyRenderDistance", bodyCover);
                modes.get(mode).put("bodyRenderDistance", bodyCover);
            }
        }
        active = mode;
        return out;
    }

    public static boolean needsBodyCover(String mode) {
        return "imp".equals(mode) || "impGlow".equals(mode);
    }

    public static Map<String, Number> defaults(String mode, float cover) {
        Map<String, Number> values = new LinkedHashMap<>();
        values.put("nodeGlowThetaPx", 16f);
        values.put("nodeGlowActivateDist", 0f);
        values.put("nodeGlowMaxDepth", 8);
        values.put("nodeGlowIntensity", 0.003f);
        values.put("minImpostorSize", 1.0E-5f);
        values.put("bodyRenderDistance", needsBodyCover(mode) ? cover : 1000f);
        return values;
    }

    public void load(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = mapper.readValue(file, Map.class);
            Object cover = root.get("bodyCover");
            if (cover instanceof Number number) {
                bodyCover = number.floatValue();
            }
            Object rawModes = root.get("modes");
            if (rawModes instanceof Map<?, ?> modeMap) {
                modes.clear();
                for (Map.Entry<?, ?> entry : modeMap.entrySet()) {
                    if (!(entry.getValue() instanceof Map<?, ?> values)) {
                        continue;
                    }
                    Map<String, Number> converted = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> value : values.entrySet()) {
                        if (value.getValue() instanceof Number number) {
                            converted.put(String.valueOf(value.getKey()), number);
                        }
                    }
                    modes.put(String.valueOf(entry.getKey()), converted);
                }
            }
            Object activeMode = root.get("active");
            if (activeMode instanceof String text && !text.isBlank()) {
                active = text;
            }
        } catch (IOException e) {
            System.err.println("Failed to load render profiles: " + e.getMessage());
        }
    }

    public void save(File file) {
        if (file == null) {
            return;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("active", active);
            root.put("bodyCover", bodyCover);
            root.put("modes", modes);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file, root);
        } catch (IOException e) {
            System.err.println("Failed to save render profiles: " + e.getMessage());
        }
    }

    private static Map<String, Number> copy(Map<String, Number> values) {
        return new LinkedHashMap<>(values);
    }
}
