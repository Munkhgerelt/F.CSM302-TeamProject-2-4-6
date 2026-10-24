package sorting.core;

import sorting.trace.TraceRecorder;

/**
 * The third thread: merges the two sorted halves list[leftStart, mid) and
 * list[mid, end) into the second global array result[leftStart, end).
 * Classic two-pointer merge; ties take the left element (stable).
 */
public final class MergingThread extends Thread {
    private final MergeParams params;
    private final Ops ops;
    private final long runStartNanos;

    private volatile long startedAtNanos;
    private volatile long finishedAtNanos;
    private volatile Throwable failure;

    public MergingThread(SharedData data, MergeParams params, long runStartNanos, TraceRecorder trace) {
        this(data, params, runStartNanos, trace, null);
    }

    public MergingThread(SharedData data, MergeParams params, long runStartNanos, TraceRecorder trace, StepGate gate) {
        super(params.threadName);
        this.params = params;
        this.ops = new Ops(data, params.threadName, trace, gate);
        this.runStartNanos = runStartNanos;
    }

    @Override
    public void run() {
        startedAtNanos = System.nanoTime() - runStartNanos;
        try {
            ops.event("M_START", params.leftStart, params.mid, params.end);
            merge();
            ops.event("M_DONE", params.leftStart, params.end, Ops.NONE);
        } catch (Throwable t) {
            failure = t;
        } finally {
            finishedAtNanos = System.nanoTime() - runStartNanos;
        }
    }

    private void merge() {
        int i = params.leftStart;
        int j = params.mid;
        int k = params.leftStart;
        while (i < params.mid && j < params.end) {
            if (ops.mergeCompare(i, j) <= 0) {
                ops.mergeWrite(k++, i++);
            } else {
                ops.mergeWrite(k++, j++);
            }
        }
        while (i < params.mid) ops.mergeWrite(k++, i++);
        while (j < params.end) ops.mergeWrite(k++, j++);
    }

    public MergeParams params() { return params; }
    public Ops ops() { return ops; }
    public long startedAtNanos() { return startedAtNanos; }
    public long finishedAtNanos() { return finishedAtNanos; }
    public Throwable failure() { return failure; }
}
