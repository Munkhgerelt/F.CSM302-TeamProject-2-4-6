package sorting;

import sorting.algo.Algorithm;
import sorting.core.MultithreadedSorter;
import sorting.core.SortResult;
import sorting.core.SortingThread;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Console entry point.
 * Examples:
 *   java -cp out sorting.Main 7 12 19 3 18 4 2 6 15 8
 *   java -cp out sorting.Main --input "7,12,19,3,18,4,2,6,15,8" --algo-left insertion --algo-right quick
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        useUtf8Console();
        Config cfg;
        int[] input;
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
            input = parseNumbers(cfg.inputText);
        } catch (IllegalArgumentException e) {
            System.err.println("Алдаа: " + e.getMessage());
            System.err.println("Жишээ: ./run.sh --input \"7,12,19,3,18,4,2,6,15,8\" --algo merge");
            System.exit(2);
            return;
        }

        SortResult r = MultithreadedSorter.sort(input,
                cfg.left.key, cfg.left.create(),
                cfg.right.key, cfg.right.create());
        print(r);
    }

    /** Mongolian text must print correctly regardless of the OS default charset. */
    static void useUtf8Console() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    /** Minimal number parser (full validation is added separately). */
    static int[] parseNumbers(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("оролт хоосон байна.");
        }
        String[] tokens = text.trim().split("[,\\s]+");
        int[] a = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                a[i] = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + tokens[i] + "' нь бүхэл тоо биш.");
            }
        }
        return a;
    }

    static void printUsage() {
        System.out.println("Хэрэглээ: ./run.sh [сонголтууд] [тоонууд...]");
        System.out.println("  --input \"a,b,c\"       эрэмбэлэх бүхэл тоонууд (таслал эсвэл зайгаар)");
        System.out.println("  --algo NAME           хоёр sorting thread-д ижил алгоритм");
        System.out.println("  --algo-left NAME      Sorter-L (зүүн хэсэг)-ийн алгоритм");
        System.out.println("  --algo-right NAME     Sorter-R (баруун хэсэг)-ийн алгоритм");
        System.out.println("  --list-algos          боломжит алгоритмуудыг харуулах");
        System.out.println("  -h, --help            энэ тусламж");
        System.out.println("Алгоритм: " + Algorithm.keysList() + " (анхдагч: merge)");
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

    static void print(SortResult r) {
        int n = r.original.length;
        System.out.println("Эх жагсаалт (n=" + n + "): " + Arrays.toString(r.original));
        System.out.printf("Хуваалт: зүүн [0,%d) хэмжээ %d, баруун [%d,%d) хэмжээ %d%n",
                r.mid, r.mid, r.mid, n, n - r.mid);
        printSorter(r.left);
        printSorter(r.right);
        System.out.printf("[%s] tid=%d нэгтгэл list[0,%d) + list[%d,%d) -> result[0,%d): compare=%d write=%d, %.3f-%.3f ms%n",
                r.merger.getName(), r.merger.getId(), r.mid, r.mid, n, n,
                r.merger.ops().compares(), r.merger.ops().writes(),
                r.merger.startedAtNanos() / 1e6, r.merger.finishedAtNanos() / 1e6);
        System.out.println("Parent thread бүх thread-ийг join хийлээ.");
        System.out.println("Эрэмбэлсэн үр дүн: " + Arrays.toString(r.result));

        int[] expected = r.original.clone();
        Arrays.sort(expected);
        boolean ok = Arrays.equals(expected, r.result);
        System.out.println("Шалгалт (Arrays.sort-той харьцуулав): " + (ok ? "ЗӨВ" : "БУРУУ"));
        System.out.printf("Хугацаа: эрэмбэлэх үе %.3f ms, нийт %.3f ms%n",
                r.sortPhaseNanos / 1e6, r.totalNanos / 1e6);
    }
}
