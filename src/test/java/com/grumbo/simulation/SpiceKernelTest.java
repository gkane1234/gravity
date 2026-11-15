package com.grumbo.simulation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic SPK files. Coefficients are low-degree Chebyshev polynomials with hand-computed values.
 */
class SpiceKernelTest {

    @TempDir
    Path temp;

    @Test
    void type2PositionAndVelocity() throws IOException {
        // c0=10, c1=4, c2=1. At epoch 3 (s=0.5) over a 4-second record: x=11.5 km, vx=3 km/s.
        Path file = write(ByteOrder.LITTLE_ENDIAN, type2(399, 0, 0, 4, 10, 4, 1));
        SpiceKernel.LoadResult result = SpiceKernel.load(file, 3);
        assertEquals(1, result.planets.size());
        Planet earth = result.planets.get(0);
        assertEquals(399, earth.name);
        assertEquals(11500f, earth.position.x, 0f);
        assertEquals(0f, earth.position.y, 0f);
        assertEquals(3000f, earth.velocity.x, 0f);
        assertTrue(earth.mass > 1e24f);
        assertTrue(result.summary.contains("1 body"));
        assertTrue(!result.summary.contains("unsupported"));
    }

    @Test
    void bigEndianMatchesLittleEndian() throws IOException {
        byte[] little = bytes(ByteOrder.LITTLE_ENDIAN, type2(399, 0, 0, 4, 10, 4, 1));
        byte[] big = bytes(ByteOrder.BIG_ENDIAN, type2(399, 0, 0, 4, 10, 4, 1));
        Planet a = SpiceKernel.load(Files.write(temp.resolve("little.bsp"), little), 3).planets.get(0);
        Planet b = SpiceKernel.load(Files.write(temp.resolve("big.bsp"), big), 3).planets.get(0);
        assertEquals(a.position.x, b.position.x, 0f);
        assertEquals(a.velocity.x, b.velocity.x, 0f);
    }

    @Test
    void uncoveredNowUsesSegmentMidpoint() throws IOException {
        // Coverage is year 2000, not today. Midpoint epoch 2 is s=0, so x = c0 - c2 = 9 km.
        Path file = write(ByteOrder.LITTLE_ENDIAN, type2(399, 0, 0, 4, 10, 4, 1));
        SpiceKernel.LoadResult result = SpiceKernel.load(file);
        assertEquals(2.0, result.epochSecondsPastJ2000, 0.0);
        assertEquals(9000f, result.planets.get(0).position.x, 0f);
        assertEquals(2000f, result.planets.get(0).velocity.x, 0f);
    }

    @Test
    void type3UsesVelocityCoefficients() throws IOException {
        // Position c0=10 km. Velocity c0=7 km/s. Differentiating the position would give 0.
        Path file = write(ByteOrder.LITTLE_ENDIAN, type3(399, 0, 0, 4, 10, 7));
        Planet earth = SpiceKernel.load(file, 1).planets.get(0);
        assertEquals(10000f, earth.position.x, 0f);
        assertEquals(7000f, earth.velocity.x, 0f);
    }

    @Test
    void chainsBodiesRelativeToTheSunAndSkipsBarycenters() throws IOException {
        // Sun at 100 km from the SSB, EMB at 50 km, Earth 1 km from the EMB.
        // Earth relative to the Sun is -49 km. The EMB (NAIF 3) is not a body.
        Path file = write(ByteOrder.LITTLE_ENDIAN,
            type2(10, 0, 0, 10, 100, 0, 0),
            type2(3, 0, 0, 10, 50, 0, 0),
            type2(399, 3, 0, 10, 1, 0, 0));
        SpiceKernel.LoadResult result = SpiceKernel.load(file, 5);
        assertEquals(2, result.planets.size());
        Planet sun = planet(result, 10);
        Planet earth = planet(result, 399);
        assertEquals(0f, sun.position.x, 0f);
        assertEquals(-49000f, earth.position.x, 0f);
    }

    @Test
    void moonKernelPlacesTheCenterAtTheOrigin() throws IOException {
        Path file = write(ByteOrder.LITTLE_ENDIAN, type2(301, 399, 0, 10, 10, 0, 0));
        SpiceKernel.LoadResult result = SpiceKernel.load(file, 5);
        assertEquals(2, result.planets.size());
        assertEquals(0f, planet(result, 399).position.x, 0f);
        assertEquals(10000f, planet(result, 301).position.x, 0f);
    }

    @Test
    void type9Degree1Interpolates() throws IOException {
        Path file = write(ByteOrder.LITTLE_ENDIAN, type9(399, 0, new double[] {0, 10}, new double[] {0, 10}, new double[] {0, 1}));
        Planet earth = SpiceKernel.load(file, 5).planets.get(0);
        assertEquals(5000f, earth.position.x, 0f);
        assertEquals(500f, earth.velocity.x, 0f);
    }

    @Test
    void unsupportedSegmentIsSkipped() throws IOException {
        Segment bogus = new Segment(501, 0, 5, 0, 10, new double[] {0, 1});
        Path file = write(ByteOrder.LITTLE_ENDIAN, bogus, type2(399, 0, 0, 10, 4, 0, 0));
        SpiceKernel.LoadResult result = SpiceKernel.load(file, 5);
        assertEquals(1, result.planets.size());
        assertEquals(399, result.planets.get(0).name);
        assertTrue(result.summary.contains("1 unsupported"));
    }

