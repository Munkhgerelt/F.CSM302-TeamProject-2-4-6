package sorting.core;

import sorting.algo.SortAlgorithm;

/**
 * Parameters passed to a sorting thread (the textbook's "parameters" struct):
 * which part of the shared list to sort and with which algorithm.
 * The range is half-open: [startIndex, endIndex).
 */
public final class SortParams {
    public final String threadName;
    public final int startIndex;
    public final int endIndex;
    public final String algorithmName;
    public final SortAlgorithm algorithm;

    public SortParams(String threadName, int startIndex, int endIndex,
                      String algorithmName, SortAlgorithm algorithm) {
        if (startIndex < 0 || endIndex < startIndex) {
            throw new IllegalArgumentException("invalid range [" + startIndex + ", " + endIndex + ")");
        }
        this.threadName = threadName;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.algorithmName = algorithmName;
        this.algorithm = algorithm;
    }

    public int length() {
        return endIndex - startIndex;
    }
}
