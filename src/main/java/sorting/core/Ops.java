package sorting.core;

/**
 * Per-thread access object to the shared arrays. Every algorithm step
 * (compare / swap / write) goes through this class so that it can be counted.
 * One instance belongs to exactly one thread, so the counters need no locking.
 */
public final class Ops {
    private final int[] list;
    private final int[] result;
    private final String threadName;

    private long compares;
    private long swaps;
    private long writes;

    public Ops(SharedData data, String threadName) {
        this.list = data.list;
        this.result = data.result;
        this.threadName = threadName;
    }

    public String threadName() { return threadName; }

    // ---------- operations used by the sorting algorithms (on list) ----------

    /** Plain read without counting (used e.g. to copy into a private buffer). */
    public int get(int i) {
        return list[i];
    }

    /** Compares list[i] with list[j]; returns <0, 0 or >0. */
    public int compare(int i, int j) {
        compares++;
        return Integer.compare(list[i], list[j]);
    }

    /** Compares list[i] with a value held by the algorithm (e.g. a pivot). */
    public int compareToValue(int i, int value) {
        compares++;
        return Integer.compare(list[i], value);
    }

    /**
     * Compares two values taken from positions i and j of the algorithm's
     * private buffer (merge sort). Positions are kept for visualisation.
     */
    public int compareValues(int i, int j, int x, int y) {
        compares++;
        return Integer.compare(x, y);
    }

    public void swap(int i, int j) {
        swaps++;
        int t = list[i];
        list[i] = list[j];
        list[j] = t;
    }

    public void set(int i, int value) {
        writes++;
        list[i] = value;
    }

    // ---------- operations used by the merging thread ----------

    /** Merging thread: compares list[i] (left run) with list[j] (right run). */
    public int mergeCompare(int i, int j) {
        compares++;
        return Integer.compare(list[i], list[j]);
    }

    /** Merging thread: result[k] = list[src]. */
    public void mergeWrite(int k, int src) {
        writes++;
        result[k] = list[src];
    }

    // ---------- statistics ----------

    public long compares() { return compares; }
    public long swaps() { return swaps; }
    public long writes() { return writes; }
}
