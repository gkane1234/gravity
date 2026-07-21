package com.grumbo.gpu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * Loads text resources from the classpath first, with a filesystem fallback for
 * local development when running from the project root.
 */
public final class ResourceLoader {
    private ResourceLoader() {}

    /**
     * Reads a UTF-8 text resource.
     * @param classpathPath path relative to classpath root, e.g. {@code shaders/compute/bh_main.comp}
     *                      (leading slash optional)
     */
    public static String readText(String classpathPath) throws IOException {
        String normalized = normalize(classpathPath);
        try (InputStream in = openStream(normalized)) {
            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    return reader.lines().collect(Collectors.joining("\n")) + "\n";
                }
            }
        }
        Path fsPath = Paths.get("src/main/resources").resolve(normalized);
        if (Files.exists(fsPath)) {
            return Files.readString(fsPath);
        }
        throw new IOException("Resource not found on classpath or filesystem: " + normalized);
    }

    /**
     * Opens an input stream for a classpath resource, or null if missing.
     */
    public static InputStream openStream(String classpathPath) {
        String normalized = normalize(classpathPath);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream in = cl != null ? cl.getResourceAsStream(normalized) : null;
        if (in == null) {
            in = ResourceLoader.class.getResourceAsStream("/" + normalized);
        }
        return in;
    }

    private static String normalize(String path) {
        String p = path.replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.startsWith("src/main/resources/")) {
            p = p.substring("src/main/resources/".length());
        }
        return p;
    }
}
