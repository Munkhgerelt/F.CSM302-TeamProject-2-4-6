package sorting.trace;

/**
 * One recorded step of a real thread.
 * <p>Event types and the meaning of a / b / c:</p>
 * <pre>
 *  RUN_START  parent  a=n, b=mid (first index of the right half)
 *  START      parent  note=thread that was started
 *  JOIN       parent  note=thread whose join() returned
 *  T_START    sorter  a=startIndex, b=endIndex (exclusive)
 *  CMP        sorter  compare list[a] with list[b], c=sign(list[a]-list[b])
 *  CMPV       sorter  compare list[a] with value b (quick-sort pivot), c=sign
 *  CMPA       sorter  merge-sort compare of buffered values from positions a and b, c=sign
 *  SWP        sorter  swap list[a] and list[b]
 *  SET        sorter  list[a] = b (merge-sort write-back)
 *  T_DONE     sorter  sublist [a, b) is sorted
 *  M_START    merger  a=0, b=mid, c=n
 *  MCMP       merger  compare list[a] (left run) with list[b] (right run), c=sign
 *  MW         merger  result[a] = list[b], c=value written
 *  M_DONE     merger  result[] complete
 *  RUN_END    parent  a=1 if result verified sorted else 0
 * </pre>
 * {@code t} is nanoseconds since the run started (System.nanoTime based),
 * {@code seq} is a global sequence number taken when the event was recorded.
 * Fields are {@code long} so that the "no value" marker ({@link #NONE}) can never
 * collide with a real int value such as Integer.MIN_VALUE.
 */
public final class TraceEvent {
    public static final long NONE = Long.MIN_VALUE;

    public final long seq;
    public final long t;
    public final String thread;
    public final String type;
    public final long a;
    public final long b;
    public final long c;
    public final String note;

    public TraceEvent(long seq, long t, String thread, String type, long a, long b, long c, String note) {
        this.seq = seq;
        this.t = t;
        this.thread = thread;
        this.type = type;
        this.a = a;
        this.b = b;
        this.c = c;
        this.note = note;
    }
}
