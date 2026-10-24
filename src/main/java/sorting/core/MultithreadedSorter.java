package sorting.core;

import sorting.algo.SortAlgorithm;
import sorting.trace.TraceRecorder;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * The parent-thread logic of the textbook project:
 * <ol>
 *   <li>split the list into two halves (left = ceil(n/2), right = floor(n/2)),</li>
 *   <li>start two sorting threads, each with its own {@link SortParams},</li>
 *   <li>join both sorting threads,</li>
 *   <li>start the merging thread with {@link MergeParams} and join it,</li>
 *   <li>return the merged second array.</li>
 * </ol>
 * When a {@link TraceRecorder} is given, every step of every thread is recorded.
 */
public final class MultithreadedSorter {
    public static final String LEFT_NAME = "Sorter-L";
    public static final String RIGHT_NAME = "Sorter-R";
    public static final String MERGER_NAME = "Merger";
    public static final String PARENT_NAME = "Parent";

    private MultithreadedSorter() { }

    /** Index where the right half starts; the left half gets the extra element when n is odd. */
    public static int splitIndex(int n) {
        return (n + 1) / 2;
    }

    /** Untraced run. */
    public static SortResult sort(int[] input,
                                  String leftAlgoName, SortAlgorithm leftAlgo,
                                  String rightAlgoName, SortAlgorithm rightAlgo)
            throws InterruptedException {
        return sort(input, leftAlgoName, leftAlgo, rightAlgoName, rightAlgo, RunOptions.none());
    }

    /**
     * @param trace recorder for all events, or null to disable tracing.
     *              When given, its t0 is used as the run's time origin.
     */
    public static SortResult sort(int[] input,
                                  String leftAlgoName, SortAlgorithm leftAlgo,
                                  String rightAlgoName, SortAlgorithm rightAlgo,
                                  TraceRecorder trace)
            throws InterruptedException {
        return sort(input, leftAlgoName, leftAlgo, rightAlgoName, rightAlgo, RunOptions.traced(trace));
    }

    /**
     * Full version: optional trace recorder, optional lockstep gate and an optional
     * callback that receives each worker thread right after it has been created.
     */
    public static SortResult sort(int[] input,
                                  String leftAlgoName, SortAlgorithm leftAlgo,
                                  String rightAlgoName, SortAlgorithm rightAlgo,
                                  RunOptions options)
            throws InterruptedException {
        final TraceRecorder trace = options.trace;
        final StepGate gate = options.gate;
        final int n = input.length;
        final int mid = splitIndex(n);
        final SharedData data = new SharedData(input);
        final long t0 = (trace != null) ? trace.t0() : System.nanoTime();
        if (trace != null) trace.record(PARENT_NAME, "RUN_START", n, mid);

        SortingThread left = new SortingThread(data,
                new SortParams(LEFT_NAME, 0, mid, leftAlgoName, leftAlgo), t0, trace, gate);
        SortingThread right = new SortingThread(data,
                new SortParams(RIGHT_NAME, mid, n, rightAlgoName, rightAlgo), t0, trace, gate);
        created(options, left);
        created(options, right);

        started(trace, left);
        left.start();
        started(trace, right);
        right.start();
        left.join();
        joined(trace, left);
        right.join();
        joined(trace, right);
        long sortPhase = System.nanoTime() - t0;
        rethrowIfFailed(left.getName(), left.failure());
        rethrowIfFailed(right.getName(), right.failure());

        MergingThread merger = new MergingThread(data, new MergeParams(MERGER_NAME, 0, mid, n), t0, trace, gate);
        created(options, merger);
        started(trace, merger);
        merger.start();
        merger.join();
        joined(trace, merger);
        long total = System.nanoTime() - t0;
        rethrowIfFailed(merger.getName(), merger.failure());

        if (trace != null) trace.record(PARENT_NAME, "RUN_END", isSorted(data.result) ? 1 : 0, n);
        return new SortResult(input.clone(), data.result, mid, left, right, merger, sortPhase, total);
    }

    /**
     * Honest wall-clock time of an untraced run: a few warm-up runs first (JIT, thread
     * pool of the OS warmed up), then the median of {@code reps} measured runs.
     */
    public static long medianUntracedNanos(int[] input,
                                           String leftName, Supplier<SortAlgorithm> left,
                                           String rightName, Supplier<SortAlgorithm> right,
                                           int warmups, int reps) throws InterruptedException {
        for (int i = 0; i < warmups; i++) sort(input, leftName, left.get(), rightName, right.get());
        long[] t = new long[Math.max(reps, 1)];
        for (int i = 0; i < t.length; i++) t[i] = sort(input, leftName, left.get(), rightName, right.get()).totalNanos;
        Arrays.sort(t);
        return t[t.length / 2];
    }

    private static void created(RunOptions options, Thread t) {
        if (options.onThreadCreated != null) options.onThreadCreated.accept(t);
    }

    private static void started(TraceRecorder trace, Thread t) {
        if (trace != null) trace.note(PARENT_NAME, "START", t.getName());
    }

    private static void joined(TraceRecorder trace, Thread t) {
        if (trace != null) trace.note(PARENT_NAME, "JOIN", t.getName());
    }

    static boolean isSorted(int[] a) {
        for (int i = 1; i < a.length; i++) if (a[i - 1] > a[i]) return false;
        return true;
    }

    private static void rethrowIfFailed(String name, Throwable t) {
        if (t != null) {
            throw new IllegalStateException("thread " + name + " failed: " + t, t);
        }
    }
}
