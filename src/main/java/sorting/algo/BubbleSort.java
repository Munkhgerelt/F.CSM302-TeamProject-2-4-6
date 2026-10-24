package sorting.algo;

import sorting.core.Ops;

/** Bubble sort with early exit when a pass makes no swaps. */
public final class BubbleSort implements SortAlgorithm {
    @Override
    public void sort(Ops ops, int from, int to) {
        for (int end = to - 1; end > from; end--) {
            boolean swapped = false;
            for (int j = from; j < end; j++) {
                if (ops.compare(j, j + 1) > 0) {
                    ops.swap(j, j + 1);
                    swapped = true;
                }
            }
            if (!swapped) return;
        }
    }
}
