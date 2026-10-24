package sorting.core;

import sorting.algo.SortAlgorithm;

/**
 * The parent-thread logic of the textbook project:
 * <ol>
 *   <li>split the list into two halves (left = ceil(n/2), right = floor(n/2)),</li>
 *   <li>start two sorting threads, each with its own {@link SortParams},</li>
 *   <li>join both sorting threads,</li>
 *   <li>start the merging thread with {@link MergeParams} and join it,</li>
 *   <li>return the merged second array.</li>
 * </ol>
 */
public final class MultithreadedSorter {
    public static final String LEFT_NAME = "Sorter-L";
    public static final String RIGHT_NAME = "Sorter-R";
    public static final String MERGER_NAME = "Merger";

    private MultithreadedSorter() { }

    /** Index where the right half starts; the left half gets the extra element when n is odd. */
    public static int splitIndex(int n) {
        return (n + 1) / 2;
    }

    public static SortResult sort(int[] input,
                                  String leftAlgoName, SortAlgorithm leftAlgo,
                                  String rightAlgoName, SortAlgorithm rightAlgo)
            throws InterruptedException {
        final int n = input.length;
        final int mid = splitIndex(n);
        final SharedData data = new SharedData(input);
        final long t0 = System.nanoTime();

        SortingThread left = new SortingThread(data,
                new SortParams(LEFT_NAME, 0, mid, leftAlgoName, leftAlgo), t0);
        SortingThread right = new SortingThread(data,
                new SortParams(RIGHT_NAME, mid, n, rightAlgoName, rightAlgo), t0);

        left.start();
        right.start();
        left.join();
        right.join();
        long sortPhase = System.nanoTime() - t0;
        rethrowIfFailed(left.getName(), left.failure());
        rethrowIfFailed(right.getName(), right.failure());

        MergingThread merger = new MergingThread(data, new MergeParams(MERGER_NAME, 0, mid, n), t0);
        merger.start();
        merger.join();
        long total = System.nanoTime() - t0;
        rethrowIfFailed(merger.getName(), merger.failure());

        return new SortResult(input.clone(), data.result, mid, left, right, merger, sortPhase, total);
    }

    private static void rethrowIfFailed(String name, Throwable t) {
        if (t != null) {
            throw new IllegalStateException("thread " + name + " failed: " + t, t);
        }
    }
}
