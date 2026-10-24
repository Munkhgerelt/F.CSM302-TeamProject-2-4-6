package sorting.trace;

import sorting.core.MergingThread;
import sorting.core.SortResult;
import sorting.core.SortingThread;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serialises a traced run into the JSON trace format "ii-4-6-trace/1":
 * metadata, input/result arrays, per-thread summary, timings and the full
 * ordered event list (one event per line).
 */
public final class TraceJson {
    public static final String FORMAT = "ii-4-6-trace/1";

    private TraceJson() { }

    /**
     * @param untracedTotalNanos wall time of an identical run without tracing, or -1 if not measured
     */
    public static String toJson(SortResult r, TraceRecorder trace, long untracedTotalNanos) {
        return toJson(r, trace, untracedTotalNanos, 1);
    }

    /** @param untracedRuns how many measured runs the untraced (median) time is based on */
    public static String toJson(SortResult r, TraceRecorder trace, long untracedTotalNanos, int untracedRuns) {
        List<TraceEvent> events = trace.snapshot();
        StringBuilder sb = new StringBuilder(64 + events.size() * 64);
        sb.append("{\n");
        sb.append("\"format\":").append(Json.str(FORMAT)).append(",\n");
        sb.append("\"createdAt\":").append(Json.str(Instant.now().toString())).append(",\n");
        sb.append("\"n\":").append(r.original.length).append(",\"mid\":").append(r.mid).append(",\n");
        sb.append("\"algorithms\":{\"left\":").append(Json.str(r.left.params().algorithmName))
          .append(",\"right\":").append(Json.str(r.right.params().algorithmName)).append("},\n");
        sb.append("\"input\":");
        Json.intArray(sb, r.original);
        sb.append(",\n\"result\":");
        Json.intArray(sb, r.result);
        sb.append(",\n\"sorted\":").append(isSorted(r.result)).append(",\n");
        sb.append("\"threads\":[\n");
        sorter(sb, r.left);
        sb.append(",\n");
        sorter(sb, r.right);
        sb.append(",\n");
        merger(sb, r.merger);
        sb.append("\n],\n");
        sb.append("\"timing\":{\"tracedSortPhaseNs\":").append(r.sortPhaseNanos)
          .append(",\"tracedTotalNs\":").append(r.totalNanos)
          .append(",\"untracedTotalNs\":").append(untracedTotalNanos)
          .append(",\"untracedRuns\":").append(untracedRuns).append("},\n");
        sb.append("\"eventCounts\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : countByType(events).entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(Json.str(e.getKey())).append(':').append(e.getValue());
        }
        sb.append("},\n");
        sb.append("\"eventCount\":").append(events.size())
          .append(",\"truncated\":").append(trace.truncated()).append(",\n");
        sb.append("\"events\":[\n");
        for (int i = 0; i < events.size(); i++) {
            Json.event(sb, events.get(i));
            sb.append(i + 1 < events.size() ? ",\n" : "\n");
        }
        sb.append("]\n}\n");
        return sb.toString();
    }

    public static Map<String, Integer> countByType(List<TraceEvent> events) {
        Map<String, Integer> m = new TreeMap<>();
        for (TraceEvent e : events) m.merge(e.type, 1, Integer::sum);
        return m;
    }

    private static void sorter(StringBuilder sb, SortingThread t) {
        sb.append("{\"name\":").append(Json.str(t.getName()))
          .append(",\"role\":\"sort\",\"tid\":").append(t.getId())
          .append(",\"start\":").append(t.params().startIndex)
          .append(",\"end\":").append(t.params().endIndex)
          .append(",\"algorithm\":").append(Json.str(t.params().algorithmName))
          .append(",\"startedNs\":").append(t.startedAtNanos())
          .append(",\"finishedNs\":").append(t.finishedAtNanos())
          .append(",\"compares\":").append(t.ops().compares())
          .append(",\"swaps\":").append(t.ops().swaps())
          .append(",\"writes\":").append(t.ops().writes()).append('}');
    }

    private static void merger(StringBuilder sb, MergingThread t) {
        sb.append("{\"name\":").append(Json.str(t.getName()))
          .append(",\"role\":\"merge\",\"tid\":").append(t.getId())
          .append(",\"start\":").append(t.params().leftStart)
          .append(",\"mid\":").append(t.params().mid)
          .append(",\"end\":").append(t.params().end)
          .append(",\"startedNs\":").append(t.startedAtNanos())
          .append(",\"finishedNs\":").append(t.finishedAtNanos())
          .append(",\"compares\":").append(t.ops().compares())
          .append(",\"writes\":").append(t.ops().writes()).append('}');
    }

    private static boolean isSorted(int[] a) {
        for (int i = 1; i < a.length; i++) if (a[i - 1] > a[i]) return false;
        return true;
    }
}
