import sorting.algo.Algorithm;
import sorting.algo.SortAlgorithm;
import sorting.core.MultithreadedSorter;
import sorting.core.Ops;
import sorting.core.SharedData;
import sorting.core.SortResult;
import sorting.input.DataGenerator;
import sorting.trace.TraceRecorder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Timing measurements for the report. Run with ./bench.sh (or bench.bat).
 *
 * Variants measured for every algorithm and size n:
 *  - single : one thread sorts the whole array with the same algorithm (baseline, no extra threads),
 *  - split  : one thread does the same work as the textbook design one after another
 *             (sort left half, sort right half, merge); isolates the effect of splitting,
 *  - multi  : the textbook design (2 sorting threads + 1 merging thread, parent joins).
 * Total speed-up = single / multi; parallel speed-up = split / multi.
 *
 * The JIT may compile a method differently in different JVM runs (we saw bimodal timings),
 * so the parent process starts JVMS separate worker JVMs. Each worker reports the median of
 * REPS runs after WARMUPS warm-up runs; the parent reports the median over the workers and
 * the min..max range of the 3-thread time.
 *
 * Output: tests/results/benchmark.md (tables) and tests/results/benchmark.csv (for pgfplots).
 */
public final class Benchmark {

    private static final int JVMS = 3;
    private static final int WARMUPS = 3;
    private static final int REPS = 9;
    private static final long SEED = 1;

    private static final int[] SIZES_QUADRATIC = {1_000, 5_000, 10_000, 20_000};
    private static final int[] SIZES_LOGLINEAR = {1_000, 10_000, 100_000, 1_000_000};
    private static final int[] SIZES_SMALL = {10, 100, 1_000};

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        if (args.length > 0 && args[0].equals("--worker")) {
            worker();
            return;
        }
        Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();

