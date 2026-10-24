package sorting.core;

/**
 * The two "global" arrays described in the textbook project:
 * <ul>
 *   <li>{@code list}   - the original unsorted integers; the two sorting threads
 *                        sort their halves of this array in place,</li>
 *   <li>{@code result} - a second array of the same size into which the merging
 *                        thread merges the two sorted halves.</li>
 * </ul>
 * One instance is shared by all worker threads of a run (the Java equivalent of
 * C global arrays). Each sorting thread only touches its own disjoint index range,
 * and the merging thread starts only after both sorters were joined, so no locking
 * is needed: Thread.join() establishes the required happens-before ordering.
 */
public final class SharedData {
    public final int[] list;
    public final int[] result;

    public SharedData(int[] input) {
        this.list = input.clone();
        this.result = new int[input.length];
    }

    public int size() {
        return list.length;
    }
}
