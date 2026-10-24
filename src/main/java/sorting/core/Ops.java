package sorting.core;

import sorting.trace.TraceEvent;
import sorting.trace.TraceRecorder;

/**
 * Per-thread access object to the shared arrays. Every algorithm step
 * (compare / swap / write) goes through this class so that it can be counted
 * and, when a {@link TraceRecorder} is attached, recorded as an event.
 * One instance belongs to exactly one thread, so the counters need no locking.
 */
public final class Ops {
    private final int[] list;
    private final int[] result;
    private final String threadName;
    private final TraceRecorder trace;   // null = tracing off

    private long compares;
    private long swaps;
    private long writes;

    public Ops(SharedData data, String threadName, TraceRecorder trace) {
        this.list = data.list;
        this.result = data.result;
        this.threadName = threadName;
        this.trace = trace;
    }

    public String threadName() { return threadName; }

    /** Marker for an unused event field. */
    static final long NONE = TraceEvent.NONE;

    /** Records a lifecycle event of this thread (no-op when tracing is off). */
    void event(String type, long a, long b, long c) {
        if (trace != null) trace.record(threadName, type, a, b, c);
    }

    // ---------- operations used by the sorting algorithms (on list) ----------

    /** Plain read without counting (used e.g. to copy into a private buffer). */
    public int get(int i) {
        return list[i];
    }

    /** Compares list[i] with list[j]; returns <0, 0 or >0. */
    public int compare(int i, int j) {
        compares++;
        int r = Integer.compare(list[i], list[j]);
        if (trace != null) trace.record(threadName, "CMP", i, j, r);
        return r;
    }

    /** Compares list[i] with a value held by the algorithm (e.g. a pivot). */
    public int compareToValue(int i, int value) {
        compares++;
        int r = Integer.compare(list[i], value);
        if (trace != null) trace.record(threadName, "CMPV", i, value, r);
        return r;
    }

    /**
     * Compares two values taken from positions i and j of the algorithm's
     * private buffer (merge sort). Positions are kept for visualisation.
     */
    public int compareValues(int i, int j, int x, int y) {
        compares++;
        int r = Integer.compare(x, y);
        if (trace != null) trace.record(threadName, "CMPA", i, j, r);
        return r;
    }

    public void swap(int i, int j) {
        swaps++;
        int t = list[i];
        list[i] = list[j];
        list[j] = t;
        if (trace != null) trace.record(threadName, "SWP", i, j);
    }

    public void set(int i, int value) {
        writes++;
        list[i] = value;
        if (trace != null) trace.record(threadName, "SET", i, value);
    }

    // ---------- operations used by the merging thread ----------

    /** Merging thread: compares list[i] (left run) with list[j] (right run). */
    public int mergeCompare(int i, int j) {
        compares++;
        int r = Integer.compare(list[i], list[j]);
        if (trace != null) trace.record(threadName, "MCMP", i, j, r);
        return r;
    }

    /** Merging thread: result[k] = list[src]. */
    public void mergeWrite(int k, int src) {
        writes++;
        result[k] = list[src];
        if (trace != null) trace.record(threadName, "MW", k, src, result[k]);
    }

    // ---------- statistics ----------

    public long compares() { return compares; }
    public long swaps() { return swaps; }
    public long writes() { return writes; }
}