        // key -> list of per-JVM value arrays
        Map<String, List<double[]>> rows = new LinkedHashMap<>();
        for (int j = 1; j <= JVMS; j++) {
            System.out.println("JVM " + j + "/" + JVMS + " ...");
            for (String line : runWorker()) {
                String[] f = line.split(",");
                String key = f[0] + "," + f[1] + "," + f[2];
                double[] v = new double[f.length - 3];
                for (int i = 3; i < f.length; i++) v[i - 3] = Double.parseDouble(f[i]);
                rows.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
            }
        }
        writeResults(root, rows);
    }

    // ------------------------------------------------------------------ worker (one JVM)

    private static void worker() throws InterruptedException {
        for (Algorithm a : Algorithm.values()) {
            for (int n : a.quadratic ? SIZES_QUADRATIC : SIZES_LOGLINEAR) emit("M", a, n);
        }
        for (Algorithm a : new Algorithm[] {Algorithm.INSERTION, Algorithm.MERGE}) {
            for (int n : SIZES_SMALL) emit("S", a, n);
        }
        for (Algorithm a : new Algorithm[] {Algorithm.MERGE, Algorithm.INSERTION}) {
            double[] t = traceOverhead(a, 1_000);
            System.out.printf(Locale.ROOT, "T,%s,%d,%.5f,%.5f,%.0f%n", a.key, 1_000, t[0], t[1], t[2]);
        }
    }

    private static void emit(String kind, Algorithm a, int n) throws InterruptedException {
        int[] input = DataGenerator.generate(n, SEED, Integer.MIN_VALUE, Integer.MAX_VALUE, DataGenerator.Pattern.RANDOM);
        long[] single = new long[REPS];
        long[] split = new long[REPS];
        long[] multi = new long[REPS];
        long[] sortPhase = new long[REPS];
        for (int i = 0; i < WARMUPS; i++) {
            runSingle(a, input);
            runSplit(a, input);
            MultithreadedSorter.sort(input, a.key, a.create(), a.key, a.create());
        }
        // interleave the variants so that drifting machine load affects all of them equally
        for (int i = 0; i < REPS; i++) {
            single[i] = runSingle(a, input);
            split[i] = runSplit(a, input);
            SortResult r = MultithreadedSorter.sort(input, a.key, a.create(), a.key, a.create());
            multi[i] = r.totalNanos;
            sortPhase[i] = r.sortPhaseNanos;
        }
        System.out.printf(Locale.ROOT, "%s,%s,%d,%.5f,%.5f,%.5f,%.5f%n", kind, a.key, n,
                median(single) / 1e6, median(split) / 1e6, median(multi) / 1e6, median(sortPhase) / 1e6);
    }

    /** Baseline: the same algorithm sorts the whole array in the calling thread. */
    private static long runSingle(Algorithm a, int[] input) {
        SharedData data = new SharedData(input);
        Ops ops = new Ops(data, "single", null);
        SortAlgorithm alg = a.create();
        long t = System.nanoTime();
        alg.sort(ops, 0, input.length);
        long dt = System.nanoTime() - t;
        if (!isSorted(data.list)) throw new IllegalStateException("single-thread baseline produced wrong order");
        return dt;
    }

    /** Same work as the three threads, but done one after another in the calling thread. */
    private static long runSplit(Algorithm a, int[] input) {
        SharedData data = new SharedData(input);
        Ops ops = new Ops(data, "split", null);
        int n = input.length;
        int mid = MultithreadedSorter.splitIndex(n);
        long t = System.nanoTime();
        a.create().sort(ops, 0, mid);
        a.create().sort(ops, mid, n);
        int i = 0, j = mid, k = 0;
        while (i < mid && j < n) {
            if (ops.mergeCompare(i, j) <= 0) ops.mergeWrite(k++, i++);
            else ops.mergeWrite(k++, j++);
        }
        while (i < mid) ops.mergeWrite(k++, i++);
        while (j < n) ops.mergeWrite(k++, j++);
        long dt = System.nanoTime() - t;
        if (!isSorted(data.result)) throw new IllegalStateException("split baseline produced wrong order");
        return dt;
    }

    /** @return {untraced ms, traced ms, events} */
    private static double[] traceOverhead(Algorithm a, int n) throws InterruptedException {
        int[] input = DataGenerator.generate(n, SEED, Integer.MIN_VALUE, Integer.MAX_VALUE, DataGenerator.Pattern.RANDOM);
        long[] plain = new long[REPS];
        long[] traced = new long[REPS];
        int events = 0;
        for (int i = 0; i < WARMUPS + REPS; i++) {
            SortResult p = MultithreadedSorter.sort(input, a.key, a.create(), a.key, a.create());
            TraceRecorder rec = new TraceRecorder(System.nanoTime());
            SortResult t = MultithreadedSorter.sort(input, a.key, a.create(), a.key, a.create(), rec);
            if (i >= WARMUPS) {
                plain[i - WARMUPS] = p.totalNanos;
                traced[i - WARMUPS] = t.totalNanos;
                events = rec.size();
            }
        }
        return new double[] {median(plain) / 1e6, median(traced) / 1e6, events};
    }

    // ------------------------------------------------------------------ parent

    private static List<String> runWorker() throws Exception {
        List<String> cmd = List.of(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "Benchmark", "--worker");
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        Process p = pb.start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.matches("^[MST],.*")) {
                    lines.add(line);
                    System.out.println("  " + line);
                } else {
                    System.out.println("  (worker) " + line);
                }
            }
        }
        if (p.waitFor() != 0) throw new IllegalStateException("benchmark worker failed");
        return lines;
    }

    private static double med(List<double[]> runs, int col) {
        double[] v = runs.stream().mapToDouble(r -> r[col]).sorted().toArray();
        return v[v.length / 2];
    }

    private static double min(List<double[]> runs, int col) {
        return runs.stream().mapToDouble(r -> r[col]).min().orElse(0);
    }

    private static double max(List<double[]> runs, int col) {
        return runs.stream().mapToDouble(r -> r[col]).max().orElse(0);
    }

    private static String fmtN(int n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', ' ');
    }

    private static void writeResults(Path root, Map<String, List<double[]>> rows) throws Exception {
        Path dir = root.resolve("tests").resolve("results");
        Files.createDirectories(dir);

        StringBuilder csv = new StringBuilder(
                "algo,n,single_ms,split_ms,multi_ms,multi_min_ms,multi_max_ms,sort_phase_ms,merge_phase_ms,speedup,parallel_speedup\n");
        StringBuilder t1 = new StringBuilder();
        StringBuilder t2 = new StringBuilder();
        StringBuilder t3 = new StringBuilder();
        for (Map.Entry<String, List<double[]>> e : rows.entrySet()) {
            String[] k = e.getKey().split(",");
            List<double[]> r = e.getValue();
            String algo = k[1];
            int n = Integer.parseInt(k[2]);
            if (k[0].equals("T")) {
                t3.append(String.format(Locale.ROOT, "| %s | %.3f | %.3f | %.1f× | %,d |%n",
                        algo, med(r, 0), med(r, 1), med(r, 1) / med(r, 0), (long) med(r, 2)).replace(',', ' '));
                continue;
            }
            double single = med(r, 0), split = med(r, 1), multi = med(r, 2), sort = med(r, 3);
            double merge = Math.max(0, multi - sort);
            if (k[0].equals("M")) {
                csv.append(String.format(Locale.ROOT, "%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.3f,%.3f%n",
                        algo, n, single, split, multi, min(r, 2), max(r, 2), sort, merge, single / multi, split / multi));
                t1.append(String.format(Locale.ROOT, "| %s | %s | %.3f | %.3f | %.3f (%.3f–%.3f) | %.3f | %.3f | %.2f | %.2f |%n",
                        algo, fmtN(n), single, split, multi, min(r, 2), max(r, 2), sort, merge, single / multi, split / multi));
            } else {
                t2.append(String.format(Locale.ROOT, "| %s | %s | %.3f | %.3f | %.2f |%n",
                        algo, fmtN(n), single, multi, single / multi));
            }
        }
        Files.writeString(dir.resolve("benchmark.csv"), csv.toString(), StandardCharsets.UTF_8);

        StringBuilder md = new StringBuilder();
        md.append("# Хугацааны хэмжилт\n\n");
        md.append("Энэ файлыг `./bench.sh` автоматаар үүсгэсэн. ").append(JVMS).append(" тусдаа JVM ажиллуулсан. JVM тус бүрд ")
          .append(WARMUPS).append(" дулаацуулах ажиллалтын дараа ").append(REPS)
          .append(" удаа хэмжиж медианыг авсан. Хүснэгтэд тэдгээр ").append(JVMS).append(" медианы **медиан**-ыг (ms) харуулав. ")
          .append("Хаалтанд 3 thread-ийн хугацааны JVM хоорондын min–max мужийг өгөв. ")
          .append("Оролт: санамсаргүй int (seed = ").append(SEED).append("). Trace унтраалттай.\n\n");
        md.append("- **1 thread:** нэг thread бүх массивыг ижил алгоритмаар эрэмбэлнэ (харьцуулах суурь).\n");
        md.append("- **1 thread, хувааж:** номын бүтэцтэй ижил ажлыг (зүүн хэсэг, баруун хэсэг, нэгтгэл) нэг thread дараалан хийнэ.\n");
        md.append("- **3 thread:** номын бүтэц (2 sorting thread + 1 merging thread, parent join хийнэ).\n");
        md.append("- **Нийт хурдсалт** = 1 thread / 3 thread.\n");
        md.append("- **Зэрэгцээний хурдсалт** = 1 thread хувааж / 3 thread, өөрөөр хэлбэл хоёр хэсгийг зэрэг ажиллуулснаас олсон ашиг.\n\n");
        md.append("| | |\n|---|---|\n");
        md.append("| Огноо | ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append(" |\n");
        md.append("| Java | ").append(System.getProperty("java.vendor")).append(' ')
          .append(System.getProperty("java.runtime.version")).append(" |\n");
        md.append("| OS | ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
          .append(" (").append(System.getProperty("os.arch")).append(") |\n");
        md.append("| CPU цөм | ").append(Runtime.getRuntime().availableProcessors()).append(" |\n\n");

        md.append("## 1. Алгоритм тус бүрээр (хоёр sorter ижил алгоритмтай)\n\n");
        md.append("| Алгоритм | n | 1 thread | 1 thread, хувааж | 3 thread (min–max) | Эрэмбэлэх үе | Нэгтгэх үе | Нийт хурдсалт | Зэрэгцээний хурдсалт |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        md.append(t1);
        md.append("\n## 2. Жижиг n дээрх thread-ийн зардал\n\n");
        md.append("| Алгоритм | n | 1 thread | 3 thread | Нийт хурдсалт |\n|---|---:|---:|---:|---:|\n");
        md.append(t2);
        md.append("\n## 3. Event trace-ийн зардал (n = 1 000, 3 thread)\n\n");
        md.append("| Алгоритм | Trace-гүй (ms) | Trace-тэй (ms) | Удаашрал | Event-ийн тоо |\n|---|---:|---:|---:|---:|\n");
        md.append(t3);
        md.append("\nТэмдэглэл: хугацаа нь машин, JVM, бусад ачааллаас хамаарч ажиллах бүрд өөр гардаг. ")
          .append("1 ms-ээс бага утгууд шуугиантай тул зөвхөн чиг хандлагыг харна.\n");
        Files.writeString(dir.resolve("benchmark.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.println("Үр дүн: tests/results/benchmark.md, tests/results/benchmark.csv");
    }

    private static long median(long[] a) {
        long[] c = a.clone();
        Arrays.sort(c);
        return c[c.length / 2];
    }

    private static boolean isSorted(int[] a) {
        for (int i = 1; i < a.length; i++) if (a[i - 1] > a[i]) return false;
        return true;
    }

    private Benchmark() { }
}
