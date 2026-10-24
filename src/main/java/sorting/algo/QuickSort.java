package sorting.algo;

import sorting.core.Ops;

/**
 * Quick sort with Hoare partitioning and a middle-element pivot.
 * Recurses into the smaller part and loops on the larger one, so the
 * recursion depth stays O(log n). Handles many duplicates well.
 */
public final class QuickSort implements SortAlgorithm {
    @Override
    public void sort(Ops ops, int from, int to) {
        quick(ops, from, to - 1);
    }

    private void quick(Ops ops, int lo, int hi) {
        while (lo < hi) {
            int pivot = ops.get(lo + ((hi - lo) >>> 1));
            int i = lo - 1, j = hi + 1;
            while (true) {
                do { i++; } while (ops.compareToValue(i, pivot) < 0);
                do { j--; } while (ops.compareToValue(j, pivot) > 0);
                if (i >= j) break;
                ops.swap(i, j);
            }
            // [lo, j] and [j+1, hi]
            if (j - lo < hi - j) {
                quick(ops, lo, j);
                lo = j + 1;
            } else {
                quick(ops, j + 1, hi);
                hi = j;
            }
        }
    }
}
