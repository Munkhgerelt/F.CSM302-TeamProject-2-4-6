package sorting.input;

import java.util.Locale;
import java.util.Random;

/**
 * Reproducible test-data generator. The same (n, seed, min, max, pattern)
 * always produces the same array, so every experiment can be repeated.
 */
public final class DataGenerator {

    public enum Pattern {
        RANDOM("random"), SORTED("sorted"), REVERSED("reversed"), DUPLICATES("duplicates");

        public final String key;

        Pattern(String key) { this.key = key; }

        public static Pattern fromKey(String s) {
            String k = s.trim().toLowerCase(Locale.ROOT);
            for (Pattern p : values()) if (p.key.equals(k)) return p;
            throw new InputException("'" + s + "' гэсэн өгөгдлийн хэлбэр байхгүй. Боломжит: random, sorted, reversed, duplicates");
        }
    }

    public static final int DEFAULT_MIN = 1;
    public static final int DEFAULT_MAX = 100;

    private DataGenerator() { }

    public static int[] generate(long n, long seed, long min, long max, Pattern pattern) {
        if (n <= 0) {
            throw new InputException("n (хэмжээ) эерэг бүхэл тоо байх ёстой (одоо " + n + ").");
        }
        if (n > Limits.HARD_MAX_N) {
            throw new InputException("n = " + n + " нь дээд хязгаараас хэтэрсэн (n ≤ " + Limits.HARD_MAX_N + ").");
        }
        if (min < Integer.MIN_VALUE || max > Integer.MAX_VALUE) {
            throw new InputException("min/max нь int мужид байх ёстой: [" + Integer.MIN_VALUE + ", " + Integer.MAX_VALUE + "].");
        }
        if (min > max) {
            throw new InputException("min (" + min + ") нь max (" + max + ")-аас их байж болохгүй.");
        }
        Random rnd = new Random(seed);
        long span = max - min + 1;               // fits in long, ≥ 1
        int[] a = new int[(int) n];
        if (pattern == Pattern.DUPLICATES) {
            long distinct = Math.min(5, span);   // only a few distinct values
            for (int i = 0; i < n; i++) a[i] = (int) (min + Math.floorMod(rnd.nextLong(), distinct));
            return a;
        }
        for (int i = 0; i < n; i++) a[i] = (int) (min + Math.floorMod(rnd.nextLong(), span));
        if (pattern == Pattern.SORTED || pattern == Pattern.REVERSED) {
            java.util.Arrays.sort(a);
            if (pattern == Pattern.REVERSED) {
                for (int i = 0, j = a.length - 1; i < j; i++, j--) {
                    int t = a[i]; a[i] = a[j]; a[j] = t;
                }
            }
        }
        return a;
    }
}
