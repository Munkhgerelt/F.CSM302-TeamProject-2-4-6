package sorting.algo;

import java.util.Locale;

/**
 * Selectable sorting algorithms for the two sorting threads.
 * Each algorithm declares its complexity class and the maximum input size
 * the program accepts for it (O(n^2) algorithms get a much smaller limit).
 */
public enum Algorithm {
    INSERTION("insertion", "Insertion sort", "O(n^2)", true),
    BUBBLE("bubble", "Bubble sort", "O(n^2)", true),
    SELECTION("selection", "Selection sort", "O(n^2)", true),
    MERGE("merge", "Merge sort", "O(n log n)", false),
    QUICK("quick", "Quick sort (Hoare, middle pivot)", "O(n log n) avg", false);

    /** Max total n accepted when this algorithm is used by a sorting thread. */
    public static final int MAX_N_QUADRATIC = 20_000;
    public static final int MAX_N_LOGLINEAR = 1_000_000;

    public final String key;
    public final String displayName;
    public final String complexity;
    public final boolean quadratic;

    Algorithm(String key, String displayName, String complexity, boolean quadratic) {
        this.key = key;
        this.displayName = displayName;
        this.complexity = complexity;
        this.quadratic = quadratic;
    }

    public int maxN() {
        return quadratic ? MAX_N_QUADRATIC : MAX_N_LOGLINEAR;
    }

    /** Creates a fresh, stateless-per-call implementation. */
    public SortAlgorithm create() {
        switch (this) {
            case INSERTION: return new InsertionSort();
            case BUBBLE: return new BubbleSort();
            case SELECTION: return new SelectionSort();
            case MERGE: return new MergeSort();
            case QUICK: return new QuickSort();
            default: throw new IllegalStateException("unknown algorithm " + this);
        }
    }

    /** Parses a user-supplied key; returns null when unknown. */
    public static Algorithm fromKey(String s) {
        if (s == null) return null;
        String k = s.trim().toLowerCase(Locale.ROOT);
        for (Algorithm a : values()) {
            if (a.key.equals(k)) return a;
        }
        return null;
    }

    public static String keysList() {
        StringBuilder sb = new StringBuilder();
        for (Algorithm a : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(a.key);
        }
        return sb.toString();
    }
}
