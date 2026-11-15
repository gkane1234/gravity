package com.grumbo.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grumbo.gpu.ResourceLoader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads a SPICE SPK kernel ({@code .bsp}) and turns the bodies it can place into planets.
 * <p>
 * The file is a DAF. This loader evaluates SPK type 2 (Chebyshev position), type 3
 * (Chebyshev position and velocity), and type 9 degree 1 (linear states). Those are the
 * segment types used by the planetary kernels this project already exports. Other segment
 * types are counted and skipped.
 * <p>
 * Positions in the kernel are kilometers; velocities are kilometers per second. Planets are
 * stored in meters with {@link UnitSet#METRIC}. Mass is GM from {@code naif_gm.json} divided
 * by {@code 6.6743e-20}, the same constant {@code planet_data.py} uses. A body with no GM is
 * still included, with mass 0. NAIF ids 0 through 9 are barycenters: they are used as centers
 * and are not added as bodies.
 */
public final class SpiceKernel implements AutoCloseable {

    /** FTP check string stored in a modern DAF file record. */
    static final byte[] FTPSTR = new byte[] {
        'F', 'T', 'P', 'S', 'T', 'R', ':', '\r', ':', '\n', ':', '\r', '\n', ':', '\r', 0,
        ':', (byte) 0x81, ':', 0x10, (byte) 0xCE, ':', 'E', 'N', 'D', 'F', 'T', 'P'
    };

    /** km^3 s^-2 kg^-1, matching planet_data.py so masses agree with the shipped JSON. */
    private static final double GM_PER_KG = 6.6743e-20;
    private static final double METERS_PER_KM = 1000.0;
    /** Same visual density Planet.fromJson uses for the shipped solar system. */
    private static final float BODY_DENSITY = 100f;
    /** Unix seconds of 2000-01-01 12:00:00 UTC, the J2000 epoch this loader treats as TDB. */
    private static final double UNIX_J2000 = 946728000.0;

    private final Path path;
    private final FileChannel channel;
    private final ByteOrder order;
    private final int fward;

    private SpiceKernel(Path path) throws IOException {
        this.path = path;
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
        byte[] record = readRecordBytes(1);
        String id = ascii(record, 0, 8).trim();
        if (id.startsWith("DAF/")) {
            String fmt = ascii(record, 88, 8);
            if ("LTL-IEEE".equals(fmt)) {
                order = ByteOrder.LITTLE_ENDIAN;
            } else if ("BIG-IEEE".equals(fmt)) {
                order = ByteOrder.BIG_ENDIAN;
            } else {
                throw new IOException("Unknown SPK endian marker \"" + fmt + "\" in " + path.getFileName());
            }
            if (!ftpOk(record)) {
                throw new IOException("SPK file record failed the FTP check: " + path.getFileName());
            }
        } else if ("NAIF/DAF".equals(id)) {
            ByteOrder found = null;
            for (ByteOrder candidate : new ByteOrder[] {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN}) {
                if (ByteBuffer.wrap(record).order(candidate).getInt(8) == 2) {
                    found = candidate;
                    break;
                }
            }
            if (found == null) {
                throw new IOException("Could not read this NAIF/DAF file: " + path.getFileName());
            }
            order = found;
        } else {
            throw new IOException("Not a SPICE SPK kernel: " + path.getFileName());
        }

        ByteBuffer header = ByteBuffer.wrap(record).order(order);
        int nd = header.getInt(8);
        int ni = header.getInt(12);
        fward = header.getInt(76);
        if (nd != 2 || ni != 6) {
            throw new IOException("This DAF is not an SPK ephemeris (ND=" + nd + ", NI=" + ni + ")");
        }
        if (fward < 1) {
            throw new IOException("SPK summary record pointer is invalid in " + path.getFileName());
        }
    }

    /**
     * Loads the kernel and evaluates it at the current time when any segment covers that time.
     * Otherwise the epoch is the midpoint of the supported segment that ends latest.
     */
    public static LoadResult load(Path path) throws IOException {
        return load(path, Double.NaN);
    }

    /**
     * Loads the kernel at an explicit epoch, in seconds past J2000.
     * Pass {@link Double#NaN} to choose the epoch the way {@link #load(Path)} does.
     */
    public static LoadResult load(Path path, double epochSecondsPastJ2000) throws IOException {
        try (SpiceKernel kernel = new SpiceKernel(path)) {
            return kernel.loadAt(epochSecondsPastJ2000);
        }
    }

    /** Seconds past J2000 for the current UTC time. SPICE TDB differs by about a minute. */
    public static double nowSecondsPastJ2000() {
        return System.currentTimeMillis() / 1000.0 - UNIX_J2000;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private LoadResult loadAt(double requestedEpoch) throws IOException {
        List<Segment> all = readSegments();
        int unsupported = 0;
        List<Segment> supported = new ArrayList<>();
        for (Segment segment : all) {
            if (segment.type != 2 && segment.type != 3 && segment.type != 9) {
                unsupported++;
                continue;
            }
            if (segment.type == 9 && chebyshevDegree(segment) != 1) {
                unsupported++;
                continue;
            }
            supported.add(segment);
        }

        double epoch = chooseEpoch(supported, requestedEpoch);
        int outside = 0;
        List<Segment> covering = new ArrayList<>();
        for (Segment segment : supported) {
            if (epoch < segment.start || epoch > segment.end) {
                outside++;
            } else {
                covering.add(segment);
            }
        }

        int reference = chooseReference(covering);
        Map<Integer, Segment> byTarget = pickSegments(covering, reference);

        Map<Integer, double[]> memo = new HashMap<>();
        Set<Integer> failed = new HashSet<>();
        Set<Integer> unreadableIds = new HashSet<>();
        Set<Integer> stack = new HashSet<>();
        int unchained = 0;
        Map<Integer, double[]> placed = new TreeMap<>();
        for (int target : byTarget.keySet()) {
            if (target >= 0 && target <= 9) {
                continue;
            }
            double[] absolute = stateFrom(target, byTarget, reference, epoch, memo, failed, unreadableIds, stack);
            double[] origin = stateFrom(reference, byTarget, reference, epoch, memo, failed, unreadableIds, stack);
            if (absolute == null || origin == null) {
                if (!unreadableIds.contains(target)) {
                    unchained++;
                }
                continue;
            }
            placed.put(target, subtract(absolute, origin));
        }
        if ((reference > 9 || reference < 0) && !placed.isEmpty()) {
            placed.putIfAbsent(reference, new double[6]);
        }
        int unreadable = unreadableIds.size();

        Map<Integer, Double> gm = loadGmTable();
        List<Planet> planets = new ArrayList<>();
        int massless = 0;
        for (Map.Entry<Integer, double[]> entry : placed.entrySet()) {
            Double gmValue = gm.get(entry.getKey());
            float mass = 0f;
            if (gmValue != null) {
                mass = (float) (gmValue / GM_PER_KG);
            } else {
                massless++;
            }
            double[] km = entry.getValue();
            Planet planet = new Planet(
                (float) (km[0] * METERS_PER_KM),
                (float) (km[1] * METERS_PER_KM),
                (float) (km[2] * METERS_PER_KM),
                (float) (km[3] * METERS_PER_KM),
                (float) (km[4] * METERS_PER_KM),
                (float) (km[5] * METERS_PER_KM),
                mass,
                BODY_DENSITY,
                UnitSet.METRIC
            );
            planet.name = entry.getKey();
            planets.add(planet);
        }
        planets.sort(Comparator.comparingInt(p -> p.name));

        String summary = summary(planets.size(), massless, unsupported, outside, unchained, unreadable, epoch);
        return new LoadResult(planets, summary, epoch);
    }

    private String summary(int bodies, int massless, int unsupported, int outside, int unchained, int unreadable, double epoch) {
        StringBuilder text = new StringBuilder();
        text.append(path.getFileName()).append(": ").append(bodies).append(bodies == 1 ? " body" : " bodies");
        if (massless > 0) {
            text.append(", ").append(massless).append(" without a GM");
        }
        if (unsupported > 0) {
            text.append(", ").append(unsupported).append(" unsupported segments");
        }
        if (outside > 0) {
            text.append(", ").append(outside).append(" outside the epoch");
        }
        if (unchained > 0) {
            text.append(", ").append(unchained).append(" could not be placed");
        }
        if (unreadable > 0) {
            text.append(", ").append(unreadable).append(" unreadable segments");
        }
        text.append(". Epoch ").append(formatEpoch(epoch)).append('.');
        return text.toString();
    }

    private static String formatEpoch(double secondsPastJ2000) {
        long unix = (long) Math.floor(secondsPastJ2000 + UNIX_J2000);
        return Instant.ofEpochSecond(unix).toString();
    }

    private static double chooseEpoch(List<Segment> supported, double requested) {
        if (!Double.isNaN(requested)) {
            return requested;
        }
        if (supported.isEmpty()) {
            return nowSecondsPastJ2000();
        }
        double now = nowSecondsPastJ2000();
        for (Segment segment : supported) {
            if (now >= segment.start && now <= segment.end) {
                return now;
            }
        }
        Segment latest = supported.get(0);
        for (Segment segment : supported) {
            if (segment.end > latest.end) {
                latest = segment;
            }
        }
        return (latest.start + latest.end) / 2.0;
    }

    private static int chooseReference(List<Segment> covering) {
        boolean hasSun = false;
        boolean hasSsb = false;
        Map<Integer, Integer> centers = new HashMap<>();
        for (Segment segment : covering) {
            if (segment.target == 10 || segment.center == 10) {
                hasSun = true;
            }
            if (segment.center == 0) {
                hasSsb = true;
            }
            centers.merge(segment.center, 1, Integer::sum);
        }
        if (hasSun) {
            return 10;
        }
        if (hasSsb) {
            return 0;
        }
        int reference = 0;
        int best = -1;
        for (Map.Entry<Integer, Integer> entry : centers.entrySet()) {
            if (entry.getValue() > best || (entry.getValue() == best && entry.getKey() < reference)) {
                best = entry.getValue();
                reference = entry.getKey();
            }
        }
        return reference;
    }

    /** One segment per target. Prefer a center that is the reference, then the SSB. */
    private static Map<Integer, Segment> pickSegments(List<Segment> covering, int reference) {
        Map<Integer, Segment> byTarget = new HashMap<>();
        for (Segment segment : covering) {
            Segment existing = byTarget.get(segment.target);
            if (existing == null || centerScore(segment, reference) < centerScore(existing, reference)) {
                byTarget.put(segment.target, segment);
            }
        }
        return byTarget;
    }

    private static int centerScore(Segment segment, int reference) {
        if (segment.center == reference) {
            return 0;
        }
        if (segment.center == 0) {
            return 1;
        }
        return 2;
    }

    private double[] stateFrom(int id, Map<Integer, Segment> byTarget, int reference, double epoch,
                               Map<Integer, double[]> memo, Set<Integer> failed, Set<Integer> unreadableIds,
                               Set<Integer> stack) {
        if (id == 0) {
            return new double[6];
        }
        if (failed.contains(id)) {
            return null;
        }
        double[] cached = memo.get(id);
        if (cached != null) {
            return cached;
        }
        if (!stack.add(id)) {
            failed.add(id);
            return null;
        }
        Segment segment = byTarget.get(id);
        double[] result = null;
        if (segment == null) {
            if (id == reference) {
                result = new double[6];
            }
        } else {
            try {
                double[] local = evaluate(segment, epoch);
                double[] parent = stateFrom(segment.center, byTarget, reference, epoch, memo, failed, unreadableIds, stack);
                if (parent != null) {
                    result = add(local, parent);
                }
            } catch (IOException e) {
                unreadableIds.add(id);
            }
        }
        stack.remove(id);
        if (result == null) {
            failed.add(id);
        } else {
            memo.put(id, result);
        }
        return result;
    }

    private int chebyshevDegree(Segment segment) throws IOException {
        double[] dir = readDoubles(segment.endWord - 1, segment.endWord);
        return (int) Math.round(dir[0]);
    }

    /** Kilometers and kilometers per second, target relative to its center. */
    private double[] evaluate(Segment segment, double epoch) throws IOException {
        if (segment.type == 9) {
            return evaluateType9(segment, epoch);
        }
        double[] dir = readDoubles(segment.endWord - 3, segment.endWord);
        double init = dir[0];
        double intlen = dir[1];
        int rsize = (int) Math.round(dir[2]);
        int nrec = (int) Math.round(dir[3]);
        int components = segment.type == 2 ? 3 : 6;
        if (intlen <= 0 || rsize < 2 || nrec < 1 || (rsize - 2) % components != 0) {
            throw new IOException("SPK type " + segment.type + " directory is unusable for NAIF " + segment.target);
        }
        int nCoeff = (rsize - 2) / components;
        if (nCoeff < 1) {
            throw new IOException("SPK type " + segment.type + " has no Chebyshev coefficients for NAIF " + segment.target);
        }
        double into = epoch - init;
        int index = (int) Math.floor(into / intlen);
        double local = into - index * intlen;
        if (index == nrec) {
            index = nrec - 1;
            local = intlen;
        }
        if (index < 0 || index >= nrec) {
            throw new IOException("Epoch is outside the Chebyshev records for NAIF " + segment.target);
        }
        long word = (long) segment.startWord + (long) index * rsize;
        if (word > Integer.MAX_VALUE) {
            throw new IOException("SPK address does not fit a 32-bit DAF word for NAIF " + segment.target);
        }
        double[] record = readDoubles((int) word, (int) word + rsize - 1);
        double s = 2.0 * local / intlen - 1.0;
        double[] state = new double[6];
        double[] valueAndDeriv = new double[2];
        for (int comp = 0; comp < components; comp++) {
            chebyshev(record, 2 + comp * nCoeff, nCoeff, s, valueAndDeriv);
            if (segment.type == 2) {
                state[comp] = valueAndDeriv[0];
                state[comp + 3] = valueAndDeriv[1] * (2.0 / intlen);
            } else if (comp < 3) {
                state[comp] = valueAndDeriv[0];
            } else {
                state[comp] = valueAndDeriv[0];
            }
        }
        return state;
    }

    private double[] evaluateType9(Segment segment, double epoch) throws IOException {
        double[] dir = readDoubles(segment.endWord - 1, segment.endWord);
        int degree = (int) Math.round(dir[0]);
        int n = (int) Math.round(dir[1]);
        if (degree != 1 || n < 1) {
            throw new IOException("Only SPK type 9 degree 1 is supported (NAIF " + segment.target + ")");
        }
        int epochStart = segment.startWord + 6 * n;
        double[] epochs = readDoubles(epochStart, epochStart + n - 1);
        if (epoch < epochs[0] || epoch > epochs[n - 1]) {
            throw new IOException("Epoch is outside the type 9 states for NAIF " + segment.target);
        }
        int hi = 1;
        if (n == 1) {
            return readState9(segment.startWord);
        }
        while (hi < n - 1 && epochs[hi] < epoch) {
            hi++;
        }
        if (epochs[hi] < epoch) {
            hi = n - 1;
        }
        int lo = hi - 1;
        double span = epochs[hi] - epochs[lo];
        double u = span == 0.0 ? 0.0 : (epoch - epochs[lo]) / span;
        double[] a = readState9(segment.startWord + lo * 6);
        double[] b = readState9(segment.startWord + hi * 6);
        double[] state = new double[6];
        for (int i = 0; i < 6; i++) {
            state[i] = a[i] * (1.0 - u) + b[i] * u;
        }
        return state;
    }

    private double[] readState9(int word) throws IOException {
        return readDoubles(word, word + 5);
    }

    /** Chebyshev T0..T(n-1), low degree first. Writes value and d(value)/ds. */
    private static void chebyshev(double[] coeff, int offset, int nCoeff, double s, double[] out) {
        double t0 = 1.0;
        double t1 = s;
        double dt0 = 0.0;
        double dt1 = 1.0;
        double value = coeff[offset];
        double deriv = 0.0;
        if (nCoeff > 1) {
            value += coeff[offset + 1] * t1;
            deriv += coeff[offset + 1] * dt1;
        }
        for (int k = 2; k < nCoeff; k++) {
            double t = 2.0 * s * t1 - t0;
            double dt = 2.0 * t1 + 2.0 * s * dt1 - dt0;
            value += coeff[offset + k] * t;
            deriv += coeff[offset + k] * dt;
            t0 = t1;
            t1 = t;
            dt0 = dt1;
            dt1 = dt;
        }
        out[0] = value;
        out[1] = deriv;
    }

    private List<Segment> readSegments() throws IOException {
        int summaryStep = 40;
        List<Segment> segments = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        int recordNumber = fward;
        while (recordNumber > 0 && seen.add(recordNumber)) {
            byte[] data = readRecordBytes(recordNumber);
            ByteBuffer summary = ByteBuffer.wrap(data).order(order);
            int next = (int) summary.getDouble(0);
            int count = (int) summary.getDouble(16);
            for (int i = 0; i < count; i++) {
                int off = 24 + i * summaryStep;
                if (off + summaryStep > data.length) {
                    break;
                }
                ByteBuffer one = ByteBuffer.wrap(data, off, summaryStep).order(order);
                double start = one.getDouble();
                double end = one.getDouble();
                int target = one.getInt();
                int center = one.getInt();
                one.getInt(); // frame
                int type = one.getInt();
                int startWord = one.getInt();
                int endWord = one.getInt();
                segments.add(new Segment(target, center, type, start, end, startWord, endWord));
            }
            recordNumber = next;
        }
        return segments;
    }

    private double[] readDoubles(int startWord, int endWord) throws IOException {
        if (startWord < 1 || endWord < startWord) {
            throw new IOException("Bad DAF address " + startWord + ".." + endWord + " in " + path.getFileName());
        }
        int count = endWord - startWord + 1;
        ByteBuffer buf = ByteBuffer.allocate(count * 8).order(order);
        long pos = (startWord - 1L) * 8L;
        while (buf.hasRemaining()) {
            int n = channel.read(buf, pos);
            if (n < 0) {
                break;
            }
            pos += n;
        }
        if (buf.hasRemaining()) {
            throw new IOException("Short read at DAF word " + startWord + " in " + path.getFileName());
        }
        buf.flip();
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = buf.getDouble();
        }
        return values;
    }

    private byte[] readRecordBytes(int recordNumber) throws IOException {
        if (recordNumber < 1) {
            throw new IOException("Bad DAF record " + recordNumber);
        }
        ByteBuffer buf = ByteBuffer.allocate(1024);
        long pos = (recordNumber - 1L) * 1024L;
        while (buf.hasRemaining()) {
            int n = channel.read(buf, pos);
            if (n < 0) {
                break;
            }
            pos += n;
        }
        if (buf.hasRemaining()) {
            throw new IOException("SPK record " + recordNumber + " is past the end of " + path.getFileName());
        }
        return buf.array();
    }

    private static boolean ftpOk(byte[] record) {
        int start = 500;
        int end = 1000;
        while (start < end && record[start] == 0) {
            start++;
        }
        while (end > start && record[end - 1] == 0) {
            end--;
        }
        if (end - start != FTPSTR.length) {
            return false;
        }
        for (int i = 0; i < FTPSTR.length; i++) {
            if (record[start + i] != FTPSTR[i]) {
                return false;
            }
        }
        return true;
    }

    private static Map<Integer, Double> loadGmTable() throws IOException {
        JsonNode node = new ObjectMapper().readTree(ResourceLoader.readText("planet_data/naif_gm.json"));
        Map<Integer, Double> masses = new HashMap<>();
        node.fields().forEachRemaining(entry -> {
            try {
                masses.put(Integer.parseInt(entry.getKey()), entry.getValue().asDouble());
            } catch (NumberFormatException ignored) {
                // A non-numeric key is not a NAIF id.
            }
        });
        return masses;
    }

    private static double[] add(double[] a, double[] b) {
        double[] out = new double[6];
        for (int i = 0; i < 6; i++) {
            out[i] = a[i] + b[i];
        }
        return out;
    }

    private static double[] subtract(double[] a, double[] b) {
        double[] out = new double[6];
        for (int i = 0; i < 6; i++) {
            out[i] = a[i] - b[i];
        }
        return out;
    }

    private static String ascii(byte[] raw, int offset, int length) {
        int n = Math.min(length, raw.length - offset);
        if (n <= 0) {
            return "";
        }
        return new String(raw, offset, n, StandardCharsets.US_ASCII);
    }

    private static final class Segment {
        final int target;
        final int center;
        final int type;
        final double start;
        final double end;
        final int startWord;
        final int endWord;

        Segment(int target, int center, int type, double start, double end, int startWord, int endWord) {
            this.target = target;
            this.center = center;
            this.type = type;
            this.start = start;
            this.end = end;
            this.startWord = startWord;
            this.endWord = endWord;
        }
    }

    /** Bodies produced from one kernel, plus a one-line description of what was skipped. */
    public static final class LoadResult {
        public final List<Planet> planets;
        public final String summary;
        public final double epochSecondsPastJ2000;

        LoadResult(List<Planet> planets, String summary, double epochSecondsPastJ2000) {
            this.planets = List.copyOf(planets);
            this.summary = summary;
            this.epochSecondsPastJ2000 = epochSecondsPastJ2000;
        }
    }
}
