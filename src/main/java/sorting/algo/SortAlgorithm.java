package sorting.algo;

import sorting.core.Ops;

/**
 * A sorting algorithm that sorts the half-open range [from, to) of the shared
 * list. All reads/compares/writes go through {@link Ops} so that every step
 * can be counted, traced and (in lockstep mode) gated by the UI.
 */
public interface SortAlgorithm {
    void sort(Ops ops, int from, int to);
}
