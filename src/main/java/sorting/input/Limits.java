package sorting.input;

import sorting.algo.Algorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * Size limits. O(n^2) algorithms (insertion/bubble/selection) are limited to
 * n ≤ 20 000 because they become very slow beyond that; O(n log n) algorithms
 * (merge/quick) accept n ≤ 1 000 000. The quadratic limit can be lifted with
 * --force (for experiments), the hard limit cannot.
 */
public final class Limits {
    public static final int HARD_MAX_N = Algorithm.MAX_N_LOGLINEAR;
    /**
     * Max n for writing an event trace (every compare/swap becomes one JSON line).
     * O(n^2) algorithms produce ~n^2/4 events, so they get a much smaller limit;
     * this keeps every allowed trace below the recorder's 2 000 000 event cap.
     */
    public static final int TRACE_MAX_N = 10_000;
    public static final int TRACE_MAX_N_QUADRATIC = 1_000;

    private Limits() { }

    /**
     * Validates n for the chosen algorithms.
     * @return warnings to show (may be empty); throws when the input is rejected.
     */
    public static List<String> check(int n, Algorithm left, Algorithm right, boolean force) {
        return check(n, left, right, force, false);
    }

    public static List<String> check(int n, Algorithm left, Algorithm right, boolean force, boolean trace) {
        List<String> warnings = new ArrayList<>();
        if (trace) {
            boolean quadratic = left.quadratic || right.quadratic;
            int traceMax = quadratic ? TRACE_MAX_N_QUADRATIC : TRACE_MAX_N;
            if (n > traceMax) {
                throw new InputException("--trace-ийн хязгаар: O(n^2) алгоритмд n ≤ " + TRACE_MAX_N_QUADRATIC
                        + ", merge/quick-д n ≤ " + TRACE_MAX_N + " (одоо n = " + n + "). Алхам бүр нэг event болдог тул"
                        + " файл хэт томорно. n-ийг багасгах эсвэл --trace-гүй ажиллуулна уу.");
            }
        }
        if (n <= 0) {
            throw new InputException("Оролт хоосон байна (n = 0).");
        }
        if (n > HARD_MAX_N) {
            throw new InputException("n = " + n + " нь дээд хязгаараас хэтэрсэн (n ≤ " + HARD_MAX_N + ").");
        }
        Algorithm slow = left.quadratic ? left : (right.quadratic ? right : null);
        if (slow != null && n > Algorithm.MAX_N_QUADRATIC) {
            String msg = slow.key + " нь " + slow.complexity + " алгоритм тул n ≤ " + Algorithm.MAX_N_QUADRATIC
                    + " гэж хязгаарласан (одоо n = " + n + ").";
            if (!force) {
                throw new InputException(msg + " merge эсвэл quick сонгох, n-ийг багасгах, эсвэл --force нэмж"
                        + " удаан ажиллахыг зөвшөөрнө үү.");
            }
            warnings.add("Анхааруулга: " + msg + " --force өгсөн тул үргэлжлүүлж байна, удаан ажиллаж магадгүй.");
        }
        if (n % 2 == 1 && n > 1) {
            warnings.add("Тэмдэглэл: n = " + n + " сондгой тул зүүн хэсэг " + ((n + 1) / 2)
                    + ", баруун хэсэг " + (n / 2) + " элементтэй (⌈n/2⌉ / ⌊n/2⌋).");
        }
        if (n == 1) {
            warnings.add("Тэмдэглэл: n = 1 тул баруун хэсэг хоосон; Sorter-R thread ажиллах ч эрэмбэлэх зүйлгүй.");
        }
        return warnings;
    }
}
