package sorting.core;

/** Everything the parent thread knows after joining all worker threads. */
public final class SortResult {
    public final int[] original;
    public final int[] result;
    public final int mid;
    public final SortingThread left;
    public final SortingThread right;
    public final MergingThread merger;
    /** Time from run start until both sorters were joined. */
    public final long sortPhaseNanos;
    /** Time from run start until the merger was joined. */
    public final long totalNanos;

    SortResult(int[] original, int[] result, int mid,
               SortingThread left, SortingThread right, MergingThread merger,
               long sortPhaseNanos, long totalNanos) {
        this.original = original;
        this.result = result;
        this.mid = mid;
        this.left = left;
        this.right = right;
        this.merger = merger;
        this.sortPhaseNanos = sortPhaseNanos;
        this.totalNanos = totalNanos;
    }
}
