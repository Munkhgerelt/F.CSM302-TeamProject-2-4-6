package sorting.algo;

import sorting.core.Ops;

/** Selection sort: repeatedly selects the minimum of the unsorted suffix. */
public final class SelectionSort implements SortAlgorithm {
    @Override
    public void sort(Ops ops, int from, int to) {
        for (int i = from; i < to - 1; i++) {
            int min = i;
            for (int j = i + 1; j < to; j++) {
                if (ops.compare(j, min) < 0) min = j;
            }
            if (min != i) ops.swap(i, min);
        }
    }
}
