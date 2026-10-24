package sorting.core;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lockstep control for the real worker threads. Before every compare / swap /
 * write a worker thread must take one permit; while no permit is available the
 * thread is really blocked inside {@link Semaphore#acquire()} (its
 * {@link Thread#getState()} is WAITING). The UI's "Step" button releases permits.
 * The semaphore is fair (FIFO): a released permit goes to the thread that has been
 * waiting longest, so when both sorters are waiting they advance alternately
 * (L, R, L, R ...). With a non-fair semaphore the order would be up to the scheduler.
 */
public final class StepGate {
    /** Thrown inside a worker thread when the session is cancelled. */
    public static final class CancelledException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CancelledException() {
            super("lockstep session cancelled");
        }
    }

    private final Semaphore permits = new Semaphore(0, true);
    private final AtomicLong consumed = new AtomicLong();
    private volatile boolean cancelled;

    /** Called by a worker thread before each gated operation. */
    public void pass() {
        if (cancelled) throw new CancelledException();
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancelledException();
        }
        if (cancelled) throw new CancelledException();
        consumed.incrementAndGet();
    }

    /** Lets {@code count} more operations happen. */
    public void release(int count) {
        if (count > 0) permits.release(count);
    }

    /** Number of operations that have passed the gate so far. */
    public long consumed() {
        return consumed.get();
    }

    /** Number of threads currently blocked waiting for a permit. */
    public int waitingThreads() {
        return permits.getQueueLength();
    }

    public void cancel() {
        cancelled = true;
        permits.release(Integer.MAX_VALUE / 2);   // wake everybody so they can exit
    }

    public boolean cancelled() {
        return cancelled;
    }
}
