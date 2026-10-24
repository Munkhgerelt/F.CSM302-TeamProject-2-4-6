package sorting;

import sorting.algo.InsertionSort;
import sorting.core.MultithreadedSorter;
import sorting.core.SortResult;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Console entry point.
 * Usage: java -cp out sorting.Main 7 12 19 3 18 4 2 6 15 8
 *        java -cp out sorting.Main --input "7,12,19,3,18,4,2,6,15,8"
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        useUtf8Console();
        int[] input;
        try {
            input = parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Алдаа: " + e.getMessage());
            System.err.println("Жишээ: ./run.sh --input \"7,12,19,3,18,4,2,6,15,8\"");
            System.exit(2);
            return;
        }

        SortResult r = MultithreadedSorter.sort(input,
                "insertion", new InsertionSort(),
                "insertion", new InsertionSort());
        print(r);
    }

    /** Mongolian text must print correctly regardless of the OS default charset. */
    static void useUtf8Console() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    /** Minimal parser: integers given as arguments or via --input "a,b,c". */
    static int[] parse(String[] args) {
        String text;
        if (args.length >= 2 && args[0].equals("--input")) {
            text = args[1];
        } else {
            text = String.join(" ", args);
        }
        String[] tokens = text.trim().split("[,\\s]+");
        if (text.trim().isEmpty()) {
            throw new IllegalArgumentException("оролт хоосон байна.");
        }
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

    static void print(SortResult r) {
        int n = r.original.length;
        System.out.println("Эх жагсаалт (n=" + n + "): " + Arrays.toString(r.original));
        System.out.printf("Хуваалт: зүүн [0,%d) хэмжээ %d, баруун [%d,%d) хэмжээ %d%n",
                r.mid, r.mid, r.mid, n, n - r.mid);
        System.out.printf("[%s] tid=%d муж=[%d,%d) %s: compare=%d swap=%d, %.3f-%.3f ms%n",
                r.left.getName(), r.left.getId(), r.left.params().startIndex, r.left.params().endIndex,
                r.left.params().algorithmName, r.left.ops().compares(), r.left.ops().swaps(),
                r.left.startedAtNanos() / 1e6, r.left.finishedAtNanos() / 1e6);
        System.out.printf("[%s] tid=%d муж=[%d,%d) %s: compare=%d swap=%d, %.3f-%.3f ms%n",
                r.right.getName(), r.right.getId(), r.right.params().startIndex, r.right.params().endIndex,
                r.right.params().algorithmName, r.right.ops().compares(), r.right.ops().swaps(),
                r.right.startedAtNanos() / 1e6, r.right.finishedAtNanos() / 1e6);
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
