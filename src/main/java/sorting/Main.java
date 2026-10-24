package sorting;

import sorting.algo.Algorithm;
import sorting.core.MultithreadedSorter;
import sorting.core.SortResult;
import sorting.core.SortingThread;
import sorting.input.InputException;
import sorting.input.Limits;
import sorting.trace.TraceEvent;
import sorting.trace.TraceJson;
import sorting.trace.TraceRecorder;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Console entry point.
 * Examples:
 *   java -cp out sorting.Main 7 12 19 3 18 4 2 6 15 8
 *   java -cp out sorting.Main --input "7,12,19,3,18,4,2,6,15,8" --algo-left insertion --algo-right quick
 *   java -cp out sorting.Main --random 1000 --seed 42 --pattern reversed --algo quick
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        useUtf8Console();
        Config cfg;
        int[] input;
        List<String> notes;
        try {
            cfg = Config.parse(args);
            if (cfg.help) {
                printUsage();
                return;
            }
            if (cfg.listAlgos) {
                printAlgorithms();
                return;
            }
            input = cfg.resolveInput();
            notes = Limits.check(input.length, cfg.left, cfg.right, cfg.force, cfg.tracePath != null);
        } catch (InputException e) {
            System.err.println("Алдаа: " + e.getMessage());
            System.err.println("Жишээ: ./run.sh --input \"7,12,19,3,18,4,2,6,15,8\" --algo merge   (тусламж: --help)");
            System.exit(2);
            return;
        }

        System.out.println("Оролт: " + cfg.sourceDescription);
        for (String note : notes) System.out.println(note);
        SortResult r = MultithreadedSorter.sort(input,
                cfg.left.key, cfg.left.create(),
                cfg.right.key, cfg.right.create());
        boolean ok = print(r, cfg.full);
        if (cfg.tracePath != null) {
            ok &= writeTrace(cfg, input, r);
        }
        if (!ok) System.exit(1);
    }

    /**
     * Repeats the same run with tracing enabled and writes the JSON trace.
     * The untraced run above gives the reference time (tracing adds overhead).
     */
    static boolean writeTrace(Config cfg, int[] input, SortResult untraced) throws InterruptedException {
        TraceRecorder rec = new TraceRecorder(System.nanoTime());
        SortResult traced = MultithreadedSorter.sort(input,
                cfg.left.key, cfg.left.create(),
                cfg.right.key, cfg.right.create(), rec);
        boolean same = Arrays.equals(traced.result, untraced.result);
        String json = TraceJson.toJson(traced, rec, untraced.totalNanos);
        Path out = Path.of(cfg.tracePath);
        try {
            if (out.getParent() != null) Files.createDirectories(out.getParent());
            Files.writeString(out, json, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            System.err.println("Алдаа: trace файлыг бичиж чадсангүй: " + cfg.tracePath + " (" + e.getMessage() + ")");
            return false;
        }
        List<TraceEvent> events = rec.snapshot();
        System.out.println();
        System.out.println("=== Event trace ===");
        System.out.println("Файл: " + out.toAbsolutePath() + " (" + json.length() / 1024 + " KB)");
        System.out.println("Event-ийн тоо: " + events.size() + (rec.truncated() ? " (ХЯЗГААРТ ХҮРЧ ТАСАЛСАН)" : ""));
        System.out.println("Төрлөөр: " + TraceJson.countByType(events));
        Map<String, Integer> byThread = new TreeMap<>();
        for (TraceEvent e : events) byThread.merge(e.thread, 1, Integer::sum);
        System.out.println("Thread-ээр: " + byThread);
        System.out.printf("Хугацаа: trace-гүй %.3f ms, trace-тэй %.3f ms (trace бүртгэлийн нэмэгдэл)%n",
                untraced.totalNanos / 1e6, traced.totalNanos / 1e6);
        System.out.println("Trace-тэй ажиллалтын үр дүн trace-гүйтэй " + (same ? "ИЖИЛ" : "ЗӨРҮҮТЭЙ"));
        int show = Math.min(12, events.size());
        System.out.println("Эхний " + show + " event:");
        for (int i = 0; i < show; i++) System.out.println("  " + format(events.get(i)));
        return same && !rec.truncated();
    }

    static String format(TraceEvent e) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("#%-4d %9.3f ms  %-8s %-9s", e.seq, e.t / 1e6, e.thread, e.type));
        if (e.a != TraceEvent.NONE) sb.append(" a=").append(e.a);
        if (e.b != TraceEvent.NONE) sb.append(" b=").append(e.b);
        if (e.c != TraceEvent.NONE) sb.append(" c=").append(e.c);
        if (e.note != null) sb.append(" ").append(e.note);
        return sb.toString();
    }

    /** Mongolian text must print correctly regardless of the OS default charset. */
    static void useUtf8Console() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    static void printUsage() {
        System.out.println("Хэрэглээ: ./run.sh <оролт> [сонголтууд]");
        System.out.println("Оролт (аль нэг нь заавал):");
        System.out.println("  --input \"a,b,c\"       бүхэл тоонууд (таслал, цэг таслал эсвэл зайгаар тусгаарлана)");
        System.out.println("  a b c ...             тоонуудыг шууд аргументаар");
        System.out.println("  --file PATH           текст файлаас унших ('#'-ээс хойш тайлбар)");
        System.out.println("  --random N            N санамсаргүй тоо үүсгэх");
        System.out.println("     --seed S           давтагдах үр дүнд seed (анхдагч: одоогийн цаг, хэвлэгдэнэ)");
        System.out.println("     --min A --max B    утгын муж (анхдагч: 1..100)");
        System.out.println("     --pattern P        random | sorted | reversed | duplicates");
        System.out.println("Алгоритм:");
        System.out.println("  --algo NAME           хоёр sorting thread-д ижил алгоритм (анхдагч: merge)");
        System.out.println("  --algo-left NAME      Sorter-L (зүүн хэсэг)-ийн алгоритм");
        System.out.println("  --algo-right NAME     Sorter-R (баруун хэсэг)-ийн алгоритм");
        System.out.println("  --list-algos          боломжит алгоритмууд");
        System.out.println("Бусад:");
        System.out.println("  --force               O(n^2) алгоритмын n ≤ " + Algorithm.MAX_N_QUADRATIC + " хязгаарыг үл тоох");
        System.out.println("  --full                n > " + PREVIEW_LIMIT + " үед ч массивыг бүтнээр хэвлэх");
        System.out.println("  --trace FILE          thread бүрийн алхмыг JSON event trace болгон бичих (n ≤ " + Limits.TRACE_MAX_N + ")");
        System.out.println("  -h, --help            энэ тусламж");
        System.out.println("Хязгаар: O(n^2) (insertion/bubble/selection) n ≤ " + Algorithm.MAX_N_QUADRATIC
                + ", O(n log n) (merge/quick) n ≤ " + Algorithm.MAX_N_LOGLINEAR + ", утга нь int муж.");
    }

    static void printAlgorithms() {
        for (Algorithm a : Algorithm.values()) {
            System.out.printf("  %-10s %-34s %s%n", a.key, a.displayName, a.complexity);
        }
    }

    private static void printSorter(SortingThread t) {
        System.out.printf("[%s] tid=%d муж=[%d,%d) %s: compare=%d swap=%d write=%d, %.3f-%.3f ms%n",
                t.getName(), t.getId(), t.params().startIndex, t.params().endIndex,
                t.params().algorithmName, t.ops().compares(), t.ops().swaps(), t.ops().writes(),
                t.startedAtNanos() / 1e6, t.finishedAtNanos() / 1e6);
    }

    static final int PREVIEW_LIMIT = 50;

    /** Prints the whole array for small n, otherwise the first and last 10 values. */
    static String show(int[] a, boolean full) {
        if (full || a.length <= PREVIEW_LIMIT) return Arrays.toString(a);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(a[i]).append(", ");
        sb.append("... (").append(a.length - 20).append(" утга нуусан) ...");
        for (int i = a.length - 10; i < a.length; i++) sb.append(", ").append(a[i]);
        return sb.append("]").toString();
    }

    /** @return true when the result is verified correct. */
    static boolean print(SortResult r, boolean full) {
        int n = r.original.length;
        System.out.println("Эх жагсаалт (n=" + n + "): " + show(r.original, full));
        System.out.printf("Хуваалт: зүүн [0,%d) хэмжээ %d, баруун [%d,%d) хэмжээ %d%n",
                r.mid, r.mid, r.mid, n, n - r.mid);
        printSorter(r.left);
        printSorter(r.right);
        System.out.printf("[%s] tid=%d нэгтгэл list[0,%d) + list[%d,%d) -> result[0,%d): compare=%d write=%d, %.3f-%.3f ms%n",
                r.merger.getName(), r.merger.getId(), r.mid, r.mid, n, n,
                r.merger.ops().compares(), r.merger.ops().writes(),
                r.merger.startedAtNanos() / 1e6, r.merger.finishedAtNanos() / 1e6);
        System.out.println("Parent thread бүх thread-ийг join хийлээ.");
        System.out.println("Эрэмбэлсэн үр дүн: " + show(r.result, full));

        int[] expected = r.original.clone();
        Arrays.sort(expected);
        boolean ok = Arrays.equals(expected, r.result);
        System.out.println("Шалгалт (Arrays.sort-той харьцуулав): " + (ok ? "ЗӨВ" : "БУРУУ"));
        System.out.printf("Хугацаа: эрэмбэлэх үе %.3f ms, нийт %.3f ms%n",
                r.sortPhaseNanos / 1e6, r.totalNanos / 1e6);
        return ok;
    }
}
