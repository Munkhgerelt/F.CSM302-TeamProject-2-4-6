package sorting.trace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, lock-free event recorder shared by all threads of one run.
 * Each worker thread appends its own events; the global sequence number
 * gives a total order of recording. The two sorting threads touch disjoint
 * index ranges and the merger starts only after both were joined, so
 * replaying events in seq order reproduces the real array states.
 */
public final class TraceRecorder {
    /** Safety cap so that a large O(n^2) run cannot exhaust memory. */
    public static final int DEFAULT_MAX_EVENTS = 2_000_000;

    private final long t0;
    private final int maxEvents;
    private final AtomicLong seq = new AtomicLong();
    private final ConcurrentLinkedQueue<TraceEvent> events = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean truncated = new AtomicBoolean(false);
    /**
     * Ordered mode (lockstep sessions): seq assignment and append happen under one
     * lock, so the list order equals seq order and a reader can safely ask for
     * "everything after index i" while the threads are still running.
     */
    private final boolean ordered;
    private final List<TraceEvent> orderedEvents = new ArrayList<>();

    public TraceRecorder(long t0) {
        this(t0, DEFAULT_MAX_EVENTS);
    }

    public TraceRecorder(long t0, int maxEvents) {
        this(t0, maxEvents, false);
    }

    public TraceRecorder(long t0, int maxEvents, boolean ordered) {
        this.t0 = t0;
        this.maxEvents = maxEvents;
        this.ordered = ordered;
    }

    public long t0() { return t0; }

    public void record(String thread, String type, long a, long b, long c, String note) {
        if (ordered) {
            synchronized (orderedEvents) {
                long s = seq.getAndIncrement();
                if (s >= maxEvents) {
                    truncated.set(true);
                    return;
                }
                orderedEvents.add(new TraceEvent(s, System.nanoTime() - t0, thread, type, a, b, c, note));
            }
            return;
        }
        long s = seq.getAndIncrement();
        if (s >= maxEvents) {
            truncated.set(true);
            return;
        }
        events.add(new TraceEvent(s, System.nanoTime() - t0, thread, type, a, b, c, note));
    }

    public void record(String thread, String type, long a, long b, long c) {
        record(thread, type, a, b, c, null);
    }

    public void record(String thread, String type, long a, long b) {
        record(thread, type, a, b, TraceEvent.NONE, null);
    }

    public void note(String thread, String type, String note) {
        record(thread, type, TraceEvent.NONE, TraceEvent.NONE, TraceEvent.NONE, note);
    }

    public boolean truncated() { return truncated.get(); }

    /** Number of events actually stored. */
    public int size() {
        if (ordered) {
            synchronized (orderedEvents) {
                return orderedEvents.size();
            }
        }
        return events.size();
    }

    /** Ordered mode only: copy of the events with list index >= from. */
    public List<TraceEvent> eventsFrom(int from) {
        if (!ordered) throw new IllegalStateException("eventsFrom requires ordered mode");
        synchronized (orderedEvents) {
            int start = Math.max(0, Math.min(from, orderedEvents.size()));
            return new ArrayList<>(orderedEvents.subList(start, orderedEvents.size()));
        }
    }

    /** All recorded events ordered by sequence number. */
    public List<TraceEvent> snapshot() {
        if (ordered) return eventsFrom(0);
        List<TraceEvent> list = new ArrayList<>(events);
        list.sort(Comparator.comparingLong(e -> e.seq));
        return list;
    }
}
