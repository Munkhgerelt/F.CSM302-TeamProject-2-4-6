package sorting.algo;

import sorting.core.Ops;

/** Insertion sort implemented with adjacent swaps (easy to visualise). */
public final class InsertionSort implements SortAlgorithm {
    @Override
    public void sort(Ops ops, int from, int to) {
        for (int i = from + 1; i < to; i++) {
            int j = i;
            while (j > from && ops.compare(j - 1, j) > 0) {
                ops.swap(j - 1, j);
                j--;
            }
        }
    }
}
