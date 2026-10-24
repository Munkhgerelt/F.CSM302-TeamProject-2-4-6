package sorting.algo;

import sorting.core.Ops;

/**
 * Top-down merge sort on [from, to). Uses a private auxiliary buffer; every
 * write back into the shared list goes through {@link Ops#set(int, int)}.
 * Stable, O(n log n) time, O(n) extra space.
 */
public final class MergeSort implements SortAlgorithm {
    private int[] aux;
    private int base; // first index of the range this thread owns

    @Override
    public void sort(Ops ops, int from, int to) {
        if (to - from < 2) return;
        aux = new int[to - from];
        base = from;
        sortRange(ops, from, to);
        aux = null;
    }

    private void sortRange(Ops ops, int lo, int hi) {
        if (hi - lo < 2) return;
        int mid = (lo + hi) >>> 1;
        sortRange(ops, lo, mid);
        sortRange(ops, mid, hi);
        merge(ops, lo, mid, hi);
    }

    private void merge(Ops ops, int lo, int mid, int hi) {
        // copy the current [lo, hi) into aux (no event: plain read)
        for (int k = lo; k < hi; k++) aux[k - base] = ops.get(k);
        int i = lo, j = mid;
        for (int k = lo; k < hi; k++) {
            if (i >= mid) {
                ops.set(k, aux[j++ - base]);
            } else if (j >= hi) {
                ops.set(k, aux[i++ - base]);
            } else if (ops.compareValues(i, j, aux[i - base], aux[j - base]) <= 0) {
                ops.set(k, aux[i++ - base]);
            } else {
                ops.set(k, aux[j++ - base]);
            }
        }
    }
}
