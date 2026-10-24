package sorting.core;

/**
 * Parameters passed to the merging thread: the left sorted run is
 * list[leftStart, mid) and the right one is list[mid, end).
 * The merged output is written to result[leftStart, end).
 */
public final class MergeParams {
    public final String threadName;
    public final int leftStart;
    public final int mid;
    public final int end;

    public MergeParams(String threadName, int leftStart, int mid, int end) {
        if (leftStart < 0 || mid < leftStart || end < mid) {
            throw new IllegalArgumentException("invalid merge bounds " + leftStart + "/" + mid + "/" + end);
        }
        this.threadName = threadName;
        this.leftStart = leftStart;
        this.mid = mid;
        this.end = end;
    }
}
