package sorting.core;

/**
 * One of the two sorting threads. Sorts list[startIndex, endIndex) of the
 * shared data in place with the algorithm given in its {@link SortParams}.
 */
public final class SortingThread extends Thread {
    private final SortParams params;
    private final Ops ops;
    private final long runStartNanos;

    private volatile long startedAtNanos;
    private volatile long finishedAtNanos;
    private volatile Throwable failure;

    public SortingThread(SharedData data, SortParams params, long runStartNanos) {
        super(params.threadName);
        this.params = params;
        this.ops = new Ops(data, params.threadName);
        this.runStartNanos = runStartNanos;
    }

    @Override
    public void run() {
        startedAtNanos = System.nanoTime() - runStartNanos;
        try {
            params.algorithm.sort(ops, params.startIndex, params.endIndex);
        } catch (Throwable t) {
            failure = t;
        } finally {
            finishedAtNanos = System.nanoTime() - runStartNanos;
        }
    }

    public SortParams params() { return params; }
    public Ops ops() { return ops; }
    public long startedAtNanos() { return startedAtNanos; }
    public long finishedAtNanos() { return finishedAtNanos; }
    public Throwable failure() { return failure; }
}