    @Test
    void rejectsAFileThatIsNotAnSpk() {
        Path file = temp.resolve("notes.txt");
        assertThrows(IOException.class, () -> {
            Files.writeString(file, "not a kernel");
            SpiceKernel.load(file);
        });
    }

    @Test
    void setupConvertsMetersIntoTheSceneUnitSet() throws IOException {
        Path file = write(ByteOrder.LITTLE_ENDIAN, type2(399, 0, 0, 4, 10, 4, 1));
        SpiceKernel.LoadResult result = SpiceKernel.load(file, 3);
        SimulationSetup setup = new SimulationSetup();
        setup.clear();
        setup.addSpiceBodies(result.planets, "earth.bsp");
        assertEquals(1, setup.totalBodies());
        Planet planet = setup.buildGenerator().nextPlanet();
        float expected = 11500f / (float) UnitSet.SOLAR_SYSTEM_SECOND.len();
        assertEquals(expected, planet.position.x, Math.abs(expected) * 1e-5f);
        assertEquals(UnitSet.SOLAR_SYSTEM_SECOND, planet.getUnitSet());
    }

    private static Planet planet(SpiceKernel.LoadResult result, int naifId) {
        for (Planet planet : result.planets) {
            if (planet.name == naifId) {
                return planet;
            }
        }
        throw new AssertionError("missing NAIF " + naifId);
    }

    private Path write(ByteOrder order, Segment... segments) throws IOException {
        Path file = temp.resolve(order == ByteOrder.LITTLE_ENDIAN ? "kernel.bsp" : "kernel-be.bsp");
        return Files.write(file, bytes(order, segments));
    }

    private static byte[] bytes(ByteOrder order, Segment... segments) {
        if (segments.length > 25) {
            throw new IllegalArgumentException("test builder only fills one summary record");
        }
        int cursor = 385;
        int[] start = new int[segments.length];
        int[] end = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            start[i] = cursor;
            end[i] = cursor + segments[i].payload.length - 1;
            cursor += segments[i].payload.length;
        }
        int free = cursor;
        int fileBytes = (int) ((((free - 1L) * 8L) + 1023) / 1024 * 1024);
        ByteBuffer buf = ByteBuffer.allocate(fileBytes).order(order);
        buf.put("DAF/SPK ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buf.putInt(2);
        buf.putInt(6);
        buf.put(new byte[60]);
        buf.putInt(2);
        buf.putInt(2);
        buf.putInt(free);
        String marker = order == ByteOrder.LITTLE_ENDIAN ? "LTL-IEEE" : "BIG-IEEE";
        buf.put(marker.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buf.position(699);
        buf.put(SpiceKernel.FTPSTR);

        buf.position(1024);
        buf.putDouble(0);
        buf.putDouble(0);
        buf.putDouble(segments.length);
        for (int i = 0; i < segments.length; i++) {
            Segment segment = segments[i];
            buf.putDouble(segment.start);
            buf.putDouble(segment.end);
            buf.putInt(segment.target);
            buf.putInt(segment.center);
            buf.putInt(1);
            buf.putInt(segment.type);
            buf.putInt(start[i]);
            buf.putInt(end[i]);
        }
        for (int i = 0; i < segments.length; i++) {
            buf.position((start[i] - 1) * 8);
            for (double value : segments[i].payload) {
                buf.putDouble(value);
            }
        }
        return buf.array();
    }

    /** One type-2 record. x coefficients are c0, c1, c2. y and z are zero. Coverage is [start, start+intlen]. */
    private static Segment type2(int target, int center, double start, double intlen, double c0, double c1, double c2) {
        int rsize = 11;
        double[] payload = new double[rsize + 4];
        payload[0] = start + intlen / 2.0;
        payload[1] = intlen / 2.0;
        payload[2] = c0;
        payload[3] = c1;
        payload[4] = c2;
        payload[rsize] = start;
        payload[rsize + 1] = intlen;
        payload[rsize + 2] = rsize;
        payload[rsize + 3] = 1;
        return new Segment(target, center, 2, start, start + intlen, payload);
    }

    /** Type 3, one coefficient each. Position x is px km. Velocity x is vx km/s. */
    private static Segment type3(int target, int center, double start, double intlen, double px, double vx) {
        int rsize = 8;
        double[] payload = new double[rsize + 4];
        payload[0] = start + intlen / 2.0;
        payload[1] = intlen / 2.0;
        payload[2] = px;
        payload[5] = vx;
        payload[rsize] = start;
        payload[rsize + 1] = intlen;
        payload[rsize + 2] = rsize;
        payload[rsize + 3] = 1;
        return new Segment(target, center, 3, start, start + intlen, payload);
    }

    /** Type 9 degree 1. Parallel arrays of epoch (seconds), x (km), vx (km/s). */
    private static Segment type9(int target, int center, double[] epochs, double[] x, double[] vx) {
        int n = epochs.length;
        double[] payload = new double[7 * n + 2];
        for (int i = 0; i < n; i++) {
            payload[i * 6] = x[i];
            payload[i * 6 + 3] = vx[i];
        }
        for (int i = 0; i < n; i++) {
            payload[6 * n + i] = epochs[i];
        }
        payload[7 * n] = 1;
        payload[7 * n + 1] = n;
        return new Segment(target, center, 9, epochs[0], epochs[n - 1], payload);
    }

    private static final class Segment {
        final int target;
        final int center;
        final int type;
        final double start;
        final double end;
        final double[] payload;

        Segment(int target, int center, int type, double start, double end, double[] payload) {
            this.target = target;
            this.center = center;
            this.type = type;
            this.start = start;
            this.end = end;
            this.payload = payload;
        }
    }
}
