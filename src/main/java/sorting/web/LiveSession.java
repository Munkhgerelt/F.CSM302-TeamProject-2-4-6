package sorting.web;

import sorting.algo.Algorithm;
import sorting.core.MultithreadedSorter;
import sorting.core.RunOptions;
import sorting.core.SortResult;
import sorting.core.StepGate;
import sorting.trace.Json;
import sorting.trace.TraceEvent;
import sorting.trace.TraceRecorder;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One lockstep run: the real parent + 2 sorting threads + merging thread are
 * running, but every compare/swap/write waits on a {@link StepGate} permit
 * that the browser grants with the "Step" button.
 */
final class LiveSession {
    private static final AtomicLong IDS = new AtomicLong();

    final String id = "s" + IDS.incrementAndGet();
    final int[] input;
    final Algorithm left;
    final Algorithm right;
    final String source;
    final List<String> notes;
    final StepGate gate = new StepGate();
    final TraceRecorder trace = new TraceRecorder(System.nanoTime(), TraceRecorder.DEFAULT_MAX_EVENTS, true);
    final List<Thread> workers = new CopyOnWriteArrayList<>();
    final Thread parent;

    private volatile SortResult result;
    private volatile String error;
    private volatile long lastTouched = System.currentTimeMillis();

    LiveSession(int[] input, Algorithm left, Algorithm right, String source, List<String> notes) {
        this.input = input;
        this.left = left;
        this.right = right;
        this.source = source;
        this.notes = notes;
        this.parent = new Thread(this::runParent, MultithreadedSorter.PARENT_NAME);
        this.parent.setDaemon(true);
    }

    void start() {
        parent.start();
    }

    private void runParent() {
        try {
            result = MultithreadedSorter.sort(input, left.key, left.create(), right.key, right.create(),
                    new RunOptions(trace, gate, workers::add));
        } catch (InterruptedException e) {
            error = "Цуцлагдсан";
        } catch (RuntimeException e) {
            error = gate.cancelled() ? "Цуцлагдсан" : "Алдаа: " + e.getMessage();
        }
    }

    boolean finished() {
        return !parent.isAlive();
    }

    void touch() {
        lastTouched = System.currentTimeMillis();
    }

    long idleMillis() {
        return System.currentTimeMillis() - lastTouched;
    }

    /**
     * Grants {@code count} operations and waits (up to timeoutMs) until they
     * have really been executed by the worker threads, or the run is over.
     */
    void step(int count, long timeoutMs) throws InterruptedException {
        touch();
        long target = gate.consumed() + count;
        gate.release(count);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (gate.consumed() < target && !finished() && System.currentTimeMillis() < deadline) {
            Thread.sleep(1);
        }
        // let lifecycle events that follow the last operation (T_DONE, JOIN, ...) arrive
        if (!finished()) Thread.sleep(2);
    }

    void cancel() {
        gate.cancel();
        parent.interrupt();
        for (Thread t : workers) t.interrupt();
    }

    /** JSON with the events after index {@code from} and the live Java thread states. */
    String stateJson(int from) {
        touch();
        List<TraceEvent> events = trace.eventsFrom(from);
        StringBuilder sb = new StringBuilder(256 + events.size() * 64);
        sb.append("{\"ok\":true,\"id\":").append(Json.str(id))
          .append(",\"from\":").append(from)
          .append(",\"next\":").append(from + events.size())
          .append(",\"finished\":").append(finished())
          .append(",\"consumed\":").append(gate.consumed())
          .append(",\"waitingForPermit\":").append(gate.waitingThreads())
          .append(",\"error\":").append(Json.str(error))
          .append(",\"threads\":[");
        sb.append("{\"name\":").append(Json.str(parent.getName()))
          .append(",\"tid\":").append(parent.getId())
          .append(",\"javaState\":").append(Json.str(parent.getState().name())).append('}');
        for (Thread t : workers) {
            sb.append(",{\"name\":").append(Json.str(t.getName()))
              .append(",\"tid\":").append(t.getId())
              .append(",\"javaState\":").append(Json.str(t.getState().name())).append('}');
        }
        sb.append("],\"events\":[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) sb.append(',');
            Json.event(sb, events.get(i));
        }
        sb.append(']');
        SortResult r = result;
        if (r != null) {
            sb.append(",\"result\":");
            Json.intArray(sb, r.result);
            sb.append(",\"sorted\":").append(isSorted(r.result));
        }
        return sb.append('}').toString();
    }

    /** Static description sent once when the session starts. */
    String startJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":true,\"id\":").append(Json.str(id))
          .append(",\"source\":").append(Json.str(source))
          .append(",\"n\":").append(input.length)
          .append(",\"mid\":").append(MultithreadedSorter.splitIndex(input.length))
          .append(",\"algorithms\":{\"left\":").append(Json.str(left.key))
          .append(",\"right\":").append(Json.str(right.key)).append("},\"input\":");
        Json.intArray(sb, input);
        sb.append(",\"notes\":[");
        for (int i = 0; i < notes.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(Json.str(notes.get(i)));
        }
        return sb.append("]}").toString();
    }

    private static boolean isSorted(int[] a) {
        for (int i = 1; i < a.length; i++) if (a[i - 1] > a[i]) return false;
        return true;
    }
}
